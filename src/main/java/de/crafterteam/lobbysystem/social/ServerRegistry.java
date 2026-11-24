package de.crafterteam.lobbysystem.social;

import de.crafterteam.lobbysystem.LobbySystem;
import org.bukkit.Bukkit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Heartbeat aller Lobby-Server in ls_servers – für Lobby-Switcher und Online-Status von Freunden. */
public final class ServerRegistry {

    public record ServerInfo(String name, int online, int max, boolean current) {
    }

    private final LobbySystem plugin;

    public ServerRegistry(LobbySystem plugin) {
        this.plugin = plugin;
    }

    private String name() {
        return plugin.configs().cfg().proxyServerName;
    }

    /** Alle 10 Sekunden (Main-Thread → Werte erfassen, DB async). */
    public void heartbeat() {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        String name = name();
        plugin.database().submit(con -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = con.prepareStatement("update ls_servers set online = ?, max_players = ?, updated = ? where name = ?")) {
                ps.setInt(1, online);
                ps.setInt(2, max);
                ps.setLong(3, now);
                ps.setString(4, name);
                if (ps.executeUpdate() == 0) {
                    try (PreparedStatement ins = con.prepareStatement("insert into ls_servers (name, online, max_players, updated) values (?, ?, ?, ?)")) {
                        ins.setString(1, name);
                        ins.setInt(2, online);
                        ins.setInt(3, max);
                        ins.setLong(4, now);
                        ins.executeUpdate();
                    }
                }
            }
            return null;
        });
    }

    public CompletableFuture<List<ServerInfo>> lobbies() {
        String prefix = plugin.configs().cfg().lobbyPrefix;
        String current = name();
        return plugin.database().submit(con -> {
            List<ServerInfo> out = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement("select name, online, max_players from ls_servers where updated > ? order by name")) {
                ps.setLong(1, System.currentTimeMillis() - 30_000L);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String n = rs.getString(1);
                        if (prefix == null || prefix.isBlank() || n.toLowerCase().startsWith(prefix.toLowerCase())) {
                            out.add(new ServerInfo(n, rs.getInt(2), rs.getInt(3), n.equalsIgnoreCase(current)));
                        }
                    }
                }
            }
            return out;
        });
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
