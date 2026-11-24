package de.crafterteam.lobbysystem.display;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.ConfigManager;
import de.crafterteam.lobbysystem.config.PluginConfig;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.util.Text;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sidebar (ohne rote Zahlen dank NumberFormat.blank() – ab 1.20.3),
 * Ränge in Tab & über dem Kopf (Teams), Tablist-Header/Footer.
 */
public final class SidebarManager {

    private static final String OBJECTIVE = "ls_sidebar";
    private final LobbySystem plugin;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private final Map<UUID, ConfigManager.Rank> ranks = new ConcurrentHashMap<>();

    public SidebarManager(LobbySystem plugin) {
        this.plugin = plugin;
    }

    private ConfigManager c() {
        return plugin.configs();
    }

    public ConfigManager.Rank rank(Player player) {
        ConfigManager.Rank cached = ranks.get(player.getUniqueId());
        return cached != null ? cached : resolveRank(player);
    }

    private ConfigManager.Rank resolveRank(Player player) {
        List<ConfigManager.Rank> list = c().ranks();
        for (ConfigManager.Rank r : list) {
            if (r.permission == null || r.permission.isBlank() || player.hasPermission(r.permission)) return r;
        }
        ConfigManager.Rank fallback = new ConfigManager.Rank();
        fallback.name = "Spieler";
        fallback.prefix = "<gray>";
        fallback.display = "<gray>Spieler";
        fallback.color = "gray";
        fallback.priority = 99;
        return fallback;
    }

    public void create(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        boards.put(player.getUniqueId(), board);
        ranks.put(player.getUniqueId(), resolveRank(player));
        player.setScoreboard(board);
        updateSidebar(player);
        updateTeams();
        updateTab(player);
    }

    public void remove(Player player) {
        boards.remove(player.getUniqueId());
        ranks.remove(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, this::updateTeams);
    }

    /** Wird jede Sekunde aufgerufen. */
    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ConfigManager.Rank newRank = resolveRank(p);
            ConfigManager.Rank old = ranks.put(p.getUniqueId(), newRank);
            if (old != null && !old.name.equals(newRank.name)) updateTeams();
            updateSidebar(p);
            updateTab(p);
        }
    }

    public void updateSidebar(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) return;
        PluginConfig.Scoreboard sb = c().cfg().scoreboard;
        PlayerData data = plugin.data().get(player);
        Objective obj = board.getObjective(OBJECTIVE);
        if (!sb.enabled || !data.scoreboard()) {
            if (obj != null) obj.unregister();
            return;
        }
        Map<String, String> ph = placeholders(player, data);
        if (obj == null) {
            obj = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, Text.parse(sb.title, ph));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(NumberFormat.blank());
        } else {
            obj.displayName(Text.parse(sb.title, ph));
        }
        List<String> lines = sb.lines;
        int size = Math.min(15, lines.size());
        for (int i = 0; i < size; i++) {
            String entry = "l" + i;
            var score = obj.getScore(entry);
            score.setScore(size - i);
            score.customName(Text.parse(lines.get(i), ph));
        }
        // überzählige Zeilen (z. B. nach Reload) entfernen
        for (int i = size; i < 15; i++) {
            if (obj.getScore("l" + i).isScoreSet()) obj.getScore("l" + i).resetScore();
        }
    }

    public Map<String, String> placeholders(Player player, PlayerData data) {
        ConfigManager.Rank rank = rank(player);
        PluginConfig.ActionBar ab = c().cfg().actionbar;
        ZonedDateTime now = ZonedDateTime.now(zone(ab.timezone));
        var clan = plugin.clans().cached(player.getUniqueId()).orElse(null);
        int[] friends = plugin.friends().count(player.getUniqueId());
        return ConfigManager.map(
                "clan", clan == null ? "<gray>-" : "<white>" + Text.escape(clan.name()),
                "clan_tag", clan == null ? "" : "<dark_gray>[</dark_gray><yellow>#" + clan.tag() + "</yellow><dark_gray>]</dark_gray>",
                "clan_members", clan == null ? "<gray>Kein Clan" : "<green>" + clan.members().size() + "<dark_gray>/</dark_gray><green>" + c().cfg().social.clanMax,
                "friends_online", Integer.toString(friends[0]),
                "friends_total", Integer.toString(friends[1]),
                "streak", Integer.toString(data.streak()),
                "proxy_server", c().cfg().proxyServerName,
                "name", player.getName(),
                "coins", Text.formatNumber(data.coins()),
                "lose", Integer.toString(data.lose()),
                "playtime", Text.formatDuration(data.playtime()),
                "rank", rank.display == null ? rank.name : rank.display,
                "online", Integer.toString(Bukkit.getOnlinePlayers().size()),
                "max", Integer.toString(Bukkit.getMaxPlayers()),
                "server", c().cfg().serverName,
                "date", now.format(DateTimeFormatter.ofPattern(ab.dateFormat)),
                "time", now.format(DateTimeFormatter.ofPattern(ab.timeFormat)));
    }

    static ZoneId zone(String id) {
        try {
            return ZoneId.of(id);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    public void updateTab(Player player) {
        PluginConfig.Tablist tab = c().cfg().tablist;
        if (!tab.enabled) return;
        Map<String, String> ph = placeholders(player, plugin.data().get(player));
        player.sendPlayerListHeaderAndFooter(Text.parse(tab.header, ph), Text.parse(tab.footer, ph));
    }

    /** Teams auf allen Boards aktualisieren: sortierter Tab + Prefix über dem Kopf. */
    public void updateTeams() {
        for (Map.Entry<UUID, Scoreboard> e : boards.entrySet()) {
            Scoreboard board = e.getValue();
            for (Player target : Bukkit.getOnlinePlayers()) {
                ConfigManager.Rank rank = rank(target);
                var clan = plugin.clans().cached(target.getUniqueId()).orElse(null);
                String clanTag = clan == null ? "" : clan.tag();
                String rankKey = rank.name.replaceAll("[^A-Za-z0-9]", "");
                if (rankKey.length() > 6) rankKey = rankKey.substring(0, 6);
                String teamName = String.format("%03d", Math.max(0, Math.min(999, rank.priority))) + rankKey + clanTag;
                Team team = board.getTeam(teamName);
                if (team == null) {
                    team = board.registerNewTeam(teamName);
                }
                team.prefix(Text.parse(rank.prefix));
                team.suffix(clan == null ? net.kyori.adventure.text.Component.empty()
                        : Text.parse(c().cfg().social.clanSuffix, ConfigManager.map("tag", clan.tag())));
                team.color(color(rank.color));
                Team current = board.getEntryTeam(target.getName());
                if (current != null && !current.equals(team)) current.removeEntry(target.getName());
                if (!team.hasEntry(target.getName())) team.addEntry(target.getName());
            }
            // leere Teams aufräumen
            for (Team t : new java.util.ArrayList<>(board.getTeams())) if (t.getEntries().isEmpty()) t.unregister();
        }
    }

    private static NamedTextColor color(String s) {
        if (s == null) return NamedTextColor.WHITE;
        NamedTextColor named = NamedTextColor.NAMES.value(s.toLowerCase());
        if (named != null) return named;
        TextColor hex = TextColor.fromHexString(s.startsWith("#") ? s : "#" + s);
        return hex == null ? NamedTextColor.WHITE : NamedTextColor.nearestTo(hex);
    }
}
