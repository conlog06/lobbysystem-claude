package de.crafterteam.lobbysystem.social;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.ConfigManager;
import de.crafterteam.lobbysystem.storage.PlayerDataManager.Profile;
import de.crafterteam.lobbysystem.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static de.crafterteam.lobbysystem.storage.PlayerDataManager.profiles;

/** Party-System über MySQL – Mitglieder werden beim Serverwechsel des Leaders mitgenommen. */
public final class PartyManager {

    private static final String P = "party_prefix";
    private final LobbySystem plugin;

    public PartyManager(LobbySystem plugin) {
        this.plugin = plugin;
    }

    public record Party(String id, UUID leader, List<Profile> members) {
        public boolean isLeader(UUID uuid) {
            return leader.equals(uuid);
        }
    }

    private ConfigManager c() {
        return plugin.configs();
    }

    private <T> CompletableFuture<T> db(de.crafterteam.lobbysystem.storage.Database.SqlTask<T> task) {
        return plugin.database().submit(task);
    }

    private void sync(Runnable r) {
        plugin.messenger().sync(r);
    }

    private void msg(Player p, String key, String... ph) {
        sync(() -> {
            if (p.isOnline()) c().sendP(p, P, key, ph);
        });
    }

    private int max() {
        return Math.max(2, c().cfg().social.partyMax);
    }

    // ---------------------------------------------------------------- DB-Helfer

    private static void exec(Connection con, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        }
    }

    static Party load(Connection con, UUID member) throws SQLException {
        String id = null;
        try (PreparedStatement ps = con.prepareStatement("select party_id from ls_party_members where uuid = ?")) {
            ps.setString(1, member.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) id = rs.getString(1);
            }
        }
        if (id == null) return null;
        UUID leader = null;
        try (PreparedStatement ps = con.prepareStatement("select leader from ls_parties where id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) leader = UUID.fromString(rs.getString(1));
            }
        }
        if (leader == null) {
            exec(con, "delete from ls_party_members where party_id = ?", id);
            return null;
        }
        List<UUID> ids = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("select uuid from ls_party_members where party_id = ? order by joined")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(UUID.fromString(rs.getString(1)));
            }
        }
        Map<UUID, Profile> profiles = profiles(con, ids);
        List<Profile> members = new ArrayList<>();
        for (UUID u : ids) if (profiles.containsKey(u)) members.add(profiles.get(u));
        return new Party(id, leader, members);
    }

    public CompletableFuture<Party> party(UUID uuid) {
        return db(con -> load(con, uuid));
    }

    /** Nachricht an alle Party-Mitglieder (serverübergreifend). */
    private void broadcast(Party party, Component message) {
        sync(() -> {
            for (Profile m : party.members()) plugin.messenger().send(m.name(), message);
        });
    }

    // ---------------------------------------------------------------- Aktionen

    public void invite(Player p, String name) {
        if (plugin.proxy().forward(p, "party", "party", "invite " + name)) return;
        if (p.getName().equalsIgnoreCase(name)) return;
        withProfile(p, name, t -> db(con -> {
            Party party = load(con, p.getUniqueId());
            if (party == null) {
                String id = UUID.randomUUID().toString();
                long now = System.currentTimeMillis();
                exec(con, "insert into ls_parties (id, leader, created) values (?, ?, ?)", id, p.getUniqueId().toString(), now);
                exec(con, "insert into ls_party_members (uuid, party_id, joined) values (?, ?, ?)", p.getUniqueId().toString(), id, now);
                party = load(con, p.getUniqueId());
            }
            if (!party.isLeader(p.getUniqueId())) {
                msg(p, "party_not_leader");
                return null;
            }
            if (party.members().size() >= max()) {
                msg(p, "party_full", "max", Integer.toString(max()));
                return null;
            }
            Party other = load(con, t.uuid());
            if (other != null && other.members().size() > 1) {
                msg(p, "party_target_in_party", "name", t.name());
                return null;
            }
            if (!t.setting("party")) {
                msg(p, "party_invites_disabled", "name", t.name());
                return null;
            }
            exec(con, "delete from ls_party_invites where party_id = ? and uuid = ?", party.id(), t.uuid().toString());
            exec(con, "insert into ls_party_invites (party_id, uuid, expires) values (?, ?, ?)", party.id(), t.uuid().toString(),
                    System.currentTimeMillis() + c().cfg().social.inviteSeconds * 1000L);
            msg(p, "party_invite_sent", "name", t.name());
            sync(() -> plugin.messenger().send(t.name(), c().msgP(P, "party_invite_received", "name", p.getName())));
            return null;
        }));
    }

    public void accept(Player p, String leaderName) {
        if (plugin.proxy().forward(p, "party", "party", "accept " + leaderName)) return;
        withProfile(p, leaderName, leader -> db(con -> {
            Party own = load(con, p.getUniqueId());
            if (own != null) {
                if (own.members().size() > 1) {
                    msg(p, "party_already_in");
                    return null;
                }
                disband(con, own.id()); // eigene leere Party automatisch auflösen
            }
            Party party = load(con, leader.uuid());
            if (party == null || !inviteValid(con, party.id(), p.getUniqueId())) {
                msg(p, "party_no_invite", "name", leader.name());
                return null;
            }
            if (party.members().size() >= max()) {
                msg(p, "party_full", "max", Integer.toString(max()));
                return null;
            }
            exec(con, "delete from ls_party_invites where party_id = ? and uuid = ?", party.id(), p.getUniqueId().toString());
            exec(con, "insert into ls_party_members (uuid, party_id, joined) values (?, ?, ?)", p.getUniqueId().toString(), party.id(), System.currentTimeMillis());
            broadcast(load(con, p.getUniqueId()), c().msgP(P, "party_joined", "name", p.getName()));
            return null;
        }));
    }

    private static boolean inviteValid(Connection con, String partyId, UUID uuid) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("select expires from ls_party_invites where party_id = ? and uuid = ?")) {
            ps.setString(1, partyId);
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > System.currentTimeMillis();
            }
        }
    }

    public void deny(Player p, String leaderName) {
        if (plugin.proxy().forward(p, "party", "party", "deny " + leaderName)) return;
        withProfile(p, leaderName, leader -> db(con -> {
            Party party = load(con, leader.uuid());
            if (party == null || !inviteValid(con, party.id(), p.getUniqueId())) {
                msg(p, "party_no_invite", "name", leader.name());
                return null;
            }
            exec(con, "delete from ls_party_invites where party_id = ? and uuid = ?", party.id(), p.getUniqueId().toString());
            return null;
        }));
    }

    public void leave(Player p) {
        if (plugin.proxy().forward(p, "party", "party", "leave")) return;
        db(con -> {
            Party party = load(con, p.getUniqueId());
            if (party == null) {
                msg(p, "party_not_in");
                return null;
            }
            removeMember(con, party, p.getUniqueId(), p.getName(), "party_left");
            return null;
        });
    }

    private void removeMember(Connection con, Party party, UUID uuid, String name, String key) throws SQLException {
        broadcast(party, c().msgP(P, key, "name", name));
        exec(con, "delete from ls_party_members where uuid = ?", uuid.toString());
        List<Profile> rest = party.members().stream().filter(m -> !m.uuid().equals(uuid)).toList();
        if (rest.size() < 2) {
            // Party mit nur noch einem Mitglied auflösen
            if (!rest.isEmpty()) sync(() -> plugin.messenger().send(rest.get(0).name(), c().msgP(P, "party_disbanded")));
            disband(con, party.id());
            return;
        }
        if (party.isLeader(uuid)) {
            Profile next = rest.get(0);
            exec(con, "update ls_parties set leader = ? where id = ?", next.uuid().toString(), party.id());
            broadcast(load(con, next.uuid()), c().msgP(P, "party_promoted", "name", next.name()));
        }
    }

    private static void disband(Connection con, String id) throws SQLException {
        exec(con, "delete from ls_party_members where party_id = ?", id);
        exec(con, "delete from ls_party_invites where party_id = ?", id);
        exec(con, "delete from ls_parties where id = ?", id);
    }

    public void kick(Player p, String name) {
        if (plugin.proxy().forward(p, "party", "party", "kick " + name)) return;
        leaderAction(p, party -> {
            Profile target = find(party, name);
            if (target == null) {
                msg(p, "party_not_member", "name", name);
                return;
            }
            if (target.uuid().equals(p.getUniqueId())) return;
            db(con -> {
                removeMember(con, party, target.uuid(), target.name(), "party_kicked");
                return null;
            });
        });
    }

    public void promote(Player p, String name) {
        if (plugin.proxy().forward(p, "party", "party", "promote " + name)) return;
        leaderAction(p, party -> {
            Profile target = find(party, name);
            if (target == null) {
                msg(p, "party_not_member", "name", name);
                return;
            }
            db(con -> {
                exec(con, "update ls_parties set leader = ? where id = ?", target.uuid().toString(), party.id());
                broadcast(party, c().msgP(P, "party_promoted", "name", target.name()));
                return null;
            });
        });
    }

    public void disband(Player p) {
        if (plugin.proxy().forward(p, "party", "party", "disband")) return;
        leaderAction(p, party -> db(con -> {
            broadcast(party, c().msgP(P, "party_disbanded"));
            disband(con, party.id());
            return null;
        }));
    }

    public void list(Player p) {
        if (plugin.proxy().forward(p, "party", "party", "list")) return;
        party(p.getUniqueId()).thenAccept(party -> sync(() -> {
            if (party == null) {
                c().sendP(p, P, "party_not_in");
                return;
            }
            c().sendP(p, P, "party_list_header", "count", Integer.toString(party.members().size()), "max", Integer.toString(max()));
            for (Profile m : party.members()) {
                p.sendMessage(Text.parse(c().raw("clan_member_entry"), ConfigManager.map(
                        "status", m.online() ? "<green>●" : "<red>●", "name", m.name(),
                        "role", party.isLeader(m.uuid()) ? "Leader" : "Mitglied")));
            }
        }));
    }

    public void chat(Player p, String message) {
        if (plugin.proxy().forward(p, "party", "party", "chat " + message)) return;
        party(p.getUniqueId()).thenAccept(party -> {
            if (party == null) {
                msg(p, "party_not_in");
                return;
            }
            broadcast(party, Text.parse(c().cfg().social.partyChatFormat, ConfigManager.map("name", p.getName(), "message", message)));
        });
    }

    /** /party warp – alle Mitglieder zum Leader holen. */
    public void warp(Player p) {
        if (plugin.proxy().forward(p, "party", "party", "warp")) return;
        leaderAction(p, party -> sync(() -> pull(p, party, c().cfg().proxyServerName, true)));
    }

    /** Vom LobbyManager beim Serverwechsel des Leaders aufgerufen. */
    public void onConnect(Player leader, String server) {
        if (!c().cfg().social.party || !c().cfg().social.partyFollow) return;
        if (plugin.proxy().active("party")) return; // Party folgt über das BungeeSystem
        party(leader.getUniqueId()).thenAccept(party -> {
            if (party == null || !party.isLeader(leader.getUniqueId())) return;
            sync(() -> pull(leader, party, server, false));
        });
    }

    private void pull(Player leader, Party party, String server, boolean teleportLocal) {
        c().sendP(leader, P, "party_warp", "server", server);
        for (Profile m : party.members()) {
            if (m.uuid().equals(leader.getUniqueId()) || !m.online()) continue;
            Player local = Bukkit.getPlayer(m.uuid());
            if (local != null && teleportLocal) plugin.lobby().teleport(local, leader.getLocation(), true);
            else if (local != null) plugin.lobby().connect(local, server, false);
            else plugin.messenger().connectOther(m.name(), server);
        }
    }

    /** Alte, verwaiste Parties aufräumen (z. B. nach Server-Absturz). */
    public void cleanup() {
        if (plugin.proxy().active("party")) return; // räumt das BungeeSystem auf
        db(con -> {
            exec(con, "delete from ls_party_invites where expires < ?", System.currentTimeMillis());
            exec(con, "delete from ls_party_members where party_id not in (select id from ls_parties)");
            exec(con, "delete from ls_parties where created < ? and id not in (select party_id from ls_party_members m "
                    + "join ls_players p on p.uuid = m.uuid where p.online_server <> '')", System.currentTimeMillis() - 6 * 3_600_000L);
            exec(con, "delete from ls_party_members where party_id not in (select id from ls_parties)");
            return null;
        });
    }

    // ---------------------------------------------------------------- Helfer

    private static Profile find(Party party, String name) {
        for (Profile m : party.members()) if (m.name().equalsIgnoreCase(name)) return m;
        return null;
    }

    private void leaderAction(Player p, Consumer<Party> action) {
        party(p.getUniqueId()).thenAccept(party -> {
            if (party == null) msg(p, "party_not_in");
            else if (!party.isLeader(p.getUniqueId())) msg(p, "party_not_leader");
            else action.accept(party);
        });
    }

    private void withProfile(Player p, String name, Consumer<Profile> action) {
        plugin.data().lookup(name).thenAccept(opt -> {
            if (opt.isEmpty()) sync(() -> c().send(p, "player_not_found", "name", name));
            else action.accept(opt.get());
        });
    }
}
