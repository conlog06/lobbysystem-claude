package de.crafterteam.lobbysystem.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Wird nach jeder Coin-Änderung (im Main-Thread) ausgelöst. */
public final class CoinsChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID uuid;
    private final long oldCoins;
    private final long newCoins;
    private final String reason;

    public CoinsChangeEvent(UUID uuid, long oldCoins, long newCoins, String reason) {
        this.uuid = uuid;
        this.oldCoins = oldCoins;
        this.newCoins = newCoins;
        this.reason = reason;
    }

    public UUID getUniqueId() {
        return uuid;
    }

    public long getOldCoins() {
        return oldCoins;
    }

    public long getNewCoins() {
        return newCoins;
    }

    public long getDifference() {
        return newCoins - oldCoins;
    }

    /** z. B. "daily_reward", "lottery", "cosmetic_buy", "command", "api" */
    public String getReason() {
        return reason;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
