package de.crafterteam.lobbysystem;

import de.crafterteam.lobbysystem.command.LicenseCommand;
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

        getServer().getMessenger().registerOutgoingPluginChannel(this, LobbyManager.BUNGEE_CHANNEL);

        LobbyCommands commands = new LobbyCommands(this);
        for (String cmd : new String[]{"build", "lobbycoins", "setreward", "setsettings", "setspawn", "setwarp", "warp", "spawn", "tpcoord", "lobbysystem"}) {
            register(cmd, commands);
        }
        register("license", new LicenseCommand(this));

        applyWorldRules();
        startTasks();

        // Reload-sicher: bereits verbundene Spieler übernehmen
        for (Player p : Bukkit.getOnlinePlayers()) {
            data.preloadAsync(p, () -> initPlayer(p, false));
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
        // jede Minute: Spielzeit
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) data.get(p).addPlaytime(60);
        }, 1200L, 1200L);
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
        data.saveAllBlocking();
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
}
