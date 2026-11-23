package de.crafterteam.lobbysystem.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Text-Helfer: Unterstützt moderne MiniMessage-Formatierung (Gradients, Hex, Hover …)
 * und – für alte Configs – weiterhin §/&-Farbcodes.
 */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final Pattern AMP_CODE = Pattern.compile("&([0-9a-fk-orA-FK-OR#])");

    private static final String NORMAL = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private Text() {
    }

    public static boolean isLegacy(String s) {
        return s.indexOf('§') >= 0 || AMP_CODE.matcher(s).find();
    }

    public static Component parse(String s) {
        if (s == null || s.isEmpty()) return Component.empty();
        if (isLegacy(s)) {
            return LEGACY.deserialize(AMP_CODE.matcher(s).replaceAll("§$1"));
        }
        return MM.deserialize(s);
    }

    /** Ersetzt {key}-Platzhalter. Werte werden für MiniMessage escaped (keine Tag-Injection über Namen). */
    public static Component parse(String s, Map<String, String> placeholders) {
        return parse(apply(s, placeholders));
    }

    public static String apply(String s, Map<String, String> placeholders) {
        if (s == null) return "";
        if (placeholders == null || placeholders.isEmpty()) return s;
        boolean legacy = isLegacy(s);
        String out = s;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue();
            if (!legacy && USER_KEYS.contains(e.getKey())) v = MM.escapeTags(v);
            out = out.replace("{" + e.getKey() + "}", v);
        }
        return out;
    }

    /** Platzhalter mit Spieler-Eingaben werden escaped – Rang-Prefixe usw. bleiben formatiert. */
    private static final java.util.Set<String> USER_KEYS = java.util.Set.of("name", "player", "target", "holder", "warp", "message", "input");

    /** Text für Items: Kursiv standardmäßig aus (moderne Items). */
    public static Component item(String s, Map<String, String> ph) {
        return parse(s, ph).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> itemLore(List<String> lines, Map<String, String> ph) {
        List<Component> out = new ArrayList<>();
        if (lines == null) return out;
        for (String line : lines) {
            for (String part : apply(line, ph).split("\n")) {
                out.add(parse(part).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            }
        }
        return out;
    }

    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    public static String escape(String s) {
        return MM.escapeTags(s);
    }

    /** Wandelt Text in die moderne "Small-Caps"-Schrift um (ʟᴏʙʙʏ). */
    public static String smallCaps(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            int i = NORMAL.indexOf(Character.toLowerCase(c));
            sb.append(i >= 0 ? SMALL.charAt(i) : c);
        }
        return sb.toString();
    }

    public static String formatNumber(long n) {
        return String.format(java.util.Locale.GERMANY, "%,d", n);
    }

    public static String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        if (h >= 1) return h + (h == 1 ? " Stunde" : " Stunden");
        return m + (m == 1 ? " Minute" : " Minuten");
    }

    public static String formatCooldown(long millis) {
        long s = Math.max(0, millis / 1000);
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}
