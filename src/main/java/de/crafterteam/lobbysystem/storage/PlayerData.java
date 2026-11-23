package de.crafterteam.lobbysystem.storage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerData {

    public enum Visibility { ALL, VIP, NONE }

    private final UUID uuid;
    private volatile String name;
    private volatile long coins;
    private volatile int lose;
    private volatile long playtime;
    private volatile long lastDaily;
    private volatile long lastPremium;

    private volatile boolean doubleJump = true;
    private volatile boolean scoreboard = true;
    private volatile boolean bars = true;
    private volatile boolean tpEffects = true;
    private volatile boolean silent = false;
    private volatile Visibility visibility = Visibility.ALL;

    /** Kategorie -> Cosmetic-ID */
    private final Map<String, String> selected = new ConcurrentHashMap<>();
    private final Set<String> owned = ConcurrentHashMap.newKeySet();

    private volatile boolean dirty;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    // ---------------------------------------------------------------- Serialisierung

    public String settingsString() {
        return "dj=" + b(doubleJump) + ";sb=" + b(scoreboard) + ";bars=" + b(bars) + ";tp=" + b(tpEffects)
                + ";silent=" + b(silent) + ";vis=" + visibility.name();
    }

    public void loadSettings(String s) {
        Map<String, String> m = parse(s);
        doubleJump = !"0".equals(m.get("dj"));
        scoreboard = !"0".equals(m.get("sb"));
        bars = !"0".equals(m.get("bars"));
        tpEffects = !"0".equals(m.get("tp"));
        silent = "1".equals(m.get("silent"));
        try {
            visibility = Visibility.valueOf(m.getOrDefault("vis", "ALL"));
        } catch (IllegalArgumentException e) {
            visibility = Visibility.ALL;
        }
    }

    public String selectedString() {
        StringBuilder sb = new StringBuilder();
        selected.forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
        return sb.toString();
    }

    public void loadSelected(String s) {
        selected.clear();
        selected.putAll(parse(s));
    }

    public String ownedString() {
        return String.join(",", owned);
    }

    public void loadOwned(String s) {
        owned.clear();
        if (s == null || s.isBlank()) return;
        for (String id : s.split(",")) if (!id.isBlank()) owned.add(id.trim());
    }

    private static Map<String, String> parse(String s) {
        Map<String, String> m = new LinkedHashMap<>();
        if (s == null) return m;
        for (String part : s.split(";")) {
            int i = part.indexOf('=');
            if (i > 0) m.put(part.substring(0, i), part.substring(i + 1));
        }
        return m;
    }

    private static String b(boolean v) {
        return v ? "1" : "0";
    }

    // ---------------------------------------------------------------- Getter / Setter

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public void name(String name) { this.name = name; dirty = true; }

    /*
     * Coins & Lose werden als Differenz gespeichert (coins = coins + ?), damit mehrere
     * Lobby-Server mit derselben MySQL-Datenbank sich nicht gegenseitig überschreiben.
     */
    private volatile long coinsDelta;
    private volatile int loseDelta;
    private volatile boolean coinsAbsolute;

    public long coins() { return coins; }
    public synchronized void coins(long value) { coins = Math.max(0, value); coinsAbsolute = true; dirty = true; }
    public synchronized void addCoins(long amount) {
        long before = coins;
        coins = Math.max(0, coins + amount);
        coinsDelta += coins - before;
        dirty = true;
    }
    public void loadCoins(long value) { coins = value; }

    public int lose() { return lose; }
    public synchronized void addLose(int amount) {
        int before = lose;
        lose = Math.max(0, lose + amount);
        loseDelta += lose - before;
        dirty = true;
    }
    public void loadLose(int value) { lose = value; }

    /** Liefert [coinsDelta, loseDelta, absolut?] und setzt die Differenzen zurück. */
    public synchronized long[] drainDeltas() {
        long[] out = {coinsDelta, loseDelta, coinsAbsolute ? 1 : 0};
        coinsDelta = 0;
        loseDelta = 0;
        coinsAbsolute = false;
        return out;
    }

    public long playtime() { return playtime; }
    private volatile long playtimeDelta;
    public synchronized void addPlaytime(long seconds) { playtime += seconds; playtimeDelta += seconds; dirty = true; }
    public void loadPlaytime(long seconds) { playtime = seconds; }
    public synchronized long drainPlaytime() { long d = playtimeDelta; playtimeDelta = 0; return d; }

    public long lastDaily() { return lastDaily; }
    public void lastDaily(long t) { lastDaily = t; dirty = true; }
    public long lastPremium() { return lastPremium; }
    public void lastPremium(long t) { lastPremium = t; dirty = true; }

    public boolean doubleJump() { return doubleJump; }
    public void doubleJump(boolean v) { doubleJump = v; dirty = true; }
    public boolean scoreboard() { return scoreboard; }
    public void scoreboard(boolean v) { scoreboard = v; dirty = true; }
    public boolean bars() { return bars; }
    public void bars(boolean v) { bars = v; dirty = true; }
    public boolean tpEffects() { return tpEffects; }
    public void tpEffects(boolean v) { tpEffects = v; dirty = true; }
    public boolean silent() { return silent; }
    public void silent(boolean v) { silent = v; dirty = true; }
    public Visibility visibility() { return visibility; }
    public void visibility(Visibility v) { visibility = v; dirty = true; }

    public String selected(String category) { return selected.get(category); }
    public void select(String category, String id) {
        if (id == null) selected.remove(category); else selected.put(category, id);
        dirty = true;
    }
    public void clearSelected() { selected.clear(); dirty = true; }

    public Set<String> owned() { return Collections.unmodifiableSet(owned); }
    public boolean owns(String id) { return owned.contains(id); }
    public void addOwned(String id) { owned.add(id); dirty = true; }

    public boolean dirty() { return dirty; }
    public void clean() { dirty = false; }
}
