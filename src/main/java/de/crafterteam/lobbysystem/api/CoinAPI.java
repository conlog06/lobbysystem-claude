package de.crafterteam.lobbysystem.api;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Öffentliche Coin-Schnittstelle für andere Plugins.
 *
 * <pre>
 * CoinAPI coins = LobbySystemAPI.coins();
 * coins.addCoins(player.getUniqueId(), 100, "minigame_win");
 * coins.removeCoins(uuid, 50, "shop").thenAccept(ok -> { ... });
 * </pre>
 *
 * Alternativ über den ServicesManager: {@code Bukkit.getServicesManager().load(CoinAPI.class)}.
 * Funktioniert für Online- und Offline-Spieler (gleiche MySQL-Datenbank auf allen Servern).
 * Nach jeder Änderung wird im Main-Thread ein {@link CoinsChangeEvent} ausgelöst.
 */
public interface CoinAPI {

    /** Aktueller Stand (online aus dem Cache, offline aus der Datenbank). -1 = Spieler unbekannt. */
    CompletableFuture<Long> getCoins(UUID uuid);

    /** Sofortiger Stand eines Online-Spielers auf diesem Server. */
    long getCoins(Player player);

    CompletableFuture<Boolean> hasCoins(UUID uuid, long amount);

    /** @return false, wenn der Spieler unbekannt ist */
    CompletableFuture<Boolean> addCoins(UUID uuid, long amount, String reason);

    /** Entfernt nur, wenn genug Coins vorhanden sind. @return true bei Erfolg */
    CompletableFuture<Boolean> removeCoins(UUID uuid, long amount, String reason);

    CompletableFuture<Boolean> setCoins(UUID uuid, long amount, String reason);

    /** Coins von a an b überweisen (nur wenn a genug hat). */
    CompletableFuture<Boolean> transfer(UUID from, UUID to, long amount, String reason);

    default CompletableFuture<Boolean> addCoins(UUID uuid, long amount) {
        return addCoins(uuid, amount, "api");
    }

    default CompletableFuture<Boolean> removeCoins(UUID uuid, long amount) {
        return removeCoins(uuid, amount, "api");
    }
}
