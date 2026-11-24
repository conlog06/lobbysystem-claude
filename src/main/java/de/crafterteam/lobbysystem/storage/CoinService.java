package de.crafterteam.lobbysystem.storage;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.api.CoinAPI;
import de.crafterteam.lobbysystem.api.CoinsChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Implementierung der CoinAPI – Online-Spieler über den Cache, Offline-Spieler direkt in der DB. */
public final class CoinService implements CoinAPI {

    private final LobbySystem plugin;

    public CoinService(LobbySystem plugin) {
        this.plugin = plugin;
    }

    private Optional<PlayerData> online(UUID uuid) {
        return Bukkit.getPlayer(uuid) == null ? Optional.empty() : plugin.data().cached(uuid);
    }

    private void fire(UUID uuid, long before, long after, String reason) {
        if (before == after) return;
        Runnable r = () -> Bukkit.getPluginManager().callEvent(new CoinsChangeEvent(uuid, before, after, reason));
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }

    /** Interne Kurzform für Online-Spieler (Belohnungen, Shop, Lotterie …). */
    public long change(UUID uuid, long delta, String reason) {
        PlayerData d = plugin.data().cached(uuid).orElse(null);
        if (d == null) {
            if (delta >= 0) addCoins(uuid, delta, reason);
            else removeCoins(uuid, -delta, reason);
            return -1;
        }
        long before, after;
        synchronized (d) {
            before = d.coins();
            d.addCoins(delta);
            after = d.coins();
        }
        fire(uuid, before, after, reason);
        return after;
    }

    @Override
    public CompletableFuture<Long> getCoins(UUID uuid) {
        Optional<PlayerData> d = online(uuid);
        if (d.isPresent()) return CompletableFuture.completedFuture(d.get().coins());
        return plugin.database().submit(con -> {
            try (PreparedStatement ps = con.prepareStatement("select coins from ls_players where uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : -1L;
                }
            }
        });
    }

    @Override
    public long getCoins(Player player) {
        return plugin.data().get(player).coins();
    }

    @Override
    public CompletableFuture<Boolean> hasCoins(UUID uuid, long amount) {
        return getCoins(uuid).thenApply(c -> c >= amount);
    }

    @Override
    public CompletableFuture<Boolean> addCoins(UUID uuid, long amount, String reason) {
        if (amount < 0) return removeCoins(uuid, -amount, reason);
        Optional<PlayerData> d = online(uuid);
        if (d.isPresent()) {
            change(uuid, amount, reason);
            return CompletableFuture.completedFuture(true);
        }
        return update(uuid, "update ls_players set coins = coins + ? where uuid = ?", amount, false, reason);
    }

    @Override
    public CompletableFuture<Boolean> removeCoins(UUID uuid, long amount, String reason) {
        if (amount < 0) return addCoins(uuid, -amount, reason);
        Optional<PlayerData> d = online(uuid);
        if (d.isPresent()) {
            PlayerData data = d.get();
            long before, after;
            synchronized (data) {
                before = data.coins();
                if (before < amount) return CompletableFuture.completedFuture(false);
                data.addCoins(-amount);
                after = data.coins();
            }
            fire(uuid, before, after, reason);
            return CompletableFuture.completedFuture(true);
        }
        return update(uuid, "update ls_players set coins = coins - ? where uuid = ? and coins >= ?", amount, true, reason);
    }

    @Override
    public CompletableFuture<Boolean> setCoins(UUID uuid, long amount, String reason) {
        long value = Math.max(0, amount);
        Optional<PlayerData> d = online(uuid);
        if (d.isPresent()) {
            long before = d.get().coins();
            d.get().coins(value);
            fire(uuid, before, value, reason);
            return CompletableFuture.completedFuture(true);
        }
        return plugin.database().submit(con -> {
            try (PreparedStatement ps = con.prepareStatement("update ls_players set coins = ? where uuid = ?")) {
                ps.setLong(1, value);
                ps.setString(2, uuid.toString());
                return ps.executeUpdate() > 0;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> transfer(UUID from, UUID to, long amount, String reason) {
        if (amount <= 0) return CompletableFuture.completedFuture(false);
        return removeCoins(from, amount, reason).thenCompose(ok -> ok ? addCoins(to, amount, reason) : CompletableFuture.completedFuture(false));
    }

    private CompletableFuture<Boolean> update(UUID uuid, String sql, long amount, boolean checkBalance, String reason) {
        return plugin.database().submit(con -> {
            long before;
            try (PreparedStatement ps = con.prepareStatement("select coins from ls_players where uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                    before = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setLong(1, amount);
                ps.setString(2, uuid.toString());
                if (checkBalance) ps.setLong(3, amount);
                if (ps.executeUpdate() == 0) return false;
            }
            fire(uuid, before, checkBalance ? before - amount : before + amount, reason);
            return true;
        });
    }
}
