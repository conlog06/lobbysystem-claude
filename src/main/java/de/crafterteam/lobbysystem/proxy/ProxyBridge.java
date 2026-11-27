package de.crafterteam.lobbysystem.proxy;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anbindung an das BungeeSystem (github.com/conlog06/bungeesystem-crafterteam) über den Kanal {@value #CHANNEL}.
 * <ul>
 *   <li>Freunde/Party/Clan laufen dann auf dem Proxy – Klicks im Social-Menü werden dorthin weitergereicht</li>
 *   <li>Chat-Filter & Anti-Spam übernimmt der Proxy, Party-Nachziehen und Online-Meldungen ebenso</li>
 *   <li>Clan-Gründung bucht die Coins über diesen Server ab (Cache bleibt korrekt)</li>
 *   <li>Netzwerkweite Onlinezeit für das Scoreboard: {onlinetime}</li>
 *   <li>/coins, /advert & Bug-Belohnungen vom Proxy laufen über den Coin-Cache dieses Servers; /hub in der Lobby = Spawn</li>
 * </ul>
 */
public final class ProxyBridge implements PluginMessageListener {

    public static final String CHANNEL = "crafterteam:bungee";

    private final LobbySystem plugin;
    private volatile boolean helloReceived;
    private final Set<String> modules = ConcurrentHashMap.newKeySet();
    private final Map<String, String> commands = new ConcurrentHashMap<>();
    private final Map<UUID, Long> onlineTime = new ConcurrentHashMap<>();

    public ProxyBridge(LobbySystem plugin) {
        this.plugin = plugin;
    }

    public void start() {
        var m = plugin.getServer().getMessenger();
        m.registerOutgoingPluginChannel(plugin, CHANNEL);
        m.registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    private PluginConfig.BungeeSystem cfg() {
        return plugin.configs().cfg().bungeeSystem;
    }

    /** BungeeSystem aktiv (und Modul eingeschaltet)? */
    public boolean active(String module) {
        String mode = cfg().mode == null ? "auto" : cfg().mode.toLowerCase();
        if (mode.equals("off")) return false;
        if (mode.equals("on")) return true;
        return helloReceived && (module == null || modules.contains(module));
    }

    /**
     * Aktion an den Proxy weiterreichen ("friend add Name" …). true = erledigt, das LobbySystem macht nichts mehr selbst.
     */
    public boolean forward(Player p, String module, String key, String args) {
        if (!active(module) || p == null || !p.isOnline()) return false;
        String cmd = commands.getOrDefault(key, key) + (args == null || args.isBlank() ? "" : " " + args);
        send(p, "RUN", out -> {
            out.writeUTF(p.getUniqueId().toString());
            out.writeUTF(cmd);
        });
        return true;
    }

    private final java.util.Set<UUID> packHandled = ConcurrentHashMap.newKeySet();

    /** Hat der Proxy für diesen Spieler schon über das Pack entschieden? */
    public boolean packHandled(UUID uuid) {
        return packHandled.contains(uuid);
    }

    public void packStatus(Player p, boolean ok) {
        if (!helloReceived) return;
        send(p, "PACK_STATUS", out -> {
            out.writeUTF(p.getUniqueId().toString());
            out.writeBoolean(ok);
        });
    }

    public long onlineTimeSeconds(UUID uuid) {
        Long ms = onlineTime.get(uuid);
        return ms == null ? -1 : ms / 1000;
    }

    public void forget(UUID uuid) {
        onlineTime.remove(uuid);
        packHandled.remove(uuid);
        plugin.pack().forget(uuid);
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    private void send(Player via, String type, Writer w) {
        Runnable r = () -> {
            if (!via.isOnline()) return;
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeUTF(type);
                w.write(out);
                via.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
            } catch (IOException e) {
                plugin.getLogger().warning("BungeeSystem-Nachricht fehlgeschlagen: " + e.getMessage());
            }
        };
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player carrier, byte @NotNull [] message) {
        if (!CHANNEL.equals(channel)) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String type = in.readUTF();
            switch (type) {
                case "HELLO" -> {
                    in.readInt();
                    String mods = in.readUTF();
                    String cmds = in.available() > 0 ? in.readUTF() : "";
                    modules.clear();
                    for (String m : mods.split(",")) if (!m.isBlank()) modules.add(m.trim());
                    for (String pair : cmds.split(";")) {
                        int eq = pair.indexOf('=');
                        if (eq > 0) commands.put(pair.substring(0, eq), pair.substring(eq + 1));
                    }
                    String serverName = in.available() > 0 ? in.readUTF() : "";
                    // echten Namen aus der Proxy-Config übernehmen (Lobby-Switcher, Freunde-Status, Party)
                    var cfg = plugin.configs().cfg();
                    if (!serverName.isBlank() && !serverName.equals(cfg.proxyServerName)) {
                        plugin.getLogger().info("Servername vom Proxy übernommen: " + serverName + " (config.json: " + cfg.proxyServerName + ")");
                        cfg.proxyServerName = serverName;
                    }
                    if (!helloReceived) plugin.getLogger().info("BungeeSystem erkannt – Module: " + mods);
                    helloReceived = true;
                    // Antwort: eigenes Chat-Format (Rang-Badges) + welche Datenbank genutzt wird
                    var my = cfg.mysql;
                    send(carrier, "SERVER_INFO", out -> {
                        out.writeUTF("LobbySystem");
                        out.writeUTF(plugin.getPluginMeta().getVersion());
                        out.writeBoolean(cfg.chat.enabled);
                        out.writeUTF(my.enabled ? "mysql" : "sqlite");
                        out.writeUTF(my.enabled ? my.host + ":" + my.port + "/" + my.database : "plugins/LobbySystem/data.db");
                        out.writeUTF(carrier.getUniqueId().toString()); // UUID-Abgleich mit dem Proxy
                        // Ressourcenpaket der Lobby → der Proxy verteilt es netzwerkweit
                        out.writeUTF(plugin.pack().url());
                        out.writeUTF(plugin.pack().sha1());
                    });
                }
                case "ONLINETIME" -> onlineTime.put(UUID.fromString(in.readUTF()), in.readLong());
                case "CLANTAG" -> {
                    UUID uuid = UUID.fromString(in.readUTF());
                    if (Bukkit.getPlayer(uuid) != null) plugin.clans().refresh(uuid);
                }
                case "FRIEND_REFRESH" -> plugin.friends().refreshCount(UUID.fromString(in.readUTF()));
                case "FRIEND_SETTINGS" -> {
                    UUID uuid = UUID.fromString(in.readUTF());
                    String settings = in.readUTF();
                    plugin.data().cached(uuid).ifPresent(d -> d.loadFriendSettings(settings));
                }
                case "COINS_TAKE" -> {
                    int id = in.readInt();
                    UUID uuid = UUID.fromString(in.readUTF());
                    long amount = in.readLong();
                    plugin.coins().removeCoins(uuid, amount, "bungeesystem").thenAccept(ok -> {
                        Player via = Bukkit.getPlayer(uuid);
                        send(via != null ? via : carrier, "COINS_RESULT", out -> {
                            out.writeInt(id);
                            out.writeBoolean(Boolean.TRUE.equals(ok));
                        });
                    });
                }
                case "COINS_ADD" -> {
                    int id = in.readInt();
                    UUID uuid = UUID.fromString(in.readUTF());
                    long amount = in.readLong();
                    plugin.coins().addCoins(uuid, amount, "bungeesystem").thenAccept(ok -> {
                        Player via = Bukkit.getPlayer(uuid);
                        send(via != null ? via : carrier, "COINS_RESULT", out -> {
                            out.writeInt(id);
                            out.writeBoolean(Boolean.TRUE.equals(ok));
                        });
                    });
                }
                case "COINS_GET" -> {
                    int id = in.readInt();
                    UUID uuid = UUID.fromString(in.readUTF());
                    plugin.coins().getCoins(uuid).thenAccept(c -> {
                        Player via = Bukkit.getPlayer(uuid);
                        send(via != null ? via : carrier, "COINS_VALUE", out -> {
                            out.writeInt(id);
                            out.writeLong(c == null ? -1 : c);
                        });
                    });
                }
                case "PACK" -> {
                    // url, sha1, required, prompt, loaded – die Lobby hostet das Pack selbst, wichtig ist nur "loaded"
                    UUID uuid = UUID.fromString(in.readUTF());
                    in.readUTF();
                    in.readUTF();
                    in.readBoolean();
                    in.readUTF();
                    boolean alreadyLoaded = in.readBoolean();
                    packHandled.add(uuid);
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) return;
                    if (alreadyLoaded) plugin.pack().markLoaded(p);
                    else Bukkit.getScheduler().runTask(plugin, () -> plugin.pack().send(p));
                }
                case "SPAWN" -> {
                    Player p = Bukkit.getPlayer(UUID.fromString(in.readUTF()));
                    if (p != null) Bukkit.getScheduler().runTask(plugin, () -> plugin.lobby().teleportSpawn(p, true));
                }
                default -> {
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            plugin.getLogger().warning("Ungültige BungeeSystem-Nachricht: " + e.getMessage());
        }
    }
}
