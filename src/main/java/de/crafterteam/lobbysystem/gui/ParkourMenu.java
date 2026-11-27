package de.crafterteam.lobbysystem.gui;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.lobby.ParkourManager;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import de.crafterteam.lobbysystem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Parkour-Übersicht: persönliche Bestzeit, Top 3, Klick = zum Start teleportieren. */
public final class ParkourMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private final Map<String, Long> personal;
    private final Map<String, List<String[]>> tops;

    private ParkourMenu(LobbySystem plugin, Player player, Map<String, Long> personal, Map<String, List<String[]>> tops) {
        super(plugin, player);
        this.personal = personal;
        this.tops = tops;
        create(4, "<dark_gray>↪</dark_gray> <gradient:#00d2ff:#3a7bd5>ᴘᴀʀᴋᴏᴜʀ</gradient>");
    }

    public static void open(LobbySystem plugin, Player player) {
        ParkourManager pm = plugin.parkour();
        if (pm == null) return;
        List<String> ids = new ArrayList<>(pm.courses().keySet());
        Map<String, List<String[]>> tops = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.concurrent.CompletableFuture<?>[] all = new java.util.concurrent.CompletableFuture<?>[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            all[i] = pm.top(id, 3).thenAccept(l -> tops.put(id, l));
        }
        java.util.concurrent.CompletableFuture.allOf(all).thenCompose(v -> pm.personal(player.getUniqueId()))
                .exceptionally(e -> Map.of())
                .thenAccept(personal -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) new ParkourMenu(plugin, player, personal, tops).open();
                }));
    }

    @Override
    protected void build() {
        ParkourManager pm = plugin.parkour();
        int i = 0;
        if (pm.courses().isEmpty()) {
            set(13, new ItemBuilder(Material.BARRIER).name("<red>Noch kein Parkour eingerichtet")
                    .lore("<dark_gray>↪</dark_gray> <gray>Admins: <white>/parkour create <id>").build());
        }
        for (Map.Entry<String, ParkourManager.Course> e : pm.courses().entrySet()) {
            if (i >= SLOTS.length) break;
            String id = e.getKey();
            ParkourManager.Course c = e.getValue();
            Long best = personal.get(id);
            List<String> lore = new ArrayList<>();
            lore.add("<dark_gray>↪</dark_gray> <gray>Checkpoints: <white>" + c.checkpoints.size());
            lore.add("<dark_gray>↪</dark_gray> <gray>Deine Bestzeit: " + (best == null ? "<red>–" : "<aqua>" + ParkourManager.format(best)));
            lore.add("<dark_gray>↪</dark_gray> {i_coin} <gray>Erster Abschluss: <yellow>" + Text.formatNumber(c.reward) + " Coins");
            lore.add("");
            List<String[]> top = tops.getOrDefault(id, List.of());
            if (top.isEmpty()) lore.add("<dark_gray>↪</dark_gray> <gray>Noch keine Zeiten – sei der Erste!");
            String[] medals = {"<gold>", "<gray>", "<#cd7f32>"};
            for (int t = 0; t < top.size(); t++) {
                lore.add(medals[t] + (t + 1) + ". <white>" + Text.escape(top.get(t)[0]) + " <dark_gray>»</dark_gray> <aqua>" + top.get(t)[1]);
            }
            lore.add("");
            lore.add("<dark_gray>»</dark_gray> <yellow>Klicke zum Teleportieren");
            set(SLOTS[i++], new ItemBuilder(Material.LIGHT_WEIGHTED_PRESSURE_PLATE).name("<dark_gray>»</dark_gray> " + c.display)
                    .lore(lore).glow(best != null).hideFlags().build(), ev -> {
                Location l = c.start == null ? null : c.start.toLocation();
                if (l == null) {
                    deny();
                    return;
                }
                click();
                player.closeInventory();
                player.teleport(l.getBlock().getLocation().add(0.5, 0.1, 0.5).setDirection(player.getLocation().getDirection()));
            });
        }
        set(31, new ItemBuilder(Material.ARROW).name("<gray>« Zurück zum Profil").build(), ev -> {
            click();
            ProfileMenu.open(plugin, player);
        });
        fill(Material.GRAY_STAINED_GLASS_PANE);
    }
}
