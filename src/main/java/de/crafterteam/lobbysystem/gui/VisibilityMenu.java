package de.crafterteam.lobbysystem.gui;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

/** Spielersichtbarkeit im Braustand-Design. */
public final class VisibilityMenu extends Menu {

    public VisibilityMenu(LobbySystem plugin, Player player) {
        super(plugin, player);
        create(InventoryType.BREWING, "<dark_gray>»</dark_gray> <gradient:#00d2ff:#3a7bd5>ꜱᴘɪᴇʟᴇʀꜱɪᴄʜᴛʙᴀʀᴋᴇɪᴛ</gradient>");
    }

    @Override
    protected void build() {
        PlayerData data = plugin.data().get(player);
        option(0, Material.LIME_DYE, "<dark_gray>»</dark_gray> <green>Alle anzeigen", PlayerData.Visibility.ALL, data);
        option(1, Material.ORANGE_DYE, "<dark_gray>»</dark_gray> <gold>Nur VIPs anzeigen", PlayerData.Visibility.VIP, data);
        option(2, Material.RED_DYE, "<dark_gray>»</dark_gray> <red>Niemanden anzeigen", PlayerData.Visibility.NONE, data);
        set(3, new ItemBuilder(Material.NETHER_STAR).name("<dark_gray>»</dark_gray> <aqua>Aktuell: " + plugin.lobby().visibilityLabel(data.visibility()))
                .lore("<dark_gray>»</dark_gray> <gray>Wähle unten aus, wen du sehen willst").build());
        // Silent-Lobby (vorher eigenes Hotbar-Item)
        if (plugin.configs().has(player, "silentlobby")) {
            set(4, new ItemBuilder(Material.TNT).name("<dark_gray>»</dark_gray> <red>Silent-Lobby")
                    .lore(data.silent() ? "<dark_gray>»</dark_gray> <green>Aktiv" : "<dark_gray>»</dark_gray> <gray>Aus",
                            "<dark_gray>»</dark_gray> <gray>Allein in der Lobby – keine Spieler, kein Chat", "",
                            "<dark_gray>»</dark_gray> <yellow>Klicke zum Umschalten")
                    .glow(data.silent()).build(), e -> {
                click();
                data.silent(!data.silent());
                plugin.configs().send(player, data.silent() ? "silent_on" : "silent_off");
                plugin.lobby().updateVisibility();
                plugin.lobby().giveHotbar(player);
                refresh();
            });
        }
    }

    private void option(int slot, Material mat, String name, PlayerData.Visibility v, PlayerData data) {
        boolean active = data.visibility() == v;
        set(slot, new ItemBuilder(mat).name(name)
                .lore(active ? "<dark_gray>»</dark_gray> <green>Ausgewählt" : "<dark_gray>»</dark_gray> <gray>Klicke zum Auswählen")
                .glow(active).build(), e -> {
            if (data.visibility() == v) return;
            data.visibility(v);
            click();
            plugin.lobby().updateVisibility();
            plugin.lobby().giveHotbar(player);
            plugin.configs().send(player, "visibility_changed", "state", plugin.lobby().visibilityLabel(v));
            refresh();
        });
    }
}
