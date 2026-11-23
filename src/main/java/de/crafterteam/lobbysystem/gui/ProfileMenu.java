package de.crafterteam.lobbysystem.gui;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import de.crafterteam.lobbysystem.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class ProfileMenu extends Menu {

    public ProfileMenu(LobbySystem plugin, Player player) {
        super(plugin, player);
        create(3, "<dark_gray>↪</dark_gray> <gradient:#56ab2f:#a8e063>ᴘʀᴏꜰɪʟ</gradient>");
    }

    @Override
    protected void build() {
        PlayerData d = plugin.data().get(player);
        var rank = plugin.sidebar().rank(player);
        set(11, new ItemBuilder(Material.PLAYER_HEAD).skull(player)
                .name("<dark_gray>»</dark_gray> <white>" + Text.escape(player.getName()))
                .lore("<dark_gray>↪</dark_gray> <gray>Rang: " + (rank.display == null ? rank.name : rank.display),
                        "<dark_gray>↪</dark_gray> <gray>Coins: <yellow>" + Text.formatNumber(d.coins()),
                        "<dark_gray>↪</dark_gray> <gray>Lose: <yellow>" + d.lose(),
                        "<dark_gray>↪</dark_gray> <gray>Spielzeit: <aqua>" + Text.formatDuration(d.playtime())).build());
        set(13, new ItemBuilder(Material.COMPARATOR).name("<dark_gray>»</dark_gray> <aqua>Einstellungen")
                .lore("<dark_gray>↪</dark_gray> <gray>Passe deine Lobby an").build(), e -> {
            click();
            new SettingsMenu(plugin, player).open();
        });
        set(15, new ItemBuilder(Material.CLOCK).name("<dark_gray>»</dark_gray> <light_purple>Tägliche Belohnung")
                .lore("<dark_gray>↪</dark_gray> <gray>Hol dir Coins & Lose").build(), e -> {
            click();
            new DailyRewardMenu(plugin, player).open();
        });
        fill(Material.GRAY_STAINED_GLASS_PANE);
    }
}
