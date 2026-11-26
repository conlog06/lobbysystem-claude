package de.crafterteam.lobbysystem.gui;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class SettingsMenu extends Menu {

    public SettingsMenu(LobbySystem plugin, Player player) {
        super(plugin, player);
        create(3, "<dark_gray>↪</dark_gray> <gradient:#00d2ff:#3a7bd5>ᴇɪɴꜱᴛᴇʟʟᴜɴɢᴇɴ</gradient>");
    }

    @Override
    protected void build() {
        PlayerData d = plugin.data().get(player);
        toggle(10, Material.ENDER_PEARL, "Teleport-Effekte", d::tpEffects, d::tpEffects, () -> {
        });
        toggle(12, Material.MAP, "Scoreboard", d::scoreboard, d::scoreboard, () -> plugin.sidebar().updateSidebar(player));
        toggle(14, Material.CHERRY_HANGING_SIGN, "Bossbar & Actionbar", d::bars, d::bars, () -> {
            plugin.bars().hide(player);
            plugin.bars().show(player);
            if (!d.bars()) player.sendActionBar(net.kyori.adventure.text.Component.empty());
        });
        toggle(16, Material.IRON_BOOTS, "Doppelsprung", d::doubleJump, d::doubleJump, () -> plugin.lobby().applyDoubleJump(player, d));
        if (plugin.configs().perm("fly") != null && plugin.configs().has(player, "fly")) {
            toggle(22, Material.ELYTRA, "Fliegen", d::fly, d::fly, () -> plugin.lobby().applyDoubleJump(player, d));
        }
        fill(Material.GRAY_STAINED_GLASS_PANE);
    }

    private void toggle(int slot, Material mat, String name, BooleanSupplier get, Consumer<Boolean> setter, Runnable after) {
        boolean on = get.getAsBoolean();
        String state = on ? "<green>" + name + " aktiviert" : "<red>" + name + " deaktiviert";
        set(slot, new ItemBuilder(mat).name("<dark_gray>-●</dark_gray> <aqua>" + name)
                .lore("<dark_gray>↪</dark_gray> " + state, "", "<dark_gray>»</dark_gray> <gray>Klicke zum Umschalten")
                .glow(on).hideFlags().build(), e -> {
            boolean now = !get.getAsBoolean();
            setter.accept(now);
            click();
            after.run();
            plugin.configs().send(player, "setting_changed", "setting", name,
                    "state", plugin.configs().raw(now ? "state_on" : "state_off"));
            refresh();
        });
    }
}
