package de.crafterteam.lobbysystem.display;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.ConfigManager;
import de.crafterteam.lobbysystem.config.PluginConfig;
import de.crafterteam.lobbysystem.util.SLoc;
import de.crafterteam.lobbysystem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bestenlisten als TextDisplay-Hologramm (Top Coins, Spielzeit, Streak, Freunde).
 * Daten werden asynchron alle X Sekunden aus der DB geladen – kein Lag im Main-Thread.
 */
public final class LeaderboardManager implements Listener {

    /** Eine Bestenliste: Key (z. B. "coins", "bedwars_wins"), Titel, SQL (ein ?-Limit, optional Spiel-Parameter). */
    public record Board(String key, String title, String sql, String gameParam, String unit) {
    }

    private static final java.util.regex.Pattern GAME_KEY = java.util.regex.Pattern.compile("([a-z0-9]+)_(wins|kills|deaths|games|level)");

    /** Löst einen Key auf: eingebaute Listen oder beliebige Spielmodi aus ls_game_stats. null = unbekannt. */
    public Board board(String rawKey) {
        if (rawKey == null) return null;
        String original = rawKey.toLowerCase();
        Board b = resolve(original);
        return b == null ? null : new Board(original, b.title(), b.sql(), b.gameParam(), b.unit());
    }

    private Board resolve(String key) {
        // Kompatibilität zu 2.4: mw_wins → missilewars_wins
        if (key.startsWith("mw_")) key = "missilewars_" + key.substring(3);
        return switch (key) {
            case "coins" -> new Board(key, "<yellow>ᴄᴏɪɴꜱ", "select name, coins as v from ls_players order by coins desc limit ?", null, "number");
            case "playtime" -> new Board(key, "<aqua>ꜱᴘɪᴇʟᴢᴇɪᴛ", "select name, playtime as v from ls_players order by playtime desc limit ?", null, "time");
            case "streak" -> new Board(key, "<gold>ʟᴏɢɪɴ-ꜱᴛʀᴇᴀᴋ", "select name, streak as v from ls_players order by streak desc limit ?", null, "days");
            case "level" -> new Board(key, "<gold>ʟᴏʙʙʏ-ʟᴇᴠᴇʟ", "select name, xp as v from ls_players order by xp desc limit ?", null, "level");
            case "cookies" -> new Board(key, "<#c79081>ᴄᴏᴏᴋɪᴇ ᴄʟɪᴄᴋᴇʀ", "select name, cookies as v from ls_players order by cookies desc limit ?", null, "cookies");
            case "friends" -> new Board(key, "<dark_aqua>ꜰʀᴇᴜɴᴅᴇ", "select p.name, count(*) as v from ls_friends f join ls_players p on p.uuid = f.uuid "
                    + "group by p.uuid, p.name order by v desc limit ?", null, "number");
            default -> {
                java.util.regex.Matcher m = GAME_KEY.matcher(key);
                if (!m.matches()) yield null;
                String game = m.group(1), stat = m.group(2);
                String gameName = plugin.configs().cfg().integrations.games.getOrDefault(game, "<aqua>" + game);
                String statName = switch (stat) {
                    case "wins" -> "ꜱɪᴇɢᴇ";
                    case "kills" -> "ᴋɪʟʟꜱ";
                    case "deaths" -> "ᴛᴏᴅᴇ";
                    case "games" -> "ʀᴜɴᴅᴇɴ";
                    default -> "ʟᴇᴠᴇʟ";
                };
                yield new Board(key, gameName + " <dark_gray>·</dark_gray> <gray>" + statName,
                        "select name, " + stat + " as v from ls_game_stats where game = ? order by " + stat + " desc limit ?", game,
                        stat.equals("level") ? "plainlevel" : "number");
            }
        };
    }

    private final LobbySystem plugin;
    private final NamespacedKey key;
    private final Map<String, TextDisplay> displays = new HashMap<>();
    private final Map<String, String> texts = new HashMap<>();

    public LeaderboardManager(LobbySystem plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "leaderboard");
    }

    private ConfigManager c() {
        return plugin.configs();
    }

    public void set(Board board, Location loc) {
        if (loc == null) c().locations().leaderboards.remove(board.key());
        else c().locations().leaderboards.put(board.key(), SLoc.of(loc));
        c().saveLocations();
        despawn(board.key());
        refresh();
    }

    /** Daten neu laden (async) und Hologramme aktualisieren. */
    public void refresh() {
        PluginConfig.Leaderboards cfg = c().cfg().leaderboards;
        for (String k : new ArrayList<>(c().locations().leaderboards.keySet())) {
            Board type = board(k);
            if (type == null) continue;
            plugin.database().submit(con -> {
                List<String[]> rows = new ArrayList<>();
                try (PreparedStatement ps = con.prepareStatement(type.sql())) {
                    int i = 1;
                    if (type.gameParam() != null) ps.setString(i++, type.gameParam());
                    ps.setInt(i, Math.max(1, Math.min(30, cfg.size)));
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            long v = (long) rs.getDouble(2);
                            if (v <= 0) continue;
                            rows.add(new String[]{rs.getString(1), format(type, v)});
                        }
                    }
                }
                return rows;
            }).thenAccept(rows -> Bukkit.getScheduler().runTask(plugin, () -> render(type, rows)));
        }
    }

    private String format(Board type, long v) {
        return switch (type.unit()) {
            case "time" -> Text.formatDuration(v);
            case "days" -> v + (v == 1 ? " Tag" : " Tage");
            case "level" -> "Level " + plugin.levels().level(v);
            case "plainlevel" -> "Level " + v;
            case "cookies" -> de.crafterteam.lobbysystem.progression.CookieManager.format(v) + " Kekse";
            default -> Text.formatNumber(v);
        };
    }

    private void render(Board type, List<String[]> rows) {
        PluginConfig.Leaderboards cfg = c().cfg().leaderboards;
        StringBuilder sb = new StringBuilder(Text.apply(cfg.header, ConfigManager.map(
                "size", Integer.toString(cfg.size), "title", type.title())));
        sb.append("\n");
        if (rows.isEmpty()) sb.append("\n").append(cfg.empty);
        for (int i = 0; i < rows.size(); i++) {
            String color = i < cfg.podium.size() ? cfg.podium.get(i) : "";
            sb.append("\n").append(color).append(Text.apply(cfg.line, ConfigManager.map(
                    "place", Integer.toString(i + 1), "name", rows.get(i)[0], "value", rows.get(i)[1])));
        }
        texts.put(type.key(), sb.toString());
        TextDisplay d = displays.get(type.key());
        if (d != null && d.isValid()) d.text(Text.parse(sb.toString()));
        else spawn(type.key());
    }

    /** Alle 5 Sekunden: fehlende Hologramme (Chunk-Unload) neu spawnen. */
    public void tick() {
        for (String k : new ArrayList<>(c().locations().leaderboards.keySet())) {
            TextDisplay d = displays.get(k);
            if (d == null || !d.isValid()) spawn(k);
        }
    }

    private void spawn(String type) {
        SLoc s = c().locations().leaderboards.get(type);
        Location loc = s == null ? null : s.toLocation();
        if (loc == null) return;
        World w = loc.getWorld();
        if (!w.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;
        despawn(type);
        String text = texts.getOrDefault(type, "<gray>Lädt...");
        TextDisplay d = w.spawn(loc.clone().add(0, 1.5, 0), TextDisplay.class, t -> {
            t.text(Text.parse(text));
            t.setBillboard(Display.Billboard.VERTICAL);
            t.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            t.setShadowed(true);
            t.setLineWidth(250);
            t.setPersistent(false);
            t.setViewRange(0.6f);
            t.getPersistentDataContainer().set(key, PersistentDataType.STRING, type);
        });
        displays.put(type, d);
    }

    public void despawn(String type) {
        TextDisplay d = displays.remove(type);
        if (d != null) d.remove();
    }

    public void despawnAll() {
        for (String t : new ArrayList<>(displays.keySet())) despawn(t);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (e.getPersistentDataContainer().has(key, PersistentDataType.STRING) && !displays.containsValue(e)) e.remove();
        }
    }
}
