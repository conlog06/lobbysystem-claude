package de.crafterteam.lobbysystem.gui;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.PluginConfig;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Navigator aus config.json → "navigatorItems":
 * "Slot;MATERIAL;Ziel;Name;Lore"  (Lore-Zeilen mit \n trennen)
 * Beispiel: "12;IRON_AXE;connect:CityBuild-1;§7» §2CityBuild;§7» §7Klicke zum Joinen"
 */
public final class NavigatorMenu extends Menu {

    public NavigatorMenu(LobbySystem plugin, Player player) {
        super(plugin, player);
        PluginConfig cfg = plugin.configs().cfg();
        create(cfg.navigatorRows, cfg.navigatorTitle);
    }

    @Override
    protected void build() {
        for (String line : plugin.configs().cfg().navigatorItems) {
            String[] p = line.split(";", 5);
            if (p.length < 4) {
                plugin.getLogger().warning("Ungültiges navigatorItem: " + line);
                continue;
            }
            int slot;
            try {
                slot = Integer.parseInt(p[0].trim());
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Ungültiger Slot in navigatorItem: " + line);
                continue;
            }
            Material mat = ItemBuilder.material(p[1], Material.STONE);
            String target = p[2].trim();
            List<String> lore = p.length > 4 ? new ArrayList<>(Arrays.asList(p[4].split("\\\\n|\n"))) : List.of();
            set(slot, new ItemBuilder(mat).name(p[3]).lore(lore).hideFlags().build(), e -> {
                click();
                plugin.lobby().runTarget(player, target);
            });
        }
        border(Material.BLACK_STAINED_GLASS_PANE);
    }
}
