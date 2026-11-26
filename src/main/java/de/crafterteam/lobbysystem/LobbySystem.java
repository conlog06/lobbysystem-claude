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
    private de.crafterteam.lobbysystem.display.LeaderboardManager leaderboards;
    private de.crafterteam.lobbysystem.lobby.SitListener sit;
    private de.crafterteam.lobbysystem.storage.GameStatsService gameStats;
    private de.crafterteam.lobbysystem.progression.LevelManager levels;
    private de.crafterteam.lobbysystem.progression.CookieManager cookies;
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

        boolean lobbyMode = lobbyMode();
        data = new PlayerDataManager(this, database);
        coins = new CoinService(this);
        gameStats = new de.crafterteam.lobbysystem.storage.GameStatsService(this);
        messenger = new Messenger(this);
        friends = new FriendManager(this);
        parties = new PartyManager(this);
        clans = new ClanManager(this);
        servers = new ServerRegistry(this);
        levels = new de.crafterteam.lobbysystem.progression.LevelManager(this);
        cookies = new de.crafterteam.lobbysystem.progression.CookieManager(this);
        pack = new ResourcePackManager(this);
        lobby = new LobbyManager(this);
        sidebar = new SidebarManager(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, LobbyManager.BUNGEE_CHANNEL);
        LobbyCommands commands = new LobbyCommands(this);
        SocialCommands social = new SocialCommands(this);
        for (String cmd : new String[]{"friend", "party", "clan"}) register(cmd, social);
        for (String cmd : new String[]{"lobbysystem", "pay", "level", "lobbyswitcher"}) register(cmd, commands);
        register("license", new LicenseCommand(this));

        if (lobbyMode) {
            pack.start();
            bars = new BarManager(this);
            cosmetics = new CosmeticManager(this);
            npcs = new NpcManager(this);
            leaderboards = new de.crafterteam.lobbysystem.display.LeaderboardManager(this);
            sit = new de.crafterteam.lobbysystem.lobby.SitListener(this);
            pm.registerEvents(new LobbyListener(this), this);
            pm.registerEvents(cosmetics, this);
            pm.registerEvents(npcs, this);
            pm.registerEvents(pack, this);
            pm.registerEvents(leaderboards, this);
            pm.registerEvents(sit, this);
            for (String cmd : new String[]{"build", "lobbycoins", "setreward", "setsettings", "setspawn", "setwarp", "warp", "spawn",
                    "tpcoord", "setleaderboard", "cookie"}) {
                register(cmd, commands);
            }
        } else {
            // Spielserver: nur Brücke (Coins, Freunde/Party/Clan, Online-Status, Stats-API)
            pack.applyIcons();
            servers.state(de.crafterteam.lobbysystem.api.GameAPI.WAITING);
            servers.game(configs.cfg().proxyServerName.replaceAll("[-_ ]?\\d+$", "").toLowerCase());
            pm.registerEvents(new de.crafterteam.lobbysystem.lobby.GameModeListener(this), this);
            register("lobbycoins", commands);
            getLogger().info("Modus: GAME – LobbySystem läuft als Brücke (Spielmodus: " + servers.game() + ").");
        }

        // ---- API für andere Plugins
        getServer().getServicesManager().register(CoinAPI.class, coins, this, ServicePriority.Normal);
        getServer().getServicesManager().register(de.crafterteam.lobbysystem.api.GameAPI.class, gameStats, this, ServicePriority.Normal);
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

            @Override
            public int level(Player player) {
                return levels.level(player);
            }
        }, gameStats);
        if (pm.getPlugin("PlaceholderAPI") != null) {
            try {
                new de.crafterteam.lobbysystem.hook.LobbyPlaceholders(this).register();
                getLogger().info("PlaceholderAPI-Expansion registriert (%lobbysystem_...%).");
            } catch (Throwable t) {
                getLogger().warning("PlaceholderAPI-Hook fehlgeschlagen: " + t.getMessage());
            }
        }
        if (configs.cfg().integrations.vault && pm.getPlugin("Vault") != null) {
            try {
                de.crafterteam.lobbysystem.hook.VaultEconomy.register(this);
            } catch (Throwable t) {
                getLogger().warning("Vault-Hook fehlgeschlagen: " + t.getMessage());
            }
        }
        if (configs.cfg().integrations.luckPermsRanks) {
            getLogger().info(pm.getPlugin("LuckPerms") != null ? "Ränge kommen aus LuckPerms." : "luckPermsRanks aktiv, aber LuckPerms fehlt – nutze ranks.json.");
        }

        if (lobbyMode) applyWorldRules();
        startTasks(lobbyMode);

        // Reload-sicher: bereits verbundene Spieler übernehmen
        for (Player p : Bukkit.getOnlinePlayers()) {
            data.preloadAsync(p, () -> {
                if (lobbyMode) initPlayer(p, false);
                data.presence(p.getUniqueId(), configs.cfg().proxyServerName);
                clans.refresh(p.getUniqueId());
                friends.refreshCount(p.getUniqueId());
            });
        }
        if (lobbyMode) npcs.cleanup();
        started = true;
        getLogger().info("LobbySystem v" + getPluginMeta().getVersion() + " gestartet (Paper 1.21.10 – 26.3, Modus: "
                + (lobbyMode ? "LOBBY" : "GAME") + ").");
    }

    public boolean lobbyMode() {
        return !"game".equalsIgnoreCase(configs.cfg().mode);
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

    private void startTasks(boolean lobbyMode) {
        if (lobbyMode) {
            // jede Sekunde: Scoreboard, Tab, Bossbar, Actionbar, Cookie-Produktion
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                sidebar.tick();
                bars.tick();
                cookies.second();
                if (configs.cfg().protection.alwaysDay) {
                    for (World w : Bukkit.getWorlds()) w.setTime(6000);
                }
            }, 20L, 20L);
            // jeden Tick: Ballone & Partikel
            Bukkit.getScheduler().runTaskTimer(this, cosmetics::tick, 1L, 1L);
            // alle 2 Sekunden: NPCs prüfen, alle 5 Sekunden: Bestenlisten-Hologramme
            Bukkit.getScheduler().runTaskTimer(this, npcs::tick, 40L, 40L);
            Bukkit.getScheduler().runTaskTimer(this, leaderboards::tick, 100L, 100L);
            long lbTicks = Math.max(10, configs.cfg().leaderboards.updateSeconds) * 20L;
            Bukkit.getScheduler().runTaskTimer(this, leaderboards::refresh, 60L, lbTicks);
        }
        // jede Minute: Spielzeit, Level-XP, Freunde-Zähler, Clan-Cache, Party-Aufräumen
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) data.get(p).addPlaytime(60);
            if (lobbyMode) levels.minute();
            friends.refreshAll();
            clans.refreshAll();
            parties.cleanup();
        }, 1200L, 1200L);
        // alle 10 Sekunden: Server-Heartbeat (Lobby-Switcher, Navigator, Online-Status)
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
        de.crafterteam.lobbysystem.util.Text.clearCache();
        if (!lobbyMode()) {
            pack.applyIcons();
            return;
        }
        cosmetics.reload();
        leaderboards.despawnAll();
        leaderboards.refresh();
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
        if (cosmetics != null) cosmetics.shutdown();
        if (npcs != null) npcs.despawnAll();
        if (leaderboards != null) leaderboards.despawnAll();
        if (sit != null) sit.shutdown();
        if (bars != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                bars.hide(p);
                cosmetics.clear(p);
                if (lobby.isBuilding(p)) lobby.toggleBuild(p);
            }
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

    public de.crafterteam.lobbysystem.display.LeaderboardManager leaderboards() {
        return leaderboards;
    }

    public de.crafterteam.lobbysystem.progression.LevelManager levels() {
        return levels;
    }

    public de.crafterteam.lobbysystem.progression.CookieManager cookies() {
        return cookies;
    }

    public de.crafterteam.lobbysystem.storage.GameStatsService gameStats() {
        return gameStats;
    }

    public ResourcePackManager pack() {
        return pack;
    }
}
