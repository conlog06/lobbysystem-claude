package de.crafterteam.lobbysystem.gui;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.PluginConfig;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Navigator aus config.json → "navigatorItems":
 * "Slot;MATERIAL;Ziel;Name;Lore"  (Lore-Zeilen mit \n trennen)
 * Ziele: warp:Name · connect:Server · connect-best:MissileWars · spawn · command:befehl
 * Live-Platzhalter: {players:MissileWars*} {max:Server} {state:MissileWars*} (aktualisiert sich, solange offen)
 */
public final class NavigatorMenu extends Menu {

    private static final Pattern LIVE = Pattern.compile("\\{(players|max|state):([^}]+)}");
    private BukkitTask refresher;

    public NavigatorMenu(LobbySystem plugin, Player player) {
        super(plugin, player);
        PluginConfig cfg = plugin.configs().cfg();
        create(cfg.navigatorRows, cfg.navigatorTitle);
    }

    @Override
    public void open() {
        super.open();
        refresher = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder(false) != this) {
                if (refresher != null) refresher.cancel();
                return;
            }
            refresh();
        }, 40L, 40L);
    }

    @Override
    public void onClose() {
        if (refresher != null) refresher.cancel();
    }

    private String live(String s) {
        if (s.indexOf('{') < 0) return s;
        Matcher m = LIVE.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = switch (m.group(1)) {
                case "players" -> Integer.toString(plugin.servers().players(m.group(2)));
                case "max" -> Integer.toString(plugin.servers().maxPlayers(m.group(2)));
                default -> plugin.servers().stateText(m.group(2));
            };
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        return sb.toString();
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
            // "TNT@mode_missilewars" = Material + Icon aus dem Ressourcenpaket
            String[] matIcon = p[1].split("@", 2);
            Material mat = ItemBuilder.material(matIcon[0], Material.STONE);
            String icon = matIcon.length > 1 ? matIcon[1].trim() : "";
            String target = p[2].trim();
            List<String> lore = p.length > 4 ? new ArrayList<>(Arrays.asList(live(p[4]).split("\\\\n|\n"))) : List.of();
            int amount = 1;
            Matcher pm = Pattern.compile("connect-best:(.+)").matcher(target);
            if (pm.matches()) amount = Math.max(1, Math.min(64, plugin.servers().players(pm.group(1) + "*")));
            set(slot, new ItemBuilder(mat).name(live(p[3])).lore(lore).amount(amount).hideFlags().icon(player, icon).build(), e -> {
                click();
                plugin.lobby().runTarget(player, target);
            });
        }
        border(Material.BLACK_STAINED_GLASS_PANE);
    }
}
