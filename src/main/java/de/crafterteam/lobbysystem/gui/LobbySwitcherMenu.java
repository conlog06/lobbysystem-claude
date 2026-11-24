package de.crafterteam.lobbysystem.gui;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.social.ServerRegistry.ServerInfo;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Lobby-Switcher: zeigt alle Lobby-Server (Heartbeat aus MySQL) mit Spielerzahl. */
public final class LobbySwitcherMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private final List<ServerInfo> servers;

    private LobbySwitcherMenu(LobbySystem plugin, Player player, List<ServerInfo> servers) {
        super(plugin, player);
        this.servers = servers;
        create(4, "<dark_gray>↪</dark_gray> <gradient:#00d2ff:#3a7bd5>ʟᴏʙʙʏ-ꜱᴡɪᴛᴄʜᴇʀ</gradient>");
    }

    public static void open(LobbySystem plugin, Player player) {
        plugin.servers().lobbies().thenAccept(list -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) new LobbySwitcherMenu(plugin, player, list).open();
        }));
    }

    @Override
    protected void build() {
        for (int i = 0; i < servers.size() && i < SLOTS.length; i++) {
            ServerInfo s = servers.get(i);
            boolean full = s.max() > 0 && s.online() >= s.max();
            Material mat = s.current() ? Material.LIME_STAINED_GLASS : full ? Material.RED_STAINED_GLASS : Material.WHITE_STAINED_GLASS;
            int fill = s.max() <= 0 ? 0 : (int) Math.round(10.0 * s.online() / s.max());
            String bar = "<green>" + "▌".repeat(fill) + "<dark_gray>" + "▌".repeat(10 - fill);
            set(SLOTS[i], new ItemBuilder(mat).name("<dark_gray>»</dark_gray> " + (s.current() ? "<green>" : "<aqua>") + s.name())
                    .lore("<dark_gray>↪</dark_gray> <gray>Spieler: <white>" + s.online() + "<dark_gray>/</dark_gray><white>" + s.max(),
                            "<dark_gray>↪</dark_gray> " + bar, "",
                            s.current() ? "<dark_gray>»</dark_gray> <green>Du bist hier" : full ? "<dark_gray>»</dark_gray> <red>Voll" : "<dark_gray>»</dark_gray> <yellow>Klicke zum Wechseln")
                    .amount(Math.max(1, s.online())).glow(s.current()).build(), e -> {
                if (s.current()) {
                    deny();
                    plugin.configs().send(player, "lobby_switch_same");
                    return;
                }
                click();
                player.closeInventory();
                plugin.lobby().connect(player, s.name());
            });
        }
        border(Material.BLACK_STAINED_GLASS_PANE);
    }
}
