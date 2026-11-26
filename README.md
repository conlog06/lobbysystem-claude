# LobbySystem – Paper 1.21.10 bis 26.3

Modernes LobbySystem mit Navigator, Cosmetics, Daily-Reward-Hexe, Einstellungs-Dorfbewohner,
Lotterie, Scoreboard, Tablist, Bossbar, Actionbar und Lizenzsystem.

**Download:** [Releases → latest](https://github.com/conlog06/lobbysystem-claude/releases/tag/latest) (wird bei jedem Push automatisch gebaut)

## Was ist neu gegenüber der 1.16.5 – 1.18.1 Version?

| Bereich | Alt | Neu (1.21.10 – 26.3) |
|---|---|---|
| Schrift / Farben | `§`-Codes | **MiniMessage**: Gradients, Hex-Farben, Hover/Klick, `ꜱᴍᴀʟʟ ᴄᴀᴘꜱ`-Schrift (alte `§`/`&`-Codes funktionieren weiterhin) |
| Prefix | `§7» §2…` | `<gradient:#00d2ff:#3a7bd5><b>ʟᴏʙʙʏ</b></gradient> »` – komplett in der config.json |
| Scoreboard | rote Zahlen rechts | **ohne Zahlen** (NumberFormat.blank) |
| Hologramme | Armorstands | **TextDisplay** mit Hintergrund & Billboard |
| Ballone | Armorstand + Leine | **BlockDisplay** mit flüssiger Interpolation |
| Item-Glanz | Fake-Verzauberung | Enchantment-Glint-Override |
| Gadgets | – | u. a. **Windstoß** (Wind Charge, 1.21) |
| Doppelsprung | Wolke | Breeze-Sound + Gust-Partikel |
| Speicher | nur MySQL | MySQL **oder** lokal SQLite; Coins werden als Differenz gespeichert (mehrere Lobbys gleichzeitig sicher) |

## Neu in 2.1

- **Freunde** (`/friend`, `/f`): Anfragen mit Klick-Buttons, Online-Status über alle Lobbys, Nachspringen, Nachrichten, Join/Quit-Benachrichtigung, Limit (Premium mehr)
- **Party** (`/party`, `/p`): Einladen, Leader, Kick, Party-Chat – Mitglieder **folgen dem Leader** automatisch auf andere Server
- **Clans** (`/clan`): Gründen gegen Coins, Tag `[#TAG]` in Tab, über dem Kopf & im Scoreboard, Rollen (Leader/Moderator/Mitglied), Clan-Chat
- **Social-Menü** (Hotbar-Kopf): Freunde, Anfragen, Party, Clan & Einstellungen per Klick
- **CoinAPI** für eigene Plugins (siehe unten) + `CoinsChangeEvent`
- **Ressourcenpaket** mit eigenen Icons (Coin, Uhr, Clan, Freunde …) – wird vom Plugin **selbst gehostet**, kein externer Webspace nötig
- **Lobby-Switcher** (`/lobbyswitcher`) mit Live-Spielerzahlen aller Lobbys
- **Jump-Pads**, **Login-Streak** bei der täglichen Belohnung, **Auto-Broadcast**, Speed in der Lobby
- **PlaceholderAPI**: `%lobbysystem_coins%`, `_clan_tag`, `_friends_online`, `_rank`, `_streak` …

> Freunde/Party/Clan laufen über MySQL – alle Lobbys müssen dieselbe Datenbank nutzen und in `proxyServerName`
> ihren Namen aus der BungeeCord/Velocity-Config eintragen (z. B. `Lobby-1`). Nachrichten an Spieler auf anderen
> Servern gehen über den Proxy (BungeeCord-Channel, funktioniert auch mit Velocity).

## Neu in 2.6 – BungeeSystem-Integration

Mit dem [BungeeSystem](https://github.com/conlog06/bungeesystem-crafterteam) auf dem Proxy (gleiche MySQL-Datenbank) wird es automatisch erkannt
(`"bungeeSystem": { "mode": "auto" }` – `on`/`off` erzwingt es):

- **Freunde, Party & Clans laufen auf dem Proxy** – `/friend`, `/party`, `/clan` funktionieren auf jedem Server; das Social-Menü
  in der Lobby bleibt und schickt die Klicks an den Proxy (gleiche Tabellen, keine Daten gehen verloren)
- **Party folgt dem Leader** auf jeden Server (nicht nur über den Navigator), Online-Meldungen der Freunde netzwerkweit
- **Clan gründen** bucht die Coins hier ab – das Guthaben im Scoreboard stimmt sofort
- **Chat**: Anti-Spam & Wortfilter macht der Proxy (keine doppelten Meldungen mehr)
- **Netzwerk-Onlinezeit** im Scoreboard: `{onlinetime}` · PlaceholderAPI `%lobbysystem_onlinetime%`
  (ohne BungeeSystem = Lobby-Spielzeit)
- **Report, Ban, Mute** kommen komplett vom Proxy; GUIs zeigt das BungeeSystem-Addon
- `/coins`, `/advert`, Bug-Belohnungen vom Proxy buchen über den Coin-Cache der Lobby; `/hub` bzw. `/l` in der Lobby = Spawn

## Neu in 2.5

**Aufgeräumt:** Die `config.json` ist jetzt in 10 Bereiche sortiert (Allgemein · Datenbank & Rechte · Anzeigen ·
Hotbar & Navigator · Lobby · Belohnungen & Progression · Cosmetics · Freunde/Party/Clan · Integrationen · Nachrichten).
Bestehende Configs werden beim Start automatisch umsortiert – deine Werte bleiben erhalten.

**Lobby-Level:** XP aus Spielzeit (2/min), täglicher Belohnung, Cookie Clicker und Spielmodi (API).
Level-Up gibt Coins (Level × 50), Titel & Effekte, Ansage alle 10 Level. Level + Fortschritt in der XP-Leiste.
`/level [Spieler]`, Platzhalter `{level}`, `{xp}`, `{xp_needed}`, `{xp_bar}`, Bestenliste `/setleaderboard level`.

**Cookie Clicker** (`/cookie`, oder im Profil): großer Keks mit kritischen Klicks, 8 Upgrades (Cursor, Oma, Farm, Mine,
Fabrik, Bank, Tempel, Goldene Backform), passive Produktion jede Sekunde, **goldene Kekse**, Kekse → XP,
Tausch in Coins mit Tageslimit, Bestenliste `/setleaderboard cookies`. Anti-Autoklicker (max. 20 Klicks/s).

**Integrationen**
| | |
|---|---|
| **Vault** | Lobby-Coins als Economy → Shop-, Jobs-, Crate-Plugins nutzen sie direkt (`integrations.vault`) |
| **LuckPerms** | Ränge direkt aus LuckPerms: Gruppe, Prefix, Gewichtung, Badge automatisch (`integrations.luckPermsRanks`) |
| **Game-Modus** | `"mode": "game"` auf Spielservern (BedWars, SkyWars …): nur Brücke – Coins/Vault, `/friend` `/party` `/clan`, Online-Status, Stats-API. Keine Hotbar, kein Schutz, kein Scoreboard. |
| **GameAPI** | `LobbySystemAPI.game()` – Status (Wartet/Läuft) für den Navigator, Stats, Level, Lobby-XP |
| **Bestenlisten** | jeder Spielmodus: `/setleaderboard <spiel>_<wins\|kills\|deaths\|games\|level>` |
| **PlaceholderAPI** | neu: `%lobbysystem_level%`, `_xp`, `_cookies` |

```java
GameAPI game = LobbySystemAPI.game();
game.setGame("bedwars");
game.setState(GameAPI.INGAME);
game.addStats(uuid, name, 1, 5, 2, 1);   // +Sieg, +Kills, +Tode, +Runde
game.addXp(uuid, 25);                    // Lobby-XP
LobbySystemAPI.coins().addCoins(uuid, 50, "bedwars_win");
```

## Neu in 2.4 – MissileWars-Integration

Zusammen mit [MissileWars 2.9+](https://github.com/conlog06/missile-wars) (gleiche MySQL-Datenbank, dort `lobbysystem.enabled: true`):

- **Gemeinsame Coins** – in MissileWars verdient/ausgegeben = Lobby-Guthaben (Cosmetics, Clans, /pay …)
- **Navigator mit Live-Daten** – `connect-best:MissileWars` schickt in die beste Runde (wartende zuerst),
  Platzhalter `{players:MissileWars*}`, `{max:…}`, `{state:…}` (aktualisiert sich alle 2 s, solange offen)
- **Freunde** sehen „MissileWars-1" als Standort und können per `/friend jump` nachkommen; Party folgt dem Leader
- **Bestenlisten:** `/setleaderboard missilewars_wins|missilewars_kills|missilewars_level`
- **Profil** zeigt MissileWars-Stats (Level, Siege, Winrate, K/D)
- PlaceholderAPI: `%lobbysystem_players_MissileWars*%`, `%lobbysystem_state_MissileWars*%`

Navigator-Eintrag für bestehende Configs (in `navigatorItems` einfügen):
```
"29;TNT;connect-best:MissileWars;<red>ᴍɪꜱꜱɪʟᴇ ᴡᴀʀꜱ;<gray>Spieler: <white>{players:MissileWars*}\n{state:MissileWars*}"
```

## Neu in 2.2

- **Performance:** Scoreboard/Tab senden nur noch Änderungen, Platzhalter 1×/Sekunde pro Spieler (statt 3×),
  gebündelte Team-Updates, Text-Parse-Cache, Bewegungs-Events nur bei Blockwechsel, Partikel-Zuschauer gecacht
- **Bestenlisten-Hologramme:** `/setleaderboard <coins|playtime|streak|friends>` – Top 10, aktualisiert alle 60 s
- **/pay <Spieler> <Coins>** – Coins überweisen (auch offline / anderer Server)
- **Hinsetzen** auf Treppen & Stufen per Rechtsklick
- **Chat:** Anti-Spam (Cooldown, Wiederholung), Wortfilter, **@Erwähnungen** mit Ping-Sound
- **Fliegen** als Einstellung für Spieler mit `lobbysystem.fly`
- **Erstjoin-Bonus** (500 Coins) + Willkommensnachricht mit Spielernummer
- **Animierter Scoreboard-Titel** (`scoreboard.titleFrames`), neuer Platzhalter `{ping}`

## Commands

| Befehl | Beschreibung |
|---|---|
| `/build [Spieler]` | De-/aktiviert den Baumodus |
| `/lobbycoins add\|remove\|set\|get <Spieler> [Coins]` | Coins vergeben, entfernen, setzen oder anzeigen (auch offline) |
| `/setreward [remove]` | Setzt die Daily-Reward Hexe oder entfernt sie |
| `/setsettings [remove]` | Setzt den Einstellungs-Dorfbewohner oder entfernt ihn |
| `/setspawn` | Setzt den Spawn-Punkt |
| `/setwarp <Name>` · `/setwarp remove <Name>` · `/setwarp list` | Warp-Punkte für den Navigator |
| `/warp <Name>` · `/spawn` | Teleport zu Warp / Spawn |
| `/tpcoord <x> <y> <z> [Welt] [Yaw] [Pitch]` | Teleport zu Koordinaten (`~` relativ möglich) |
| `/friend add\|remove\|accept\|deny\|list\|requests\|jump\|msg\|toggle` | Freundesystem (ohne Argument: Menü) |
| `/party invite\|accept\|deny\|leave\|kick\|promote\|disband\|list\|warp\|chat` | Party-System |
| `/clan create <Name> <Tag>\|invite\|accept\|deny\|leave\|kick\|promote\|demote\|disband\|info\|chat` | Clan-System |
| `/lobbyswitcher` | Lobby wechseln |
| `/pay <Spieler> <Coins>` | Coins überweisen |
| `/setleaderboard [remove] <coins\|playtime\|streak\|friends>` | Bestenliste setzen/entfernen |
| `/lobbysystem reload\|info` | Konfiguration neu laden / Infos |
| `/license create\|list\|verify` | **Nur SujucDerBreite** – Lizenzen verwalten |

## Permissions

Die Permissions sind frei in der `config.json` einstellbar. Die gesamte Permission setzt sich aus
`prefix` + `.` + jeweiligem Suffix zusammen:

```json
"permissions": {
  "prefix": "lobbysystem",
  "suffix_build": "build",
  "suffix_coins": "coins",
  "suffix_bypass": "bypass"
}
```

→ Baumodus = `lobbysystem.build`. Ein **leerer** Suffix bedeutet: jeder darf es (Standard beim Doppelsprung).
Ränge werden in der `ranks.json` über die dort eingetragene Permission vergeben (z. B. `lobbysystem.rank.admin`).

## Installation (Paper / Spigot-Fork)

1. JAR aus den Releases herunterladen.
2. In den `plugins`-Ordner der Lobby-Server legen.
3. Server starten → `plugins/LobbySystem/config.json` wird erstellt.
   - `license` eintragen (Lizenz gibt es nur von **SujucDerBreite**).
   - MySQL-Daten unter `mysql` eintragen und `enabled: true` setzen (gleiche DB wie die anderen Server). Ohne MySQL wird lokal SQLite genutzt.
4. Server neu starten.
5. Optional: Ränge in `ranks.json` anpassen, Nachrichten in der `config.json` ändern.

### CloudNet (v3/v4)

1. JAR nach `local/templates/Lobby/default/plugins/` (v4) bzw. `local/templates/Lobby/plugins/` (v3).
2. Gruppe starten → Config in `temp/Lobby-X/plugins/LobbySystem/` bearbeiten (Lizenz + MySQL).
3. Geänderte Config in den Template-Ordner kopieren und Gruppe neu starten.

## Navigator konfigurieren

In der `config.json` unter `navigatorItems`, eine Zeile pro Item:

```
Slot;MATERIAL;Ziel;Name;Lore
```

- **Ziel:** `warp:<Name>` · `connect:<Server>` (BungeeCord/Velocity) · `spawn` · `command:<Befehl>`
- Mehrere Lore-Zeilen mit `\n` trennen.

Beispiel: `"12;IRON_AXE;connect:CityBuild-1;<dark_gray>»</dark_gray> <dark_green>ᴄɪᴛʏʙᴜɪʟᴅ;<gray>Klicke zum Joinen"`
(alte Schreibweise `§7» §2CityBuild` funktioniert auch).

## Ressourcenpaket

```json
"resourcepack": {
  "enabled": true,
  "required": false,
  "url": "",
  "selfHost": { "port": 8165, "publicHost": "play.deinserver.de" },
  "icons": true
}
```

- `url` leer → das Plugin hostet `plugins/LobbySystem/resourcepack/LobbyPack.zip` selbst (Port muss offen sein).
- Eigenes Pack? ZIP ersetzen + `/lobbysystem reload` – SHA-1 wird automatisch berechnet.
- Icons in allen Texten: `{i_coin} {i_clock} {i_rank} {i_clan} {i_friends} {i_party} {i_heart} {i_ticket} {i_online} {i_offline} {i_gem}`
  (ohne Pack automatisch Unicode-Ersatz). Quellen & Generator: `resourcepack/`, `tools/generate_pack.py`.

### Rang-Badges

Mit aktivem Ressourcenpaket (`resourcepack.enabled` + `icons`) werden die Text-Prefixe in **Tab, über dem Kopf
und im Chat** automatisch durch Grafik-Badges ersetzt:

`owner, leitung, admin, dev, mod, sup, builder, content, youtuber, premiumplus, premium, vip, spieler, team`

Die Zuordnung passiert automatisch über den Rangnamen in der `ranks.json` (z. B. „Moderator“ → `mod`).
Manuell: `"badge": "admin"` beim Rang eintragen, `"badge": "none"` = kein Badge. Ohne Pack wird der normale
`prefix` genutzt. Platzhalter im Scoreboard: `{rank_badge}`.
Eigene Badges: In `tools/generate_pack.py` bei `BADGES` eintragen und Pack neu bauen.

## CoinAPI (für Entwickler)

```java
CoinAPI coins = LobbySystemAPI.coins();                 // oder Bukkit.getServicesManager().load(CoinAPI.class)
coins.addCoins(player.getUniqueId(), 100, "bedwars_win");
coins.removeCoins(uuid, 500, "shop").thenAccept(ok -> { /* true = genug Coins */ });
coins.getCoins(uuid).thenAccept(amount -> ...);         // funktioniert auch offline
coins.transfer(from, to, 50, "pay");

@EventHandler
public void onCoins(CoinsChangeEvent e) { e.getUniqueId(); e.getDifference(); e.getReason(); }

LobbySystemAPI.social().clanTag(uuid);                  // Optional<String>
```

In `plugin.yml` deines Plugins: `depend: [LobbySystem]`, JAR als `provided`-Dependency einbinden.

## Lizenzsystem

- Lizenzschlüssel sind mit **Ed25519** signiert. Im Plugin steckt nur der öffentliche Schlüssel –
  **nur wer die `license-private.key` besitzt (SujucDerBreite), kann Lizenzen erstellen.**
- Owner-Server: `license-private.key` nach `plugins/LobbySystem/` legen → Plugin läuft ohne Lizenz
  und `/license create <Inhaber> [Tage]` ist für den Spieler `SujucDerBreite` freigeschaltet.
- Offline ohne Server: `java -jar LobbySystem-2.0.0.jar create "<Inhaber>" [Tage] license-private.key`
- Die `license-private.key` **niemals** hochladen oder weitergeben (ist per `.gitignore` ausgeschlossen).

## Build

```
mvn package
```

Java 21, kompiliert gegen `paper-api 1.21.10` – läuft auf allen Paper-Versionen bis 26.3.
