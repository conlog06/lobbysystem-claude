package de.crafterteam.lobbysystem.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import de.crafterteam.lobbysystem.util.SLoc;
import de.crafterteam.lobbysystem.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class ConfigManager {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final JavaPlugin plugin;
    private final Path folder;

    private PluginConfig config;
    private List<Rank> ranks;
    private Locations locations;
    private Map<String, SLoc> warps;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folder = plugin.getDataFolder().toPath();
    }

    public void loadAll() {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Konnte Plugin-Ordner nicht erstellen", e);
        }
        config = loadMerged("config.json", new PluginConfig(), PluginConfig.class);
        ranks = loadList("ranks.json", Rank.defaults(), new TypeToken<List<Rank>>() {}.getType());
        ranks.sort((a, b) -> Integer.compare(a.priority, b.priority));
        locations = loadMerged("locations.json", new Locations(), Locations.class);
        warps = loadList("warps.json", new LinkedHashMap<String, SLoc>(), new TypeToken<LinkedHashMap<String, SLoc>>() {}.getType());
    }

    /** Lädt eine JSON-Datei, ergänzt fehlende Schlüssel aus den Defaults und schreibt sie zurück. */
    private <T> T loadMerged(String file, T defaults, Class<T> type) {
        Path path = folder.resolve(file);
        JsonObject def = GSON.toJsonTree(defaults).getAsJsonObject();
        try {
            if (Files.exists(path)) {
                JsonElement user = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
                if (user.isJsonObject()) merge(def, user.getAsJsonObject());
            }
            Files.writeString(path, GSON.toJson(def), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler in " + file + " – es werden Standardwerte genutzt!", e);
            return defaults;
        }
        return GSON.fromJson(def, type);
    }

    private <T> T loadList(String file, T defaults, Type type) {
        Path path = folder.resolve(file);
        try {
            if (Files.exists(path)) {
                T value = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
                if (value != null) return value;
            }
            Files.writeString(path, GSON.toJson(defaults, type), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler in " + file + " – es werden Standardwerte genutzt!", e);
        }
        return defaults;
    }

    /** user überschreibt base; verschachtelte Objekte werden rekursiv gemerged, Listen ersetzt. */
    private static void merge(JsonObject base, JsonObject user) {
        for (Map.Entry<String, JsonElement> e : user.entrySet()) {
            JsonElement existing = base.get(e.getKey());
            if (existing != null && existing.isJsonObject() && e.getValue().isJsonObject()) {
                merge(existing.getAsJsonObject(), e.getValue().getAsJsonObject());
            } else {
                base.add(e.getKey(), e.getValue());
            }
        }
    }

    public void saveLocations() {
        write("locations.json", GSON.toJson(locations));
    }

    public void saveWarps() {
        write("warps.json", GSON.toJson(warps));
    }

    private void write(String file, String json) {
        try {
            Files.writeString(folder.resolve(file), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Konnte " + file + " nicht speichern", e);
        }
    }

    // ------------------------------------------------------------------ Zugriff

    public PluginConfig cfg() {
        return config;
    }

    public List<Rank> ranks() {
        return ranks;
    }

    public Locations locations() {
        return locations;
    }

    public Map<String, SLoc> warps() {
        return warps;
    }

    /** Permission aus prefix + suffix. Leerer Suffix => keine Permission nötig (null). */
    public String perm(String key) {
        String suffix = config.permissions.get("suffix_" + key);
        if (suffix == null || suffix.isBlank()) return null;
        String prefix = config.permissions.getOrDefault("prefix", "lobbysystem");
        return prefix.isBlank() ? suffix : prefix + "." + suffix;
    }

    public boolean has(CommandSender sender, String key) {
        String p = perm(key);
        return p == null || sender.hasPermission(p);
    }

    public boolean hasCosmetic(CommandSender sender, String id) {
        String base = perm("cosmetic");
        if (has(sender, "cosmetics_all") && perm("cosmetics_all") != null) return true;
        return base != null && sender.hasPermission(base + id);
    }

    public String raw(String key) {
        return config.messages.getOrDefault(key, "<red>Fehlende Nachricht: " + key);
    }

    public Component msg(String key, String... ph) {
        return Text.parse(config.prefix + raw(key), map(ph));
    }

    public void send(CommandSender to, String key, String... ph) {
        to.sendMessage(msg(key, ph));
    }

    /** Nachricht mit eigenem Prefix (z. B. friend_prefix, party_prefix, clan_prefix). */
    public Component msgP(String prefixKey, String key, String... ph) {
        return Text.parse(raw(prefixKey) + raw(key), map(ph));
    }

    public void sendP(CommandSender to, String prefixKey, String key, String... ph) {
        to.sendMessage(msgP(prefixKey, key, ph));
    }

    public static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    // ------------------------------------------------------------------ Modelle

    public static class Locations {
        public SLoc spawn;
        public SLoc reward;
        public SLoc settings;
        /** Bestenlisten: Typ (coins, playtime, streak, friends) -> Position */
        public Map<String, SLoc> leaderboards = new LinkedHashMap<>();
        public List<String> info = new ArrayList<>(List.of(
                "Hier werden Spawn & NPC-Positionen gespeichert. Bitte über /setspawn, /setreward, /setsettings ändern."));
    }

    public static class Rank {
        public String name;
        /** leer = Standard-Rang */
        public String permission;
        public int priority;
        /** Prefix in Tab, über dem Kopf und im Chat */
        public String prefix;
        /** Farbe des Namens (Tab/Nametag): Name wie red, dark_red, aqua … */
        public String color;
        /** Anzeige im Scoreboard */
        public String display;
        /**
         * Rang-Badge aus dem Ressourcenpaket: owner, leitung, admin, dev, mod, sup, builder, content, youtuber,
         * premiumplus, premium, vip, spieler, team – leer = automatisch nach Rangname, "none" = kein Badge.
         */
        public String badge;

        public Rank() {
        }

        Rank(String name, String permission, int priority, String prefix, String color, String display) {
            this.badge = "";
            this.name = name;
            this.permission = permission;
            this.priority = priority;
            this.prefix = prefix;
            this.color = color;
            this.display = display;
        }

        static List<Rank> defaults() {
            List<Rank> l = new ArrayList<>();
            l.add(new Rank("Leitung", "lobbysystem.rank.leitung", 1, "<dark_red>ʟᴇɪᴛᴜɴɢ <dark_gray>•</dark_gray> ", "dark_red", "<dark_red>Leitung"));
            l.add(new Rank("Admin", "lobbysystem.rank.admin", 2, "<red>ᴀᴅᴍɪɴ <dark_gray>•</dark_gray> ", "red", "<red>Admin"));
            l.add(new Rank("Developer", "lobbysystem.rank.dev", 3, "<aqua>ᴅᴇᴠ <dark_gray>•</dark_gray> ", "aqua", "<aqua>Developer"));
            l.add(new Rank("Moderator", "lobbysystem.rank.mod", 4, "<dark_green>ᴍᴏᴅ <dark_gray>•</dark_gray> ", "dark_green", "<dark_green>Moderator"));
            l.add(new Rank("Supporter", "lobbysystem.rank.sup", 5, "<green>ꜱᴜᴘ <dark_gray>•</dark_gray> ", "green", "<green>Supporter"));
            l.add(new Rank("Builder", "lobbysystem.rank.builder", 6, "<yellow>ʙᴜɪʟᴅᴇʀ <dark_gray>•</dark_gray> ", "yellow", "<yellow>Builder"));
            l.add(new Rank("Premium+", "lobbysystem.rank.premiumplus", 7, "<gold>ᴘʀᴇᴍɪᴜᴍ+ <dark_gray>•</dark_gray> ", "gold", "<gold>Premium+"));
            l.add(new Rank("Premium", "lobbysystem.rank.premium", 8, "<gold>ᴘʀᴇᴍɪᴜᴍ <dark_gray>•</dark_gray> ", "gold", "<gold>Premium"));
            l.add(new Rank("Spieler", "", 99, "<gray>", "gray", "<gray>Spieler"));
            return l;
        }
    }
}
