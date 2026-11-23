package de.crafterteam.lobbysystem.gui;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import de.crafterteam.lobbysystem.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Basis für alle GUIs – Klicks werden pro Slot registriert. */
public abstract class Menu implements InventoryHolder {

    protected final LobbySystem plugin;
    protected final Player player;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    private Inventory inventory;

    protected Menu(LobbySystem plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    protected void create(int rows, String title) {
        inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, Text.parse(title));
    }

    protected void create(InventoryType type, String title) {
        inventory = Bukkit.createInventory(this, type, Text.parse(title));
    }

    protected void create(int rows, Component title) {
        inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, title);
    }

    /** Baut den Inhalt (wird bei open() und refresh() aufgerufen). */
    protected abstract void build();

    public void open() {
        build();
        player.openInventory(inventory);
    }

    public void refresh() {
        actions.clear();
        inventory.clear();
        build();
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
    }

    /** Modernes, dezentes Rahmen-Design. */
    protected void fill(Material material) {
        ItemStack pane = new ItemBuilder(material).name(" ").edit(m -> m.setHideTooltip(true)).build();
        for (int i = 0; i < inventory.getSize(); i++) if (inventory.getItem(i) == null) inventory.setItem(i, pane);
    }

    protected void border(Material material) {
        ItemStack pane = new ItemBuilder(material).name(" ").edit(m -> m.setHideTooltip(true)).build();
        int size = inventory.getSize();
        for (int i = 0; i < size; i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == size / 9 - 1 || col == 0 || col == 8) {
                if (inventory.getItem(i) == null) inventory.setItem(i, pane);
            }
        }
    }

    public void handle(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) action.accept(event);
    }

    public void onClose() {
    }

    protected void click() {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    protected void deny() {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
    }

    protected void success() {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
