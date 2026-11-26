package de.crafterteam.lobbysystem.storage;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.api.GameAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.util.UUID;

/** Implementierung der GameAPI – schreibt in ls_game_stats / ls_servers / ls_players. */
public final class GameStatsService implements GameAPI {

    private final LobbySystem plugin;

    public GameStatsService(LobbySystem plugin) {
        this.plugin = plugin;
    }

    @Override
    public void setGame(String gameId) {
        plugin.servers().game(gameId);
        plugin.servers().heartbeat();
    }

    @Override
    public String game() {
        return plugin.servers().game();
    }

    @Override
    public void setState(String state) {
        plugin.servers().state(state);
        plugin.servers().heartbeat();
    }

    @Override
    public void addStats(UUID uuid, String name, int wins, int kills, int deaths, int games) {
        String game = game();
        plugin.database().submit(con -> {
            try (PreparedStatement ps = con.prepareStatement("update ls_game_stats set name = ?, wins = wins + ?, kills = kills + ?, "
                    + "deaths = deaths + ?, games = games + ?, updated = ? where uuid = ? and game = ?")) {
                ps.setString(1, name);
                ps.setInt(2, wins);
                ps.setInt(3, kills);
                ps.setInt(4, deaths);
                ps.setInt(5, games);
                ps.setLong(6, System.currentTimeMillis());
                ps.setString(7, uuid.toString());
                ps.setString(8, game);
                if (ps.executeUpdate() > 0) return null;
            }
            try (PreparedStatement ps = con.prepareStatement("insert into ls_game_stats (uuid, game, name, wins, kills, deaths, games, level, updated) "
                    + "values (?, ?, ?, ?, ?, ?, ?, 0, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, game);
                ps.setString(3, name);
                ps.setInt(4, Math.max(0, wins));
                ps.setInt(5, Math.max(0, kills));
                ps.setInt(6, Math.max(0, deaths));
                ps.setInt(7, Math.max(0, games));
                ps.setLong(8, System.currentTimeMillis());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void setLevel(UUID uuid, String name, int level) {
        addStats(uuid, name, 0, 0, 0, 0);
        String game = game();
        plugin.database().submit(con -> {
            try (PreparedStatement ps = con.prepareStatement("update ls_game_stats set level = ? where uuid = ? and game = ?")) {
                ps.setInt(1, level);
                ps.setString(2, uuid.toString());
                ps.setString(3, game);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void addXp(UUID uuid, long xp) {
        if (xp <= 0) return;
        Runnable r = () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                if (plugin.lobbyMode()) plugin.levels().addXp(p, xp);
                else plugin.data().get(p).addXp(xp);
                return;
            }
            plugin.database().submit(con -> {
                try (PreparedStatement ps = con.prepareStatement("update ls_players set xp = xp + ? where uuid = ?")) {
                    ps.setLong(1, xp);
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                }
                return null;
            });
        };
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }
}
