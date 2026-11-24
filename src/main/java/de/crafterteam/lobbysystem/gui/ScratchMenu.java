package de.crafterteam.lobbysystem.gui;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.PluginConfig;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Rubbellos: 5 verdeckte Felder, eins wählen – Gewinn wird gutgeschrieben. */
public final class ScratchMenu extends Menu {

    private static final int[] SLOTS = {11, 12, 13, 14, 15};
    private final int[] prizes = new int[SLOTS.length];
    private boolean picked;

    public ScratchMenu(LobbySystem plugin, Player player) {
        super(plugin, player);
        create(3, "<dark_gray>↪</dark_gray> <gradient:#f7971e:#ffd200>ʀᴜʙʙᴇʟʟᴏꜱ</gradient> <dark_gray>|</dark_gray> <gray>Wähle ein Feld");
        for (int i = 0; i < prizes.length; i++) prizes[i] = roll(plugin.configs().cfg().lottery.rewards);
    }

    private static int roll(List<PluginConfig.LotteryReward> rewards) {
        int total = rewards.stream().mapToInt(r -> Math.max(0, r.weight)).sum();
        if (total <= 0) return 0;
        int x = ThreadLocalRandom.current().nextInt(total);
        for (PluginConfig.LotteryReward r : rewards) {
            x -= Math.max(0, r.weight);
            if (x < 0) return r.coins;
        }
        return rewards.get(rewards.size() - 1).coins;
    }

    @Override
    protected void build() {
        for (int i = 0; i < SLOTS.length; i++) {
            int idx = i;
            set(SLOTS[i], new ItemBuilder(Material.LIGHT_GRAY_CONCRETE_POWDER).name("<dark_gray>»</dark_gray> <gray>???")
                    .lore("<dark_gray>»</dark_gray> <yellow>Klicke zum Freirubbeln").build(), e -> pick(idx));
        }
        fill(Material.BLACK_STAINED_GLASS_PANE);
    }

    private void pick(int idx) {
        if (picked) return;
        picked = true;
        PlayerData d = plugin.data().get(player);
        int win = prizes[idx];
        plugin.coins().change(player.getUniqueId(), win, "lottery");
        plugin.configs().send(player, "lottery_win", "coins", Integer.toString(win));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        // Alle Felder aufdecken – das gewählte leuchtet
        for (int i = 0; i < SLOTS.length; i++) {
            boolean chosen = i == idx;
            set(SLOTS[i], new ItemBuilder(chosen ? Material.GOLD_BLOCK : Material.GRAY_CONCRETE)
                    .name((chosen ? "<gold>" : "<gray>") + prizes[i] + " Coins")
                    .lore(chosen ? "<dark_gray>»</dark_gray> <green>Dein Gewinn!" : "<dark_gray>»</dark_gray> <gray>Nicht gewählt")
                    .glow(chosen).build());
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder(false) == this) {
                new LotteryMenu(plugin, player).open();
            }
        }, 50L);
    }

    @Override
    public void onClose() {
        // Los wurde schon verbraucht → beim Schließen automatisch ein Feld wählen
        if (!picked) pick(ThreadLocalRandom.current().nextInt(SLOTS.length));
    }
}
