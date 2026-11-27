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
import java.util.List;
import java.util.UUID;

/** Elo-Bestenliste (aus ls_game_stats.elo, z. B. MissileWars) + eigener Platz. */
public final class EloTopMenu extends Menu {

    record Row(UUID uuid, String name, int elo, int wins, int games) {
    }

    private static final int[] SLOTS = {
            4,
            12, 14,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};
    private final String game;
    private final List<Row> rows;
    private final int ownElo, ownRank;

    private EloTopMenu(LobbySystem plugin, Player player, String game, List<Row> rows, int ownElo, int ownRank) {
        super(plugin, player);
        this.game = game;
        this.rows = rows;
        this.ownElo = ownElo;
        this.ownRank = ownRank;
        create(6, "<dark_gray>↪</dark_gray> <gradient:#c471ed:#f64f59>ᴇʟᴏ-ʙᴇꜱᴛᴇɴʟɪꜱᴛᴇ</gradient>");
    }

    /** Ränge wie in MissileWars */
    static String tier(int elo) {
        if (elo >= 1900) return "<#FF2E63>Champion";
        if (elo >= 1700) return "<#C471ED>Meister";
        if (elo >= 1500) return "<#5AB0FF>Diamant";
        if (elo >= 1300) return "<#4FD1C5>Platin";
        if (elo >= 1100) return "<#FFC145>Gold";
        if (elo >= 900) return "<#C0C0C0>Silber";
        return "<#CD7F32>Bronze";
    }

    public static void open(LobbySystem plugin, Player player, String game) {
        String id = player.getUniqueId().toString();
        plugin.database().submit(con -> {
            List<Row> out = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement("select uuid, name, elo, wins, games from ls_game_stats where game = ? and elo > 0 "
                    + "order by elo desc limit " + SLOTS.length)) {
                ps.setString(1, game);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new Row(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)));
                }
            }
            int own = 0, rank = 0;
            try (PreparedStatement ps = con.prepareStatement("select elo from ls_game_stats where game = ? and uuid = ?")) {
                ps.setString(1, game);
                ps.setString(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) own = rs.getInt(1);
                }
            }
            if (own > 0) {
                try (PreparedStatement ps = con.prepareStatement("select count(*) from ls_game_stats where game = ? and elo > ?")) {
                    ps.setString(1, game);
                    ps.setInt(2, own);
                    try (ResultSet rs = ps.executeQuery()) {
                        rank = (rs.next() ? rs.getInt(1) : 0) + 1;
                    }
                }
            }
            return new Object[]{out, own, rank};
        }).exceptionally(e -> new Object[]{List.of(), 0, 0}).thenAccept(r -> Bukkit.getScheduler().runTask(plugin, () -> {
            @SuppressWarnings("unchecked") List<Row> list = (List<Row>) r[0];
            if (player.isOnline()) new EloTopMenu(plugin, player, game, list, (Integer) r[1], (Integer) r[2]).open();
        }));
    }

    @Override
    protected void build() {
        for (int i = 0; i < rows.size() && i < SLOTS.length; i++) {
            Row r = rows.get(i);
            String place = i == 0 ? "<gold><b>#1</b>" : i == 1 ? "<gray><b>#2</b>" : i == 2 ? "<#cd7f32><b>#3</b>" : "<dark_gray>#" + (i + 1);
            double wr = r.games() == 0 ? 0 : 100.0 * r.wins() / r.games();
            set(SLOTS[i], new ItemBuilder(Material.PLAYER_HEAD).skull(r.uuid(), r.name())
                    .name(place + " <white>" + Text.escape(r.name()))
                    .lore("<dark_gray>↪</dark_gray> <gray>Elo: <white>" + r.elo() + " <dark_gray>·</dark_gray> " + tier(r.elo()),
                            "<dark_gray>↪</dark_gray> <gray>Siege: <green>" + r.wins() + " <dark_gray>(" + Math.round(wr) + " %)").build());
        }
        if (rows.isEmpty()) set(22, new ItemBuilder(Material.BARRIER).name("<red>Noch keine Elo-Wertungen")
                .lore("<dark_gray>↪</dark_gray> <gray>Spiele eine Runde, um gewertet zu werden").build());
        set(49, new ItemBuilder(Material.NETHER_STAR).name("<dark_gray>»</dark_gray> <white>Dein Platz")
                .lore(ownElo <= 0 ? "<dark_gray>↪</dark_gray> <gray>Noch nicht gewertet"
                        : "<dark_gray>↪</dark_gray> <gray>Platz <white>#" + ownRank + " <dark_gray>·</dark_gray> <gray>Elo <white>" + ownElo + " " + tier(ownElo)).build());
        set(45, new ItemBuilder(Material.ARROW).name("<gray>« Zurück zum Profil").build(), e -> {
            click();
            ProfileMenu.open(plugin, player);
        });
        fill(Material.GRAY_STAINED_GLASS_PANE);
    }
}
