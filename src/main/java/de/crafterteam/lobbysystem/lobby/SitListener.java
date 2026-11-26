package de.crafterteam.lobbysystem.lobby;

import de.crafterteam.lobbysystem.LobbySystem;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;
import org.bukkit.event.entity.EntityDismountEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Rechtsklick auf Treppen/Stufen → hinsetzen (unsichtbarer Sitz, wird beim Aufstehen entfernt). */
public final class SitListener implements Listener {

    private final LobbySystem plugin;
    private final Map<UUID, Entity> seats = new HashMap<>();
    private final Map<Block, UUID> occupied = new HashMap<>();

    public SitListener(LobbySystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Player p = event.getPlayer();
        if (!plugin.configs().cfg().sit.enabled || !plugin.configs().has(p, "sit")) return;
        if (p.isSneaking() || p.isInsideVehicle() || plugin.lobby().isBuilding(p) || p.isFlying()) return;
        Block b = event.getClickedBlock();
        if (b == null || occupied.containsKey(b)) return;
        if (!isSeatBlock(b) || p.getLocation().distanceSquared(b.getLocation().add(0.5, 0.5, 0.5)) > 9) return;
        if (!b.getRelative(0, 1, 0).isPassable()) return;
        event.setCancelled(true);

        Location loc = b.getLocation().add(0.5, plugin.configs().cfg().sit.yOffset, 0.5);
        if (b.getBlockData() instanceof Stairs st) {
            // Blickrichtung weg von der Lehne
            Vector dir = st.getFacing().getOppositeFace().getDirection();
            loc.setDirection(dir);
            loc.add(dir.clone().multiply(0.1));
        } else {
            loc.setYaw(p.getLocation().getYaw());
        }
        ItemDisplay seatEntity = b.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setInvulnerable(true);
        });
        if (!seatEntity.addPassenger(p)) {
            seatEntity.remove();
            return;
        }
        seats.put(p.getUniqueId(), seatEntity);
        occupied.put(b, p.getUniqueId());
    }

    public static boolean isSeatBlock(Block b) {
        return (b.getBlockData() instanceof Stairs st && st.getHalf() == Bisected.Half.BOTTOM)
                || (b.getBlockData() instanceof Slab sl && sl.getType() == Slab.Type.BOTTOM);
    }

    private void stand(Player p) {
        Entity seat = seats.remove(p.getUniqueId());
        occupied.values().removeIf(id -> id.equals(p.getUniqueId()));
        if (seat == null) return;
        seat.eject();
        seat.remove();
        Location up = p.getLocation().add(0, 0.6, 0);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) p.teleport(up, PlayerTeleportEvent.TeleportCause.PLUGIN);
        });
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player p && seats.containsKey(p.getUniqueId())) stand(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stand(event.getPlayer());
    }

    public void shutdown() {
        seats.values().forEach(e -> {
            e.eject();
            e.remove();
        });
        seats.clear();
        occupied.clear();
    }
}
