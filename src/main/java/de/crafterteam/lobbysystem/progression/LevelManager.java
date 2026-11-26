package de.crafterteam.lobbysystem.progression;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.ConfigManager;
import de.crafterteam.lobbysystem.config.PluginConfig;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;

/** Lobby-Level: XP aus Spielzeit, Belohnungen, Cookie Clicker und Spielmodi (über die API). */
public final class LevelManager {

    private final LobbySystem plugin;

    public LevelManager(LobbySystem plugin) {
        this.plugin = plugin;
    }

    private PluginConfig.Levels cfg() {
        return plugin.configs().cfg().levels;
    }

    /** XP, die für den Schritt von Level n auf n+1 nötig sind. */
    public long needed(int level) {
        return Math.max(1, cfg().base + (long) (level - 1) * cfg().step);
    }

    public int level(long xp) {
        int level = 1;
        long rest = xp;
        while (rest >= needed(level) && level < 10_000) {
            rest -= needed(level);
            level++;
        }
        return level;
    }

    /** XP innerhalb des aktuellen Levels. */
    public long progress(long xp) {
        int level = 1;
        long rest = xp;
        while (rest >= needed(level) && level < 10_000) {
            rest -= needed(level);
            level++;
        }
        return rest;
    }

    public int level(Player p) {
        return level(plugin.data().get(p).xp());
    }

    /** Fortschrittsbalken für Scoreboard/Menüs. */
    public String bar(long xp, int length) {
        int level = level(xp);
        double ratio = progress(xp) / (double) needed(level);
        int filled = (int) Math.round(ratio * length);
        return "<gradient:#f7971e:#ffd200>" + "|".repeat(filled) + "</gradient><dark_gray>" + "|".repeat(Math.max(0, length - filled));
    }

    public void addXp(Player p, long amount) {
        if (!cfg().enabled || amount <= 0) return;
        PlayerData d = plugin.data().get(p);
        int before = level(d.xp());
        d.addXp(amount);
        int after = level(d.xp());
        if (after > before) levelUp(p, before, after);
        updateBar(p);
    }

    private void levelUp(Player p, int from, int to) {
        long coins = 0;
        for (int l = from + 1; l <= to; l++) coins += (long) l * cfg().coinsPerLevel;
        if (coins > 0) plugin.coins().change(p.getUniqueId(), coins, "level_up");
        ConfigManager c = plugin.configs();
        c.send(p, "level_up", "level", Integer.toString(to), "coins", Text.formatNumber(coins));
        p.showTitle(Title.title(Text.parse("<gradient:#f7971e:#ffd200><b>ʟᴇᴠᴇʟ " + to + "</b></gradient>"),
                Text.parse("<gray>+" + Text.formatNumber(coins) + " Coins"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800), Duration.ofMillis(500))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 50, 0.4, 0.8, 0.4, 0.35);
        int every = cfg().announceEvery;
        if (every > 0 && to % every == 0) {
            Bukkit.broadcast(c.msg("level_broadcast", "name", p.getName(), "level", Integer.toString(to)));
        }
    }

    /** Level in der Vanilla-XP-Leiste anzeigen. */
    public void updateBar(Player p) {
        if (!cfg().enabled || !cfg().expBar || !plugin.lobbyMode()) return;
        long xp = plugin.data().get(p).xp();
        int level = level(xp);
        p.setLevel(level);
        p.setExp((float) Math.min(0.999, progress(xp) / (double) needed(level)));
    }

    /** Jede Minute für alle Online-Spieler. */
    public void minute() {
        int xp = cfg().xpPerMinute;
        if (xp <= 0) return;
        for (Player p : Bukkit.getOnlinePlayers()) addXp(p, xp);
    }
}
