package de.crafterteam.lobbysystem.gui;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.progression.CookieManager;
import de.crafterteam.lobbysystem.progression.CookieManager.Upgrade;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import de.crafterteam.lobbysystem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Cookie Clicker – großer Keks, Upgrades, goldene Kekse, Tausch in Coins. Aktualisiert sich jede Sekunde. */
public final class CookieMenu extends Menu {

    private static final int COOKIE = 20;
    private static final int[] UPGRADE_SLOTS = {14, 15, 16, 23, 24, 25, 32, 33};
    private static final int[] GOLDEN_SLOTS = {10, 12, 28, 30, 37, 39, 41, 43};
    private BukkitTask task;
    private int goldenSlot = -1;
    private int goldenTicks;
    private String lastClick = "";

    public CookieMenu(LobbySystem plugin, Player player) {
        super(plugin, player);
        create(6, "<dark_gray>»</dark_gray> <gradient:#c79081:#dfa579>ᴄᴏᴏᴋɪᴇ ᴄʟɪᴄᴋᴇʀ</gradient>");
    }

    private CookieManager cm() {
        return plugin.cookies();
    }

    @Override
    public void open() {
        super.open();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder(false) != this) {
                task.cancel();
                return;
            }
            if (goldenSlot >= 0 && --goldenTicks <= 0) goldenSlot = -1;
            if (goldenSlot < 0 && ThreadLocalRandom.current().nextDouble() * 100 < plugin.configs().cfg().cookieClicker.goldenChance) {
                goldenSlot = GOLDEN_SLOTS[ThreadLocalRandom.current().nextInt(GOLDEN_SLOTS.length)];
                goldenTicks = 4;
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.6f);
            }
            refresh();
        }, 20L, 20L);
    }

    @Override
    public void onClose() {
        if (task != null) task.cancel();
    }

    @Override
    protected void build() {
        PlayerData d = plugin.data().get(player);
        long xp = d.xp();
        int level = plugin.levels().level(xp);

        set(4, new ItemBuilder(Material.KNOWLEDGE_BOOK).name("<gold>" + CookieManager.format(d.cookies()) + " Kekse")
                .lore("<dark_gray>↪</dark_gray> <gray>Pro Klick: <white>" + CookieManager.format(cm().perClick(d)),
                        "<dark_gray>↪</dark_gray> <gray>Pro Sekunde: <white>" + CookieManager.format(cm().perSecond(d)),
                        "<dark_gray>↪</dark_gray> <gray>Insgesamt gebacken: <white>" + CookieManager.format(d.cookie("baked")),
                        "",
                        "<dark_gray>↪</dark_gray> <gray>Lobby-Level: <gold>" + level,
                        "<dark_gray>↪</dark_gray> " + plugin.levels().bar(xp, 30)).build());

        set(COOKIE, new ItemBuilder(Material.COOKIE).name("<gold><b>ᴋʟɪᴄᴋ ᴍɪᴄʜ!</b>")
                .lore("<dark_gray>↪</dark_gray> <gray>+" + CookieManager.format(cm().perClick(d)) + " Kekse pro Klick",
                        lastClick.isEmpty() ? "" : "<dark_gray>↪</dark_gray> " + lastClick)
                .glow(true).build(), e -> {
            long baked = cm().click(player);
            if (baked == 0) return;
            if (baked < 0) {
                lastClick = "<gold><b>ᴋʀɪᴛɪꜱᴄʜ!</b></gold> <yellow>+" + CookieManager.format(-baked);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.4f);
            } else {
                lastClick = "<yellow>+" + CookieManager.format(baked);
                player.playSound(player.getLocation(), Sound.BLOCK_WOOL_BREAK, 0.6f, 1.6f);
            }
            refresh();
        });

        for (int i = 0; i < Upgrade.values().length && i < UPGRADE_SLOTS.length; i++) {
            Upgrade u = Upgrade.values()[i];
            long owned = cm().owned(d, u);
            double cost = cm().cost(d, u, 1);
            double cost10 = cm().cost(d, u, 10);
            boolean affordable = d.cookies() >= cost && owned < u.max();
            List<String> lore = new ArrayList<>(List.of(
                    "<dark_gray>↪</dark_gray> <gray>" + u.description,
                    "<dark_gray>↪</dark_gray> <gray>Besitz: <white>" + owned + (u == Upgrade.MOLD ? "/" + u.max() : ""),
                    ""));
            if (owned >= u.max()) lore.add("<dark_gray>»</dark_gray> <green>Maximal ausgebaut");
            else {
                lore.add("<dark_gray>»</dark_gray> " + (affordable ? "<green>" : "<red>") + "Links: " + CookieManager.format(cost) + " Kekse");
                if (u != Upgrade.MOLD) lore.add("<dark_gray>»</dark_gray> <gray>Shift: 10× für " + CookieManager.format(cost10));
            }
            set(UPGRADE_SLOTS[i], new ItemBuilder(u.icon).name((affordable ? "<green>" : "<gray>") + u.label)
                    .lore(lore).amount((int) Math.max(1, Math.min(64, owned))).glow(affordable).hideFlags().build(), e -> {
                int amount = e.getClick().isShiftClick() ? 10 : 1;
                if (cm().buy(player, u, amount)) {
                    success();
                    plugin.configs().send(player, "cookie_bought", "upgrade", u.label, "amount", Integer.toString(amount));
                } else {
                    deny();
                    double missing = cm().cost(plugin.data().get(player), u, amount) - plugin.data().get(player).cookies();
                    if (missing > 0) plugin.configs().send(player, "cookie_not_enough", "missing", CookieManager.format(missing));
                }
                refresh();
            });
        }

        if (goldenSlot >= 0) {
            set(goldenSlot, new ItemBuilder(Material.GOLDEN_APPLE).name("<gold><b>ɢᴏʟᴅᴇɴᴇʀ ᴋᴇᴋꜱ</b>")
                    .lore("<dark_gray>↪</dark_gray> <yellow>Schnell klicken!").glow(true).build(), e -> {
                long bonus = Math.max(100, cm().perSecond(d) * 60 + cm().perClick(d) * 20);
                cm().bake(player, d, bonus);
                goldenSlot = -1;
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
                plugin.configs().send(player, "cookie_golden", "cookies", CookieManager.format(bonus));
                refresh();
            });
        }

        long rate = plugin.configs().cfg().cookieClicker.cookiesPerCoin;
        if (rate > 0) {
            set(48, new ItemBuilder(Material.SUNFLOWER).name("<yellow>Kekse tauschen")
                    .lore("<dark_gray>↪</dark_gray> <gray>" + CookieManager.format(rate) + " Kekse = 1 Coin",
                            "<dark_gray>↪</dark_gray> <gray>Heute getauscht: <white>" + soldToday(d) + "<dark_gray>/</dark_gray><white>"
                                    + plugin.configs().cfg().cookieClicker.maxCoinsPerDay + " Coins",
                            "", "<dark_gray>»</dark_gray> <yellow>Klicke zum Tauschen").build(), e -> {
                long[] res = cm().sell(player);
                if (res == null) return;
                if (res[1] <= 0) {
                    deny();
                    plugin.configs().send(player, "cookie_sell_limit");
                } else {
                    success();
                    plugin.configs().send(player, "cookie_sold", "cookies", CookieManager.format(res[0]), "coins", Text.formatNumber(res[1]));
                }
                refresh();
            });
        }
        set(50, new ItemBuilder(Material.BARRIER).name("<red>Schließen").build(), e -> player.closeInventory());
        fill(Material.BROWN_STAINED_GLASS_PANE);
    }

    private long soldToday(PlayerData d) {
        long today = Long.parseLong(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
        return d.cookie("day") == today ? d.cookie("sold") : 0;
    }
}
