package de.crafterteam.lobbysystem.api;

import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/** Statischer Einstieg in die LobbySystem-API. */
public final class LobbySystemAPI {

    private static CoinAPI coins;
    private static SocialAPI social;
    private static GameAPI game;

    private LobbySystemAPI() {
    }

    public static CoinAPI coins() {
        if (coins == null) throw new IllegalStateException("LobbySystem ist nicht geladen");
        return coins;
    }

    public static SocialAPI social() {
        if (social == null) throw new IllegalStateException("LobbySystem ist nicht geladen");
        return social;
    }

    /** Spielmodus-Schnittstelle (Status, Stats, XP) – siehe {@link GameAPI}. */
    public static GameAPI game() {
        if (game == null) throw new IllegalStateException("LobbySystem ist nicht geladen");
        return game;
    }

    /** Intern – wird vom Plugin gesetzt. */
    public static void init(CoinAPI coinApi, SocialAPI socialApi, GameAPI gameApi) {
        coins = coinApi;
        social = socialApi;
        game = gameApi;
    }

    /** Clan-/Freundes-Infos für andere Plugins (z. B. Tab- oder Chat-Plugins). */
    public interface SocialAPI {
        /** Clan-Tag eines Online-Spielers (aus dem Cache) */
        Optional<String> clanTag(UUID uuid);

        Optional<String> clanName(UUID uuid);

        /** [online, gesamt] */
        int[] friendCount(UUID uuid);

        String rankName(Player player);

        /** Lobby-Level eines Online-Spielers */
        int level(Player player);
    }
}
