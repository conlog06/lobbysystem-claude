package de.crafterteam.lobbysystem.progression;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.PluginConfig;
import de.crafterteam.lobbysystem.storage.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cookie Clicker zum Leveln: Kekse klicken, Upgrades kaufen (passive Produktion),
 * gebackene Kekse geben Lobby-XP, Kekse lassen sich (begrenzt) in Coins tauschen.
 */
public final class CookieManager {

    public enum Upgrade {
        CURSOR("Cursor", Material.ARROW, 15, 0, 1, "+1 Keks pro Klick"),
        GRANDMA("Oma", Material.CAKE, 100, 1, 0, "+1 Keks/Sekunde"),
        FARM("Keksfarm", Material.WHEAT, 1_100, 8, 0, "+8 Kekse/Sekunde"),
        MINE("Keksmine", Material.IRON_PICKAXE, 12_000, 47, 0, "+47 Kekse/Sekunde"),
        FACTORY("Fabrik", Material.FURNACE, 130_000, 260, 0, "+260 Kekse/Sekunde"),
        BANK("Bank", Material.GOLD_BLOCK, 1_400_000, 1_400, 0, "+1.400 Kekse/Sekunde"),
        TEMPLE("Tempel", Material.ENCHANTING_TABLE, 20_000_000, 7_800, 0, "+7.800 Kekse/Sekunde"),
        MOLD("Goldene Backform", Material.GOLDEN_CARROT, 5_000, 0, 0, "×2 Kekse pro Klick (max. 10)");

        public final String label;
        public final Material icon;
        public final long baseCost;
        public final long perSecond;
        public final long perClick;
        public final String description;

        Upgrade(String label, Material icon, long baseCost, long perSecond, long perClick, String description) {
            this.label = label;
            this.icon = icon;
            this.baseCost = baseCost;
            this.perSecond = perSecond;
            this.perClick = perClick;
            this.description = description;
        }

        public String key() {
            return name().toLowerCase();
        }

        public int max() {
            return this == MOLD ? 10 : 1000;
        }
    }

    private final LobbySystem plugin;
    private final Map<UUID, long[]> clickWindow = new HashMap<>();

    public CookieManager(LobbySystem plugin) {
        this.plugin = plugin;
    }

    private PluginConfig.CookieClicker cfg() {
        return plugin.configs().cfg().cookieClicker;
    }

    public long owned(PlayerData d, Upgrade u) {
        return d.cookie(u.key());
    }

    public double cost(PlayerData d, Upgrade u, int amount) {
        double total = 0;
        long owned = owned(d, u);
        double base = u == Upgrade.MOLD ? u.baseCost * Math.pow(10, owned) : u.baseCost * Math.pow(1.15, owned);
        for (int i = 0; i < amount; i++) {
            total += base;
            base *= u == Upgrade.MOLD ? 10 : 1.15;
        }
        return Math.ceil(total);
    }

    public long perClick(PlayerData d) {
        long base = 1 + owned(d, Upgrade.CURSOR);
        return base * (1L << Math.min(10, owned(d, Upgrade.MOLD)));
    }

    public long perSecond(PlayerData d) {
        long sum = 0;
        for (Upgrade u : Upgrade.values()) sum += u.perSecond * owned(d, u);
        return sum;
    }

    /** @return gebackene Kekse dieses Klicks (0 = Anti-Autoklicker hat geblockt), negativ = kritisch */
    public long click(Player p) {
        long now = System.currentTimeMillis();
        long[] w = clickWindow.computeIfAbsent(p.getUniqueId(), k -> new long[]{now, 0});
        if (now - w[0] > 1000) {
            w[0] = now;
            w[1] = 0;
        }
        if (++w[1] > 20) return 0; // max. 20 Klicks pro Sekunde
        PlayerData d = plugin.data().get(p);
        long amount = perClick(d);
        boolean crit = Math.random() * 100 < cfg().critChance;
        if (crit) amount *= Math.max(1, cfg().critMultiplier);
        bake(p, d, amount);
        return crit ? -amount : amount;
    }

    /** Kekse gutschreiben + Statistik + XP-Umrechnung. */
    public void bake(Player p, PlayerData d, double amount) {
        if (amount <= 0) return;
        d.addCookies(amount);
        d.cookie("baked", d.cookie("baked") + (long) amount);
        long per = Math.max(1, cfg().cookiesPerXp);
        long pool = d.cookie("xppool") + (long) amount;
        long xp = pool / per;
        d.cookie("xppool", pool % per);
        if (xp > 0) plugin.levels().addXp(p, Math.min(xp, 500)); // Deckel gegen Level-Explosion
    }

    public boolean buy(Player p, Upgrade u, int amount) {
        PlayerData d = plugin.data().get(p);
        amount = (int) Math.min(amount, u.max() - owned(d, u));
        if (amount <= 0) return false;
        double cost = cost(d, u, amount);
        synchronized (d) {
            if (d.cookies() < cost) return false;
            d.cookies(d.cookies() - cost);
        }
        d.cookie(u.key(), owned(d, u) + amount);
        return true;
    }

    /** Kekse in Coins tauschen (mit Tageslimit). @return [Kekse, Coins] oder null */
    public long[] sell(Player p) {
        long rate = cfg().cookiesPerCoin;
        if (rate <= 0) return null;
        PlayerData d = plugin.data().get(p);
        long today = Long.parseLong(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        if (d.cookie("day") != today) {
            d.cookie("day", today);
            d.cookie("sold", 0);
        }
        long left = cfg().maxCoinsPerDay - d.cookie("sold");
        long coins = Math.min(left, (long) (d.cookies() / rate));
        if (coins <= 0) return new long[]{0, 0};
        long cookies = coins * rate;
        d.cookies(d.cookies() - cookies);
        d.cookie("sold", d.cookie("sold") + coins);
        plugin.coins().change(p.getUniqueId(), coins, "cookie_sell");
        return new long[]{cookies, coins};
    }

    /** Jede Sekunde: passive Produktion für alle Online-Spieler. */
    public void second() {
        if (!cfg().enabled) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.data().get(p);
            long ps = perSecond(d);
            if (ps > 0) bake(p, d, ps);
        }
    }

    public void quit(UUID id) {
        clickWindow.remove(id);
    }

    public static String format(double v) {
        if (v >= 1e12) return String.format(java.util.Locale.GERMANY, "%.2f Bio.", v / 1e12);
        if (v >= 1e9) return String.format(java.util.Locale.GERMANY, "%.2f Mrd.", v / 1e9);
        if (v >= 1e6) return String.format(java.util.Locale.GERMANY, "%.2f Mio.", v / 1e6);
        return String.format(java.util.Locale.GERMANY, "%,d", (long) v);
    }
}
