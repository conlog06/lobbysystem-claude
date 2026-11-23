package de.crafterteam.lobbysystem.cosmetics;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.ConfigManager;
import de.crafterteam.lobbysystem.config.PluginConfig;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import de.crafterteam.lobbysystem.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class CosmeticManager implements Listener {

    private final LobbySystem plugin;
    private final Map<Cosmetic.Category, List<Cosmetic>> cosmetics = new EnumMap<>(Cosmetic.Category.class);
    private final Map<UUID, BlockDisplay> balloons = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private long ticks;

    public CosmeticManager(LobbySystem plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        PluginConfig.Cosmetics c = plugin.configs().cfg().cosmetics;
        cosmetics.clear();
        load(Cosmetic.Category.HEAD, c.heads);
        load(Cosmetic.Category.GADGET, c.gadgets);
        load(Cosmetic.Category.BALLOON, c.balloons);
        load(Cosmetic.Category.PARTICLE, c.particles);
    }

    private void load(Cosmetic.Category cat, List<String> lines) {
        List<Cosmetic> list = new ArrayList<>();
        for (String line : lines) {
            Cosmetic cos = Cosmetic.parse(cat, line);
            if (cos != null) list.add(cos);
            else plugin.getLogger().warning("Ungültiges Cosmetic in config.json: " + line);
        }
        cosmetics.put(cat, list);
    }

    public List<Cosmetic> list(Cosmetic.Category cat) {
        return cosmetics.getOrDefault(cat, List.of());
    }

    public Cosmetic find(Cosmetic.Category cat, String id) {
        if (id == null) return null;
        for (Cosmetic c : list(cat)) if (c.id().equals(id)) return c;
        return null;
    }

    public Cosmetic selected(Player player, Cosmetic.Category cat) {
        return find(cat, plugin.data().get(player).selected(cat.key()));
    }

    public boolean owns(Player player, Cosmetic c) {
        return c.price() <= 0 || plugin.data().get(player).owns(c.fullId()) || plugin.configs().hasCosmetic(player, c.fullId());
    }

    // ---------------------------------------------------------------- Auswahl

    public void select(Player player, Cosmetic c) {
        PlayerData data = plugin.data().get(player);
        if (c.id().equals(data.selected(c.category().key()))) {
            data.select(c.category().key(), null);
        } else {
            data.select(c.category().key(), c.id());
        }
        refresh(player, c.category());
    }

    public void removeAll(Player player) {
        clear(player);
        plugin.data().get(player).clearSelected();
        plugin.lobby().giveHotbar(player);
    }

    private void refresh(Player player, Cosmetic.Category cat) {
        switch (cat) {
            case HEAD -> applyHead(player);
            case BALLOON -> {
                removeBalloon(player);
                spawnBalloon(player);
            }
            case GADGET -> plugin.lobby().giveHotbar(player);
            default -> {
            }
        }
    }

    public void apply(Player player) {
        if (plugin.lobby().isBuilding(player)) return;
        applyHead(player);
        removeBalloon(player);
        spawnBalloon(player);
    }

    public void clear(Player player) {
        removeBalloon(player);
        ItemStack helmet = player.getInventory().getHelmet();
        if ("cosmetic_head".equals(Keys.itemTag(helmet))) player.getInventory().setHelmet(null);
    }

    public void quit(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    public void shutdown() {
        balloons.values().forEach(Entity::remove);
        balloons.clear();
    }

    // ---------------------------------------------------------------- Heads

    public void applyHead(Player player) {
        Cosmetic head = selected(player, Cosmetic.Category.HEAD);
        if (head == null || !owns(player, head)) {
            if ("cosmetic_head".equals(Keys.itemTag(player.getInventory().getHelmet()))) player.getInventory().setHelmet(null);
            return;
        }
        player.getInventory().setHelmet(headItem(head).tag("cosmetic_head").build());
    }

    public ItemBuilder headItem(Cosmetic head) {
        ItemBuilder b = new ItemBuilder(head.icon()).name(head.name());
        if (head.icon() == Material.PLAYER_HEAD && !head.data().isBlank()) b.skull(head.data());
        return b;
    }

    // ---------------------------------------------------------------- Ballons

    private void spawnBalloon(Player player) {
        Cosmetic c = selected(player, Cosmetic.Category.BALLOON);
        if (c == null || !owns(player, c) || plugin.lobby().isBuilding(player)) return;
        Material block = ItemBuilder.material(c.data(), c.icon());
        if (!block.isBlock()) block = Material.RED_TERRACOTTA;
        Material finalBlock = block;
        BlockDisplay display = player.getWorld().spawn(balloonTarget(player), BlockDisplay.class, d -> {
            d.setBlock(finalBlock.createBlockData());
            d.setTransformation(new Transformation(new Vector3f(-0.3f, 0f, -0.3f), new AxisAngle4f(),
                    new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
            d.setTeleportDuration(3);
            d.setPersistent(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.getPersistentDataContainer().set(Keys.GADGET, PersistentDataType.STRING, "balloon");
        });
        balloons.put(player.getUniqueId(), display);
        updateBalloonVisibility();
    }

    private void removeBalloon(Player player) {
        BlockDisplay d = balloons.remove(player.getUniqueId());
        if (d != null) d.remove();
    }

    private Location balloonTarget(Player p) {
        Location l = p.getLocation();
        Vector dir = l.getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) dir = new Vector(0, 0, 1);
        dir.normalize();
        Vector right = new Vector(-dir.getZ(), 0, dir.getX()).multiply(0.9);
        double bob = Math.sin((ticks + p.getEntityId()) / 12.0) * 0.15;
        return l.clone().add(right).add(dir.clone().multiply(-0.5)).add(0, 2.6 + bob, 0);
    }

    public void updateBalloonVisibility() {
        for (Map.Entry<UUID, BlockDisplay> e : balloons.entrySet()) {
            Player owner = Bukkit.getPlayer(e.getKey());
            if (owner == null) continue;
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (plugin.lobby().canSee(viewer, owner)) viewer.showEntity(plugin, e.getValue());
                else viewer.hideEntity(plugin, e.getValue());
            }
        }
    }

    // ---------------------------------------------------------------- Tick (jeden Tick)

    public void tick() {
        ticks++;
        for (Map.Entry<UUID, BlockDisplay> e : new ArrayList<>(balloons.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            BlockDisplay d = e.getValue();
            if (p == null || !d.isValid()) {
                balloons.remove(e.getKey());
                if (d.isValid()) d.remove();
                if (p != null) spawnBalloon(p);
                continue;
            }
            Location target = balloonTarget(p);
            if (!d.getWorld().equals(p.getWorld())) {
                removeBalloon(p);
                spawnBalloon(p);
                continue;
            }
            d.teleport(target);
            if (ticks % 2 == 0) drawString(p, target);
        }
        if (ticks % 3 == 0) {
            for (Player p : Bukkit.getOnlinePlayers()) particle(p);
        }
    }

    private void drawString(Player p, Location balloon) {
        Location hand = p.getLocation().add(0, 1.0, 0);
        Vector diff = balloon.toVector().subtract(hand.toVector());
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(235, 235, 235), 0.35f);
        int points = 7;
        for (int i = 1; i < points; i++) {
            Location pt = hand.clone().add(diff.clone().multiply(i / (double) points));
            for (Player viewer : p.getWorld().getPlayers()) {
                if (viewer.getLocation().distanceSquared(pt) < 900 && plugin.lobby().canSee(viewer, p)) {
                    viewer.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, dust);
                }
            }
        }
    }

    private void particle(Player p) {
        if (plugin.lobby().isBuilding(p)) return;
        Cosmetic c = selected(p, Cosmetic.Category.PARTICLE);
        if (c == null || !owns(p, c)) return;
        String data = c.data().isBlank() ? "HEART" : c.data();
        Particle particle;
        Object extra = null;
        try {
            if (data.toUpperCase().startsWith("DUST")) {
                particle = Particle.DUST;
                Color color = Color.RED;
                int idx = data.indexOf('#');
                if (idx >= 0) color = Color.fromRGB(Integer.parseInt(data.substring(idx + 1, Math.min(data.length(), idx + 7)), 16));
                extra = new Particle.DustOptions(color, 1.1f);
            } else {
                particle = Particle.valueOf(data.toUpperCase());
                if (particle.getDataType() != Void.class) return; // Partikel mit Pflichtdaten werden nicht unterstützt
            }
        } catch (Exception e) {
            return;
        }
        double angle = (ticks / 3.0) * 0.45;
        Location base = p.getLocation();
        boolean moving = p.getVelocity().clone().setY(0).lengthSquared() > 0.003;
        List<Location> points = new ArrayList<>();
        if (moving) {
            points.add(base.clone().add(0, 0.15, 0));
        } else {
            for (int i = 0; i < 2; i++) {
                double a = angle + i * Math.PI;
                points.add(base.clone().add(Math.cos(a) * 0.7, 0.2 + (Math.sin(angle * 0.5) + 1) * 0.9, Math.sin(a) * 0.7));
            }
        }
        for (Player viewer : p.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(base) > 2304 || !plugin.lobby().canSee(viewer, p)) continue;
            for (Location pt : points) {
                if (extra != null) viewer.spawnParticle(particle, pt, 1, 0, 0, 0, 0, extra);
                else viewer.spawnParticle(particle, pt, 1, 0.05, 0.05, 0.05, 0);
            }
        }
    }

    // ---------------------------------------------------------------- Gadgets

    public ItemStack gadgetItem(Player player) {
        Cosmetic g = selected(player, Cosmetic.Category.GADGET);
        if (g == null || !owns(player, g)) return null;
        return new ItemBuilder(g.icon()).name(g.name())
                .lore("<dark_gray>»</dark_gray> <gray>Rechtsklick zum Benutzen", "<dark_gray>»</dark_gray> <gray>Cooldown: <white>" + cooldownSeconds(g) + "s")
                .tag("gadget:" + g.id()).hideFlags().build();
    }

    private int cooldownSeconds(Cosmetic g) {
        try {
            return Math.max(0, Integer.parseInt(g.data()));
        } catch (NumberFormatException e) {
            return 3;
        }
    }

    private boolean checkCooldown(Player p, Cosmetic g) {
        Map<String, Long> map = cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        long now = System.currentTimeMillis();
        long until = map.getOrDefault(g.id(), 0L);
        if (until > now) {
            long secs = (until - now + 999) / 1000;
            p.sendActionBar(plugin.configs().msg("gadget_cooldown", "seconds", Long.toString(secs)));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
            return false;
        }
        map.put(g.id(), now + cooldownSeconds(g) * 1000L);
        return true;
    }

    public void useGadget(Player p, String id, PlayerInteractEvent event) {
        Cosmetic g = find(Cosmetic.Category.GADGET, id);
        if (g == null || !owns(p, g)) {
            event.setCancelled(true);
            return;
        }
        if (id.equals("grappling")) return; // Angel normal auswerfen, Zug passiert im FishEvent
        event.setCancelled(true);
        if (!checkCooldown(p, g)) return;
        switch (id) {
            case "enderpearl" -> {
                EnderPearl pearl = p.launchProjectile(EnderPearl.class);
                pearl.getPersistentDataContainer().set(Keys.GADGET, PersistentDataType.STRING, "enderpearl");
                pearl.addPassenger(p);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 1f, 1f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (pearl.isValid()) {
                        pearl.eject();
                        pearl.remove();
                    }
                }, 200L);
            }
            case "firework" -> plugin.lobby().spawnFirework(p.getLocation(), false);
            case "windcharge" -> {
                WindCharge charge = p.launchProjectile(WindCharge.class);
                charge.getPersistentDataContainer().set(Keys.GADGET, PersistentDataType.STRING, "windcharge");
                p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1f);
            }
            case "thor" -> {
                Block target = p.getTargetBlockExact(40);
                Location loc = target != null ? target.getLocation().add(0.5, 1, 0.5) : p.getLocation().add(p.getLocation().getDirection().multiply(15));
                loc.getWorld().strikeLightningEffect(loc);
                loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 40, 0.6, 0.6, 0.6, 0.2);
            }
            case "eye" -> {
                p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.8f);
                p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 120, 1, 1, 1, 0.6);
                for (Player other : p.getWorld().getPlayers()) {
                    if (!other.equals(p) && other.getLocation().distanceSquared(p.getLocation()) < 225 && p.canSee(other)) {
                        other.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false, true));
                    }
                }
            }
            case "pumpkinbomb" -> throwPumpkin(p);
            default -> {
            }
        }
    }

    private void throwPumpkin(Player p) {
        Item item = p.getWorld().dropItem(p.getEyeLocation(), new ItemStack(Material.JACK_O_LANTERN), it -> {
            it.setCanPlayerPickup(false);
            it.setCanMobPickup(false);
            it.setPickupDelay(Integer.MAX_VALUE);
            it.setPersistent(false);
            it.getPersistentDataContainer().set(Keys.GADGET, PersistentDataType.STRING, "pumpkinbomb");
            it.setVelocity(p.getLocation().getDirection().multiply(1.1).add(new Vector(0, 0.25, 0)));
        });
        p.playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 0.7f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!item.isValid()) return;
            Location loc = item.getLocation();
            item.remove();
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 3, 0.5, 0.5, 0.5, 0);
            loc.getWorld().spawnParticle(Particle.FLAME, loc, 40, 0.5, 0.5, 0.5, 0.15);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.3f);
            for (Player near : loc.getWorld().getPlayers()) {
                double dist = near.getLocation().distance(loc);
                if (dist < 5 && !plugin.lobby().isBuilding(near)) {
                    Vector push = near.getLocation().toVector().subtract(loc.toVector());
                    if (push.lengthSquared() < 1.0E-4) push = new Vector(0, 1, 0);
                    near.setVelocity(push.normalize().multiply(1.1 - dist * 0.15).setY(0.7));
                }
            }
        }, 30L);
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        Player p = event.getPlayer();
        String tag = Keys.itemTag(p.getInventory().getItemInMainHand());
        if (!"gadget:grappling".equals(tag)) return;
        switch (event.getState()) {
            case IN_GROUND, REEL_IN, CAUGHT_ENTITY, FAILED_ATTEMPT -> {
                Cosmetic g = find(Cosmetic.Category.GADGET, "grappling");
                Location hook = event.getHook().getLocation();
                Vector diff = hook.toVector().subtract(p.getLocation().toVector());
                double dist = diff.length();
                if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) event.setCancelled(true);
                if (dist < 2 || g == null) return;
                if (event.getState() == PlayerFishEvent.State.REEL_IN && !event.getHook().isOnGround()
                        && hook.clone().subtract(0, 0.3, 0).getBlock().getType().isAir()) return;
                if (!checkCooldown(p, g)) return;
                Vector v = diff.normalize().multiply(Math.min(2.6, 0.45 + dist * 0.12));
                v.setY(Math.max(0.45, v.getY() + 0.35));
                p.setVelocity(v);
                p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.8f);
            }
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) event.setCancelled(true);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        String tag = event.getEntity().getPersistentDataContainer().get(Keys.GADGET, PersistentDataType.STRING);
        if ("enderpearl".equals(tag)) {
            event.getEntity().eject();
            event.getEntity().getWorld().spawnParticle(Particle.REVERSE_PORTAL, event.getEntity().getLocation(), 40, 0.3, 0.3, 0.3, 0.05);
        }
    }

    /** Spieler für Lotterie-Effekte usw. */
    public static double random() {
        return ThreadLocalRandom.current().nextDouble();
    }

    public ConfigManager configs() {
        return plugin.configs();
    }
}
