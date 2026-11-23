package de.crafterteam.lobbysystem.npc;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.gui.DailyRewardMenu;
import de.crafterteam.lobbysystem.gui.SettingsMenu;
import de.crafterteam.lobbysystem.util.Keys;
import de.crafterteam.lobbysystem.util.SLoc;
import de.crafterteam.lobbysystem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Witch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.Map;

/**
 * NPCs (Daily-Reward-Hexe, Einstellungs-Dorfbewohner) mit modernem TextDisplay-Hologramm.
 * Die Entities sind nicht persistent und werden automatisch neu gespawnt.
 */
public final class NpcManager implements Listener {

    public enum Type { REWARD, SETTINGS }

    private final LobbySystem plugin;
    private final Map<Type, LivingEntity> npcs = new EnumMap<>(Type.class);
    private final Map<Type, TextDisplay> holos = new EnumMap<>(Type.class);

    public NpcManager(LobbySystem plugin) {
        this.plugin = plugin;
    }

    private SLoc location(Type type) {
        return type == Type.REWARD ? plugin.configs().locations().reward : plugin.configs().locations().settings;
    }

    public void set(Type type, Location loc) {
        SLoc s = loc == null ? null : SLoc.of(loc);
        if (type == Type.REWARD) plugin.configs().locations().reward = s;
        else plugin.configs().locations().settings = s;
        plugin.configs().saveLocations();
        despawn(type);
        spawn(type);
    }

    /** Alle 2 Sekunden: fehlende NPCs (z. B. nach Chunk-Unload) neu spawnen. */
    public void tick() {
        for (Type type : Type.values()) {
            LivingEntity e = npcs.get(type);
            if (e != null && e.isValid()) {
                // NPC an seinem Platz halten
                Location target = location(type) == null ? null : location(type).toLocation();
                if (target != null && e.getLocation().distanceSquared(target) > 0.25) e.teleport(target);
                continue;
            }
            spawn(type);
        }
    }

    private void spawn(Type type) {
        SLoc s = location(type);
        Location loc = s == null ? null : s.toLocation();
        if (loc == null) return;
        World world = loc.getWorld();
        if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;
        despawn(type);
        LivingEntity entity;
        if (type == Type.REWARD) {
            entity = world.spawn(loc, Witch.class, w -> prepare(w, type));
        } else {
            entity = world.spawn(loc, Villager.class, v -> {
                prepare(v, type);
                v.setProfession(Villager.Profession.LIBRARIAN);
                v.setVillagerLevel(5);
            });
        }
        npcs.put(type, entity);
        String text = type == Type.REWARD ? plugin.configs().cfg().dailyReward.hologram
                : "<gradient:#00d2ff:#3a7bd5><b>ᴇɪɴꜱᴛᴇʟʟᴜɴɢᴇɴ</b></gradient>\n<gray>Rechtsklick zum Öffnen";
        TextDisplay holo = world.spawn(loc.clone().add(0, entity.getHeight() + 0.45, 0), TextDisplay.class, t -> {
            t.text(Text.parse(text));
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
            t.setShadowed(true);
            t.setPersistent(false);
            t.getPersistentDataContainer().set(Keys.NPC, PersistentDataType.STRING, type.name());
        });
        holos.put(type, holo);
    }

    private void prepare(LivingEntity e, Type type) {
        e.setAI(false);
        e.setSilent(true);
        e.setInvulnerable(true);
        e.setCollidable(false);
        e.setRemoveWhenFarAway(false);
        e.setPersistent(false);
        e.setGravity(false);
        e.customName(Text.parse(type == Type.REWARD ? "<light_purple>Tägliche Belohnung" : "<aqua>Einstellungen"));
        e.setCustomNameVisible(false);
        e.getPersistentDataContainer().set(Keys.NPC, PersistentDataType.STRING, type.name());
    }

    public void despawn(Type type) {
        LivingEntity e = npcs.remove(type);
        if (e != null) e.remove();
        TextDisplay t = holos.remove(type);
        if (t != null) t.remove();
    }

    public void despawnAll() {
        for (Type t : Type.values()) despawn(t);
    }

    /** Alte NPCs (z. B. nach Absturz) beim Laden entfernen. */
    public void cleanup() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getPersistentDataContainer().has(Keys.NPC, PersistentDataType.STRING) && !isTracked(e)) e.remove();
            }
        }
    }

    private boolean isTracked(Entity e) {
        return npcs.containsValue(e) || holos.containsValue(e);
    }

    private Type typeOf(Entity e) {
        String s = e.getPersistentDataContainer().get(Keys.NPC, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return Type.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void open(Player player, Type type) {
        if (type == Type.REWARD) new DailyRewardMenu(plugin, player).open();
        else new SettingsMenu(plugin, player).open();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        Type type = typeOf(event.getRightClicked());
        if (type == null) return;
        event.setCancelled(true);
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        open(event.getPlayer(), type);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (typeOf(event.getRightClicked()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        Type type = typeOf(event.getEntity());
        if (type == null) return;
        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player p) open(p, type);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (typeOf(e) != null && !isTracked(e)) e.remove();
        }
    }
}
