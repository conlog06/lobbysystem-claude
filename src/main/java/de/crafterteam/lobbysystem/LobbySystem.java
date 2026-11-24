package de.crafterteam.lobbysystem;

import de.crafterteam.lobbysystem.api.CoinAPI;
import de.crafterteam.lobbysystem.api.LobbySystemAPI;
import de.crafterteam.lobbysystem.command.LicenseCommand;
import de.crafterteam.lobbysystem.command.SocialCommands;
import de.crafterteam.lobbysystem.pack.ResourcePackManager;
import de.crafterteam.lobbysystem.social.ClanManager;
import de.crafterteam.lobbysystem.social.FriendManager;
import de.crafterteam.lobbysystem.social.Messenger;
import de.crafterteam.lobbysystem.social.PartyManager;
import de.crafterteam.lobbysystem.social.ServerRegistry;
import de.crafterteam.lobbysystem.storage.CoinService;
import org.bukkit.plugin.ServicePriority;
import de.crafterteam.lobbysystem.command.LobbyCommands;
import de.crafterteam.lobbysystem.config.ConfigManager;
import de.crafterteam.lobbysystem.cosmetics.CosmeticManager;
import de.crafterteam.lobbysystem.display.BarManager;
import de.crafterteam.lobbysystem.display.SidebarManager;
import de.crafterteam.lobbysystem.gui.MenuListener;
import de.crafterteam.lobbysystem.license.LicenseManager;
import de.crafterteam.lobbysystem.lobby.LobbyListener;
import de.crafterteam.lobbysystem.lobby.LobbyManager;
import de.crafterteam.lobbysystem.npc.NpcManager;
import de.crafterteam.lobbysystem.storage.Database;
import de.crafterteam.lobbysystem.storage.PlayerDataManager;
import de.crafterteam.lobbysystem.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * LobbySystem für Paper 1.21.10 – 26.3
 * Owner & Lizenzen: SujucDerBreite
 */
public final class LobbySystem extends JavaPlugin {

    private ConfigManager configs;
    private LicenseManager licenses;
    private Database database;
    private PlayerDataManager data;
    private LobbyManager lobby;
    private SidebarManager sidebar;
    private BarManager bars;
    private CosmeticManager cosmetics;
    private NpcManager npcs;
    private CoinService coins;
    private Messenger messenger;
    private FriendManager friends;
    private PartyManager parties;
    private ClanManager clans;
    private ServerRegistry servers;
    private ResourcePackManager pack;
    private boolean started;

    @Override
    public void onEnable() {
        Keys.init(this);
        configs = new ConfigManager(this);
        configs.loadAll();

        licenses = new LicenseManager(this);
        if (!licenses.check(configs.cfg().license)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        database = new Database(this, configs.cfg().mysql);
        try {
            database.connect();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Keine Verbindung zur Datenbank (" + (database.isMySql() ? "MySQL" : "SQLite")
                    + ") – bitte config.json prüfen!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Datenbank verbunden: " + (database.isMySql() ? "MySQL" : "SQLite (lokal)"));

        data = new PlayerDataManager(this, database);
        coins = new CoinService(this);
        messenger = new Messenger(this);
        friends = new FriendManager(this);
        parties = new PartyManager(this);
        clans = new ClanManager(this);
        servers = new ServerRegistry(this);
        pack = new ResourcePackManager(this);
        pack.start();
        lobby = new LobbyManager(this);
        sidebar = new SidebarManager(this);
        bars = new BarManager(this);
        cosmetics = new CosmeticManager(this);
        npcs = new NpcManager(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new LobbyListener(this), this);
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(cosmetics, this);
        pm.registerEvents(npcs, this);
        pm.registerEvents(pack, this);

        getServer().getMessenger().registerOutgoingPluginChannel(this, LobbyManager.BUNGEE_CHANNEL);

        LobbyCommands commands = new LobbyCommands(this);
        for (String cmd : new String[]{"build", "lobbycoins", "setreward", "setsettings", "setspawn", "setwarp", "warp", "spawn", "tpcoord", "lobbysystem", "lobbyswitcher"}) {
            register(cmd, commands);
        }
        register("license", new LicenseCommand(this));
        SocialCommands social = new SocialCommands(this);
        for (String cmd : new String[]{"friend", "party", "clan"}) register(cmd, social);

        // ---- API für andere Plugins
        getServer().getServicesManager().register(CoinAPI.class, coins, this, ServicePriority.Normal);
        LobbySystemAPI.init(coins, new LobbySystemAPI.SocialAPI() {
            @Override
            public java.util.Optional<String> clanTag(java.util.UUID uuid) {
                return clans.cached(uuid).map(ClanManager.Clan::tag);
            }

            @Override
            public java.util.Optional<String> clanName(java.util.UUID uuid) {
                return clans.cached(uuid).map(ClanManager.Clan::name);
            }

            @Override
            public int[] friendCount(java.util.UUID uuid) {
                return friends.count(uuid);
            }

            @Override
            public String rankName(Player player) {
                return sidebar.rank(player).name;
            }
        });
        if (pm.getPlugin("PlaceholderAPI") != null) {
            try {
                new de.crafterteam.lobbysystem.hook.LobbyPlaceholders(this).register();
                getLogger().info("PlaceholderAPI-Expansion registriert (%lobbysystem_...%).");
            } catch (Throwable t) {
                getLogger().warning("PlaceholderAPI-Hook fehlgeschlagen: " + t.getMessage());
            }
        }

        applyWorldRules();
        startTasks();

        // Reload-sicher: bereits verbundene Spieler übernehmen
        for (Player p : Bukkit.getOnlinePlayers()) {
            data.preloadAsync(p, () -> {
                initPlayer(p, false);
                data.presence(p.getUniqueId(), configs.cfg().proxyServerName);
                clans.refresh(p.getUniqueId());
                friends.refreshCount(p.getUniqueId());
            });
        }
        npcs.cleanup();
        started = true;
        getLogger().info("LobbySystem v" + getPluginMeta().getVersion() + " gestartet (Paper 1.21.10 – 26.3).");
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) return;
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);
    }

    private void applyWorldRules() {
        for (World w : Bukkit.getWorlds()) {
            if (configs.cfg().protection.noWeather) {
                w.setStorm(false);
                w.setThundering(false);
            }
        }
    }

    private void startTasks() {
        // jede Sekunde: Scoreboard, Tab, Bossbar, Actionbar
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            sidebar.tick();
            bars.tick();
            if (configs.cfg().protection.alwaysDay) {
                for (World w : Bukkit.getWorlds()) w.setTime(6000);
            }
        }, 20L, 20L);
        // jeden Tick: Ballone & Partikel
        Bukkit.getScheduler().runTaskTimer(this, cosmetics::tick, 1L, 1L);
        // alle 2 Sekunden: NPCs prüfen
        Bukkit.getScheduler().runTaskTimer(this, npcs::tick, 40L, 40L);
        // jede Minute: Spielzeit, Freunde-Zähler, Clan-Cache, Party-Aufräumen
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) data.get(p).addPlaytime(60);
            friends.refreshAll();
            clans.refreshAll();
            parties.cleanup();
        }, 1200L, 1200L);
        // alle 10 Sekunden: Server-Heartbeat (Lobby-Switcher, Online-Status)
        servers.heartbeat();
        Bukkit.getScheduler().runTaskTimer(this, servers::heartbeat, 200L, 200L);
        // alle 5 Minuten: Autosave
        Bukkit.getScheduler().runTaskTimer(this, data::saveAll, 6000L, 6000L);
    }

    /** Spieler komplett einrichten (Join oder Reload). */
    public void initPlayer(Player p, boolean teleport) {
        lobby.setup(p, teleport);
        sidebar.create(p);
        bars.show(p);
        lobby.updateVisibility();
    }

    public void reload() {
        configs.loadAll();
        cosmetics.reload();
        bars.reload();
        pack.start();
        applyWorldRules();
        npcs.despawnAll();
        npcs.tick();
        for (Player p : Bukkit.getOnlinePlayers()) {
            lobby.giveHotbar(p);
            cosmetics.apply(p);
            sidebar.create(p);
        }
        lobby.updateVisibility();
    }

    @Override
    public void onDisable() {
        if (!started) {
            if (database != null) database.shutdown();
            return;
        }
        Bukkit.getScheduler().cancelTasks(this);
        cosmetics.shutdown();
        npcs.despawnAll();
        for (Player p : Bukkit.getOnlinePlayers()) {
            bars.hide(p);
            cosmetics.clear(p);
            if (lobby.isBuilding(p)) lobby.toggleBuild(p);
        }
        pack.stop();
        getServer().getServicesManager().unregisterAll(this);
        data.saveAllBlocking();
        servers.shutdown();
        database.shutdown();
    }

    // ---------------------------------------------------------------- Zugriff

    public ConfigManager configs() {
        return configs;
    }

    public LicenseManager licenses() {
        return licenses;
    }

    public Database database() {
        return database;
    }

    public PlayerDataManager data() {
        return data;
    }

    public LobbyManager lobby() {
        return lobby;
    }

    public SidebarManager sidebar() {
        return sidebar;
    }

    public BarManager bars() {
        return bars;
    }

    public CosmeticManager cosmetics() {
        return cosmetics;
    }

    public NpcManager npcs() {
        return npcs;
    }

    public CoinService coins() {
        return coins;
    }

    public Messenger messenger() {
        return messenger;
    }

    public FriendManager friends() {
        return friends;
    }

    public PartyManager parties() {
        return parties;
    }

    public ClanManager clans() {
        return clans;
    }

    public ServerRegistry servers() {
        return servers;
    }

    public ResourcePackManager pack() {
        return pack;
    }
}
