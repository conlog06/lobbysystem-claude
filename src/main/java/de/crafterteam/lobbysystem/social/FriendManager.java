package de.crafterteam.lobbysystem.social;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.ConfigManager;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.storage.PlayerDataManager.Profile;
import de.crafterteam.lobbysystem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static de.crafterteam.lobbysystem.storage.PlayerDataManager.profiles;

/** Freundesystem – gespeichert in MySQL, funktioniert über alle Lobbys hinweg. */
public final class FriendManager {

    private static final String P = "friend_prefix";
    private final LobbySystem plugin;
    /** uuid -> [online, gesamt] für Scoreboard */
    private final Map<UUID, int[]> counts = new ConcurrentHashMap<>();

    public FriendManager(LobbySystem plugin) {
        this.plugin = plugin;
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

    // ---------------------------------------------------------------- Abfragen (im DB-Thread)

    static List<UUID> friendIds(Connection con, UUID uuid) throws SQLException {
        List<UUID> out = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("select friend from ls_friends where uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(UUID.fromString(rs.getString(1)));
            }
        }
        return out;
    }

    private static boolean exists(Connection con, String sql, String a, String b) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, a);
            ps.setString(2, b);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void exec(Connection con, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        }
    }

    private static int count(Connection con, UUID uuid) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("select count(*) from ls_friends where uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int max(Player p) {
        return c().has(p, "friends_premium") && c().perm("friends_premium") != null
                ? c().cfg().social.friendsMaxPremium : c().cfg().social.friendsMax;
    }

    // ---------------------------------------------------------------- Aktionen

    public void add(Player p, String name) {
        if (plugin.proxy().forward(p, "friends", "friend", "add " + name)) return;
        if (p.getName().equalsIgnoreCase(name)) {
            msg(p, "friend_self");
            return;
        }
        int max = max(p);
        plugin.data().lookup(name).thenCompose(opt -> {
            if (opt.isEmpty()) {
                sync(() -> c().send(p, "player_not_found", "name", name));
                return CompletableFuture.completedFuture(null);
            }
            Profile t = opt.get();
            String me = p.getUniqueId().toString(), them = t.uuid().toString();
            return db(con -> {
                if (exists(con, "select 1 from ls_friends where uuid = ? and friend = ?", me, them)) {
                    msg(p, "friend_already");
                    return null;
                }
                // Gegenseitige Anfrage → direkt annehmen
                if (exists(con, "select 1 from ls_friend_requests where sender = ? and receiver = ?", them, me)) {
                    befriend(con, p.getUniqueId(), t.uuid());
                    msg(p, "friend_added", "name", t.name());
                    plugin.messenger().sync(() -> plugin.messenger().send(t.name(), c().msgP(P, "friend_added", "name", p.getName())));
                    return null;
                }
                if (exists(con, "select 1 from ls_friend_requests where sender = ? and receiver = ?", me, them)) {
                    msg(p, "friend_request_pending", "name", t.name());
                    return null;
                }
                if (!t.setting("req")) {
                    msg(p, "friend_requests_disabled", "name", t.name());
                    return null;
                }
                if (count(con, p.getUniqueId()) >= max) {
                    msg(p, "friend_limit", "max", Integer.toString(max));
                    return null;
                }
                exec(con, "insert into ls_friend_requests (sender, receiver, created) values (?, ?, ?)", me, them, System.currentTimeMillis());
                msg(p, "friend_request_sent", "name", t.name());
                sync(() -> plugin.messenger().send(t.name(), c().msgP(P, "friend_request_received", "name", p.getName())));
                return null;
            });
        });
    }

    private void befriend(Connection con, UUID a, UUID b) throws SQLException {
        long now = System.currentTimeMillis();
        exec(con, "delete from ls_friend_requests where (sender = ? and receiver = ?) or (sender = ? and receiver = ?)",
                a.toString(), b.toString(), b.toString(), a.toString());
        exec(con, "delete from ls_friends where (uuid = ? and friend = ?) or (uuid = ? and friend = ?)",
                a.toString(), b.toString(), b.toString(), a.toString());
        exec(con, "insert into ls_friends (uuid, friend, since) values (?, ?, ?)", a.toString(), b.toString(), now);
        exec(con, "insert into ls_friends (uuid, friend, since) values (?, ?, ?)", b.toString(), a.toString(), now);
        refreshCount(a);
        refreshCount(b);
    }

    public void accept(Player p, String name) {
        if (plugin.proxy().forward(p, "friends", "friend", "accept " + name)) return;
        int max = max(p);
        withProfile(p, name, t -> db(con -> {
            if (!exists(con, "select 1 from ls_friend_requests where sender = ? and receiver = ?", t.uuid().toString(), p.getUniqueId().toString())) {
                msg(p, "friend_no_request", "name", t.name());
                return null;
            }
            if (count(con, p.getUniqueId()) >= max) {
                msg(p, "friend_limit", "max", Integer.toString(max));
                return null;
            }
            befriend(con, p.getUniqueId(), t.uuid());
            msg(p, "friend_added", "name", t.name());
            sync(() -> plugin.messenger().send(t.name(), c().msgP(P, "friend_added", "name", p.getName())));
            return null;
        }));
    }

    public void deny(Player p, String name) {
        if (plugin.proxy().forward(p, "friends", "friend", "deny " + name)) return;
        withProfile(p, name, t -> db(con -> {
            if (!exists(con, "select 1 from ls_friend_requests where sender = ? and receiver = ?", t.uuid().toString(), p.getUniqueId().toString())) {
                msg(p, "friend_no_request", "name", t.name());
                return null;
            }
            exec(con, "delete from ls_friend_requests where sender = ? and receiver = ?", t.uuid().toString(), p.getUniqueId().toString());
            msg(p, "friend_denied", "name", t.name());
            return null;
        }));
    }

    public void remove(Player p, String name) {
        if (plugin.proxy().forward(p, "friends", "friend", "remove " + name)) return;
        withProfile(p, name, t -> db(con -> {
            if (!exists(con, "select 1 from ls_friends where uuid = ? and friend = ?", p.getUniqueId().toString(), t.uuid().toString())) {
                msg(p, "friend_not_friends", "name", t.name());
                return null;
            }
            exec(con, "delete from ls_friends where (uuid = ? and friend = ?) or (uuid = ? and friend = ?)",
                    p.getUniqueId().toString(), t.uuid().toString(), t.uuid().toString(), p.getUniqueId().toString());
            refreshCount(p.getUniqueId());
            refreshCount(t.uuid());
            msg(p, "friend_removed", "name", t.name());
            return null;
        }));
    }

    /** Freunde sortiert: online zuerst, dann alphabetisch. */
    public CompletableFuture<List<Profile>> friends(UUID uuid) {
        return db(con -> {
            List<Profile> list = new ArrayList<>(profiles(con, friendIds(con, uuid)).values());
            list.sort(Comparator.comparing((Profile pr) -> !pr.online()).thenComparing(pr -> pr.name().toLowerCase()));
            return list;
        });
    }

    public CompletableFuture<List<Profile>> requests(UUID uuid) {
        return db(con -> {
            List<UUID> ids = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement("select sender from ls_friend_requests where receiver = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) ids.add(UUID.fromString(rs.getString(1)));
                }
            }
            return new ArrayList<>(profiles(con, ids).values());
        });
    }

    public void list(Player p) {
        if (plugin.proxy().forward(p, "friends", "friend", "list")) return;
        friends(p.getUniqueId()).thenAccept(list -> sync(() -> {
            c().sendP(p, P, "friend_list_header", "count", Integer.toString(list.size()));
            for (Profile f : list) p.sendMessage(Text.parse(c().raw("friend_list_entry"), ConfigManager.map(
                    "status", f.online() ? "<green>●" : "<red>●", "name", f.name(), "where", where(f))));
        }));
    }

    public void listRequests(Player p) {
        if (plugin.proxy().forward(p, "friends", "friend", "requests")) return;
        requests(p.getUniqueId()).thenAccept(list -> sync(() -> {
            c().sendP(p, P, "friend_requests_header", "count", Integer.toString(list.size()));
            for (Profile f : list) c().sendP(p, P, "friend_request_received", "name", f.name());
        }));
    }

    public String where(Profile f) {
        if (f.online()) return "<green>" + Text.escape(f.server());
        if (f.lastSeen() <= 0) return "<gray>offline";
        return "<gray>zuletzt " + DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.of("Europe/Berlin"))
                .format(Instant.ofEpochMilli(f.lastSeen()));
    }

    public void jump(Player p, String name) {
        if (plugin.proxy().forward(p, "friends", "friend", "jump " + name)) return;
        withFriend(p, name, f -> {
            if (!f.online()) {
                msg(p, "friend_not_online", "name", f.name());
                return;
            }
            sync(() -> {
                c().sendP(p, P, "friend_jump", "name", f.name());
                Player local = Bukkit.getPlayerExact(f.name());
                if (local != null) plugin.lobby().teleport(p, local.getLocation(), true);
                else plugin.lobby().connect(p, f.server());
            });
        });
    }

    public void message(Player p, String name, String message) {
        if (plugin.proxy().forward(p, "friends", "friend", "msg " + name + " " + message)) return;
        withFriend(p, name, f -> {
            if (!f.online()) {
                msg(p, "friend_not_online", "name", f.name());
                return;
            }
            sync(() -> {
                var comp = Text.parse(c().cfg().social.friendChatFormat, ConfigManager.map("name", p.getName(), "message", message));
                plugin.messenger().send(f.name(), comp);
                p.sendMessage(comp);
            });
        });
    }

    public void toggle(Player p) {
        if (plugin.proxy().forward(p, "friends", "friend", "toggle")) return;
        PlayerData d = plugin.data().get(p);
        d.friendRequests(!d.friendRequests());
        plugin.data().save(d);
        c().sendP(p, P, "friend_toggle", "state", c().raw(d.friendRequests() ? "state_on" : "state_off"));
    }

    // ---------------------------------------------------------------- Join / Quit

    public void announce(Player p, boolean online) {
        if (plugin.proxy().active("friends")) {
            // Online-/Offline-Meldungen verschickt das BungeeSystem netzwerkweit
            refreshCount(p.getUniqueId());
            return;
        }
        PlayerData own = plugin.data().get(p);
        if (!own.friendNotify() && online) {
            refreshCount(p.getUniqueId());
            return;
        }
        String name = p.getName();
        friends(p.getUniqueId()).thenAccept(list -> sync(() -> {
            for (Profile f : list) {
                if (!f.online() || !f.setting("notify")) continue;
                plugin.messenger().send(f.name(), c().msgP(P, online ? "friend_online" : "friend_offline", "name", name));
            }
        }));
        refreshCount(p.getUniqueId());
    }

    public void refreshCount(UUID uuid) {
        db(con -> {
            List<UUID> ids = friendIds(con, uuid);
            int online = (int) profiles(con, ids).values().stream().filter(Profile::online).count();
            counts.put(uuid, new int[]{online, ids.size()});
            return null;
        });
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) refreshCount(p.getUniqueId());
    }

    public int[] count(UUID uuid) {
        return counts.getOrDefault(uuid, new int[]{0, 0});
    }

    public void forget(UUID uuid) {
        counts.remove(uuid);
    }

    // ---------------------------------------------------------------- Helfer

    private void withProfile(Player p, String name, Consumer<Profile> action) {
        plugin.data().lookup(name).thenAccept(opt -> {
            if (opt.isEmpty()) sync(() -> c().send(p, "player_not_found", "name", name));
            else action.accept(opt.get());
        });
    }

    private void withFriend(Player p, String name, Consumer<Profile> action) {
        withProfile(p, name, t -> db(con -> {
            if (!exists(con, "select 1 from ls_friends where uuid = ? and friend = ?", p.getUniqueId().toString(), t.uuid().toString())) {
                msg(p, "friend_not_friends", "name", t.name());
                return null;
            }
            action.accept(t);
            return null;
        }));
    }
}
