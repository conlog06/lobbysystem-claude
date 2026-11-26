package de.crafterteam.lobbysystem.hook;

import de.crafterteam.lobbysystem.config.ConfigManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ränge direkt aus LuckPerms: primäre Gruppe → Name, Prefix aus den Meta-Daten, Gewichtung → Sortierung in Tab.
 * Badges werden automatisch über den Gruppennamen zugeordnet (admin, mod, sup …).
 */
public final class LuckPermsRanks {

    private static final Pattern COLOR = Pattern.compile("[&§]([0-9a-fA-F])");
    private static final String[] NAMES = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold",
            "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};

    private LuckPermsRanks() {
    }

    public static ConfigManager.Rank rank(Player player) {
        LuckPerms lp = LuckPermsProvider.get();
        User user = lp.getPlayerAdapter(Player.class).getUser(player);
        String groupName = user.getPrimaryGroup();
        Group group = lp.getGroupManager().getGroup(groupName);
        int weight = group == null ? 0 : group.getWeight().orElse(0);
        String display = group != null && group.getDisplayName() != null ? group.getDisplayName()
                : Character.toUpperCase(groupName.charAt(0)) + groupName.substring(1);
        String prefix = user.getCachedData().getMetaData().getPrefix();

        ConfigManager.Rank r = new ConfigManager.Rank();
        r.name = display;
        r.permission = "";
        r.priority = Math.max(0, Math.min(999, 999 - weight));
        r.prefix = prefix == null ? "" : prefix;
        r.color = lastColor(r.prefix);
        r.display = "<" + r.color + ">" + display;
        r.badge = "";
        r.group = groupName.toLowerCase();
        return r;
    }

    /** Letzte Farbe im Prefix (für die Namensfarbe in Tab/Nametag). */
    private static String lastColor(String prefix) {
        String color = "white";
        Matcher m = COLOR.matcher(prefix);
        while (m.find()) color = NAMES[Integer.parseInt(m.group(1), 16)];
        Matcher mm = Pattern.compile("<(black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white)>").matcher(prefix);
        while (mm.find()) color = mm.group(1);
        return color;
    }
}
