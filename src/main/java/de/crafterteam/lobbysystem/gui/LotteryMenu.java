package de.crafterteam.lobbysystem.gui;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.PluginConfig;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class LotteryMenu extends Menu {

    public LotteryMenu(LobbySystem plugin, Player player) {
        super(plugin, player);
        create(1, "<dark_gray>↪</dark_gray> <gradient:#f7971e:#ffd200>ʟᴏᴛᴛᴇʀɪᴇ</gradient>");
    }

    @Override
    protected void build() {
        PlayerData d = plugin.data().get(player);
        set(1, new ItemBuilder(Material.PAPER).name("<dark_gray>»</dark_gray> <aqua>Deine Lose")
                .lore("<dark_gray>↪</dark_gray> <gray>Du hast <yellow>" + d.lose() + "</yellow> <gray>Lose",
                        "<dark_gray>↪</dark_gray> <gray>Neue Lose gibt es bei der <light_purple>täglichen Belohnung").build());

        List<PluginConfig.LotteryReward> rewards = plugin.configs().cfg().lottery.rewards;
        int total = rewards.stream().mapToInt(r -> Math.max(0, r.weight)).sum();
        List<String> lore = new ArrayList<>();
        for (PluginConfig.LotteryReward r : rewards) {
            double chance = total == 0 ? 0 : r.weight * 100.0 / total;
            lore.add("<dark_gray>»</dark_gray> <gold>" + r.coins + " Coins <dark_gray>(" + String.format(java.util.Locale.GERMANY, "%.1f", chance) + "%)");
        }
        set(4, new ItemBuilder(Material.CHEST).name("<dark_gray>»</dark_gray> <yellow>Mögliche Gewinne").lore(lore).build());

        set(7, new ItemBuilder(Material.GOLD_INGOT).name("<dark_gray>»</dark_gray> <yellow>Los benutzen")
                .lore("<dark_gray>↪</dark_gray> <gray>Rubbel dein Los frei!").glow(d.lose() > 0).build(), e -> {
            if (d.lose() <= 0) {
                deny();
                plugin.configs().send(player, "lottery_no_tickets");
                return;
            }
            d.addLose(-1);
            click();
            plugin.lobby().giveHotbar(player);
            new ScratchMenu(plugin, player).open();
        });
        fill(Material.GRAY_STAINED_GLASS_PANE);
    }
}
