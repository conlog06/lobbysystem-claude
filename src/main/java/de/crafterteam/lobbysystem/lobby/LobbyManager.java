package de.crafterteam.lobbysystem.lobby;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.ConfigManager;
import de.crafterteam.lobbysystem.config.PluginConfig;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import de.crafterteam.lobbysystem.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LobbyManager {

    public static final String BUNGEE_CHANNEL = "BungeeCord";

    private final LobbySystem plugin;
    private final Set<UUID> building = new HashSet<>();

    public LobbyManager(LobbySystem plugin) {
        this.plugin = plugin;
    }

    private ConfigManager c() {
        return plugin.configs();
    }

    // ---------------------------------------------------------------- Join / Setup

    public void setup(Player player, boolean teleport) {
        PlayerData data = plugin.data().get(player);
        player.setGameMode(gameMode());
        player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                ? player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() : 20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExp(0f);
        player.setLevel(0);
        player.setFireTicks(0);
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
        int speed = c().cfg().speed.level;
        if (speed > 0) player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SPEED, org.bukkit.potion.PotionEffect.INFINITE_DURATION, speed - 1, false, false, false));
        if (teleport && c().cfg().join.teleportToSpawn) teleportSpawn(player, false);
        giveHotbar(player);
        applyDoubleJump(player, data);
        plugin.cosmetics().apply(player);
    }

    public GameMode gameMode() {
        try {
            return GameMode.valueOf(c().cfg().protection.gameMode.toUpperCase());
        } catch (IllegalArgumentException e) {
            return GameMode.ADVENTURE;
        }
    }

    public void welcome(Player player) {
        PluginConfig.Join j = c().cfg().join;
        Map<String, String> ph = ConfigManager.map("name", player.getName(), "server", c().cfg().serverName);
        if (!j.title.isBlank() || !j.subtitle.isBlank()) {
            player.showTitle(Title.title(Text.parse(j.title, ph), Text.parse(j.subtitle, ph),
                    Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(2200), Duration.ofMillis(600))));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        if (j.firework) spawnFirework(player.getLocation(), true);
    }

    // ---------------------------------------------------------------- Hotbar

    public void giveHotbar(Player player) {
        if (isBuilding(player)) return;
        PlayerData data = plugin.data().get(player);
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setArmorContents(null);
        Map<String, String> ph = ConfigManager.map(
                "visibility", visibilityLabel(data.visibility()),
                "silent", data.silent() ? c().raw("state_on") : c().raw("state_off"),
                "lose", Integer.toString(data.lose()),
                "coins", Text.formatNumber(data.coins()));
        for (Map.Entry<String, PluginConfig.HotbarItem> e : c().cfg().hotbar.entrySet()) {
            PluginConfig.HotbarItem h = e.getValue();
            if (!h.enabled || h.slot < 0 || h.slot > 8) continue;
            String id = e.getKey();
            if (id.equals("silentlobby") && !c().has(player, "silentlobby")) continue;
            if (id.equals("gadget")) {
                ItemStack gadget = plugin.cosmetics().gadgetItem(player);
                if (gadget != null) {
                    inv.setItem(h.slot, gadget);
                    continue;
                }
            }
            ItemBuilder b = new ItemBuilder(ItemBuilder.material(h.material, Material.STONE))
                    .name(h.name, ph).lore(h.lore, ph).tag(id).hideFlags();
            if (id.equals("profile")) b.skull(player);
            if (id.equals("friends") && !c().cfg().social.friends) continue;
            if (id.equals("silentlobby") && data.silent()) b.glow(true);
            inv.setItem(h.slot, b.build());
        }
        plugin.cosmetics().applyHead(player);
        inv.setHeldItemSlot(0);
    }

    public String visibilityLabel(PlayerData.Visibility v) {
        return switch (v) {
            case ALL -> "<green>Alle Spieler";
            case VIP -> "<gold>Nur VIPs";
            case NONE -> "<red>Niemand";
        };
    }

    // ---------------------------------------------------------------- Baumodus

    public boolean isBuilding(Player player) {
        return building.contains(player.getUniqueId());
    }

    public boolean toggleBuild(Player player) {
        if (building.remove(player.getUniqueId())) {
            player.setGameMode(gameMode());
            giveHotbar(player);
            applyDoubleJump(player, plugin.data().get(player));
            plugin.cosmetics().apply(player);
            return false;
        }
        building.add(player.getUniqueId());
        plugin.cosmetics().clear(player);
        player.getInventory().clear();
        player.setGameMode(GameMode.CREATIVE);
        return true;
    }

    public void quit(Player player) {
        building.remove(player.getUniqueId());
    }

    // ---------------------------------------------------------------- Doppelsprung

    public void applyDoubleJump(Player player, PlayerData data) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        boolean allowed = data.doubleJump() && c().has(player, "doublejump");
        player.setAllowFlight(allowed);
        player.setFlying(false);
    }

    // ---------------------------------------------------------------- Sichtbarkeit

    public boolean canSee(Player viewer, Player target) {
        if (viewer.equals(target)) return true;
        PlayerData v = plugin.data().get(viewer);
        PlayerData t = plugin.data().get(target);
        if (v.silent() || t.silent()) return false;
        return switch (v.visibility()) {
            case ALL -> true;
            case VIP -> c().has(target, "visibility_vip");
            case NONE -> false;
        };
    }

    public void updateVisibility() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(target)) continue;
                if (canSee(viewer, target)) viewer.showPlayer(plugin, target);
                else viewer.hidePlayer(plugin, target);
            }
        }
        plugin.cosmetics().updateBalloonVisibility();
    }

    // ---------------------------------------------------------------- Teleport & Verbindung

    public void teleportSpawn(Player player, boolean effects) {
        Location spawn = c().locations().spawn == null ? null : c().locations().spawn.toLocation();
        if (spawn == null) {
            if (effects) c().send(player, "spawn_not_set");
            return;
        }
        teleport(player, spawn, effects);
    }

    public void teleport(Player player, Location target, boolean effects) {
        boolean fx = effects && plugin.data().get(player).tpEffects();
        if (fx) {
            player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.4);
        }
        player.teleportAsync(target).thenAccept(ok -> {
            if (!ok || !fx) return;
            player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, target.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.05);
        });
    }

    public void connect(Player player, String server) {
        connect(player, server, true);
    }

    /** @param partyFollow true = Party-Mitglieder folgen dem Leader */
    public void connect(Player player, String server, boolean partyFollow) {
        c().send(player, "connecting", "server", server);
        if (partyFollow) plugin.parties().onConnect(player, server);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte " + player.getName() + " nicht mit " + server + " verbinden: " + e.getMessage());
        }
    }

    /** Ziel aus dem Navigator: warp:Name | connect:Server | spawn | command:befehl */
    public void runTarget(Player player, String target) {
        if (target == null || target.isBlank() || target.equalsIgnoreCase("none")) return;
        String t = target.trim();
        int idx = t.indexOf(':');
        String type = idx > 0 ? t.substring(0, idx).toLowerCase() : "warp";
        String value = idx > 0 ? t.substring(idx + 1) : t;
        player.closeInventory();
        switch (type) {
            case "connect", "server" -> connect(player, value);
            case "command", "cmd" -> player.performCommand(value.startsWith("/") ? value.substring(1) : value);
            case "spawn" -> teleportSpawn(player, true);
            default -> {
                if (value.equalsIgnoreCase("spawn")) {
                    teleportSpawn(player, true);
                    return;
                }
                var loc = c().warps().get(value.toLowerCase());
                Location l = loc == null ? null : loc.toLocation();
                if (l == null) {
                    c().send(player, "warp_not_found", "warp", value);
                    return;
                }
                teleport(player, l, true);
                c().send(player, "warp_teleported", "warp", value);
            }
        }
    }

    public void spawnFirework(Location loc, boolean detonateQuick) {
        loc.getWorld().spawn(loc, Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();
            java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
            FireworkEffect.Type[] types = FireworkEffect.Type.values();
            meta.addEffect(FireworkEffect.builder()
                    .with(types[r.nextInt(types.length)])
                    .withColor(Color.fromRGB(r.nextInt(0xFFFFFF)), Color.fromRGB(r.nextInt(0xFFFFFF)))
                    .withFade(Color.WHITE).flicker(r.nextBoolean()).trail(true).build());
            meta.setPower(detonateQuick ? 0 : 1);
            fw.setFireworkMeta(meta);
            fw.getPersistentDataContainer().set(de.crafterteam.lobbysystem.util.Keys.GADGET,
                    org.bukkit.persistence.PersistentDataType.STRING, "firework");
        });
    }
}
