package de.crafterteam.lobbysystem.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material == null ? Material.STONE : material);
        this.meta = item.getItemMeta();
    }

    public static Material material(String name, Material fallback) {
        if (name == null) return fallback;
        Material m = Material.matchMaterial(name.trim().toUpperCase());
        return m == null || m.isAir() ? fallback : m;
    }

    public ItemBuilder name(String raw) {
        return name(raw, Map.of());
    }

    public ItemBuilder name(String raw, Map<String, String> ph) {
        meta.displayName(Text.item(raw, ph));
        return this;
    }

    public ItemBuilder name(Component c) {
        meta.displayName(c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        return lore(lines, Map.of());
    }

    public ItemBuilder lore(List<String> lines, Map<String, String> ph) {
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.addAll(Text.itemLore(lines, ph));
        meta.lore(lore);
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(List.of(lines));
    }

    /** Moderner Glint ohne Fake-Verzauberung (1.20.5+). */
    public ItemBuilder glow(boolean glow) {
        if (glow) meta.setEnchantmentGlintOverride(true);
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(99, amount)));
        return this;
    }

    public ItemBuilder tag(String value) {
        meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, value);
        return this;
    }

    public ItemBuilder hideFlags() {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        meta.setUnbreakable(true);
        return this;
    }

    public ItemBuilder skull(OfflinePlayer owner) {
        if (meta instanceof SkullMeta sm && owner != null) sm.setOwningPlayer(owner);
        return this;
    }

    /** Kopf über Spielernamen (z. B. MHF_Cake) oder Base64-Texture ("texture:eyJ0Z…"). */
    public ItemBuilder skull(String data) {
        if (!(meta instanceof SkullMeta sm) || data == null || data.isBlank()) return this;
        if (data.startsWith("texture:")) {
            PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(data.getBytes()), "ls_head");
            profile.setProperty(new ProfileProperty("textures", data.substring(8)));
            sm.setPlayerProfile(profile);
        } else {
            String name = data.startsWith("owner:") ? data.substring(6) : data;
            sm.setPlayerProfile(Bukkit.createProfile(name));
        }
        return this;
    }

    /** Kopf eines (evtl. offline) Spielers – Textur wird vom Server/Client nachgeladen. */
    public ItemBuilder skull(UUID uuid, String name) {
        if (meta instanceof SkullMeta sm) sm.setPlayerProfile(Bukkit.createProfile(uuid, name));
        return this;
    }

    public ItemBuilder edit(Consumer<ItemMeta> consumer) {
        consumer.accept(meta);
        return this;
    }

    /**
     * Eigenes Icon aus dem Ressourcenpaket (item_model = lobbysystem:&lt;key&gt;) – nur, wenn der Spieler das Pack geladen hat,
     * sonst bleibt das normale Material (kein lila-schwarzes "Fehlt"-Icon).
     */
    public ItemBuilder icon(org.bukkit.entity.Player viewer, String key) {
        if (key == null || key.isBlank() || viewer == null) return this;
        if (!de.crafterteam.lobbysystem.pack.ResourcePackManager.icons(viewer)) return this;
        try {
            meta.setItemModel(new org.bukkit.NamespacedKey("lobbysystem", key.toLowerCase()));
        } catch (Throwable ignored) {
            // ältere Server-API ohne item_model
        }
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}
