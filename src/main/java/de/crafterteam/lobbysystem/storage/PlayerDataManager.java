package de.crafterteam.lobbysystem.storage;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class PlayerDataManager {

    private final JavaPlugin plugin;
    private final Database db;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(JavaPlugin plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    /** Wird im AsyncPreLogin aufgerufen (eigener Thread) – darf kurz blockieren. */
    public void preload(UUID uuid, String name) {
        try {
            PlayerData data = db.submit(con -> load(con, uuid, name)).get(8, TimeUnit.SECONDS);
            cache.put(uuid, data);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Konnte Daten von " + name + " nicht laden – nutze Standardwerte.", e);
            cache.put(uuid, new PlayerData(uuid, name));
        }
    }

    /** Für bereits verbundene Spieler (Plugin-Reload): asynchron laden, danach callback im Main-Thread. */
    public void preloadAsync(Player player, Runnable callback) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        db.submit(con -> load(con, uuid, name)).whenComplete((d, err) -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            cache.put(uuid, d != null ? d : new PlayerData(uuid, name));
            if (player.isOnline()) callback.run();
        }));
    }

    private PlayerData load(Connection con, UUID uuid, String name) throws SQLException {
        PlayerData data = new PlayerData(uuid, name);
        try (PreparedStatement ps = con.prepareStatement("select * from ls_players where uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    data.loadCoins(rs.getLong("coins"));
                    data.loadLose(rs.getInt("lose"));
                    data.loadPlaytime(rs.getLong("playtime"));
                    data.lastDaily(rs.getLong("last_daily"));
                    data.lastPremium(rs.getLong("last_premium"));
                    data.loadSettings(rs.getString("settings"));
                    data.loadSelected(rs.getString("selected"));
                    data.loadOwned(rs.getString("owned"));
                } else {
                    try (PreparedStatement ins = con.prepareStatement(
                            "insert into ls_players (uuid, name, settings, selected, owned) values (?, ?, ?, '', '')")) {
                        ins.setString(1, uuid.toString());
                        ins.setString(2, name);
                        ins.setString(3, data.settingsString());
                        ins.executeUpdate();
                    }
                }
            }
        }
        data.name(name);
        data.clean();
        return data;
    }

    public PlayerData get(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), id -> new PlayerData(id, player.getName()));
    }

    public Optional<PlayerData> cached(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    public Collection<PlayerData> all() {
        return cache.values();
    }

    public CompletableFuture<Void> save(PlayerData data) {
        if (!data.dirty()) return CompletableFuture.completedFuture(null);
        data.clean();
        long[] deltas = data.drainDeltas();
        long playtime = data.drainPlaytime();
        long absoluteCoins = data.coins();
        return db.submit(con -> {
            write(con, data, deltas, playtime, absoluteCoins);
            return null;
        });
    }

    private void write(Connection con, PlayerData d, long[] deltas, long playtime, long absoluteCoins) throws SQLException {
        String coinsSql = deltas[2] == 1 ? "coins = ?" : "coins = coins + ?";
        try (PreparedStatement ps = con.prepareStatement("update ls_players set name = ?, " + coinsSql
                + ", lose = lose + ?, playtime = playtime + ?, last_daily = ?, last_premium = ?, settings = ?, selected = ?, owned = ? where uuid = ?")) {
            ps.setString(1, d.name());
            ps.setLong(2, deltas[2] == 1 ? absoluteCoins : deltas[0]);
            ps.setLong(3, deltas[1]);
            ps.setLong(4, playtime);
            ps.setLong(5, d.lastDaily());
            ps.setLong(6, d.lastPremium());
            ps.setString(7, d.settingsString());
            ps.setString(8, d.selectedString());
            ps.setString(9, d.ownedString());
            ps.setString(10, d.uuid().toString());
            if (ps.executeUpdate() == 0) {
                try (PreparedStatement ins = con.prepareStatement("insert into ls_players "
                        + "(uuid, name, coins, lose, playtime, last_daily, last_premium, settings, selected, owned) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ins.setString(1, d.uuid().toString());
                    ins.setString(2, d.name());
                    ins.setLong(3, d.coins());
                    ins.setInt(4, d.lose());
                    ins.setLong(5, d.playtime());
                    ins.setLong(6, d.lastDaily());
                    ins.setLong(7, d.lastPremium());
                    ins.setString(8, d.settingsString());
                    ins.setString(9, d.selectedString());
                    ins.setString(10, d.ownedString());
                    ins.executeUpdate();
                }
            }
        }
    }

    public void unload(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data != null) save(data);
    }

    public void saveAll() {
        for (PlayerData d : new ArrayList<>(cache.values())) save(d);
    }

    /** Beim Deaktivieren: alles speichern und warten. */
    public void saveAllBlocking() {
        CompletableFuture<?>[] futures = new ArrayList<>(cache.values()).stream().map(this::save).toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Nicht alle Spielerdaten konnten gespeichert werden.", e);
        }
    }

    // ---------------------------------------------------------------- Offline-Zugriff (Coins-Befehl)

    public record OfflineCoins(String name, long coins) {
    }

    public CompletableFuture<Optional<OfflineCoins>> offlineCoins(String name) {
        return db.submit(con -> {
            try (PreparedStatement ps = con.prepareStatement("select name, coins from ls_players where lower(name) = lower(?)")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(new OfflineCoins(rs.getString("name"), rs.getLong("coins")));
                }
            }
            return Optional.empty();
        });
    }

    /** mode: add / remove / set  → liefert neuen Stand oder empty, wenn Spieler unbekannt */
    public CompletableFuture<Optional<OfflineCoins>> offlineModify(String name, String mode, long amount) {
        return db.submit(con -> {
            String sql = switch (mode) {
                case "set" -> "update ls_players set coins = ? where lower(name) = lower(?)";
                case "remove" -> "update ls_players set coins = case when coins - ? < 0 then 0 else coins - ? end where lower(name) = lower(?)";
                default -> "update ls_players set coins = coins + ? where lower(name) = lower(?)";
            };
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setLong(1, amount);
                if (mode.equals("remove")) {
                    ps.setLong(2, amount);
                    ps.setString(3, name);
                } else {
                    ps.setString(2, name);
                }
                if (ps.executeUpdate() == 0) return Optional.empty();
            }
            try (PreparedStatement ps = con.prepareStatement("select name, coins from ls_players where lower(name) = lower(?)")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(new OfflineCoins(rs.getString("name"), rs.getLong("coins")));
                }
            }
            return Optional.empty();
        });
    }
}
