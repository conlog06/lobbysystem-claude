package de.crafterteam.lobbysystem.gui;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import de.crafterteam.lobbysystem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lobby-Auswahl eines Spielmodus mit mehreren Runden auf einem Server (z. B. MissileWars):
 * alle Lobbys mit Map, Spielern & Status, Schnell-Beitritt und – wenn alle voll sind – "Neue Lobby starten".
 * Der Wunsch wird in ls_game_joins gespeichert, der Spielserver setzt den Spieler beim Join in die richtige Runde.
 */
public final class GameLobbiesMenu extends Menu {

    public record GameLobby(String server, int id, String map, String state, int players, int max) {
        boolean open() {
            return ("WAITING".equals(state) || "COUNTDOWN".equals(state)) && (max <= 0 || players < max);
        }
    }

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final String game, server, title;
    private final List<GameLobby> lobbies;

    private GameLobbiesMenu(LobbySystem plugin, Player player, String game, String server, String title, List<GameLobby> lobbies) {
        super(plugin, player);
        this.game = game;
        this.server = server;
        this.title = title;
        this.lobbies = lobbies;
        create(5, "<dark_gray>↪</dark_gray> " + title);
    }

    /** game = Spiel-ID in der DB ("missilewars"), server = Servername-Präfix ("MissileWars"). */
    public static void open(LobbySystem plugin, Player player, String game, String server, String title) {
        plugin.database().submit(con -> {
            List<GameLobby> out = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement("select server, lobby_id, map, state, players, max_players from ls_game_lobbies "
                    + "where game = ? and updated > ? order by server, lobby_id")) {
                ps.setString(1, game);
                ps.setLong(2, System.currentTimeMillis() - 15_000L);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new GameLobby(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getInt(6)));
                }
            }
            return out;
        }).exceptionally(e -> List.of()).thenAccept(list -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) new GameLobbiesMenu(plugin, player, game, server, title, list).open();
        }));
    }

    @Override
    protected void build() {
        String icon = "mode_" + game;
        if (lobbies.isEmpty()) {
            set(22, new ItemBuilder(Material.BARRIER).name("<red>Keine Lobby gefunden")
                    .lore("<dark_gray>»</dark_gray> <gray>Der Server startet vielleicht gerade.", "<dark_gray>»</dark_gray> <gray>Klicke für den Schnell-Beitritt").build(),
                    e -> join(null, -1));
        }
        for (int i = 0; i < lobbies.size() && i < SLOTS.length; i++) {
            GameLobby l = lobbies.get(i);
            Material mat = switch (l.state()) {
                case "WAITING" -> Material.LIME_CONCRETE;
                case "COUNTDOWN" -> Material.YELLOW_CONCRETE;
                case "INGAME" -> Material.RED_CONCRETE;
                default -> Material.GRAY_CONCRETE;
            };
            String state = switch (l.state()) {
                case "WAITING" -> l.open() ? "<green>● Wartet auf Spieler" : "<gold>● Voll";
                case "COUNTDOWN" -> l.open() ? "<yellow>● Startet gleich" : "<gold>● Voll";
                case "INGAME" -> "<red>● Runde läuft <gray>(zuschauen)";
                default -> "<dark_gray>● Endet";
            };
            set(SLOTS[i], new ItemBuilder(mat).amount(Math.max(1, Math.min(64, l.players())))
                    .name("<dark_gray>»</dark_gray> <white>Lobby #" + l.id())
                    .lore("<dark_gray>↪</dark_gray> <gray>Map: <white>" + Text.escape(l.map()),
                            "<dark_gray>↪</dark_gray> <gray>Spieler: <white>" + l.players() + "<dark_gray>/</dark_gray><white>" + l.max(),
                            "<dark_gray>↪</dark_gray> " + state,
                            "",
                            "ENDING".equals(l.state()) ? "<dark_gray>»</dark_gray> <gray>Gleich wieder frei"
                                    : "<dark_gray>»</dark_gray> <yellow>Klicke zum " + (l.open() ? "Beitreten" : "Zuschauen"))
                    .glow(l.open()).hideFlags().build(), e -> {
                if ("ENDING".equals(l.state())) {
                    deny();
                    return;
                }
                join(l.server(), l.id());
            });
        }
        boolean anyOpen = lobbies.stream().anyMatch(GameLobby::open);
        // Schnell-Beitritt
        set(39, new ItemBuilder(Material.TNT).name("<dark_gray>»</dark_gray> <green>Schnell beitreten")
                .lore("<dark_gray>↪</dark_gray> <gray>Die vollste offene Lobby – Runden füllen sich schneller", "",
                        "<dark_gray>»</dark_gray> <yellow>Klicke zum Spielen").icon(player, icon).build(), e -> join(null, -1));
        // Neue Lobby starten – nur wenn alle voll sind
        if (anyOpen) {
            set(41, new ItemBuilder(Material.GRAY_DYE).name("<dark_gray>»</dark_gray> <gray>Neue Lobby starten")
                    .lore("<dark_gray>↪</dark_gray> <gray>Es gibt noch freie Lobbys –", "<dark_gray>↪</dark_gray> <gray>tritt einer davon bei!").build(), e -> deny());
        } else {
            set(41, new ItemBuilder(Material.EMERALD).name("<dark_gray>»</dark_gray> <green><b>Neue Lobby starten</b>")
                    .lore("<dark_gray>↪</dark_gray> <gray>Alle Lobbys sind voll oder spielen gerade.", "<dark_gray>↪</dark_gray> <gray>Starte deine eigene Runde!", "",
                            "<dark_gray>»</dark_gray> <yellow>Klicke zum Starten").glow(true).build(), e -> join(null, 0));
        }
        set(36, new ItemBuilder(Material.ARROW).name("<dark_gray>»</dark_gray> <gray>Zurück zum Navigator").icon(player, "back").build(), e -> {
            click();
            new NavigatorMenu(plugin, player).open();
        });
        set(44, new ItemBuilder(Material.CLOCK).name("<dark_gray>»</dark_gray> <aqua>Aktualisieren").icon(player, "switcher").build(), e -> {
            click();
            open(plugin, player, game, server, title);
        });
        border(Material.BLACK_STAINED_GLASS_PANE);
    }

    /** Wunsch speichern und verbinden. lobby: -1 = beste, 0 = neue Lobby. */
    private void join(String target, int lobby) {
        click();
        String srv = target;
        if (srv == null) {
            // Server mit der vollsten offenen Lobby, sonst der beste laut Heartbeat
            srv = lobbies.stream().filter(GameLobby::open).max(Comparator.comparingInt(GameLobby::players)).map(GameLobby::server).orElse(null);
            if (srv == null && !lobbies.isEmpty()) srv = lobbies.get(0).server();
            if (srv == null) {
                var best = plugin.servers().best(server);
                srv = best == null ? null : best.name();
            }
        }
        if (srv == null) {
            plugin.configs().send(player, "game_offline", "game", server);
            player.closeInventory();
            return;
        }
        String finalServer = srv, id = player.getUniqueId().toString();
        player.closeInventory();
        plugin.database().submit(con -> {
            try (PreparedStatement ps = con.prepareStatement("delete from ls_game_joins where uuid = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("insert into ls_game_joins (uuid, server, game, lobby_id, created) values (?, ?, ?, ?, ?)")) {
                ps.setString(1, id);
                ps.setString(2, finalServer);
                ps.setString(3, game);
                ps.setInt(4, lobby);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            }
            return null;
        }).whenComplete((v, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) plugin.lobby().connect(player, finalServer);
        }));
    }
}
