package de.crafterteam.lobbysystem.display;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.ConfigManager;
import de.crafterteam.lobbysystem.config.PluginConfig;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.util.Text;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sidebar (ohne rote Zahlen dank NumberFormat.blank()), Ränge & Clan-Tags in Tab und über dem Kopf,
 * Tablist-Header/Footer.
 * <p>
 * Performance: Platzhalter werden pro Sekunde nur EINMAL pro Spieler berechnet (auch für Actionbar),
 * Zeilen/Tab werden nur bei Änderung neu gesendet und Team-Updates werden gebündelt (1× pro Tick).
 */
public final class SidebarManager {

    private static final String OBJECTIVE = "ls_sidebar";
    private final LobbySystem plugin;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private final Map<UUID, ConfigManager.Rank> ranks = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> placeholderCache = new ConcurrentHashMap<>();
    private final Map<UUID, String[]> lastLines = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastTab = new ConcurrentHashMap<>();
    /** pro Board: Spieler -> Teamname (verhindert unnötige Team-Pakete) */
    private final Map<UUID, Map<String, String>> teamOf = new ConcurrentHashMap<>();
    private boolean teamsDirty;
    private int seconds;

    private String dateFmtKey, timeFmtKey;
    private DateTimeFormatter dateFmt, timeFmt;
    private ZoneId zone;

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
        if (c().cfg().integrations.luckPermsRanks && Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                return de.crafterteam.lobbysystem.hook.LuckPermsRanks.rank(player);
            } catch (Throwable t) {
                // LuckPerms noch nicht bereit → ranks.json
            }
        }
        for (ConfigManager.Rank r : c().ranks()) {
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

    /** Badge-Key passend zum Rang (Feld "badge" oder automatisch aus dem Namen). */
    public static String badgeKey(ConfigManager.Rank rank) {
        if (rank.badge != null && !rank.badge.isBlank()) return rank.badge.trim().toLowerCase();
        String source = rank.group != null ? rank.group : rank.name;
        String n = source == null ? "" : source.toLowerCase().replace("+", "plus").replaceAll("[^a-z]", "");
        return switch (n) {
            case "developer", "entwickler" -> "dev";
            case "moderator", "srmoderator" -> "mod";
            case "supporter", "srsupporter" -> "sup";
            case "inhaber", "owner", "besitzer" -> "owner";
            case "contentcreator", "creator" -> "content";
            case "youtube", "streamer" -> "youtuber";
            case "spieler", "default", "member", "mitglied", "user" -> "none"; // Standard-Rang ohne Badge (per "badge": "spieler" erzwingbar)
            default -> n;
        };
    }

    /** Prefix für Tab/Nametag/Chat: Badge-Grafik mit Pack, sonst Text-Prefix aus ranks.json. */
    public String prefix(ConfigManager.Rank rank) {
        String key = badgeKey(rank);
        String icon = "none".equals(key) ? null : Text.icon("r_" + key);
        return icon != null ? icon + " " : (rank.prefix == null ? "" : rank.prefix);
    }

    public void create(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        boards.put(player.getUniqueId(), board);
        teamOf.put(player.getUniqueId(), new ConcurrentHashMap<>());
        lastLines.remove(player.getUniqueId());
        lastTab.remove(player.getUniqueId());
        ranks.put(player.getUniqueId(), resolveRank(player));
        player.setScoreboard(board);
        refreshPlaceholders(player);
        updateSidebar(player);
        updateTab(player);
        requestTeams();
    }

    public void remove(Player player) {
        UUID id = player.getUniqueId();
        boards.remove(id);
        ranks.remove(id);
        placeholderCache.remove(id);
        lastLines.remove(id);
        lastTab.remove(id);
        teamOf.remove(id);
        requestTeams();
    }

    /** Team-Update gebündelt im nächsten Tick ausführen (statt n× hintereinander). */
    public void requestTeams() {
        if (teamsDirty) return;
        teamsDirty = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            teamsDirty = false;
            updateTeams();
        });
    }

    /** Jede Sekunde. */
    public void tick() {
        seconds++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            ConfigManager.Rank newRank = resolveRank(p);
            ConfigManager.Rank old = ranks.put(p.getUniqueId(), newRank);
            if (old != null && !old.name.equals(newRank.name)) requestTeams();
            refreshPlaceholders(p);
            updateSidebar(p);
            updateTab(p);
        }
    }

    private void refreshPlaceholders(Player p) {
        placeholderCache.put(p.getUniqueId(), placeholders(p, plugin.data().get(p)));
    }

    /** Platzhalter aus dem Sekunden-Cache (für Actionbar & Co.). */
    public Map<String, String> cachedPlaceholders(Player p) {
        Map<String, String> m = placeholderCache.get(p.getUniqueId());
        return m != null ? m : placeholders(p, plugin.data().get(p));
    }

    public void updateSidebar(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) return;
        PluginConfig.Scoreboard sb = c().cfg().scoreboard;
        PlayerData data = plugin.data().get(player);
        Objective obj = board.getObjective(OBJECTIVE);
        if (!sb.enabled || !data.scoreboard()) {
            if (obj != null) obj.unregister();
            lastLines.remove(player.getUniqueId());
            return;
        }
        Map<String, String> ph = cachedPlaceholders(player);
        List<String> lines = sb.lines;
        int size = Math.min(15, lines.size());
        String title = sb.titleFrames != null && !sb.titleFrames.isEmpty()
                ? sb.titleFrames.get(seconds % sb.titleFrames.size()) : sb.title;
        String titleRaw = Text.apply(title, ph);

        String[] last = lastLines.get(player.getUniqueId());
        boolean fresh = obj == null || last == null || last.length != size + 1;
        if (obj == null) {
            obj = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, Text.parse(titleRaw));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(NumberFormat.blank());
        }
        if (fresh) {
            last = new String[size + 1];
            for (int i = size; i < 15; i++) {
                if (obj.getScore("l" + i).isScoreSet()) obj.getScore("l" + i).resetScore();
            }
        }
        if (!titleRaw.equals(last[size])) {
            obj.displayName(Text.parse(titleRaw));
            last[size] = titleRaw;
        }
        for (int i = 0; i < size; i++) {
            String raw = Text.apply(lines.get(i), ph);
            if (raw.equals(last[i])) continue; // unverändert → kein Paket
            var score = obj.getScore("l" + i);
            if (last[i] == null) score.setScore(size - i);
            score.customName(Text.parse(raw));
            last[i] = raw;
        }
        lastLines.put(player.getUniqueId(), last);
    }

    public Map<String, String> placeholders(Player player, PlayerData data) {
        ConfigManager.Rank rank = rank(player);
        PluginConfig.ActionBar ab = c().cfg().actionbar;
        formatters(ab);
        ZonedDateTime now = ZonedDateTime.now(zone);
        var clan = plugin.clans().cached(player.getUniqueId()).orElse(null);
        int[] friends = plugin.friends().count(player.getUniqueId());
        return ConfigManager.map(
                "clan", clan == null ? "<gray>-" : "<white>" + Text.escape(clan.name()),
                "clan_tag", clan == null ? "" : "<dark_gray>[</dark_gray><yellow>#" + clan.tag() + "</yellow><dark_gray>]</dark_gray>",
                "clan_members", clan == null ? "<gray>Kein Clan" : "<green>" + clan.members().size() + "<dark_gray>/</dark_gray><green>" + c().cfg().social.clanMax,
                "friends_online", Integer.toString(friends[0]),
                "friends_total", Integer.toString(friends[1]),
                "streak", Integer.toString(data.streak()),
                "level", Integer.toString(plugin.levels().level(data.xp())),
                "xp", Text.formatNumber(plugin.levels().progress(data.xp())),
                "xp_needed", Text.formatNumber(plugin.levels().needed(plugin.levels().level(data.xp()))),
                "xp_bar", plugin.levels().bar(data.xp(), 20),
                "cookies", de.crafterteam.lobbysystem.progression.CookieManager.format(data.cookies()),
                "proxy_server", c().cfg().proxyServerName,
                "name", player.getName(),
                "coins", Text.formatNumber(data.coins()),
                "lose", Integer.toString(data.lose()),
                "playtime", Text.formatDuration(data.playtime()),
                "onlinetime", Text.formatDuration(plugin.proxy().onlineTimeSeconds(player.getUniqueId()) >= 0
                        ? plugin.proxy().onlineTimeSeconds(player.getUniqueId()) : data.playtime()),
                "rank", rank.display == null ? rank.name : rank.display,
                "rank_badge", prefix(rank),
                "online", Integer.toString(Bukkit.getOnlinePlayers().size()),
                "max", Integer.toString(Bukkit.getMaxPlayers()),
                "server", c().cfg().serverName,
                "ping", Integer.toString(player.getPing()),
                "date", now.format(dateFmt),
                "time", now.format(timeFmt));
    }

    /** Formatter nur neu bauen, wenn sich die Config geändert hat. */
    private void formatters(PluginConfig.ActionBar ab) {
        if (!Objects.equals(dateFmtKey, ab.dateFormat + ab.timezone) || dateFmt == null) {
            dateFmtKey = ab.dateFormat + ab.timezone;
            dateFmt = safePattern(ab.dateFormat, "dd.MM.yyyy");
            zone = zone(ab.timezone);
        }
        if (!Objects.equals(timeFmtKey, ab.timeFormat) || timeFmt == null) {
            timeFmtKey = ab.timeFormat;
            timeFmt = safePattern(ab.timeFormat, "HH:mm:ss");
        }
    }

    private static DateTimeFormatter safePattern(String p, String fallback) {
        try {
            return DateTimeFormatter.ofPattern(p);
        } catch (Exception e) {
            return DateTimeFormatter.ofPattern(fallback);
        }
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
        Map<String, String> ph = cachedPlaceholders(player);
        String header = Text.apply(tab.header, ph), footer = Text.apply(tab.footer, ph);
        String key = header + "\u0000" + footer;
        if (key.equals(lastTab.get(player.getUniqueId()))) return;
        lastTab.put(player.getUniqueId(), key);
        player.sendPlayerListHeaderAndFooter(Text.parse(header), Text.parse(footer));
    }

    /** Teams auf allen Boards: sortierter Tab + Prefix/Clan-Tag über dem Kopf. Nur Änderungen werden gesendet. */
    public void updateTeams() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        // Teamname pro Spieler einmal berechnen
        Map<Player, String[]> wanted = new java.util.HashMap<>();
        for (Player target : online) {
            ConfigManager.Rank rank = rank(target);
            var clan = plugin.clans().cached(target.getUniqueId()).orElse(null);
            String clanTag = clan == null ? "" : clan.tag();
            String rankKey = rank.name.replaceAll("[^A-Za-z0-9]", "");
            if (rankKey.length() > 6) rankKey = rankKey.substring(0, 6);
            String teamName = String.format("%03d", Math.max(0, Math.min(999, rank.priority))) + rankKey + clanTag;
            wanted.put(target, new String[]{teamName, prefix(rank), rank.color, clanTag});
        }
        for (Map.Entry<UUID, Scoreboard> e : boards.entrySet()) {
            Scoreboard board = e.getValue();
            Map<String, String> assigned = teamOf.computeIfAbsent(e.getKey(), k -> new ConcurrentHashMap<>());
            for (Player target : online) {
                String[] w = wanted.get(target);
                if (w[0].equals(assigned.get(target.getName()))) continue; // schon korrekt
                Team team = board.getTeam(w[0]);
                if (team == null) {
                    team = board.registerNewTeam(w[0]);
                    team.prefix(Text.parse(w[1]));
                    team.suffix(w[3].isEmpty() ? Component.empty()
                            : Text.parse(c().cfg().social.clanSuffix, ConfigManager.map("tag", w[3])));
                    team.color(color(w[2]));
                }
                Team current = board.getEntryTeam(target.getName());
                if (current != null && !current.equals(team)) current.removeEntry(target.getName());
                team.addEntry(target.getName());
                assigned.put(target.getName(), w[0]);
            }
            // offline gegangene Spieler & leere Teams aufräumen
            assigned.keySet().removeIf(name -> Bukkit.getPlayerExact(name) == null);
            for (Team t : new ArrayList<>(board.getTeams())) if (t.getEntries().isEmpty()) t.unregister();
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
