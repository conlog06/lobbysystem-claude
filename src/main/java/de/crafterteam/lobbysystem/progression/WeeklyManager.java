package de.crafterteam.lobbysystem.progression;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.PluginConfig;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Wochen-Challenges: Fortschritt = aktueller Zählerstand − Stand zu Wochenbeginn (Baseline in ls_weekly).
 * Quellen: ls_game_stats (alle Spielmodi), Spielzeit und Parkour-Läufe der Lobby.
 */
public final class WeeklyManager {

    public record Progress(PluginConfig.Challenge challenge, long progress, boolean claimed) {
        public boolean done() {
            return progress >= challenge.goal;
        }
    }

    private static final Set<String> GAME_STATS = Set.of("wins", "kills", "deaths", "games");

    private final LobbySystem plugin;

    public WeeklyManager(LobbySystem plugin) {
        this.plugin = plugin;
    }

    public static String week() {
        LocalDate d = LocalDate.now();
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** Sekunden bis Montag 00:00 */
    public static long secondsUntilReset() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay();
        return java.time.Duration.between(now, next).getSeconds();
    }

    private List<PluginConfig.Challenge> challenges() {
        return plugin.configs().cfg().weekly.challenges;
    }

    private static long value(Connection con, UUID id, PluginConfig.Challenge c, long playtimeMinutes) throws SQLException {
        if ("lobby".equalsIgnoreCase(c.game)) {
            return switch (c.stat.toLowerCase()) {
                case "playtime" -> playtimeMinutes;
                case "parkour" -> single(con, "select coalesce(sum(runs), 0) from ls_parkour_times where uuid = ?", id.toString());
                default -> 0;
            };
        }
        String stat = c.stat.toLowerCase();
        if (!GAME_STATS.contains(stat)) return 0;   // nur bekannte Spalten (kein SQL aus der Config)
        if ("any".equalsIgnoreCase(c.game)) return single(con, "select coalesce(sum(" + stat + "), 0) from ls_game_stats where uuid = ?", id.toString());
        return single(con, "select coalesce(sum(" + stat + "), 0) from ls_game_stats where uuid = ? and game = ?", id.toString(), c.game.toLowerCase());
    }

    private static long single(Connection con, String sql, String... args) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setString(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /** Fortschritt laden – legt fehlende Baselines (erster Besuch der Woche) gleich an. Im Main-Thread aufrufen. */
    public CompletableFuture<List<Progress>> load(Player p) {
        UUID id = p.getUniqueId();
        long playtime = plugin.data().get(p).playtime() / 60;
        List<PluginConfig.Challenge> list = new ArrayList<>(challenges());
        String week = week();
        return plugin.database().submit(con -> {
            Map<String, long[]> stored = new HashMap<>();
            try (PreparedStatement ps = con.prepareStatement("select challenge, baseline, claimed from ls_weekly where uuid = ? and week = ?")) {
                ps.setString(1, id.toString());
                ps.setString(2, week);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) stored.put(rs.getString(1), new long[]{rs.getLong(2), rs.getInt(3)});
                }
            }
            List<Progress> out = new ArrayList<>();
            for (PluginConfig.Challenge c : list) {
                long now = value(con, id, c, playtime);
                long[] s = stored.get(c.id);
                if (s == null) {
                    try (PreparedStatement ps = con.prepareStatement("insert into ls_weekly (uuid, week, challenge, baseline, claimed) values (?, ?, ?, ?, 0)")) {
                        ps.setString(1, id.toString());
                        ps.setString(2, week);
                        ps.setString(3, c.id);
                        ps.setLong(4, now);
                        ps.executeUpdate();
                    }
                    s = new long[]{now, 0};
                }
                out.add(new Progress(c, Math.max(0, now - s[0]), s[1] != 0));
            }
            // alte Wochen aufräumen (einmal pro Laden reicht, ist indexiert über den Primärschlüssel)
            try (PreparedStatement ps = con.prepareStatement("delete from ls_weekly where uuid = ? and week <> ?")) {
                ps.setString(1, id.toString());
                ps.setString(2, week);
                ps.executeUpdate();
            }
            return out;
        });
    }

    /** Belohnung abholen. true = ausgezahlt. */
    public CompletableFuture<Boolean> claim(Player p, Progress pr) {
        if (!pr.done() || pr.claimed()) return CompletableFuture.completedFuture(false);
        UUID id = p.getUniqueId();
        String week = week();
        return plugin.database().submit(con -> {
            try (PreparedStatement ps = con.prepareStatement("update ls_weekly set claimed = 1 where uuid = ? and week = ? and challenge = ? and claimed = 0")) {
                ps.setString(1, id.toString());
                ps.setString(2, week);
                ps.setString(3, pr.challenge().id);
                return ps.executeUpdate() == 1;
            }
        }).thenApply(ok -> {
            if (ok) org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> plugin.coins().change(id, pr.challenge().reward, "weekly"));
            return ok;
        });
    }

    /** Beim Join: Baselines für die neue Woche anlegen, damit der Fortschritt ab Wochenstart zählt. */
    public void join(Player p) {
        if (plugin.configs().cfg().weekly.enabled) load(p);
    }
}
