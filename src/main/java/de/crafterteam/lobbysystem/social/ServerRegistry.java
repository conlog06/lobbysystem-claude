package de.crafterteam.lobbysystem.social;

import de.crafterteam.lobbysystem.LobbySystem;
import org.bukkit.Bukkit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Heartbeat aller Server in ls_servers (Lobbys + Spielmodi wie MissileWars) – für Lobby-Switcher,
 * Navigator-Spielerzahlen ({players:MissileWars*}) und den Online-Status von Freunden.
 */
public final class ServerRegistry {

    public record ServerInfo(String name, int online, int max, boolean current, String state, String game) {
        public boolean full() {
            return max > 0 && online >= max;
        }
    }

    private final LobbySystem plugin;
    /** Schnappschuss aller aktiven Server (alle 10 s aktualisiert) – ohne DB-Zugriff im Main-Thread nutzbar. */
    private volatile Map<String, ServerInfo> snapshot = Map.of();

    public ServerRegistry(LobbySystem plugin) {
        this.plugin = plugin;
    }

    private String name() {
        return plugin.configs().cfg().proxyServerName;
    }

    /** Alle 10 Sekunden: eigenen Stand schreiben + alle Server lesen. */
    public void heartbeat() {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        String name = name();
        plugin.database().submit(con -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = con.prepareStatement("update ls_servers set online = ?, max_players = ?, updated = ?, state = 'LOBBY', game = 'lobby' where name = ?")) {
                ps.setInt(1, online);
                ps.setInt(2, max);
                ps.setLong(3, now);
                ps.setString(4, name);
                if (ps.executeUpdate() == 0) {
                    try (PreparedStatement ins = con.prepareStatement("insert into ls_servers (name, online, max_players, updated, state, game) values (?, ?, ?, ?, 'LOBBY', 'lobby')")) {
                        ins.setString(1, name);
                        ins.setInt(2, online);
                        ins.setInt(3, max);
                        ins.setLong(4, now);
                        ins.executeUpdate();
                    }
                }
            }
            Map<String, ServerInfo> map = new LinkedHashMap<>();
            try (PreparedStatement ps = con.prepareStatement("select name, online, max_players, state, game from ls_servers where updated > ? order by name")) {
                ps.setLong(1, now - 30_000L);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String n = rs.getString(1);
                        map.put(n.toLowerCase(), new ServerInfo(n, rs.getInt(2), rs.getInt(3), n.equalsIgnoreCase(name),
                                rs.getString(4), rs.getString(5)));
                    }
                }
            }
            snapshot = map;
            return null;
        });
    }

    /** Server passend zu "Name" oder "Präfix*" aus dem Schnappschuss. */
    public List<ServerInfo> match(String pattern) {
        List<ServerInfo> out = new ArrayList<>();
        if (pattern == null) return out;
        String p = pattern.toLowerCase();
        boolean wildcard = p.endsWith("*");
        if (wildcard) p = p.substring(0, p.length() - 1);
        for (ServerInfo s : snapshot.values()) {
            String n = s.name().toLowerCase();
            if (wildcard ? n.startsWith(p) : n.equals(p)) out.add(s);
        }
        return out;
    }

    public int players(String pattern) {
        return match(pattern).stream().mapToInt(ServerInfo::online).sum();
    }

    public int maxPlayers(String pattern) {
        return match(pattern).stream().mapToInt(ServerInfo::max).sum();
    }

    /** Statustext für Navigator-Lore. */
    public String stateText(String pattern) {
        List<ServerInfo> list = match(pattern);
        if (list.isEmpty()) return "<red>● Offline";
        boolean waiting = list.stream().anyMatch(s -> "WAITING".equalsIgnoreCase(s.state()) && !s.full());
        boolean ingame = list.stream().anyMatch(s -> "INGAME".equalsIgnoreCase(s.state()));
        if (waiting) return "<green>● Wartet auf Spieler";
        if (ingame) return "<gold>● Runde läuft";
        return "<green>● Online";
    }

    /**
     * Bester Server für einen Spielmodus: wartende Runden zuerst, davon die vollste (Runden füllen sich schneller),
     * sonst der leerste. null = keiner online.
     */
    public ServerInfo best(String pattern) {
        String pat = pattern.endsWith("*") ? pattern : pattern + "*";
        ServerInfo best = null;
        for (ServerInfo s : match(pat)) {
            if (s.full() || s.current()) continue;
            boolean waiting = "WAITING".equalsIgnoreCase(s.state());
            if (best == null) {
                best = s;
                continue;
            }
            boolean bestWaiting = "WAITING".equalsIgnoreCase(best.state());
            if (waiting && !bestWaiting) best = s;
            else if (waiting == bestWaiting && (waiting ? s.online() > best.online() : s.online() < best.online())) best = s;
        }
        return best;
    }

    public CompletableFuture<List<ServerInfo>> lobbies() {
        String prefix = plugin.configs().cfg().lobbyPrefix;
        List<ServerInfo> out = new ArrayList<>();
        for (ServerInfo s : snapshot.values()) {
            if (prefix == null || prefix.isBlank() || s.name().toLowerCase().startsWith(prefix.toLowerCase())) out.add(s);
        }
        if (out.stream().noneMatch(ServerInfo::current)) {
            out.add(new ServerInfo(name(), Bukkit.getOnlinePlayers().size(), Bukkit.getMaxPlayers(), true, "LOBBY", "lobby"));
        }
        out.sort(java.util.Comparator.comparing(ServerInfo::name));
        return CompletableFuture.completedFuture(out);
    }

    public void shutdown() {
        String name = name();
        try {
            plugin.database().submit(con -> {
                try (PreparedStatement ps = con.prepareStatement("update ls_servers set updated = 0, online = 0 where name = ?")) {
                    ps.setString(1, name);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement("update ls_players set online_server = '' where online_server = ?")) {
                    ps.setString(1, name);
                    ps.executeUpdate();
                }
                return null;
            }).get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }
}
