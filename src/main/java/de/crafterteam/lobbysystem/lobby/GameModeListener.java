package de.crafterteam.lobbysystem.lobby;

import de.crafterteam.lobbysystem.LobbySystem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Modus "game": LobbySystem auf einem Spielserver. Lädt nur Spielerdaten (Coins, Einstellungen),
 * setzt den Online-Status für Freunde und hält Clan/Freunde aktuell – greift NICHT ins Spiel ein
 * (keine Hotbar, kein Schutz, kein Scoreboard).
 */
public final class GameModeListener implements Listener {

    private final LobbySystem plugin;

    public GameModeListener(LobbySystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        plugin.data().preload(event.getUniqueId(), event.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        var data = plugin.data().get(p);
        if (!data.name().equals(p.getName())) data.name(p.getName());
        plugin.data().presence(p.getUniqueId(), plugin.configs().cfg().proxyServerName);
        plugin.clans().refresh(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) plugin.friends().announce(p, true);
        }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        plugin.data().offline(p.getUniqueId(), plugin.configs().cfg().proxyServerName);
        plugin.friends().announce(p, false);
        plugin.friends().forget(p.getUniqueId());
        plugin.clans().forget(p.getUniqueId());
        plugin.data().unload(p.getUniqueId());
    }
}
