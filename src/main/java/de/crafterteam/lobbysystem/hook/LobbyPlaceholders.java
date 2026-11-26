package de.crafterteam.lobbysystem.hook;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI: %lobbysystem_coins%, %lobbysystem_coins_raw%, %lobbysystem_lose%, %lobbysystem_playtime%,
 * %lobbysystem_rank%, %lobbysystem_clan_tag%, %lobbysystem_clan_name%, %lobbysystem_friends_online%,
 * %lobbysystem_friends_total%, %lobbysystem_streak%
 */
public final class LobbyPlaceholders extends PlaceholderExpansion {

    private final LobbySystem plugin;

    public LobbyPlaceholders(LobbySystem plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "lobbysystem";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SujucDerBreite";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offline, @NotNull String params) {
        if (offline == null) return "";
        Player player = offline.getPlayer();
        PlayerData d = plugin.data().cached(offline.getUniqueId()).orElse(null);
        var clan = plugin.clans().cached(offline.getUniqueId()).orElse(null);
        int[] friends = plugin.friends().count(offline.getUniqueId());
        String lower = params.toLowerCase();
        // %lobbysystem_players_MissileWars*% / %lobbysystem_state_MissileWars-1%
        if (lower.startsWith("players_")) return Integer.toString(plugin.servers().players(params.substring(8)));
        if (lower.startsWith("state_")) return de.crafterteam.lobbysystem.util.Text.plain(
                de.crafterteam.lobbysystem.util.Text.parse(plugin.servers().stateText(params.substring(6))));
        return switch (lower) {
            case "coins" -> d == null ? "0" : Text.formatNumber(d.coins());
            case "coins_raw" -> d == null ? "0" : Long.toString(d.coins());
            case "lose" -> d == null ? "0" : Integer.toString(d.lose());
            case "playtime" -> d == null ? "-" : Text.formatDuration(d.playtime());
            case "onlinetime" -> {
                long s = plugin.proxy().onlineTimeSeconds(offline.getUniqueId());
                yield s >= 0 ? Text.formatDuration(s) : d == null ? "-" : Text.formatDuration(d.playtime());
            }
            case "streak" -> d == null ? "0" : Integer.toString(d.streak());
            case "level" -> d == null ? "1" : Integer.toString(plugin.levels().level(d.xp()));
            case "xp" -> d == null ? "0" : Long.toString(d.xp());
            case "cookies" -> d == null ? "0" : de.crafterteam.lobbysystem.progression.CookieManager.format(d.cookies());
            case "rank" -> player == null ? "" : plugin.sidebar().rank(player).name;
            case "clan_tag" -> clan == null ? "" : clan.tag();
            case "clan_name" -> clan == null ? "" : clan.name();
            case "clan_members" -> clan == null ? "0" : Integer.toString(clan.members().size());
            case "friends_online" -> Integer.toString(friends[0]);
            case "friends_total" -> Integer.toString(friends[1]);
            default -> null;
        };
    }
}
