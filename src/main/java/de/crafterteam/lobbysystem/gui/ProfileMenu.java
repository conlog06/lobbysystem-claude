package de.crafterteam.lobbysystem.gui;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import de.crafterteam.lobbysystem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Profil mit Lobby-Stats und Spielmodus-Stats (z. B. MissileWars) aus ls_game_stats. */
public final class ProfileMenu extends Menu {

    /** Stats eines Spielmodus */
    public record GameStats(String game, int wins, int kills, int deaths, int games, int level) {
    }

    private final List<GameStats> games;

    private ProfileMenu(LobbySystem plugin, Player player, List<GameStats> games) {
        super(plugin, player);
        this.games = games;
        create(4, "<dark_gray>↪</dark_gray> <gradient:#56ab2f:#a8e063>ᴘʀᴏꜰɪʟ</gradient>");
    }

    public static void open(LobbySystem plugin, Player player) {
        String id = player.getUniqueId().toString();
        plugin.database().submit(con -> {
            List<GameStats> out = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(
                    "select game, wins, kills, deaths, games, level from ls_game_stats where uuid = ? order by game")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new GameStats(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6)));
                }
            }
            return out;
        }).exceptionally(e -> List.of()).thenAccept(list -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) new ProfileMenu(plugin, player, list).open();
        }));
    }

    @Override
    protected void build() {
        PlayerData d = plugin.data().get(player);
        var rank = plugin.sidebar().rank(player);
        set(11, new ItemBuilder(Material.PLAYER_HEAD).skull(player)
                .name("<dark_gray>»</dark_gray> <white>" + Text.escape(player.getName()))
                .lore("<dark_gray>↪</dark_gray> <gray>Rang: " + badge(rank) + (rank.display == null ? rank.name : rank.display),
                        "<dark_gray>↪</dark_gray> {i_coin} <gray>Coins: <yellow>" + Text.formatNumber(d.coins()),
                        "<dark_gray>↪</dark_gray> {i_ticket} <gray>Lose: <yellow>" + d.lose(),
                        "<dark_gray>↪</dark_gray> {i_clock} <gray>Spielzeit: <aqua>" + Text.formatDuration(d.playtime()),
                        "<dark_gray>↪</dark_gray> {i_heart} <gray>Login-Streak: <gold>" + d.streak() + " Tage",
                        "",
                        "<dark_gray>↪</dark_gray> {i_rank} <gray>Lobby-Level: <gold>" + plugin.levels().level(d.xp()),
                        "<dark_gray>↪</dark_gray> " + plugin.levels().bar(d.xp(), 25)).build());
        set(13, new ItemBuilder(Material.COMPARATOR).name("<dark_gray>»</dark_gray> <aqua>Einstellungen")
                .lore("<dark_gray>↪</dark_gray> <gray>Passe deine Lobby an").icon(player, "settings").build(), e -> {
            click();
            new SettingsMenu(plugin, player).open();
        });
        if (plugin.configs().cfg().cookieClicker.enabled) {
            set(31, new ItemBuilder(Material.COOKIE).name("<dark_gray>»</dark_gray> <#dfa579>Cookie Clicker")
                    .lore("<dark_gray>↪</dark_gray> <gray>" + de.crafterteam.lobbysystem.progression.CookieManager.format(d.cookies()) + " Kekse",
                            "<dark_gray>↪</dark_gray> <gray>Backe Kekse und steige im Level auf!", "",
                            "<dark_gray>»</dark_gray> <yellow>Klicke zum Spielen").glow(true).build(), e -> {
                click();
                new CookieMenu(plugin, player).open();
            });
        }
        // Kiste statt Uhr: die Uhr wird bei manchen Clients/Packs als "fehlende Textur" angezeigt
        set(15, new ItemBuilder(Material.CHEST).name("<dark_gray>»</dark_gray> <light_purple>Tägliche Belohnung")
                .lore("<dark_gray>↪</dark_gray> <gray>Hol dir Coins & Lose").icon(player, "daily").build(), e -> {
            click();
            new DailyRewardMenu(plugin, player).open();
        });

        // Rang-Shop
        if (plugin.configs().cfg().rankShop.enabled) {
            set(4, new ItemBuilder(Material.GOLD_INGOT).name("<dark_gray>»</dark_gray> <gradient:#f9d423:#ff4e50><b>Rang-Shop</b></gradient>")
                    .lore("<dark_gray>↪</dark_gray> <gray>VIP, Premium, Premium+, Elite & Legende", "<dark_gray>↪</dark_gray> <gray>mit Coins freischalten", "",
                            "<dark_gray>»</dark_gray> <yellow>Klicke zum Öffnen").glow(true).icon(player, "coins").build(), e -> {
                click();
                new RankShopMenu(plugin, player).open();
            });
        }
        // Lotterie & Lobby-Switcher (vorher eigene Hotbar-Items)
        set(29, new ItemBuilder(Material.NAME_TAG).name("<dark_gray>»</dark_gray> <yellow>Lotterie")
                .lore("<dark_gray>↪</dark_gray> <gray>Du hast <yellow>" + d.lose() + "</yellow> <gray>Lose", "",
                        "<dark_gray>»</dark_gray> <yellow>Klicke zum Öffnen").icon(player, "lottery").build(), e -> {
            click();
            new LotteryMenu(plugin, player).open();
        });
        set(33, new ItemBuilder(Material.END_CRYSTAL).name("<dark_gray>»</dark_gray> <aqua>Lobby wechseln")
                .lore("<dark_gray>↪</dark_gray> <gray>Alle Lobbys mit Spielerzahl", "",
                        "<dark_gray>»</dark_gray> <yellow>Klicke zum Öffnen").icon(player, "switcher").build(), e -> {
            click();
            LobbySwitcherMenu.open(plugin, player);
        });

        TITLES = plugin.configs().cfg().integrations.games;
        // ---- Spielmodus-Stats
        int[] slots = {20, 21, 22, 23, 24};
        if (games.isEmpty()) {
            set(22, new ItemBuilder(Material.TNT).name("<dark_gray>»</dark_gray> <red>Missile Wars")
                    .lore("<dark_gray>↪</dark_gray> <gray>Noch keine Runde gespielt", "<dark_gray>»</dark_gray> <yellow>Über den Navigator spielen")
                    .icon(player, "mode_missilewars").build());
        }
        for (int i = 0; i < games.size() && i < slots.length; i++) {
            GameStats g = games.get(i);
            double kd = g.deaths() == 0 ? g.kills() : (double) g.kills() / g.deaths();
            double wr = g.games() == 0 ? 0 : 100.0 * g.wins() / g.games();
            set(slots[i], new ItemBuilder(icon(g.game())).icon(player, "mode_" + g.game()).name("<dark_gray>»</dark_gray> " + title(g.game()))
                    .lore("<dark_gray>↪</dark_gray> <gray>Level: <gold>" + g.level(),
                            "<dark_gray>↪</dark_gray> <gray>Siege: <green>" + g.wins() + " <dark_gray>(" + String.format(Locale.GERMANY, "%.0f", wr) + " %)",
                            "<dark_gray>↪</dark_gray> <gray>Runden: <white>" + g.games(),
                            "<dark_gray>↪</dark_gray> <gray>Kills: <white>" + g.kills() + " <dark_gray>|</dark_gray> <gray>Tode: <white>" + g.deaths(),
                            "<dark_gray>↪</dark_gray> <gray>K/D: <aqua>" + String.format(Locale.GERMANY, "%.2f", kd)).build());
        }
        fill(Material.GRAY_STAINED_GLASS_PANE);
    }

    private static String badge(de.crafterteam.lobbysystem.config.ConfigManager.Rank rank) {
        String icon = Text.icon("r_" + de.crafterteam.lobbysystem.display.SidebarManager.badgeKey(rank));
        return icon == null ? "" : icon + " ";
    }

    private static String title(String game) {
        return TITLES.getOrDefault(game, "<aqua>" + Text.escape(game));
    }

    /** wird beim Bauen aus integrations.games befüllt */
    private static java.util.Map<String, String> TITLES = java.util.Map.of();

    private static Material icon(String game) {
        return switch (game) {
            case "missilewars" -> Material.TNT;
            case "bedwars" -> Material.RED_BED;
            case "skywars" -> Material.FEATHER;
            case "hideandseek" -> Material.SPYGLASS;
            case "zombies" -> Material.ZOMBIE_HEAD;
            case "worldevolution" -> Material.GRASS_BLOCK;
            default -> Material.IRON_SWORD;
        };
    }
}
