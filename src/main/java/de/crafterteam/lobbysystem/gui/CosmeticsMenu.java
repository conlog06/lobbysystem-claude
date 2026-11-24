package de.crafterteam.lobbysystem.gui;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.cosmetics.Cosmetic;
import de.crafterteam.lobbysystem.cosmetics.CosmeticManager;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import de.crafterteam.lobbysystem.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Extras: Heads, Gadgets, Ballone, Partikel – mit Tabs, Seiten & Coins-Shop. */
public final class CosmeticsMenu extends Menu {

    private static final Map<UUID, Cosmetic.Category> LAST = new HashMap<>();
    private static final int[] CONTENT = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};
    private static final int[] TAB_SLOTS = {1, 3, 5, 7};

    private final Cosmetic.Category category;
    private int page;

    public CosmeticsMenu(LobbySystem plugin, Player player, Cosmetic.Category category) {
        super(plugin, player);
        this.category = category;
        LAST.put(player.getUniqueId(), category);
        create(6, Text.parse(plugin.configs().cfg().cosmetics.title.replace("{category}", category.title)));
    }

    public static Cosmetic.Category lastCategory(Player player) {
        return LAST.getOrDefault(player.getUniqueId(), Cosmetic.Category.HEAD);
    }

    @Override
    protected void build() {
        CosmeticManager cm = plugin.cosmetics();
        PlayerData d = plugin.data().get(player);

        Cosmetic.Category[] cats = Cosmetic.Category.values();
        for (int i = 0; i < cats.length && i < TAB_SLOTS.length; i++) {
            Cosmetic.Category cat = cats[i];
            boolean active = cat == category;
            set(TAB_SLOTS[i], new ItemBuilder(cat.icon).name("<dark_gray>»</dark_gray> " + cat.title)
                    .lore(active ? "<dark_gray>↪</dark_gray> <green>Geöffnet" : "<dark_gray>↪</dark_gray> <gray>Klicke zum Öffnen")
                    .glow(active).hideFlags().build(), e -> {
                if (cat == category) return;
                click();
                new CosmeticsMenu(plugin, player, cat).open();
            });
        }

        List<Cosmetic> list = cm.list(category);
        int pages = Math.max(1, (list.size() + CONTENT.length - 1) / CONTENT.length);
        page = Math.max(0, Math.min(page, pages - 1));
        Cosmetic selected = cm.selected(player, category);
        for (int i = 0; i < CONTENT.length; i++) {
            int index = page * CONTENT.length + i;
            if (index >= list.size()) break;
            Cosmetic c = list.get(index);
            boolean owned = cm.owns(player, c);
            boolean isSelected = selected != null && selected.id().equals(c.id());
            ItemBuilder b = category == Cosmetic.Category.HEAD ? cm.headItem(c) : new ItemBuilder(c.icon()).name(c.name());
            b.lore("");
            if (isSelected) b.lore("<dark_gray>↪</dark_gray> <green>Ausgewählt", "<dark_gray>»</dark_gray> <gray>Klicke zum Ablegen");
            else if (owned) b.lore("<dark_gray>↪</dark_gray> <green>Freigeschaltet", "<dark_gray>»</dark_gray> <gray>Klicke zum Auswählen");
            else b.lore("<dark_gray>↪</dark_gray> <gray>Preis: <yellow>" + Text.formatNumber(c.price()) + " Coins",
                        "<dark_gray>»</dark_gray> " + (d.coins() >= c.price() ? "<green>Klicke zum Kaufen" : "<red>Nicht genug Coins"));
            set(CONTENT[i], b.glow(isSelected).hideFlags().build(), e -> clickCosmetic(c));
        }

        if (page > 0) set(45, new ItemBuilder(Material.ARROW).name("<gray>« Vorherige Seite").build(), e -> {
            page--;
            click();
            refresh();
        });
        if (page < pages - 1) set(53, new ItemBuilder(Material.ARROW).name("<gray>Nächste Seite »").build(), e -> {
            page++;
            click();
            refresh();
        });
        set(48, new ItemBuilder(Material.SUNFLOWER).name("<dark_gray>»</dark_gray> <yellow>" + Text.formatNumber(d.coins()) + " Coins")
                .lore("<dark_gray>↪</dark_gray> <gray>Coins gibt es bei der täglichen Belohnung & Lotterie").build());
        set(50, new ItemBuilder(Material.RED_DYE).name("<dark_gray>»</dark_gray> <red>Alle Cosmetics ablegen").build(), e -> {
            click();
            cm.removeAll(player);
            plugin.configs().send(player, "cosmetic_removed");
            refresh();
        });
        border(Material.BLACK_STAINED_GLASS_PANE);
    }

    private void clickCosmetic(Cosmetic c) {
        CosmeticManager cm = plugin.cosmetics();
        PlayerData d = plugin.data().get(player);
        if (!cm.owns(player, c)) {
            if (d.coins() < c.price()) {
                deny();
                plugin.configs().send(player, "cosmetic_no_coins", "missing", Text.formatNumber(c.price() - d.coins()));
                return;
            }
            plugin.coins().change(player.getUniqueId(), -c.price(), "cosmetic_buy");
            d.addOwned(c.fullId());
            plugin.data().save(d);
            success();
            plugin.configs().send(player, "cosmetic_bought", "cosmetic", c.name(), "price", Text.formatNumber(c.price()));
        } else {
            click();
        }
        cm.select(player, c);
        if (cm.selected(player, category) != null) plugin.configs().send(player, "cosmetic_selected", "cosmetic", c.name());
        refresh();
    }
}
