package de.crafterteam.lobbysystem.cosmetics;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.util.Keys;
import de.crafterteam.lobbysystem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerLeashEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Haustiere: folgen dem Besitzer, sind unverwundbar, still und greifen nichts an.
 * Daten in der Config: ENTITY_TYPE[:baby]  (z. B. FOX:baby, AXOLOTL, ALLAY)
 */
public final class PetManager implements Listener {

    private static final String TAG = "pet";

    private final LobbySystem plugin;
    private final Map<UUID, Mob> pets = new HashMap<>();
    private final Set<UUID> hidden = new HashSet<>();

    public PetManager(LobbySystem plugin) {
        this.plugin = plugin;
    }

    private static boolean isPet(Entity e) {
        return e != null && TAG.equals(e.getPersistentDataContainer().get(Keys.GADGET, PersistentDataType.STRING));
    }

    public void spawn(Player owner, Cosmetic c) {
        remove(owner);
        if (c == null || hidden.contains(owner.getUniqueId()) || plugin.lobby().isBuilding(owner)) return;
        String[] data = c.data().split(":");
        EntityType type;
        try {
            type = EntityType.valueOf(data[0].trim().toUpperCase());
        } catch (Exception e) {
            plugin.getLogger().warning("Unbekanntes Haustier bei Cosmetic " + c.id() + ": " + c.data());
            return;
        }
        if (type.getEntityClass() == null || !Mob.class.isAssignableFrom(type.getEntityClass())) return;
        boolean baby = data.length > 1 && data[1].equalsIgnoreCase("baby");
        Location at = behind(owner);
        @SuppressWarnings("unchecked")
        Class<? extends Mob> cls = (Class<? extends Mob>) type.getEntityClass();
        Mob mob = owner.getWorld().spawn(at, cls, m -> {
            m.getPersistentDataContainer().set(Keys.GADGET, PersistentDataType.STRING, TAG);
            m.setPersistent(false);
            m.setRemoveWhenFarAway(false);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setCanPickupItems(false);
            m.customName(Text.parse(plugin.configs().cfg().cosmetics.petName.replace("{name}", Text.escape(owner.getName())).replace("{pet}", c.name())));
            m.setCustomNameVisible(plugin.configs().cfg().cosmetics.petNameVisible);
            if (m instanceof Ageable a) {
                if (baby) a.setBaby();
                else a.setAdult();
                if (a instanceof org.bukkit.entity.Breedable b) b.setAgeLock(true);
            }
            if (m instanceof Tameable t) {
                t.setTamed(true);
                t.setOwner(owner);
            }
        });
        // Paper: alle KI-Ziele entfernen – das Tier läuft nur noch per Pathfinder hinterher
        try {
            Bukkit.getMobGoals().removeAllGoals(mob);
        } catch (Throwable ignored) {
        }
        pets.put(owner.getUniqueId(), mob);
        updateVisibility(owner);
    }

    private static Location behind(Player p) {
        Location l = p.getLocation();
        return l.clone().add(l.getDirection().setY(0).normalize().multiply(-1.5)).add(0, 0.2, 0);
    }

    public void remove(Player owner) {
        Mob m = pets.remove(owner.getUniqueId());
        if (m != null) m.remove();
    }

    /** z. B. während des Parkours ausblenden */
    public void hide(Player owner, boolean hide, Cosmetic selected) {
        if (hide) {
            hidden.add(owner.getUniqueId());
            remove(owner);
        } else {
            hidden.remove(owner.getUniqueId());
            if (selected != null) spawn(owner, selected);
        }
    }

    public void quit(Player owner) {
        hidden.remove(owner.getUniqueId());
        remove(owner);
    }

    public void shutdown() {
        pets.values().forEach(Entity::remove);
        pets.clear();
    }

    public void updateVisibility(Player owner) {
        Mob m = pets.get(owner.getUniqueId());
        if (m == null) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (plugin.lobby().canSee(viewer, owner)) viewer.showEntity(plugin, m);
            else viewer.hideEntity(plugin, m);
        }
    }

    public void updateAllVisibility() {
        for (UUID id : pets.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) updateVisibility(p);
        }
    }

    /** Alle 10 Ticks: hinterherlaufen bzw. teleportieren, wenn zu weit weg. */
    public void tick() {
        for (Map.Entry<UUID, Mob> e : new ArrayList<>(pets.entrySet())) {
            Player owner = Bukkit.getPlayer(e.getKey());
            Mob m = e.getValue();
            if (owner == null) {
                m.remove();
                pets.remove(e.getKey());
                continue;
            }
            if (!m.isValid() || !m.getWorld().equals(owner.getWorld())) {
                m.remove();
                pets.remove(e.getKey());
                Cosmetic c = plugin.cosmetics().selected(owner, Cosmetic.Category.PET);
                if (c != null && plugin.cosmetics().owns(owner, c)) spawn(owner, c);
                continue;
            }
            double d = m.getLocation().distanceSquared(owner.getLocation());
            if (d > 144 || owner.isFlying() && d > 36) {
                m.teleport(behind(owner));
            } else if (d > 6.25) {
                m.getPathfinder().moveTo(owner.getLocation(), d > 36 ? 1.6 : 1.2);
            } else {
                m.getPathfinder().stopPathfinding();
                m.lookAt(owner);
            }
        }
    }

    // ---------------------------------------------------------------- Schutz

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (isPet(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (isPet(e.getRightClicked())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAt(PlayerInteractAtEntityEvent e) {
        if (isPet(e.getRightClicked())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeash(PlayerLeashEntityEvent e) {
        if (isPet(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTarget(EntityTargetEvent e) {
        if (isPet(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCombust(EntityCombustEvent e) {
        if (isPet(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTransform(EntityTransformEvent e) {
        if (isPet(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent e) {
        if (isPet(e.getEntity())) e.setCancelled(true);
    }
}
