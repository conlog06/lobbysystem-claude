package de.crafterteam.lobbysystem.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Struktur der config.json. Alle Felder haben Standardwerte – fehlende Einträge
 * werden beim Laden automatisch ergänzt, eigene Werte bleiben erhalten.
 */
public class PluginConfig {

    // ════════════════════════ 1. ALLGEMEIN ════════════════════════
    public String license = "HIER-DEINE-LIZENZ-EINTRAGEN";

    /** lobby = komplettes LobbySystem · game = nur Brücke auf Spielservern (Coins, Freunde/Party/Clan, Stats-API, Online-Status) */
    public String mode = "lobby";

    public String prefix = "<gradient:#00d2ff:#3a7bd5><b>ʟᴏʙʙʏ</b></gradient> <dark_gray>»</dark_gray> <gray>";
    public String serverName = "ᴄʀᴀꜰᴛᴇʀᴛᴇᴀᴍ.ᴅᴇ";

    /** Name DIESES Servers im BungeeCord/Velocity (für Freunde-Status, Lobby-Switcher, Party-Nachziehen). */
    public String proxyServerName = "Lobby-1";
    /** Server, die im Lobby-Switcher erscheinen, beginnen mit diesem Namen. */
    public String lobbyPrefix = "Lobby";

    // ════════════════════════ 2. DATENBANK & RECHTE ════════════════════════
    public MySql mysql = new MySql();

    /** Gesamt-Permission = prefix + "." + suffix_xyz (leerer Suffix = jeder darf). */
    public Map<String, String> permissions = defaultPermissions();

    // ════════════════════════ 3. ANZEIGEN ════════════════════════
    public Scoreboard scoreboard = new Scoreboard();
    public Tablist tablist = new Tablist();
    public BossBarSettings bossbar = new BossBarSettings();
    public ActionBar actionbar = new ActionBar();
    public Broadcast broadcast = new Broadcast();
    public Chat chat = new Chat();
    public ResourcePack resourcepack = new ResourcePack();

    // ════════════════════════ 4. HOTBAR & NAVIGATOR ════════════════════════
    public Map<String, HotbarItem> hotbar = defaultHotbar();
    public String navigatorTitle = "<dark_gray>↪</dark_gray> <gradient:#00d2ff:#3a7bd5>ɴᴀᴠɪɢᴀᴛᴏʀ</gradient>";
    public int navigatorRows = 6;
    /** Slot;MATERIAL;Ziel;Name;Lore  — Ziel: warp:Name | connect:Server | connect-best:MissileWars | spawn | command:befehl */
    public List<String> navigatorItems = defaultNavigator();

    // ════════════════════════ 5. LOBBY ════════════════════════
    public Join join = new Join();
    public Protection protection = new Protection();
    public DoubleJump doubleJump = new DoubleJump();
    public JumpPads jumpPads = new JumpPads();
    public Speed speed = new Speed();
    public Sit sit = new Sit();

    // ════════════════════════ 6. BELOHNUNGEN & PROGRESSION ════════════════════════
    public DailyReward dailyReward = new DailyReward();
    public Lottery lottery = new Lottery();
    public Levels levels = new Levels();
    public CookieClicker cookieClicker = new CookieClicker();
    public Leaderboards leaderboards = new Leaderboards();

    // ════════════════════════ 7. COSMETICS ════════════════════════
    public Cosmetics cosmetics = new Cosmetics();

    // ════════════════════════ 8. FREUNDE · PARTY · CLAN ════════════════════════
    public Social social = new Social();

    // ════════════════════════ 9. INTEGRATIONEN ════════════════════════
    public Integrations integrations = new Integrations();

    // ════════════════════════ 10. NACHRICHTEN ════════════════════════
    public Map<String, String> messages = defaultMessages();

    /** Intern: bereits durchgeführte Config-Updates (nicht ändern) */
    public List<String> migrations = new ArrayList<>();

    // ------------------------------------------------------------------ Sektionen

    public static class MySql {
        /** false = lokale SQLite-Datei (plugins/LobbySystem/data.db) */
        public boolean enabled = false;
        public String host = "localhost";
        public int port = 3306;
        public String database = "lobby";
        public String user = "root";
        public String password = "";
        public String properties = "useSSL=false&autoReconnect=true&characterEncoding=utf8&serverTimezone=Europe/Berlin";
    }

    public static class HotbarItem {
        public boolean enabled = true;
        public int slot;
        public String material;
        public String name;
        public List<String> lore = new ArrayList<>();

        public HotbarItem() {
        }

        public HotbarItem(int slot, String material, String name, String... lore) {
            this.slot = slot;
            this.material = material;
            this.name = name;
            this.lore = new ArrayList<>(List.of(lore));
        }
    }

    public static class Scoreboard {
        public boolean enabled = true;
        public String title = "<gradient:#ffb347:#ffcc33><b>ᴄʀᴀꜰᴛᴇʀᴛᴇᴀᴍ.ᴅᴇ</b></gradient>";
        /** Animierter Titel: Frames wechseln jede Sekunde (leer = statischer Titel) */
        public List<String> titleFrames = new ArrayList<>(List.of(
                "<gradient:#ffb347:#ffcc33><b>ᴄʀᴀꜰᴛᴇʀᴛᴇᴀᴍ.ᴅᴇ</b></gradient>",
                "<gradient:#ffcc33:#ffb347><b>ᴄʀᴀꜰᴛᴇʀᴛᴇᴀᴍ.ᴅᴇ</b></gradient>",
                "<gradient:#ffffff:#ffcc33><b>ᴄʀᴀꜰᴛᴇʀᴛᴇᴀᴍ.ᴅᴇ</b></gradient>",
                "<gradient:#ffcc33:#ffffff><b>ᴄʀᴀꜰᴛᴇʀᴛᴇᴀᴍ.ᴅᴇ</b></gradient>"));
        public List<String> lines = new ArrayList<>(List.of(
                "<dark_gray>●</dark_gray> {i_coin} <gray>ᴄᴏɪɴꜱ",
                "<dark_gray>↪</dark_gray> <green>{coins}",
                "",
                "<dark_gray>●</dark_gray> {i_clock} <gray>ꜱᴘɪᴇʟᴢᴇɪᴛ",
                "<dark_gray>↪</dark_gray> <yellow>{playtime}",
                "",
                "<dark_gray>●</dark_gray> {i_rank} <gray>ʀᴀɴɢ",
                "<dark_gray>↪</dark_gray> {rank}",
                "",
                "<dark_gray>●</dark_gray> {i_clan} <gray>ᴄʟᴀɴ {clan_tag}",
                "<dark_gray>↪</dark_gray> {clan_members}",
                "",
                "<dark_gray>●</dark_gray> {i_friends} <gray>ꜰʀᴇᴜɴᴅᴇ",
                "<dark_gray>↪</dark_gray> <green>{friends_online}</green> <gray>online",
                "<dark_gray><st>                    </st>"
        ));
    }

    public static class Tablist {
        public boolean enabled = true;
        public String header = "\n<gradient:#00d2ff:#3a7bd5><b>ᴄʀᴀꜰᴛᴇʀᴛᴇᴀᴍ.ᴅᴇ</b></gradient>\n<gray>Willkommen, <white>{name}</white>!\n";
        public String footer = "\n<gray>Online: <green>{online}</green><dark_gray>/</dark_gray><green>{max}</green> <dark_gray>|</dark_gray> <gray>Server: <aqua>{server}</aqua>\n<dark_gray>» <gray>{date} <dark_gray>▌</dark_gray> <gray>{time} <dark_gray>«\n";
    }

    public static class BossBarSettings {
        public boolean enabled = true;
        public int intervalSeconds = 10;
        /** PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE */
        public String color = "GREEN";
        /** PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12, NOTCHED_20 */
        public String overlay = "PROGRESS";
        public List<String> messages = new ArrayList<>(List.of(
                "<aqua><b>ɴᴇᴜ</b></aqua><dark_gray>:</dark_gray> <green>LobbySystem</green> <gradient:#ff5f6d:#ffc371>1.21.10 – 26.3</gradient>",
                "<gray>Besuche unseren Shop <dark_gray>»</dark_gray> <gold>shop.crafterteam.de",
                "<gray>Sammle jeden Tag deine <light_purple>Tägliche Belohnung</light_purple> <gray>bei der Hexe!",
                "<gray>Hol dir <yellow>Cosmetics</yellow> <gray>über die <light_purple>Extras</light_purple> <gray>in deiner Hotbar"
        ));
    }

    public static class ActionBar {
        public boolean enabled = true;
        public String format = "<dark_gray>»</dark_gray> <aqua>{date}</aqua> <dark_gray>▌</dark_gray> <yellow>{time}</yellow> <dark_gray>«</dark_gray>";
        public String dateFormat = "dd.MM.yyyy";
        public String timeFormat = "HH:mm:ss";
        public String timezone = "Europe/Berlin";
    }

    public static class Chat {
        public boolean enabled = true;
        public String format = "{prefix}<white>{name}</white> <dark_gray>»</dark_gray> <gray>{message}";
        /** Sekunden zwischen zwei Nachrichten (Bypass-Permission: chat_bypass) */
        public double cooldownSeconds = 2.0;
        /** Gleiche Nachricht zweimal hintereinander blockieren */
        public boolean blockRepeat = true;
        /** @Name / Name im Chat hervorheben + Ping-Sound */
        public boolean mentions = true;
        public String mentionFormat = "<yellow>@{name}</yellow>";
        /** Wörter, die durch *** ersetzt werden (Groß-/Kleinschreibung egal) */
        public List<String> filter = new ArrayList<>();
    }

    public static class Join {
        public boolean teleportToSpawn = true;
        public String joinMessage = "";
        public String quitMessage = "";
        public String title = "<gradient:#00d2ff:#3a7bd5><b>ᴡɪʟʟᴋᴏᴍᴍᴇɴ</b></gradient>";
        public String subtitle = "<gray>auf <white>{server}";
        public boolean firework = false;
        /** Startguthaben + Willkommensnachricht für neue Spieler */
        public long firstJoinCoins = 500;
        public String firstJoinBroadcast = "<gray>Willkommen <white>{name}</white> <gray>auf dem Server! <dark_gray>(</dark_gray><gold>#{number}</gold><dark_gray>)";
    }

    public static class DailyReward {
        public int cooldownHours = 24;
        public int normalCoins = 200;
        public int normalLose = 1;
        public int premiumCoins = 400;
        public int premiumLose = 2;
        /** Bonus-Coins pro Tag in Folge (Streak), maximal streakMax Tage */
        public int streakBonus = 25;
        public int streakMax = 7;
        public String hologram = "<gradient:#b06ab3:#4568dc><b>ᴛäɢʟɪᴄʜᴇ ʙᴇʟᴏʜɴᴜɴɢ</b></gradient>\n<gray>Rechtsklick zum Öffnen";
    }

    public static class Lottery {
        /** Gewinne eines Loses: Coins + Gewichtung (höher = häufiger) */
        public List<LotteryReward> rewards = new ArrayList<>(List.of(
                new LotteryReward(25, 35), new LotteryReward(50, 30), new LotteryReward(100, 18),
                new LotteryReward(250, 10), new LotteryReward(500, 5), new LotteryReward(2500, 2)));
    }

    public static class Social {
        public boolean friends = true;
        public boolean party = true;
        public boolean clans = true;
        public int friendsMax = 50;
        public int friendsMaxPremium = 150;
        public int partyMax = 8;
        public int clanMax = 30;
        public long clanCreateCost = 5000;
        public int clanTagMin = 2;
        public int clanTagMax = 5;
        public int clanNameMax = 16;
        public int inviteSeconds = 300;
        /** Party-Mitglieder automatisch mitnehmen, wenn der Leader über den Navigator den Server wechselt */
        public boolean partyFollow = true;
        public String friendChatFormat = "<dark_aqua>ꜰʀᴇᴜɴᴅᴇ</dark_aqua> <dark_gray>»</dark_gray> <white>{name}</white><dark_gray>:</dark_gray> <gray>{message}";
        public String partyChatFormat = "<light_purple>ᴘᴀʀᴛʏ</light_purple> <dark_gray>»</dark_gray> <white>{name}</white><dark_gray>:</dark_gray> <gray>{message}";
        public String clanChatFormat = "<gold>ᴄʟᴀɴ</gold> <dark_gray>[</dark_gray><yellow>{tag}</yellow><dark_gray>]</dark_gray> <white>{name}</white><dark_gray>:</dark_gray> <gray>{message}";
        /** Clan-Tag hinter dem Namen in Tab & über dem Kopf */
        public String clanSuffix = " <dark_gray>[</dark_gray><yellow>#{tag}</yellow><dark_gray>]</dark_gray>";
    }

    public static class ResourcePack {
        public boolean enabled = false;
        /** Spieler müssen das Pack annehmen, sonst werden sie gekickt */
        public boolean required = false;
        public String prompt = "<gradient:#00d2ff:#3a7bd5>Lobby-Ressourcenpaket</gradient>\n<gray>Für Icons & Sounds – bitte annehmen!";
        public String kickMessage = "<red>Bitte nimm das Ressourcenpaket an, um die Lobby zu betreten.";
        /** Eigenes Pack: externe URL. Leer = das mitgelieferte Pack wird vom Plugin selbst gehostet */
        public String url = "";
        /** SHA-1 der externen URL (leer = wird automatisch berechnet) */
        public String sha1 = "";
        public SelfHost selfHost = new SelfHost();
        /** Eigene Icons (Coin, Uhr, Clan …) im Scoreboard/Tab nutzen – nur sinnvoll mit Pack */
        public boolean icons = true;
    }

    public static class SelfHost {
        public int port = 8165;
        /** Öffentliche IP/Domain, über die Spieler den Server erreichen (z. B. play.crafterteam.de) */
        public String publicHost = "";
    }

    public static class JumpPads {
        public boolean enabled = true;
        /** Druckplatte, die als Jump-Pad dient */
        public String plate = "HEAVY_WEIGHTED_PRESSURE_PLATE";
        /** Optional: Block darunter (leer = egal) */
        public String blockBelow = "REDSTONE_BLOCK";
        public double forward = 2.6;
        public double upward = 1.1;
    }

    public static class Broadcast {
        public boolean enabled = true;
        public int intervalSeconds = 240;
        public List<String> messages = new ArrayList<>(List.of(
                "<gray>Füge Freunde mit <aqua>/friend add <Name></aqua> <gray>hinzu!",
                "<gray>Gründe deinen eigenen Clan mit <gold>/clan create <Name> <Tag></gold><gray>!",
                "<gray>Spiele mit Freunden zusammen: <light_purple>/party invite <Name>",
                "<gray>Hol dir jeden Tag deine Belohnung – mit <yellow>Streak-Bonus</yellow><gray>!"
        ));
    }

    public static class Leaderboards {
        public int updateSeconds = 60;
        public int size = 10;
        public String header = "<gradient:#f7971e:#ffd200><b>ᴛᴏᴘ {size}</b></gradient> <dark_gray>»</dark_gray> {title}";
        public String line = "<gray>#{place}</gray> <white>{name}</white> <dark_gray>»</dark_gray> <yellow>{value}";
        /** Farben für Platz 1–3 */
        public List<String> podium = new ArrayList<>(List.of("<gold>", "<white>", "<#cd7f32>"));
        public String empty = "<gray>Noch keine Einträge";
    }

    public static class Sit {
        /** Rechtsklick auf Treppen/Stufen zum Hinsetzen */
        public boolean enabled = true;
        public double yOffset = 0.3;
    }

    public static class Levels {
        public boolean enabled = true;
        /** XP für Level n → n+1 = base + (n-1) * step */
        public int base = 100;
        public int step = 50;
        public int xpPerMinute = 2;
        public int xpDailyReward = 50;
        /** Coins pro Level-Up = Level × coinsPerLevel */
        public int coinsPerLevel = 50;
        /** Level in der XP-Leiste anzeigen */
        public boolean expBar = true;
        /** Ab diesem Level wird ein Level-Up im ganzen Chat angesagt (alle X Level) */
        public int announceEvery = 10;
    }

    public static class CookieClicker {
        public boolean enabled = true;
        /** XP pro gebackenen Keksen (z. B. 10 XP je 1.000 Kekse) */
        public int cookiesPerXp = 100;
        /** Kritischer Klick: Chance in % und Multiplikator */
        public double critChance = 4.0;
        public int critMultiplier = 10;
        /** Goldener Keks erscheint im Menü mit dieser Chance pro Sekunde (%) */
        public double goldenChance = 1.5;
        /** Kekse in Coins tauschen: so viele Kekse = 1 Coin (0 = aus) */
        public long cookiesPerCoin = 1000;
        public long maxCoinsPerDay = 500;
    }

    public static class Integrations {
        /** Coins als Vault-Economy für andere Plugins (Shops, Jobs …) – benötigt Vault */
        public boolean vault = true;
        /** Ränge aus LuckPerms (Gruppe, Prefix, Gewichtung) statt ranks.json – benötigt LuckPerms */
        public boolean luckPermsRanks = false;
        /** Stats anderer Spielmodi im Profil: game-id → Anzeigename */
        public Map<String, String> games = new LinkedHashMap<>(Map.of(
                "missilewars", "<red>Missile Wars",
                "bedwars", "<red>BedWars",
                "skywars", "<aqua>SkyWars"));
    }

    public static class Speed {
        /** Leichter Speed-Effekt in der Lobby (0 = aus) */
        public int level = 1;
    }

    public static class LotteryReward {
        public int coins;
        public int weight;

        public LotteryReward() {
        }

        public LotteryReward(int coins, int weight) {
            this.coins = coins;
            this.weight = weight;
        }
    }

    public static class DoubleJump {
        public double forward = 1.15;
        public double upward = 0.95;
        public int cooldownTicks = 20;
    }

    public static class Protection {
        public boolean noDamage = true;
        public boolean noHunger = true;
        public boolean noBlockBreak = true;
        public boolean noBlockPlace = true;
        public boolean noItemDrop = true;
        public boolean noItemPickup = true;
        public boolean noWeather = true;
        public boolean noMobSpawning = true;
        public boolean lockInventory = true;
        public boolean alwaysDay = false;
        /** Unterhalb dieser Höhe wird man zum Spawn teleportiert. */
        public int voidY = -10;
        public String gameMode = "ADVENTURE";
    }

    public static class Cosmetics {
        public String title = "<dark_gray>»</dark_gray> <yellow>ᴄᴏꜱᴍᴇᴛɪᴄꜱ</yellow> <dark_gray>|</dark_gray> {category}";
        /** id;MATERIAL;Name;Preis;Daten  (Daten: MHF-Name, texture:Base64 oder leer = Material) */
        public List<String> heads = new ArrayList<>(List.of(
                "villager;PLAYER_HEAD;<gold>Dorfbewohner;250;MHF_Villager",
                "cake;PLAYER_HEAD;<gold>Kuchen;500;MHF_Cake",
                "chest;PLAYER_HEAD;<gold>Truhe;300;MHF_Chest",
                "slime;PLAYER_HEAD;<green>Schleim;400;MHF_Slime",
                "melon;PLAYER_HEAD;<green>Melone;350;MHF_Melon",
                "pumpkin;PLAYER_HEAD;<gold>Kürbis;350;MHF_Pumpkin",
                "tnt;PLAYER_HEAD;<red>TNT;600;MHF_TNT",
                "present;PLAYER_HEAD;<red>Geschenk;750;MHF_Present1",
                "creeper;CREEPER_HEAD;<green>Creeper;800;",
                "zombie;ZOMBIE_HEAD;<dark_green>Zombie;800;",
                "skeleton;SKELETON_SKULL;<white>Skelett;800;",
                "piglin;PIGLIN_HEAD;<gold>Piglin;1200;",
                "witherskull;WITHER_SKELETON_SKULL;<dark_gray>Wither-Skelett;1500;",
                "dragon;DRAGON_HEAD;<light_purple>Enderdrache;5000;"
        ));
        /** id ist fest (enderpearl, grappling, firework, windcharge, thor, eye, pumpkinbomb) */
        public List<String> gadgets = new ArrayList<>(List.of(
                "enderpearl;ENDER_PEARL;<dark_aqua>Enderperlen-Ritt;500;5",
                "grappling;FISHING_ROD;<aqua>Enterhaken;750;2",
                "firework;FIREWORK_ROCKET;<red>Feuerwerk;300;3",
                "windcharge;WIND_CHARGE;<white>Windstoß;1000;2",
                "thor;IRON_AXE;<yellow>Thors Axt;1500;6",
                "eye;ENDER_EYE;<green>Magisches Auge;800;15",
                "pumpkinbomb;JACK_O_LANTERN;<gold>Kürbis-Bombe;600;5"
        ));
        /** id;MATERIAL;Name;Preis;Block (Material des Ballons) */
        public List<String> balloons = new ArrayList<>(List.of(
                "green;GREEN_TERRACOTTA;<dark_green>Grüner Ballon;400;GREEN_TERRACOTTA",
                "pink;PINK_TERRACOTTA;<light_purple>Rosa Ballon;400;PINK_TERRACOTTA",
                "brown;BROWN_TERRACOTTA;<gold>Brauner Ballon;400;BROWN_TERRACOTTA",
                "red;RED_TERRACOTTA;<red>Roter Ballon;400;RED_TERRACOTTA",
                "purple;PURPLE_TERRACOTTA;<dark_purple>Lila Ballon;400;PURPLE_TERRACOTTA",
                "yellow;YELLOW_TERRACOTTA;<yellow>Gelber Ballon;400;YELLOW_TERRACOTTA",
                "orange;ORANGE_TERRACOTTA;<gold>Oranger Ballon;400;ORANGE_TERRACOTTA",
                "magenta;MAGENTA_TERRACOTTA;<light_purple>Magenta Ballon;400;MAGENTA_TERRACOTTA",
                "cherry;CHERRY_LEAVES;<light_purple>Kirschblüten-Ballon;1200;CHERRY_LEAVES",
                "slime;SLIME_BLOCK;<green>Schleim-Ballon;1500;SLIME_BLOCK"
        ));
        /** id;MATERIAL;Name;Preis;PARTIKEL (DUST:#rrggbb für farbigen Staub) */
        public List<String> particles = new ArrayList<>(List.of(
                "snow;SNOWBALL;<white>Schneeflocken;400;SNOWFLAKE",
                "notes;MUSIC_DISC_CAT;<green>Noten;400;NOTE",
                "portal;ENDER_EYE;<dark_purple>Portal;500;PORTAL",
                "clouds;COBWEB;<white>Wolken;350;CLOUD",
                "water;WATER_BUCKET;<blue>Regen;350;DRIPPING_WATER",
                "flame;BLAZE_POWDER;<gold>Flammen;600;FLAME",
                "hearts;REDSTONE;<red>Herzen;750;HEART",
                "red;RED_DYE;<red>Roter Staub;300;DUST:#ff3b3b",
                "enchant;ENCHANTING_TABLE;<aqua>Magie;800;ENCHANT",
                "smoke;COAL;<dark_gray>Rauch;300;LARGE_SMOKE",
                "cherry;CHERRY_SAPLING;<light_purple>Kirschblüten;1000;CHERRY_LEAVES",
                "soul;SOUL_LANTERN;<aqua>Seelenfeuer;900;SOUL_FIRE_FLAME",
                "totem;TOTEM_OF_UNDYING;<yellow>Totem;2000;TOTEM_OF_UNDYING",
                "endrod;END_ROD;<white>Endstab;700;END_ROD"
        ));
    }

    // ------------------------------------------------------------------ Defaults

    private static Map<String, String> defaultPermissions() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("prefix", "lobbysystem");
        m.put("suffix_build", "build");
        m.put("suffix_build_others", "build.others");
        m.put("suffix_coins", "coins");
        m.put("suffix_coins_get_others", "coins.get");
        m.put("suffix_setreward", "setreward");
        m.put("suffix_setsettings", "setsettings");
        m.put("suffix_setspawn", "setspawn");
        m.put("suffix_setwarp", "setwarp");
        m.put("suffix_warp", "warp");
        m.put("suffix_tpcoord", "tpcoord");
        m.put("suffix_reload", "reload");
        m.put("suffix_premium_reward", "reward.premium");
        m.put("suffix_visibility_vip", "vip");
        m.put("suffix_silentlobby", "silentlobby");
        m.put("suffix_doublejump", "");
        m.put("suffix_cosmetics_all", "cosmetics.all");
        m.put("suffix_cosmetic", "cosmetics.");
        m.put("suffix_bypass", "bypass");
        m.put("suffix_friends_premium", "friends.premium");
        m.put("suffix_clan_create", "");
        m.put("suffix_party", "");
        m.put("suffix_jumppad", "");
        m.put("suffix_broadcast_bypass", "broadcast.bypass");
        m.put("suffix_setleaderboard", "setleaderboard");
        m.put("suffix_pay", "");
        m.put("suffix_fly", "fly");
        m.put("suffix_chat_bypass", "chat.bypass");
        m.put("suffix_sit", "");
        m.put("suffix_cookie", "");
        return m;
    }

    private static List<String> defaultNavigator() {
        return new ArrayList<>(List.of(
                "12;IRON_AXE;connect:CityBuild-1;<dark_gray>»</dark_gray> <dark_green>ᴄɪᴛʏʙᴜɪʟᴅ;<dark_gray>»</dark_gray> <gray>Klicke zum Joinen",
                "14;DIAMOND_PICKAXE;warp:skyblock;<dark_gray>»</dark_gray> <aqua>ꜱᴋʏʙʟᴏᴄᴋ;<dark_gray>»</dark_gray> <gray>Klicke zum Teleportieren",
                "19;CRAFTING_TABLE;warp:creative;<dark_gray>»</dark_gray> <gold>ᴄʀᴇᴀᴛɪᴠᴇ;<dark_gray>»</dark_gray> <gray>Klicke zum Teleportieren",
                "25;RED_BED;warp:bedwars;<dark_gray>»</dark_gray> <red>ʙᴇᴅᴡᴀʀꜱ;<dark_gray>»</dark_gray> <gray>Klicke zum Teleportieren",
                "31;CHEST;spawn;<dark_gray>»</dark_gray> <yellow>ꜱᴘᴀᴡɴ;<dark_gray>»</dark_gray> <gray>Zurück zum Spawn",
                "29;TNT;connect-best:MissileWars;<dark_gray>»</dark_gray> <red>ᴍɪꜱꜱɪʟᴇ ᴡᴀʀꜱ;<dark_gray>»</dark_gray> <gray>Spieler: <white>{players:MissileWars*}\n<dark_gray>»</dark_gray> {state:MissileWars*}\n\n<dark_gray>»</dark_gray> <yellow>Klicke zum Spielen",
                "38;REPEATER;warp:minigames;<dark_gray>»</dark_gray> <light_purple>ᴍɪɴɪɢᴀᴍᴇꜱ;<dark_gray>»</dark_gray> <gray>Klicke zum Teleportieren",
                "42;GLOWSTONE_DUST;warp:events;<dark_gray>»</dark_gray> <yellow>ᴇᴠᴇɴᴛꜱ;<dark_gray>»</dark_gray> <gray>Klicke zum Teleportieren"
        ));
    }

    private static Map<String, HotbarItem> defaultHotbar() {
        Map<String, HotbarItem> m = new LinkedHashMap<>();
        m.put("navigator", new HotbarItem(0, "COMPASS", "<dark_gray>-●</dark_gray> <gold><b>ᴛᴇʟᴇᴘᴏʀᴛᴇʀ</b>",
                "<dark_gray>»</dark_gray> <gray>Teleportiere dich zu den Spielmodi"));
        m.put("visibility", new HotbarItem(1, "BLAZE_ROD", "<dark_gray>-●</dark_gray> <aqua><b>ꜱᴘɪᴇʟᴇʀꜱɪᴄʜᴛʙᴀʀᴋᴇɪᴛ</b>",
                "<dark_gray>»</dark_gray> <gray>Aktuell: {visibility}"));
        m.put("silentlobby", new HotbarItem(2, "TNT", "<dark_gray>-●</dark_gray> <red><b>ꜱɪʟᴇɴᴛ ʟᴏʙʙʏ</b>",
                "<dark_gray>»</dark_gray> <gray>Status: {silent}"));
        m.put("gadget", new HotbarItem(4, "BARRIER", "<dark_gray>-●</dark_gray> <red>Kein Gadget ausgewählt",
                "<dark_gray>»</dark_gray> <gray>Wähle eines über die <light_purple>Extras</light_purple>"));
        m.put("lobbyswitcher", new HotbarItem(5, "END_CRYSTAL", "<dark_gray>-●</dark_gray> <aqua><b>ʟᴏʙʙʏ-ꜱᴡɪᴛᴄʜᴇʀ</b>",
                "<dark_gray>»</dark_gray> <gray>Wechsle die Lobby"));
        m.put("friends", new HotbarItem(3, "PLAYER_HEAD", "<dark_gray>-●</dark_gray> <dark_aqua><b>ꜰʀᴇᴜɴᴅᴇ</b>",
                "<dark_gray>»</dark_gray> <gray>Freunde, Party & Clan"));
        m.put("lottery", new HotbarItem(6, "NAME_TAG", "<dark_gray>-●</dark_gray> <yellow><b>ʟᴏᴛᴛᴇʀɪᴇ</b>",
                "<dark_gray>»</dark_gray> <gray>Du hast <yellow>{lose}</yellow> <gray>Lose"));
        m.put("extras", new HotbarItem(7, "NETHER_STAR", "<dark_gray>-●</dark_gray> <light_purple><b>ᴇxᴛʀᴀꜱ</b>",
                "<dark_gray>»</dark_gray> <gray>Wähle ein Extra aus."));
        m.put("profile", new HotbarItem(8, "PLAYER_HEAD", "<dark_gray>-●</dark_gray> <green><b>ᴘʀᴏꜰɪʟ</b>",
                "<dark_gray>»</dark_gray> <gray>Deine Statistiken & Einstellungen"));
        return m;
    }

    private static Map<String, String> defaultMessages() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("no_permission", "<red>Dazu hast du keine Rechte.");
        m.put("only_players", "<red>Dieser Befehl ist nur für Spieler.");
        m.put("player_not_found", "<red>Der Spieler <white>{name}</white> wurde nicht gefunden.");
        m.put("invalid_number", "<red>Bitte gib eine gültige Zahl an.");
        m.put("usage", "<gray>Benutze: <yellow>{usage}");
        m.put("reloaded", "<green>Die Konfiguration wurde neu geladen.");
        m.put("build_on", "<green>Baumodus aktiviert.");
        m.put("build_off", "<red>Baumodus deaktiviert.");
        m.put("build_other", "<gray>Baumodus von <white>{name}</white> wurde {state}<gray>.");
        m.put("coins_add", "<gray>Du hast <white>{name}</white> <yellow>{amount} Coins</yellow> <gray>gegeben.");
        m.put("coins_remove", "<gray>Du hast <white>{name}</white> <yellow>{amount} Coins</yellow> <gray>entfernt.");
        m.put("coins_set", "<gray>Die Coins von <white>{name}</white> <gray>wurden auf <yellow>{amount}</yellow> <gray>gesetzt.");
        m.put("coins_get", "<white>{name}</white> <gray>hat <yellow>{amount} Coins</yellow><gray>.");
        m.put("coins_get_self", "<gray>Du hast <yellow>{amount} Coins</yellow><gray>.");
        m.put("reward_set", "<green>Die Daily-Reward Hexe wurde gesetzt.");
        m.put("reward_removed", "<red>Die Daily-Reward Hexe wurde entfernt.");
        m.put("settings_set", "<green>Der Einstellungs-Dorfbewohner wurde gesetzt.");
        m.put("settings_removed", "<red>Der Einstellungs-Dorfbewohner wurde entfernt.");
        m.put("spawn_set", "<green>Der Spawn-Punkt wurde gesetzt.");
        m.put("spawn_not_set", "<red>Es wurde noch kein Spawn gesetzt. <gray>(/setspawn)");
        m.put("warp_set", "<green>Der Warp <white>{warp}</white> wurde gesetzt.");
        m.put("warp_removed", "<red>Der Warp <white>{warp}</white> wurde entfernt.");
        m.put("warp_not_found", "<red>Der Warp <white>{warp}</white> existiert nicht.");
        m.put("warp_list", "<gray>Warps: <white>{warps}");
        m.put("warp_teleported", "<gray>Du wurdest zu <aqua>{warp}</aqua> <gray>teleportiert.");
        m.put("tpcoord_done", "<gray>Teleportiert zu <aqua>{x} {y} {z}</aqua> <gray>in <white>{world}</white>.");
        m.put("world_not_found", "<red>Die Welt <white>{world}</white> existiert nicht.");
        m.put("connecting", "<gray>Du wirst mit <aqua>{server}</aqua> <gray>verbunden...");
        m.put("reward_claimed", "<green>Du hast deine Belohnung erhalten: <gold>+{coins} Coins</gold> <dark_gray>|</dark_gray> <yellow>+{lose} Lose");
        m.put("reward_cooldown", "<red>Du kannst diese Belohnung erst in <white>{time}</white> wieder abholen.");
        m.put("reward_no_premium", "<red>Diese Belohnung ist nur für <gold>Premium</gold>-Spieler.");
        m.put("lottery_no_tickets", "<red>Du hast keine Lose mehr. <gray>Hol dir neue bei der täglichen Belohnung!");
        m.put("lottery_win", "<gray>Du hast <gold>{coins} Coins</gold> <gray>gewonnen!");
        m.put("cosmetic_bought", "<green>Du hast <white>{cosmetic}</white> <green>für <yellow>{price} Coins</yellow> <green>gekauft.");
        m.put("cosmetic_no_coins", "<red>Dir fehlen <yellow>{missing} Coins</yellow> <red>für dieses Cosmetic.");
        m.put("cosmetic_selected", "<gray>Du hast <white>{cosmetic}</white> <gray>ausgewählt.");
        m.put("cosmetic_removed", "<gray>Deine Cosmetics wurden entfernt.");
        m.put("gadget_cooldown", "<red>Bitte warte noch <white>{seconds}s</white><red>.");
        m.put("visibility_changed", "<gray>Spielersichtbarkeit: {state}");
        m.put("silent_on", "<gray>Du bist nun in der <red>Silent Lobby</red><gray>. Niemand sieht oder hört dich.");
        m.put("silent_off", "<gray>Du hast die <red>Silent Lobby</red> <gray>verlassen.");
        m.put("setting_changed", "<gray>{setting} <dark_gray>»</dark_gray> {state}");
        m.put("license_owner_only", "<red>Lizenzen kann nur der Owner <white>SujucDerBreite</white> <red>verwalten.");
        m.put("license_created", "<green>Lizenz für <white>{holder}</white> <green>erstellt <gray>(Ablauf: {expires})<green>. <yellow><click:copy_to_clipboard:'{raw_key}'><hover:show_text:'Klicke zum Kopieren'>[Klicke zum Kopieren]</hover></click>");
        // ---- Freunde
        m.put("friend_prefix", "<dark_aqua><b>ꜰʀᴇᴜɴᴅᴇ</b></dark_aqua> <dark_gray>»</dark_gray> <gray>");
        m.put("friend_help", "<gray>/friend <aqua>add|remove|accept|deny|list|requests|jump|msg|toggle</aqua> <dark_gray>(/f)");
        m.put("friend_request_sent", "<gray>Freundschaftsanfrage an <white>{name}</white> <gray>gesendet.");
        m.put("friend_request_received", "<white>{name}</white> <gray>möchte dein Freund sein. <green><click:run_command:'/friend accept {name}'><hover:show_text:'Annehmen'>[Annehmen]</hover></click></green> <red><click:run_command:'/friend deny {name}'><hover:show_text:'Ablehnen'>[Ablehnen]</hover></click></red>");
        m.put("friend_added", "<green>Du bist jetzt mit <white>{name}</white> <green>befreundet.");
        m.put("friend_removed", "<gray>Du bist nicht mehr mit <white>{name}</white> <gray>befreundet.");
        m.put("friend_denied", "<gray>Anfrage von <white>{name}</white> <gray>abgelehnt.");
        m.put("friend_already", "<red>Ihr seid bereits befreundet.");
        m.put("friend_not_friends", "<red>Du bist nicht mit <white>{name}</white> <red>befreundet.");
        m.put("friend_no_request", "<red>Keine Anfrage von <white>{name}</white><red>.");
        m.put("friend_request_pending", "<red>Du hast <white>{name}</white> <red>bereits eine Anfrage geschickt.");
        m.put("friend_self", "<red>Du kannst dich nicht selbst hinzufügen.");
        m.put("friend_limit", "<red>Du hast die maximale Anzahl an Freunden erreicht ({max}).");
        m.put("friend_requests_disabled", "<red><white>{name}</white> <red>nimmt keine Anfragen an.");
        m.put("friend_online", "<white>{name}</white> <gray>ist jetzt <green>online</green><gray>.");
        m.put("friend_offline", "<white>{name}</white> <gray>ist jetzt <red>offline</red><gray>.");
        m.put("friend_list_header", "<gray>Deine Freunde <dark_gray>(<white>{count}</white>)</dark_gray>:");
        m.put("friend_list_entry", "<dark_gray> » </dark_gray>{status} <white>{name}</white> <dark_gray>|</dark_gray> <gray>{where}");
        m.put("friend_requests_header", "<gray>Offene Anfragen <dark_gray>(<white>{count}</white>)</dark_gray>:");
        m.put("friend_not_online", "<red><white>{name}</white> <red>ist nicht online.");
        m.put("friend_jump", "<gray>Du springst zu <white>{name}</white><gray>...");
        m.put("friend_toggle", "<gray>Freundschaftsanfragen: {state}");
        // ---- Party
        m.put("party_prefix", "<light_purple><b>ᴘᴀʀᴛʏ</b></light_purple> <dark_gray>»</dark_gray> <gray>");
        m.put("party_help", "<gray>/party <light_purple>invite|accept|deny|leave|kick|promote|disband|list|chat|warp</light_purple> <dark_gray>(/p)");
        m.put("party_invite_sent", "<gray>Du hast <white>{name}</white> <gray>in die Party eingeladen.");
        m.put("party_invite_received", "<white>{name}</white> <gray>lädt dich in die Party ein. <green><click:run_command:'/party accept {name}'><hover:show_text:'Beitreten'>[Beitreten]</hover></click></green> <red><click:run_command:'/party deny {name}'><hover:show_text:'Ablehnen'>[Ablehnen]</hover></click></red>");
        m.put("party_joined", "<white>{name}</white> <green>ist der Party beigetreten.");
        m.put("party_left", "<white>{name}</white> <gray>hat die Party verlassen.");
        m.put("party_kicked", "<white>{name}</white> <red>wurde aus der Party geworfen.");
        m.put("party_promoted", "<white>{name}</white> <gray>ist jetzt <gold>Party-Leader</gold><gray>.");
        m.put("party_disbanded", "<red>Die Party wurde aufgelöst.");
        m.put("party_not_in", "<red>Du bist in keiner Party.");
        m.put("party_already_in", "<red>Du bist bereits in einer Party.");
        m.put("party_target_in_party", "<red><white>{name}</white> <red>ist bereits in einer Party.");
        m.put("party_not_leader", "<red>Nur der Party-Leader kann das.");
        m.put("party_full", "<red>Die Party ist voll ({max}).");
        m.put("party_no_invite", "<red>Du hast keine Einladung von <white>{name}</white><red>.");
        m.put("party_not_member", "<red><white>{name}</white> <red>ist nicht in deiner Party.");
        m.put("party_invites_disabled", "<red><white>{name}</white> <red>nimmt keine Party-Einladungen an.");
        m.put("party_list_header", "<gray>Party <dark_gray>(<white>{count}</white><dark_gray>/</dark_gray><white>{max}</white>)</dark_gray>:");
        m.put("party_warp", "<gray>Die Party folgt dir nach <aqua>{server}</aqua><gray>.");
        // ---- Clan
        m.put("clan_prefix", "<gold><b>ᴄʟᴀɴ</b></gold> <dark_gray>»</dark_gray> <gray>");
        m.put("clan_help", "<gray>/clan <gold>create|invite|accept|deny|leave|kick|promote|demote|disband|info|list|chat</gold>");
        m.put("clan_created", "<green>Der Clan <white>{name}</white> <dark_gray>[</dark_gray><yellow>{tag}</yellow><dark_gray>]</dark_gray> <green>wurde gegründet.");
        m.put("clan_cost", "<red>Einen Clan zu gründen kostet <yellow>{cost} Coins</yellow><red>.");
        m.put("clan_exists", "<red>Ein Clan mit diesem Namen oder Tag existiert bereits.");
        m.put("clan_invalid_tag", "<red>Der Tag muss {min}–{max} Zeichen lang sein (A–Z, 0–9).");
        m.put("clan_invalid_name", "<red>Der Name darf max. {max} Zeichen haben (Buchstaben, Zahlen, _).");
        m.put("clan_already_in", "<red>Du bist bereits in einem Clan.");
        m.put("clan_not_in", "<red>Du bist in keinem Clan.");
        m.put("clan_no_rights", "<red>Dazu hast du in deinem Clan keine Rechte.");
        m.put("clan_invite_sent", "<gray>Du hast <white>{name}</white> <gray>in den Clan eingeladen.");
        m.put("clan_invite_received", "<gray>Du wurdest in den Clan <white>{clan}</white> <dark_gray>[</dark_gray><yellow>{tag}</yellow><dark_gray>]</dark_gray> <gray>eingeladen. <green><click:run_command:'/clan accept {tag}'><hover:show_text:'Beitreten'>[Beitreten]</hover></click></green> <red><click:run_command:'/clan deny {tag}'><hover:show_text:'Ablehnen'>[Ablehnen]</hover></click></red>");
        m.put("clan_joined", "<white>{name}</white> <green>ist dem Clan beigetreten.");
        m.put("clan_left", "<white>{name}</white> <gray>hat den Clan verlassen.");
        m.put("clan_kicked", "<white>{name}</white> <red>wurde aus dem Clan geworfen.");
        m.put("clan_role", "<white>{name}</white> <gray>ist jetzt <gold>{role}</gold><gray>.");
        m.put("clan_disbanded", "<red>Der Clan wurde aufgelöst.");
        m.put("clan_full", "<red>Der Clan ist voll ({max}).");
        m.put("clan_no_invite", "<red>Du hast keine Einladung von diesem Clan.");
        m.put("clan_not_found", "<red>Dieser Clan existiert nicht.");
        m.put("clan_target_in_clan", "<red><white>{name}</white> <red>ist bereits in einem Clan.");
        m.put("clan_not_member", "<red><white>{name}</white> <red>ist nicht in deinem Clan.");
        m.put("clan_owner_leave", "<red>Als Leader musst du den Clan erst übergeben (/clan promote) oder auflösen (/clan disband).");
        m.put("clan_info", "<white>{clan}</white> <dark_gray>[</dark_gray><yellow>{tag}</yellow><dark_gray>]</dark_gray> <dark_gray>|</dark_gray> <gray>Mitglieder: <white>{count}</white><dark_gray>/</dark_gray><white>{max}</white>");
        m.put("clan_member_entry", "<dark_gray> » </dark_gray>{status} <white>{name}</white> <dark_gray>|</dark_gray> <gold>{role}");
        // ---- Sonstiges
        m.put("pay_sent", "<gray>Du hast <white>{name}</white> {i_coin} <yellow>{amount} Coins</yellow> <gray>überwiesen.");
        m.put("pay_received", "<white>{name}</white> <gray>hat dir {i_coin} <yellow>{amount} Coins</yellow> <gray>überwiesen.");
        m.put("pay_self", "<red>Du kannst dir nicht selbst Coins senden.");
        m.put("pay_not_enough", "<red>Du hast nicht genug Coins.");
        m.put("leaderboard_set", "<green>Bestenliste <white>{type}</white> <green>wurde gesetzt.");
        m.put("leaderboard_removed", "<red>Bestenliste <white>{type}</white> <red>wurde entfernt.");
        m.put("leaderboard_types", "<gray>Typen: <white>coins, playtime, streak, level, cookies, friends, <spiel>_wins/kills/deaths/games/level (z. B. missilewars_wins)");
        m.put("chat_cooldown", "<red>Bitte warte kurz, bevor du wieder schreibst.");
        m.put("chat_repeat", "<red>Bitte wiederhole dich nicht.");
        m.put("mentioned", "<yellow>{name}</yellow> <gray>hat dich im Chat erwähnt!");
        m.put("fly_on", "<gray>Fliegen {state}");
        // ---- Level & Cookie Clicker
        m.put("level_up", "<gradient:#f7971e:#ffd200><b>ʟᴇᴠᴇʟ ᴜᴘ!</b></gradient> <gray>Du bist jetzt <gold>Level {level}</gold> <dark_gray>(</dark_gray><yellow>+{coins} Coins</yellow><dark_gray>)");
        m.put("level_broadcast", "<white>{name}</white> <gray>hat <gold>Level {level}</gold> <gray>erreicht!");
        m.put("level_info", "<white>{name}</white> <dark_gray>»</dark_gray> <gold>Level {level}</gold> <dark_gray>(</dark_gray><yellow>{xp}</yellow><dark_gray>/</dark_gray><yellow>{needed} XP</yellow><dark_gray>)</dark_gray><newline>{bar}");
        m.put("cookie_bought", "<gray>Gekauft: <white>{upgrade}</white> <dark_gray>(</dark_gray><gold>{amount}×</gold><dark_gray>)");
        m.put("cookie_not_enough", "<red>Dir fehlen <gold>{missing} Kekse</gold><red>.");
        m.put("cookie_golden", "<gold><b>ɢᴏʟᴅᴇɴᴇʀ ᴋᴇᴋꜱ!</b></gold> <gray>+<gold>{cookies} Kekse");
        m.put("cookie_sold", "<gray>Du hast <gold>{cookies} Kekse</gold> <gray>gegen {i_coin} <yellow>{coins} Coins</yellow> <gray>getauscht.");
        m.put("cookie_sell_limit", "<red>Tageslimit erreicht – morgen kannst du wieder Kekse tauschen.");
        m.put("game_offline", "<red>Aktuell ist kein <white>{game}</white><red>-Server erreichbar.");
        m.put("lobby_switch_same", "<red>Du bist bereits auf dieser Lobby.");
        m.put("streak", "<gray>Login-Streak: <gold>{streak} Tage</gold> <dark_gray>(</dark_gray><yellow>+{bonus} Bonus-Coins</yellow><dark_gray>)");
        m.put("state_on", "<green>aktiviert");
        m.put("state_off", "<red>deaktiviert");
        return m;
    }
}
