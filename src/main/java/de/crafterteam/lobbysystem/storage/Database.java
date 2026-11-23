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
            }
            return null;
        }).get(15, TimeUnit.SECONDS);
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
