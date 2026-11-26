package de.crafterteam.lobbysystem.api;

import java.util.UUID;

/**
 * Für Spielmodus-Plugins (BedWars, SkyWars …) auf Servern mit LobbySystem im Modus "game".
 * Alles landet in derselben MySQL-Datenbank → Navigator, Lobby-Switcher, Bestenlisten & Profil in der Lobby.
 *
 * <pre>
 * GameAPI game = LobbySystemAPI.game();
 * game.setGame("bedwars");                 // einmal beim Start
 * game.setState(GameAPI.WAITING);          // Wartelobby → Navigator zeigt "Wartet auf Spieler"
 * game.setState(GameAPI.INGAME);           // Runde läuft
 * game.addStats(uuid, name, 1, 5, 2, 1);   // nach der Runde: +1 Sieg, +5 Kills, +2 Tode, +1 Runde
 * game.setLevel(uuid, name, 12);
 * game.addXp(uuid, 25);                    // Lobby-XP (Level-System der Lobby)
 * </pre>
 * Bestenliste in der Lobby danach z. B.: /setleaderboard bedwars_wins
 */
public interface GameAPI {

    String WAITING = "WAITING";
    String INGAME = "INGAME";

    /** Spielmodus-ID dieses Servers (Kleinbuchstaben, z. B. "bedwars"). */
    void setGame(String gameId);

    String game();

    /** Status für Navigator/Lobby-Switcher: WAITING oder INGAME. */
    void setState(String state);

    /** Stats addieren (Differenzen, nicht Gesamtwerte). */
    void addStats(UUID uuid, String name, int wins, int kills, int deaths, int games);

    void setLevel(UUID uuid, String name, int level);

    /** Lobby-XP geben (zählt fürs Lobby-Level). */
    void addXp(UUID uuid, long xp);
}
