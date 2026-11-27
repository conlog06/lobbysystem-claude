package de.crafterteam.lobbysystem.cosmetics;

import org.bukkit.Material;

/** Ein Cosmetic aus der config.json:  id;MATERIAL;Name;Preis;Daten */
public record Cosmetic(Category category, String id, Material icon, String name, long price, String data) {

    public enum Category {
        HEAD("<gold>ʜᴇᴀᴅꜱ", Material.PLAYER_HEAD),
        GADGET("<aqua>ɢᴀᴅɢᴇᴛꜱ", Material.FISHING_ROD),
        BALLOON("<dark_aqua>ʙᴀʟʟᴏɴᴇ", Material.LEAD),
        PARTICLE("<blue>ᴘᴀʀᴛɪᴋᴇʟ", Material.BLAZE_POWDER),
        PET("<green>ʜᴀᴜꜱᴛɪᴇʀᴇ", Material.BONE);

        public final String title;
        public final Material icon;

        Category(String title, Material icon) {
            this.title = title;
            this.icon = icon;
        }

        public String key() {
            return name().toLowerCase();
        }
    }

    /** Eindeutige ID inkl. Kategorie (z. B. "particle:hearts") für Besitz & Permission. */
    public String fullId() {
        return category.key() + "." + id;
    }

    public static Cosmetic parse(Category category, String line) {
        String[] p = line.split(";", -1);
        if (p.length < 3) return null;
        Material m = Material.matchMaterial(p[1].trim().toUpperCase());
        if (m == null || m.isAir()) m = Material.BARRIER;
        long price = 0;
        if (p.length > 3) {
            try {
                price = Long.parseLong(p[3].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return new Cosmetic(category, p[0].trim().toLowerCase(), m, p[2], price, p.length > 4 ? p[4].trim() : "");
    }
}
