package de.crafterteam.lobbysystem.social;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.ConfigManager;
import de.crafterteam.lobbysystem.config.PluginConfig;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static de.crafterteam.lobbysystem.storage.PlayerDataManager.profiles;

/** Clan-System mit Tag in Tab/Nametag/Scoreboard, Rollen und Clan-Chat. */
public final class ClanManager {

    public static final int MEMBER = 0, MODERATOR = 1, LEADER = 2;
    private static final String P = "clan_prefix";

    private final LobbySystem plugin;
    private final Map<UUID, Clan> cache = new ConcurrentHashMap<>();

    public record Clan(String id, String name, String tag, UUID owner, Map<UUID, Integer> roles, List<Profile> members) {
        public int role(UUID uuid) {
            return roles.getOrDefault(uuid, -1);
        }
    }

    public ClanManager(LobbySystem plugin) {
        this.plugin = plugin;
    }

    private ConfigManager c() {
        return plugin.configs();
    }

    private PluginConfig.Social s() {
        return c().cfg().social;
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

    public static String roleName(int role) {
        return switch (role) {
            case LEADER -> "Leader";
            case MODERATOR -> "Moderator";
            default -> "Mitglied";
        };
    }

    // ---------------------------------------------------------------- Cache (für Tab & Scoreboard)

    public Optional<Clan> cached(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    public void refresh(UUID uuid) {
        db(con -> {
            Clan clan = loadByMember(con, uuid);
            if (clan == null) cache.remove(uuid);
            else cache.put(uuid, clan);
            return null;
        }).thenRun(() -> sync(() -> plugin.sidebar().updateTeams()));
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) refresh(p.getUniqueId());
    }

    public void forget(UUID uuid) {
        cache.remove(uuid);
    }

    private void refreshMembers(Clan clan) {
        if (clan == null) return;
        for (Profile m : clan.members()) if (Bukkit.getPlayer(m.uuid()) != null) refresh(m.uuid());
    }

    // ---------------------------------------------------------------- DB

    private static void exec(Connection con, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        }
    }

    static Clan loadByMember(Connection con, UUID uuid) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("select clan_id from ls_clan_members where uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? load(con, "id", rs.getString(1)) : null;
            }
        }
    }

    static Clan load(Connection con, String column, String value) throws SQLException {
        String id, name, tag;
        UUID owner;
        String where = column.equals("id") ? "id = ?" : "lower(" + column + ") = lower(?)";
        try (PreparedStatement ps = con.prepareStatement("select id, name, tag, owner from ls_clans where " + where)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                id = rs.getString("id");
                name = rs.getString("name");
                tag = rs.getString("tag");
                owner = UUID.fromString(rs.getString("owner"));
            }
        }
        Map<UUID, Integer> roles = new LinkedHashMap<>();
        try (PreparedStatement ps = con.prepareStatement("select uuid, role from ls_clan_members where clan_id = ? order by role desc, joined")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) roles.put(UUID.fromString(rs.getString(1)), rs.getInt(2));
            }
        }
        Map<UUID, Profile> profiles = profiles(con, roles.keySet());
        List<Profile> members = new ArrayList<>();
        for (UUID u : roles.keySet()) if (profiles.containsKey(u)) members.add(profiles.get(u));
        members.sort(Comparator.comparing((Profile pr) -> -roles.get(pr.uuid())).thenComparing(pr -> !pr.online()));
        return new Clan(id, name, tag, owner, roles, members);
    }

    private void broadcast(Clan clan, Component message) {
        if (clan == null) return;
        sync(() -> {
            for (Profile m : clan.members()) plugin.messenger().send(m.name(), message);
        });
    }

    // ---------------------------------------------------------------- Aktionen

    public void create(Player p, String name, String tag) {
        PluginConfig.Social s = s();
        if (!name.matches("[A-Za-z0-9_]{2," + s.clanNameMax + "}")) {
            msg(p, "clan_invalid_name", "max", Integer.toString(s.clanNameMax));
            return;
        }
        if (!tag.matches("[A-Za-z0-9]{" + s.clanTagMin + "," + s.clanTagMax + "}")) {
            msg(p, "clan_invalid_tag", "min", Integer.toString(s.clanTagMin), "max", Integer.toString(s.clanTagMax));
            return;
        }
        String upperTag = tag.toUpperCase();
        long cost = s.clanCreateCost;
        if (plugin.data().get(p).coins() < cost) {
            msg(p, "clan_cost", "cost", Text.formatNumber(cost));
            return;
        }
        db(con -> {
            if (loadByMember(con, p.getUniqueId()) != null) {
                msg(p, "clan_already_in");
                return null;
            }
            if (load(con, "name", name) != null || load(con, "tag", upperTag) != null) {
                msg(p, "clan_exists");
                return null;
            }
            String id = UUID.randomUUID().toString();
            long now = System.currentTimeMillis();
            exec(con, "insert into ls_clans (id, name, tag, owner, created) values (?, ?, ?, ?, ?)", id, name, upperTag, p.getUniqueId().toString(), now);
            exec(con, "insert into ls_clan_members (uuid, clan_id, role, joined) values (?, ?, ?, ?)", p.getUniqueId().toString(), id, LEADER, now);
            sync(() -> {
                if (cost > 0) plugin.coins().change(p.getUniqueId(), -cost, "clan_create");
                c().sendP(p, P, "clan_created", "name", name, "tag", upperTag);
            });
            refresh(p.getUniqueId());
            return null;
        });
    }

    public void invite(Player p, String name) {
        withClan(p, MODERATOR, clan -> plugin.data().lookup(name).thenAccept(opt -> {
            if (opt.isEmpty()) {
                sync(() -> c().send(p, "player_not_found", "name", name));
                return;
            }
            Profile t = opt.get();
            db(con -> {
                if (clan.members().size() >= s().clanMax) {
                    msg(p, "clan_full", "max", Integer.toString(s().clanMax));
                    return null;
                }
                if (loadByMember(con, t.uuid()) != null) {
                    msg(p, "clan_target_in_clan", "name", t.name());
                    return null;
                }
                exec(con, "delete from ls_clan_invites where clan_id = ? and uuid = ?", clan.id(), t.uuid().toString());
                exec(con, "insert into ls_clan_invites (clan_id, uuid, expires) values (?, ?, ?)", clan.id(), t.uuid().toString(),
                        System.currentTimeMillis() + Math.max(60, s().inviteSeconds) * 4000L);
                msg(p, "clan_invite_sent", "name", t.name());
                sync(() -> plugin.messenger().send(t.name(), c().msgP(P, "clan_invite_received", "clan", clan.name(), "tag", clan.tag())));
                return null;
            });
        }));
    }

    public void accept(Player p, String tag) {
        db(con -> {
            if (loadByMember(con, p.getUniqueId()) != null) {
                msg(p, "clan_already_in");
                return null;
            }
            Clan clan = load(con, "tag", tag);
            if (clan == null || !inviteValid(con, clan.id(), p.getUniqueId())) {
                msg(p, "clan_no_invite");
                return null;
            }
            if (clan.members().size() >= s().clanMax) {
                msg(p, "clan_full", "max", Integer.toString(s().clanMax));
                return null;
            }
            exec(con, "delete from ls_clan_invites where uuid = ?", p.getUniqueId().toString());
            exec(con, "insert into ls_clan_members (uuid, clan_id, role, joined) values (?, ?, ?, ?)",
                    p.getUniqueId().toString(), clan.id(), MEMBER, System.currentTimeMillis());
            Clan updated = load(con, "id", clan.id());
            broadcast(updated, c().msgP(P, "clan_joined", "name", p.getName()));
            refreshMembers(updated);
            return null;
        });
    }

    private static boolean inviteValid(Connection con, String clanId, UUID uuid) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("select expires from ls_clan_invites where clan_id = ? and uuid = ?")) {
            ps.setString(1, clanId);
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > System.currentTimeMillis();
            }
        }
    }

    public void deny(Player p, String tag) {
        db(con -> {
            Clan clan = load(con, "tag", tag);
            if (clan == null) {
                msg(p, "clan_not_found");
                return null;
            }
            exec(con, "delete from ls_clan_invites where clan_id = ? and uuid = ?", clan.id(), p.getUniqueId().toString());
            return null;
        });
    }

    public void leave(Player p) {
        withClan(p, MEMBER, clan -> db(con -> {
            if (clan.role(p.getUniqueId()) == LEADER) {
                if (clan.members().size() > 1) {
                    msg(p, "clan_owner_leave");
                    return null;
                }
                deleteClan(con, clan);
                msg(p, "clan_disbanded");
                refresh(p.getUniqueId());
                return null;
            }
            broadcast(clan, c().msgP(P, "clan_left", "name", p.getName()));
            exec(con, "delete from ls_clan_members where uuid = ?", p.getUniqueId().toString());
            refreshMembers(clan);
            return null;
        }));
    }

    public void kick(Player p, String name) {
        withClan(p, MODERATOR, clan -> {
            Profile t = find(clan, name);
            if (t == null) {
                msg(p, "clan_not_member", "name", name);
                return;
            }
            if (clan.role(t.uuid()) >= clan.role(p.getUniqueId())) {
                msg(p, "clan_no_rights");
                return;
            }
            db(con -> {
                broadcast(clan, c().msgP(P, "clan_kicked", "name", t.name()));
                exec(con, "delete from ls_clan_members where uuid = ?", t.uuid().toString());
                refreshMembers(clan);
                return null;
            });
        });
    }

    public void promote(Player p, String name) {
        withClan(p, LEADER, clan -> {
            Profile t = find(clan, name);
            if (t == null || t.uuid().equals(p.getUniqueId())) {
                msg(p, "clan_not_member", "name", name);
                return;
            }
            int role = clan.role(t.uuid());
            db(con -> {
                if (role == MODERATOR) {
                    // Leader-Übergabe
                    exec(con, "update ls_clan_members set role = ? where uuid = ?", LEADER, t.uuid().toString());
                    exec(con, "update ls_clan_members set role = ? where uuid = ?", MODERATOR, p.getUniqueId().toString());
                    exec(con, "update ls_clans set owner = ? where id = ?", t.uuid().toString(), clan.id());
                    broadcast(clan, c().msgP(P, "clan_role", "name", t.name(), "role", roleName(LEADER)));
                } else {
                    exec(con, "update ls_clan_members set role = ? where uuid = ?", MODERATOR, t.uuid().toString());
                    broadcast(clan, c().msgP(P, "clan_role", "name", t.name(), "role", roleName(MODERATOR)));
                }
                refreshMembers(clan);
                return null;
            });
        });
    }

    public void demote(Player p, String name) {
        withClan(p, LEADER, clan -> {
            Profile t = find(clan, name);
            if (t == null || clan.role(t.uuid()) != MODERATOR) {
                msg(p, "clan_not_member", "name", name);
                return;
            }
            db(con -> {
                exec(con, "update ls_clan_members set role = ? where uuid = ?", MEMBER, t.uuid().toString());
                broadcast(clan, c().msgP(P, "clan_role", "name", t.name(), "role", roleName(MEMBER)));
                return null;
            });
        });
    }

    public void disband(Player p) {
        withClan(p, LEADER, clan -> db(con -> {
            broadcast(clan, c().msgP(P, "clan_disbanded"));
            deleteClan(con, clan);
            refreshMembers(clan);
            return null;
        }));
    }

    private static void deleteClan(Connection con, Clan clan) throws SQLException {
        exec(con, "delete from ls_clan_members where clan_id = ?", clan.id());
        exec(con, "delete from ls_clan_invites where clan_id = ?", clan.id());
        exec(con, "delete from ls_clans where id = ?", clan.id());
    }

    public void info(Player p, String tag) {
        db(con -> tag == null ? loadByMember(con, p.getUniqueId()) : load(con, "tag", tag)).thenAccept(clan -> sync(() -> {
            if (clan == null) {
                c().sendP(p, P, tag == null ? "clan_not_in" : "clan_not_found");
                return;
            }
            c().sendP(p, P, "clan_info", "clan", clan.name(), "tag", clan.tag(),
                    "count", Integer.toString(clan.members().size()), "max", Integer.toString(s().clanMax));
            for (Profile m : clan.members()) {
                p.sendMessage(Text.parse(c().raw("clan_member_entry"), ConfigManager.map(
                        "status", m.online() ? "<green>●" : "<red>●", "name", m.name(), "role", roleName(clan.role(m.uuid())))));
            }
        }));
    }

    public void chat(Player p, String message) {
        withClan(p, MEMBER, clan -> broadcast(clan, Text.parse(s().clanChatFormat,
                ConfigManager.map("tag", clan.tag(), "name", p.getName(), "message", message))));
    }

    public CompletableFuture<Clan> clanOf(UUID uuid) {
        return db(con -> loadByMember(con, uuid));
    }

    // ---------------------------------------------------------------- Helfer

    private static Profile find(Clan clan, String name) {
        for (Profile m : clan.members()) if (m.name().equalsIgnoreCase(name)) return m;
        return null;
    }

    private void withClan(Player p, int minRole, Consumer<Clan> action) {
        clanOf(p.getUniqueId()).thenAccept(clan -> {
            if (clan == null) msg(p, "clan_not_in");
            else if (clan.role(p.getUniqueId()) < minRole) msg(p, "clan_no_rights");
            else action.accept(clan);
        });
    }
}
