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

    public enum Type {
        COINS("<yellow>ᴄᴏɪɴꜱ", "select name, coins as v from ls_players order by coins desc limit ?"),
        PLAYTIME("<aqua>ꜱᴘɪᴇʟᴢᴇɪᴛ", "select name, playtime as v from ls_players order by playtime desc limit ?"),
        STREAK("<gold>ʟᴏɢɪɴ-ꜱᴛʀᴇᴀᴋ", "select name, streak as v from ls_players order by streak desc limit ?"),
        FRIENDS("<dark_aqua>ꜰʀᴇᴜɴᴅᴇ", "select p.name, count(*) as v from ls_friends f join ls_players p on p.uuid = f.uuid "
                + "group by p.uuid, p.name order by v desc limit ?");

        final String title;
        final String sql;

        Type(String title, String sql) {
            this.title = title;
            this.sql = sql;
        }

        public static Type parse(String s) {
            try {
                return valueOf(s.toUpperCase());
            } catch (Exception e) {
                return null;
            }
        }
    }

    private final LobbySystem plugin;
    private final NamespacedKey key;
    private final Map<Type, TextDisplay> displays = new HashMap<>();
    private final Map<Type, String> texts = new HashMap<>();

    public LeaderboardManager(LobbySystem plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "leaderboard");
    }

    private ConfigManager c() {
        return plugin.configs();
    }

    public void set(Type type, Location loc) {
        if (loc == null) c().locations().leaderboards.remove(type.name().toLowerCase());
        else c().locations().leaderboards.put(type.name().toLowerCase(), SLoc.of(loc));
        c().saveLocations();
        despawn(type);
        refresh();
    }

    /** Daten neu laden (async) und Hologramme aktualisieren. */
    public void refresh() {
        PluginConfig.Leaderboards cfg = c().cfg().leaderboards;
        for (String k : c().locations().leaderboards.keySet()) {
            Type type = Type.parse(k);
            if (type == null) continue;
            plugin.database().submit(con -> {
                List<String[]> rows = new ArrayList<>();
                try (PreparedStatement ps = con.prepareStatement(type.sql)) {
                    ps.setInt(1, Math.max(1, Math.min(30, cfg.size)));
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            long v = rs.getLong(2);
                            if (v <= 0) continue;
                            rows.add(new String[]{rs.getString(1), format(type, v)});
                        }
                    }
                }
                return rows;
            }).thenAccept(rows -> Bukkit.getScheduler().runTask(plugin, () -> render(type, rows)));
        }
    }

    private static String format(Type type, long v) {
        return switch (type) {
            case PLAYTIME -> Text.formatDuration(v);
            case STREAK -> v + (v == 1 ? " Tag" : " Tage");
            default -> Text.formatNumber(v);
        };
    }

    private void render(Type type, List<String[]> rows) {
        PluginConfig.Leaderboards cfg = c().cfg().leaderboards;
        StringBuilder sb = new StringBuilder(Text.apply(cfg.header, ConfigManager.map(
                "size", Integer.toString(cfg.size), "title", type.title)));
        sb.append("\n");
        if (rows.isEmpty()) sb.append("\n").append(cfg.empty);
        for (int i = 0; i < rows.size(); i++) {
            String color = i < cfg.podium.size() ? cfg.podium.get(i) : "";
            sb.append("\n").append(color).append(Text.apply(cfg.line, ConfigManager.map(
                    "place", Integer.toString(i + 1), "name", rows.get(i)[0], "value", rows.get(i)[1])));
        }
        texts.put(type, sb.toString());
        TextDisplay d = displays.get(type);
        if (d != null && d.isValid()) d.text(Text.parse(sb.toString()));
        else spawn(type);
    }

    /** Alle 5 Sekunden: fehlende Hologramme (Chunk-Unload) neu spawnen. */
    public void tick() {
        for (String k : c().locations().leaderboards.keySet()) {
            Type type = Type.parse(k);
            if (type == null) continue;
            TextDisplay d = displays.get(type);
            if (d == null || !d.isValid()) spawn(type);
        }
    }

    private void spawn(Type type) {
        SLoc s = c().locations().leaderboards.get(type.name().toLowerCase());
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
            t.getPersistentDataContainer().set(key, PersistentDataType.STRING, type.name());
        });
        displays.put(type, d);
    }

    public void despawn(Type type) {
        TextDisplay d = displays.remove(type);
        if (d != null) d.remove();
    }

    public void despawnAll() {
        for (Type t : Type.values()) despawn(t);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (e.getPersistentDataContainer().has(key, PersistentDataType.STRING) && !displays.containsValue(e)) e.remove();
        }
    }
}
