package de.crafterteam.lobbysystem.gui;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.PluginConfig;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import de.crafterteam.lobbysystem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rang-Shop: Ränge für (sehr viele) Coins kaufen. Vergabe über einen Konsolenbefehl (Standard: LuckPerms),
 * Bestätigung per zweitem Klick innerhalb von 5 Sekunden.
 */
public final class RankShopMenu extends Menu {

    private static final int[] SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};
    /** Spieler → Rang-Key, der gerade bestätigt werden muss (+ Zeitpunkt) */
    private static final Map<UUID, Object[]> CONFIRM = new ConcurrentHashMap<>();

    public RankShopMenu(LobbySystem plugin, Player player) {
        super(plugin, player);
        create(4, "<dark_gray>↪</dark_gray> <gradient:#f9d423:#ff4e50>ʀᴀɴɢ-ꜱʜᴏᴘ</gradient>");
    }

    private PluginConfig.RankShop cfg() {
        return plugin.configs().cfg().rankShop;
    }

    @Override
    protected void build() {
        long coins = plugin.data().get(player).coins();
        set(4, new ItemBuilder(Material.GOLD_INGOT).name("<dark_gray>»</dark_gray> <yellow>Dein Guthaben")
                .lore("<dark_gray>↪</dark_gray> <gold>" + Text.formatNumber(coins) + " Coins").icon(player, "coins").build());
        List<PluginConfig.RankOffer> offers = cfg().ranks;
        for (int i = 0; i < offers.size() && i < SLOTS.length; i++) {
            PluginConfig.RankOffer o = offers.get(i);
            boolean owned = player.hasPermission("lobbysystem.rank." + o.key);
            boolean affordable = coins >= o.price;
            Object[] pending = CONFIRM.get(player.getUniqueId());
            boolean confirming = pending != null && o.key.equals(pending[0]) && (long) pending[1] > System.currentTimeMillis();
            List<String> lore = new ArrayList<>();
            lore.add("<dark_gray>↪</dark_gray> <gray>Preis: <gold>" + Text.formatNumber(o.price) + " Coins");
            lore.add("<dark_gray>↪</dark_gray> <gray>Dauer: <white>" + (o.days <= 0 ? "Permanent" : o.days + " Tage"));
            lore.add("");
            lore.add("<gray>Features:");
            for (String f : o.features) lore.add("<dark_gray>»</dark_gray> <white>" + f);
            lore.add("");
            if (owned && o.days <= 0) lore.add("<green>✔ Bereits freigeschaltet");
            else if (confirming) lore.add("<gold><b>Nochmal klicken zum Kaufen!</b>");
            else if (affordable) lore.add("<dark_gray>»</dark_gray> <yellow>" + (owned ? "Klicke zum Verlängern" : "Klicke zum Kaufen"));
            else lore.add("<red>Dir fehlen " + Text.formatNumber(o.price - coins) + " Coins");
            set(SLOTS[i], new ItemBuilder(ItemBuilder.material(o.material, Material.NAME_TAG))
                    .name(o.display).lore(lore).glow(owned || confirming).hideFlags().build(), e -> buy(o, owned));
        }
        set(31, new ItemBuilder(Material.ARROW).name("<dark_gray>»</dark_gray> <gray>Zurück zum Profil").icon(player, "back").build(), e -> {
            click();
            de.crafterteam.lobbysystem.gui.ProfileMenu.open(plugin, player);
        });
        border(Material.BLACK_STAINED_GLASS_PANE);
    }

    private void buy(PluginConfig.RankOffer o, boolean owned) {
        if (owned && o.days <= 0) {
            deny();
            return;
        }
        if (cfg().requireLuckPerms && Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            deny();
            plugin.configs().send(player, "rankshop_no_luckperms");
            return;
        }
        long coins = plugin.data().get(player).coins();
        if (coins < o.price) {
            deny();
            plugin.configs().send(player, "rankshop_not_enough", "missing", Text.formatNumber(o.price - coins), "rank", o.display);
            return;
        }
        Object[] pending = CONFIRM.get(player.getUniqueId());
        if (pending == null || !o.key.equals(pending[0]) || (long) pending[1] < System.currentTimeMillis()) {
            click();
            CONFIRM.put(player.getUniqueId(), new Object[]{o.key, System.currentTimeMillis() + 5000L});
            refresh();
            return;
        }
        CONFIRM.remove(player.getUniqueId());
        plugin.coins().removeCoins(player.getUniqueId(), o.price, "rankshop:" + o.key).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!ok) {
                deny();
                plugin.configs().send(player, "rankshop_not_enough", "missing", "?", "rank", o.display);
                return;
            }
            String template = o.days <= 0 ? cfg().commandPermanent : cfg().commandTemporary;
            String cmd = template.replace("{player}", player.getName()).replace("{uuid}", player.getUniqueId().toString())
                    .replace("{group}", o.group).replace("{days}", Integer.toString(o.days));
            boolean done = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            if (!done) {
                // Befehl unbekannt → Coins zurück
                plugin.coins().addCoins(player.getUniqueId(), o.price, "rankshop_refund");
                plugin.getLogger().warning("Rang-Shop: Befehl fehlgeschlagen: /" + cmd);
                plugin.configs().send(player, "rankshop_failed");
                return;
            }
            plugin.getLogger().info("Rang-Shop: " + player.getName() + " kaufte " + o.key + " für " + o.price + " Coins (" + cmd + ")");
            success();
            plugin.configs().send(player, "rankshop_bought", "rank", o.display, "days", o.days <= 0 ? "permanent" : o.days + " Tage");
            if (cfg().broadcast) {
                var msg = plugin.configs().msg("rankshop_broadcast", "name", player.getName(), "rank", o.display);
                for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
            }
            player.closeInventory();
            // Rang-Anzeige (Tab/Scoreboard) nach kurzer Zeit neu laden, wenn LuckPerms die Gruppe gesetzt hat
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) plugin.sidebar().create(player);
            }, 40L);
        }));
    }
}
