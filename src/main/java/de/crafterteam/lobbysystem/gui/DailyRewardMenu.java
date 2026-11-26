package de.crafterteam.lobbysystem.gui;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.PluginConfig;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import de.crafterteam.lobbysystem.util.Text;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

public final class DailyRewardMenu extends Menu {

    public DailyRewardMenu(LobbySystem plugin, Player player) {
        super(plugin, player);
        create(3, "<dark_gray>↪</dark_gray> <gradient:#b06ab3:#4568dc>ᴛäɢʟɪᴄʜᴇ ʙᴇʟᴏʜɴᴜɴɢ</gradient>");
    }

    @Override
    protected void build() {
        PluginConfig.DailyReward r = plugin.configs().cfg().dailyReward;
        PlayerData d = plugin.data().get(player);
        long cooldown = r.cooldownHours * 3_600_000L;
        long now = System.currentTimeMillis();

        long normalLeft = d.lastDaily() + cooldown - now;
        set(11, reward("<green>Tägliche Belohnung", r.normalCoins, r.normalLose, normalLeft, true), e -> {
            if (normalLeft > 0) {
                deny();
                plugin.configs().send(player, "reward_cooldown", "time", Text.formatCooldown(normalLeft));
                return;
            }
            // Streak: innerhalb von 2× Cooldown wieder abgeholt → +1, sonst Neustart
            boolean keep = d.lastDaily() > 0 && now - d.lastDaily() < cooldown * 2;
            d.streak(keep ? Math.min(r.streakMax, d.streak() + 1) : 1);
            d.lastDaily(now);
            int bonus = Math.max(0, d.streak() - 1) * r.streakBonus;
            claim(r.normalCoins + bonus, r.normalLose);
            if (bonus > 0) plugin.configs().send(player, "streak", "streak", Integer.toString(d.streak()), "bonus", Integer.toString(bonus));
        });

        boolean premium = plugin.configs().has(player, "premium_reward");
        long premiumLeft = d.lastPremium() + cooldown - now;
        set(15, reward("<green>Tägliche Belohnung <gray>(<gold>Premium</gold><gray>)", r.premiumCoins, r.premiumLose, premiumLeft, premium), e -> {
            if (!premium) {
                deny();
                plugin.configs().send(player, "reward_no_premium");
                return;
            }
            if (premiumLeft > 0) {
                deny();
                plugin.configs().send(player, "reward_cooldown", "time", Text.formatCooldown(premiumLeft));
                return;
            }
            d.lastPremium(now);
            claim(r.premiumCoins, r.premiumLose);
        });
        int streak = d.streak();
        set(13, new ItemBuilder(Material.BLAZE_POWDER).name("<dark_gray>»</dark_gray> <gold>Login-Streak: <white>" + streak + " <gray>/ " + r.streakMax)
                .lore("<dark_gray>↪</dark_gray> <gray>Jeder Tag in Folge bringt <yellow>+" + r.streakBonus + " Coins</yellow>",
                        "<dark_gray>↪</dark_gray> <gray>Aktueller Bonus: <yellow>+" + Math.max(0, streak) * r.streakBonus + " Coins").glow(streak > 0).build());
        fill(Material.GRAY_STAINED_GLASS_PANE);
    }

    private org.bukkit.inventory.ItemStack reward(String name, int coins, int lose, long left, boolean allowed) {
        boolean ready = left <= 0 && allowed;
        ItemBuilder b = new ItemBuilder(ready ? Material.PAPER : Material.MAP)
                .name("<dark_gray>»</dark_gray> " + name)
                .lore("<dark_gray>↪</dark_gray> {i_coin} <gold>+" + coins + " Coins</gold> <dark_gray>|</dark_gray> {i_ticket} <yellow>+" + lose + " Lose", "");
        if (!allowed) b.lore("<dark_gray>»</dark_gray> <red>Nur für Premium-Spieler");
        else if (left > 0) b.lore("<dark_gray>»</dark_gray> <gray>Wieder verfügbar in <white>" + Text.formatCooldown(left));
        else b.lore("<dark_gray>»</dark_gray> <green>Klicke zum Abholen!");
        return b.glow(ready).build();
    }

    private void claim(int coins, int lose) {
        PlayerData d = plugin.data().get(player);
        plugin.coins().change(player.getUniqueId(), coins, "daily_reward");
        plugin.levels().addXp(player, plugin.configs().cfg().levels.xpDailyReward);
        d.addLose(lose);
        success();
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.3);
        plugin.configs().send(player, "reward_claimed", "coins", Integer.toString(coins), "lose", Integer.toString(lose));
        plugin.data().save(d);
        plugin.lobby().giveHotbar(player);
        refresh();
    }
}
