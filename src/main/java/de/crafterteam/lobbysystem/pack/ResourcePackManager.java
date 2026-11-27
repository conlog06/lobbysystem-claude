package de.crafterteam.lobbysystem.pack;

import com.sun.net.httpserver.HttpServer;
import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.PluginConfig;
import de.crafterteam.lobbysystem.util.Text;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Ressourcenpaket: Das mitgelieferte Pack (Icons) wird nach plugins/LobbySystem/resourcepack/LobbyPack.zip
 * entpackt und – falls keine externe URL gesetzt ist – vom Plugin selbst über HTTP ausgeliefert.
 * Eigene Packs: einfach die ZIP-Datei ersetzen und /lobbysystem reload.
 */
public final class ResourcePackManager implements Listener {

    /** Gleiche ID im ganzen Netzwerk (Addon nutzt sie auch) – der Client lädt das Pack nur einmal. */
    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("lobbysystem-pack".getBytes());
    private static volatile ResourcePackManager instance;
    /** Spieler, bei denen das Pack geladen ist (eigene Item-Icons nur dann). */
    private final java.util.Set<UUID> loaded = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final LobbySystem plugin;
    private HttpServer server;
    private volatile byte[] packBytes;
    private volatile String sha1;
    private volatile URI uri;

    public ResourcePackManager(LobbySystem plugin) {
        this.plugin = plugin;
        instance = this;
    }

    /** Hat der Spieler das Pack geladen? (für ItemBuilder.icon) */
    public static boolean icons(Player p) {
        ResourcePackManager m = instance;
        return m != null && m.cfg().enabled && m.loaded.contains(p.getUniqueId());
    }

    public String url() {
        return uri == null ? "" : uri.toString();
    }

    public String sha1() {
        return sha1 == null ? "" : sha1;
    }

    public void forget(UUID uuid) {
        loaded.remove(uuid);
    }

    /** Proxy meldet: Pack ist in dieser Sitzung schon geladen → nicht erneut senden, Icons direkt an. */
    public void markLoaded(Player p) {
        if (loaded.add(p.getUniqueId())) refreshItems(p);
    }

    private void refreshItems(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) plugin.lobby().giveHotbar(p);
        });
    }

    /**
     * Beim Join: läuft das BungeeSystem, entscheidet der Proxy (Nachricht PACK), ob gesendet werden muss –
     * sonst nach 3 s selbst senden.
     */
    public void join(Player player) {
        if (!plugin.proxy().active(null)) {
            send(player);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !loaded.contains(player.getUniqueId()) && !plugin.proxy().packHandled(player.getUniqueId())) send(player);
        }, 60L);
    }

    private PluginConfig.ResourcePack cfg() {
        return plugin.configs().cfg().resourcepack;
    }

    public void start() {
        stop();
        applyIcons();
        PluginConfig.ResourcePack rp = cfg();
        if (!rp.enabled) return;
        try {
            if (rp.url == null || rp.url.isBlank()) {
                Path file = extract();
                packBytes = Files.readAllBytes(file);
                sha1 = sha1(packBytes);
                String host = rp.selfHost.publicHost == null || rp.selfHost.publicHost.isBlank()
                        ? Bukkit.getIp() : rp.selfHost.publicHost;
                if (host == null || host.isBlank()) {
                    plugin.getLogger().warning("Ressourcenpaket: Bitte 'resourcepack.selfHost.publicHost' (IP/Domain) eintragen!");
                    return;
                }
                server = HttpServer.create(new InetSocketAddress(rp.selfHost.port), 0);
                server.createContext("/", exchange -> {
                    byte[] data = packBytes;
                    exchange.getResponseHeaders().add("Content-Type", "application/zip");
                    exchange.sendResponseHeaders(200, data.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(data);
                    }
                });
                server.setExecutor(Executors.newFixedThreadPool(2, r -> {
                    Thread t = new Thread(r, "LobbySystem-Pack-HTTP");
                    t.setDaemon(true);
                    return t;
                }));
                server.start();
                uri = URI.create("http://" + host + ":" + rp.selfHost.port + "/LobbyPack.zip?v=" + sha1.substring(0, 10));
                plugin.getLogger().info("Ressourcenpaket wird gehostet: " + uri + " (Port " + rp.selfHost.port + " muss offen sein)");
            } else {
                uri = URI.create(rp.url.trim());
                if (rp.sha1 != null && rp.sha1.matches("[0-9a-fA-F]{40}")) {
                    sha1 = rp.sha1.toLowerCase();
                } else {
                    // SHA-1 automatisch berechnen
                    HttpClient.newHttpClient().sendAsync(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofByteArray())
                            .thenAccept(res -> {
                                if (res.statusCode() == 200) {
                                    sha1 = sha1(res.body());
                                    plugin.getLogger().info("Ressourcenpaket SHA-1: " + sha1);
                                } else {
                                    plugin.getLogger().warning("Ressourcenpaket konnte nicht geladen werden (HTTP " + res.statusCode() + ")");
                                }
                            });
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ressourcenpaket konnte nicht gestartet werden", e);
        }
    }

    /** Icon-Platzhalter: eigene Glyphen (mit Pack) oder Unicode-Ersatz. */
    public void applyIcons() {
        boolean glyphs = cfg().enabled && cfg().icons;
        Map<String, String> m = new LinkedHashMap<>();
        m.put("i_coin", glyphs ? "<white></white>" : "<yellow>⭐</yellow>");
        m.put("i_clock", glyphs ? "<white></white>" : "<aqua>⌚</aqua>");
        m.put("i_rank", glyphs ? "<white></white>" : "<light_purple>✦</light_purple>");
        m.put("i_clan", glyphs ? "<white></white>" : "<gold>⚑</gold>");
        m.put("i_friends", glyphs ? "<white></white>" : "<dark_aqua>☺</dark_aqua>");
        m.put("i_party", glyphs ? "<white></white>" : "<light_purple>✪</light_purple>");
        m.put("i_heart", glyphs ? "<white></white>" : "<red>❤</red>");
        m.put("i_ticket", glyphs ? "<white></white>" : "<yellow>✉</yellow>");
        m.put("i_online", glyphs ? "<white></white>" : "<green>●</green>");
        m.put("i_offline", glyphs ? "<white></white>" : "<red>●</red>");
        m.put("i_gem", glyphs ? "<white></white>" : "<aqua>◆</aqua>");
        // CrafterTeam-Logo (Tab-Header) & kleiner CT-Tag
        m.put("i_crafterteam", glyphs ? "<white>\uE200</white>" : "<gradient:#00d2ff:#3a7bd5><b>ᴄʀᴀꜰᴛᴇʀᴛᴇᴀᴍ</b></gradient>");
        m.put("i_ct", glyphs ? "<white>\uE201</white>" : "<gradient:#00d2ff:#3a7bd5><b>ᴄᴛ</b></gradient>");
        // Rang-Badges (nur mit Pack – ohne Pack wird der normale Text-Prefix aus ranks.json genutzt)
        if (glyphs) {
            String[] keys = {"owner", "leitung", "admin", "dev", "mod", "sup", "builder", "content",
                    "youtuber", "premiumplus", "premium", "vip", "spieler", "team", "elite", "legende"};
            for (int i = 0; i < keys.length; i++) {
                m.put("r_" + keys[i], "<white>" + (char) (0xE100 + i) + "</white>");
            }
        }
        Text.icons(m);
    }

    /**
     * Entpackt das mitgelieferte Pack. Wurde die Datei nicht selbst verändert, wird sie bei
     * Plugin-Updates automatisch durch die neue Version ersetzt (Marker: .bundled-sha1).
     */
    private Path extract() throws Exception {
        Path dir = plugin.getDataFolder().toPath().resolve("resourcepack");
        Files.createDirectories(dir);
        Path file = dir.resolve("LobbyPack.zip");
        Path marker = dir.resolve(".bundled-sha1");
        byte[] bundled;
        try (InputStream in = plugin.getResource("pack/LobbyPack.zip")) {
            if (in == null) throw new IllegalStateException("pack/LobbyPack.zip fehlt im Plugin");
            bundled = in.readAllBytes();
        }
        String bundledSha = sha1(bundled);
        boolean replace = !Files.exists(file);
        if (!replace) {
            String current = sha1(Files.readAllBytes(file));
            String lastBundled = Files.exists(marker) ? Files.readString(marker).trim() : "";
            // unverändertes altes Standard-Pack (oder Pack aus v2.1 ohne Marker) → aktualisieren
            replace = !current.equals(bundledSha) && (current.equals(lastBundled) || lastBundled.isEmpty() && isOldDefault(file));
        }
        if (replace) {
            Files.write(file, bundled);
            plugin.getLogger().info("Standard-Ressourcenpaket aktualisiert.");
        }
        Files.writeString(marker, bundledSha);
        return file;
    }

    /** Erkennt das Standard-Pack der Version 2.1 (noch ohne Marker-Datei). */
    private static boolean isOldDefault(Path file) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file.toFile())) {
            var entry = zip.getEntry("pack.mcmeta");
            if (entry == null) return false;
            String meta = new String(zip.getInputStream(entry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return meta.contains("LobbySystem") && meta.contains("\"max_format\": 200");
        } catch (Exception e) {
            return false;
        }
    }

    private static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void send(Player player) {
        PluginConfig.ResourcePack rp = cfg();
        if (!rp.enabled || uri == null || sha1 == null) return;
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PACK_ID, uri, sha1);
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(rp.required)
                .replace(false)
                .prompt(Text.parse(rp.prompt))
                .build());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                if (loaded.add(player.getUniqueId())) refreshItems(player);
                plugin.proxy().packStatus(player, true);
            }
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> plugin.proxy().packStatus(player, false);
            default -> {
            }
        }
        PluginConfig.ResourcePack rp = cfg();
        if (!rp.enabled || !rp.required) return;
        switch (event.getStatus()) {
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL -> event.getPlayer().kick(Text.parse(rp.kickMessage));
            default -> {
            }
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        uri = null;
    }

    public static String hex(byte[] b) {
        return HexFormat.of().formatHex(b);
    }
}
