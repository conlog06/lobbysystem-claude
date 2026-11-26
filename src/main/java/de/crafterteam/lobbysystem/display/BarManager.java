package de.crafterteam.lobbysystem.display;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.PluginConfig;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/** Bossbar mit wechselnden Nachrichten + Actionbar mit Datum/Uhrzeit. */
public final class BarManager {

    private final LobbySystem plugin;
    private BossBar bar;
    private int index;
    private int secondsLeft;
    private int broadcastSeconds;
    private int broadcastIndex;

    public BarManager(LobbySystem plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        PluginConfig.BossBarSettings s = plugin.configs().cfg().bossbar;
        BossBar.Color color;
        BossBar.Overlay overlay;
        try {
            color = BossBar.Color.valueOf(s.color.toUpperCase());
        } catch (IllegalArgumentException e) {
            color = BossBar.Color.GREEN;
        }
        try {
            overlay = BossBar.Overlay.valueOf(s.overlay.toUpperCase());
        } catch (IllegalArgumentException e) {
            overlay = BossBar.Overlay.PROGRESS;
        }
        if (bar != null) {
            bar.color(color);
            bar.overlay(overlay);
        } else {
            bar = BossBar.bossBar(net.kyori.adventure.text.Component.empty(), 1f, color, overlay);
        }
        index = 0;
        secondsLeft = Math.max(1, s.intervalSeconds);
        updateName();
        for (Player p : Bukkit.getOnlinePlayers()) {
            hide(p);
            show(p);
        }
    }

    private void updateName() {
        List<String> msgs = plugin.configs().cfg().bossbar.messages;
        if (msgs.isEmpty()) return;
        bar.name(Text.parse(msgs.get(index % msgs.size())));
    }

    public void show(Player player) {
        PlayerData data = plugin.data().get(player);
        if (plugin.configs().cfg().bossbar.enabled && data.bars()) player.showBossBar(bar);
    }

    public void hide(Player player) {
        if (bar != null) player.hideBossBar(bar);
    }

    /** Jede Sekunde. */
    public void tick() {
        PluginConfig.BossBarSettings s = plugin.configs().cfg().bossbar;
        int interval = Math.max(1, s.intervalSeconds);
        secondsLeft--;
        if (secondsLeft <= 0) {
            secondsLeft = interval;
            index++;
            updateName();
        }
        bar.progress(Math.max(0f, Math.min(1f, secondsLeft / (float) interval)));

        // Auto-Broadcast im Chat
        PluginConfig.Broadcast bc = plugin.configs().cfg().broadcast;
        if (bc.enabled && !bc.messages.isEmpty() && ++broadcastSeconds >= Math.max(10, bc.intervalSeconds)) {
            broadcastSeconds = 0;
            var msg = Text.parse(plugin.configs().cfg().prefix + bc.messages.get(broadcastIndex++ % bc.messages.size()));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!plugin.data().get(p).silent()) p.sendMessage(msg);
            }
        }

        PluginConfig.ActionBar ab = plugin.configs().cfg().actionbar;
        if (!ab.enabled) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.data().get(p);
            if (!data.bars() || plugin.lobby().isBuilding(p)) continue;
            Map<String, String> ph = plugin.sidebar().cachedPlaceholders(p);
            p.sendActionBar(Text.parse(ab.format, ph));
        }
    }
}
