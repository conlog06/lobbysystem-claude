package de.crafterteam.lobbysystem.gui;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.progression.WeeklyManager;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import de.crafterteam.lobbysystem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Wochen-Challenges mit Fortschrittsbalken & Abholen. */
public final class WeeklyMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private final List<WeeklyManager.Progress> list;

    private WeeklyMenu(LobbySystem plugin, Player player, List<WeeklyManager.Progress> list) {
        super(plugin, player);
        this.list = list;
        create(4, "<dark_gray>↪</dark_gray> <gradient:#f7971e:#ffd200>ᴡᴏᴄʜᴇɴ-ᴄʜᴀʟʟᴇɴɢᴇꜱ</gradient>");
    }

    public static void open(LobbySystem plugin, Player player) {
        if (!plugin.configs().cfg().weekly.enabled) return;
        plugin.weekly().load(player).exceptionally(e -> List.of()).thenAccept(l -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) new WeeklyMenu(plugin, player, l).open();
        }));
    }

    static String bar(long value, long goal, int width) {
        int filled = goal <= 0 ? width : (int) Math.min(width, value * width / goal);
        return "<green>" + "|".repeat(filled) + "<dark_gray>" + "|".repeat(width - filled);
    }

    @Override
    protected void build() {
        long reset = WeeklyManager.secondsUntilReset();
        set(4, new ItemBuilder(Material.CLOCK).name("<dark_gray>»</dark_gray> <gold>Diese Woche")
                .lore("<dark_gray>↪</dark_gray> <gray>Neue Challenges in <white>" + Text.formatDuration(reset),
                        "<dark_gray>↪</dark_gray> <gray>Fortschritt zählt ab Wochenbeginn").icon(player, "daily").build());
        for (int i = 0; i < list.size() && i < SLOTS.length; i++) {
            WeeklyManager.Progress pr = list.get(i);
            var c = pr.challenge();
            long shown = Math.min(pr.progress(), c.goal);
            ItemBuilder b = new ItemBuilder(pr.claimed() ? Material.LIME_DYE : ItemBuilder.material(c.icon, Material.PAPER))
                    .name("<dark_gray>»</dark_gray> " + c.name)
                    .lore("<dark_gray>↪</dark_gray> " + bar(shown, c.goal, 20) + " <gray>" + shown + "/" + c.goal,
                            "<dark_gray>↪</dark_gray> {i_coin} <gray>Belohnung: <yellow>" + Text.formatNumber(c.reward) + " Coins", "",
                            pr.claimed() ? "<dark_gray>↪</dark_gray> <green>✔ Abgeholt"
                                    : pr.done() ? "<dark_gray>»</dark_gray> <yellow>Klicke zum Abholen!" : "<dark_gray>↪</dark_gray> <gray>Noch nicht geschafft")
                    .glow(pr.done() && !pr.claimed()).hideFlags();
            set(SLOTS[i], b.build(), e -> {
                if (!pr.done() || pr.claimed()) {
                    deny();
                    return;
                }
                plugin.weekly().claim(player, pr).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (ok) {
                        success();
                        plugin.configs().send(player, "weekly_claimed", "challenge", c.name, "coins", Text.formatNumber(c.reward));
                    }
                    open(plugin, player);
                }));
            });
        }
        set(31, new ItemBuilder(Material.ARROW).name("<gray>« Zurück zum Profil").build(), e -> {
            click();
            ProfileMenu.open(plugin, player);
        });
        fill(Material.GRAY_STAINED_GLASS_PANE);
    }
}
