package de.crafterteam.lobbysystem.storage;

import de.crafterteam.lobbysystem.config.PluginConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * MySQL (geteilt mit anderen Servern) oder lokal SQLite. Alle Zugriffe laufen über
 * einen eigenen Thread – der Main-Thread wird nie blockiert.
 */
public final class Database {

    @FunctionalInterface
    public interface SqlTask<T> {
        T run(Connection con) throws SQLException;
    }

    private final JavaPlugin plugin;
    private final PluginConfig.MySql settings;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LobbySystem-Database");
        t.setDaemon(true);
        return t;
    });
    private Connection connection;

    public Database(JavaPlugin plugin, PluginConfig.MySql settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public boolean isMySql() {
        return settings.enabled;
    }

    public void connect() throws Exception {
        submit(con -> {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("create table if not exists ls_players ("
                        + "uuid varchar(36) not null primary key, "
                        + "name varchar(16) not null, "
                        + "coins bigint not null default 0, "
                        + "lose int not null default 0, "
                        + "playtime bigint not null default 0, "
                        + "last_daily bigint not null default 0, "
                        + "last_premium bigint not null default 0, "
                        + "settings varchar(255) not null default '', "
                        + "selected varchar(255) not null default '', "
                        + "owned text)");
                st.executeUpdate("create table if not exists ls_licenses ("
                        + "id varchar(36) not null primary key, "
                        + "holder varchar(64) not null, "
                        + "created bigint not null, "
                        + "expires bigint not null, "
                        + "created_by varchar(16) not null, "
                        + "license_key text not null)");
                // ---- Social: Freunde, Party, Clan, Server-Registry
                st.executeUpdate("create table if not exists ls_friends ("
                        + "uuid varchar(36) not null, friend varchar(36) not null, since bigint not null, "
                        + "primary key (uuid, friend))");
                st.executeUpdate("create table if not exists ls_friend_requests ("
                        + "sender varchar(36) not null, receiver varchar(36) not null, created bigint not null, "
                        + "primary key (sender, receiver))");
                st.executeUpdate("create table if not exists ls_parties ("
                        + "id varchar(36) not null primary key, leader varchar(36) not null, created bigint not null)");
                st.executeUpdate("create table if not exists ls_party_members ("
                        + "uuid varchar(36) not null primary key, party_id varchar(36) not null, joined bigint not null)");
                st.executeUpdate("create table if not exists ls_party_invites ("
                        + "party_id varchar(36) not null, uuid varchar(36) not null, expires bigint not null, "
                        + "primary key (party_id, uuid))");
                st.executeUpdate("create table if not exists ls_clans ("
                        + "id varchar(36) not null primary key, name varchar(32) not null, tag varchar(8) not null, "
                        + "owner varchar(36) not null, created bigint not null)");
                st.executeUpdate("create table if not exists ls_clan_members ("
                        + "uuid varchar(36) not null primary key, clan_id varchar(36) not null, role int not null default 0, "
                        + "joined bigint not null)");
                st.executeUpdate("create table if not exists ls_clan_invites ("
                        + "clan_id varchar(36) not null, uuid varchar(36) not null, expires bigint not null, "
                        + "primary key (clan_id, uuid))");
                st.executeUpdate("create table if not exists ls_servers ("
                        + "name varchar(64) not null primary key, online int not null default 0, "
                        + "max_players int not null default 0, updated bigint not null default 0)");
            }
            // ---- Spalten-Migration für bestehende Installationen (v2.0 -> v2.1)
            addColumn(con, "ls_players", "online_server", "varchar(64) not null default ''");
            addColumn(con, "ls_players", "last_seen", "bigint not null default 0");
            addColumn(con, "ls_players", "streak", "int not null default 0");
            addColumn(con, "ls_players", "friend_settings", "varchar(64) not null default ''");
            return null;
        }).get(15, TimeUnit.SECONDS);
    }

    private static void addColumn(Connection con, String table, String column, String definition) throws SQLException {
        boolean exists = false;
        try (java.sql.ResultSet rs = con.getMetaData().getColumns(con.getCatalog(), null, table, null)) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("alter table " + table + " add column " + column + " " + definition);
            }
        }
    }

    private Connection connection() throws SQLException {
        if (connection != null && connection.isValid(2)) return connection;
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
        if (settings.enabled) {
            tryDriver("com.mysql.cj.jdbc.Driver");
            String url = "jdbc:mysql://" + settings.host + ":" + settings.port + "/" + settings.database
                    + (settings.properties == null || settings.properties.isBlank() ? "" : "?" + settings.properties);
            connection = DriverManager.getConnection(url, settings.user, settings.password);
        } else {
            tryDriver("org.sqlite.JDBC");
            File file = new File(plugin.getDataFolder(), "data.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        }
        return connection;
    }

    private static void tryDriver(String name) {
        try {
            Class.forName(name);
        } catch (ClassNotFoundException ignored) {
            // Treiber wird ggf. über den ServiceLoader gefunden
        }
    }

    public <T> CompletableFuture<T> submit(SqlTask<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.run(connection());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Datenbank-Fehler: " + e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
