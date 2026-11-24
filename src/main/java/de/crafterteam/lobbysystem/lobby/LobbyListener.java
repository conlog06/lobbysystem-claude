package de.crafterteam.lobbysystem.lobby;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.ConfigManager;
import de.crafterteam.lobbysystem.config.PluginConfig;
import de.crafterteam.lobbysystem.gui.CosmeticsMenu;
import de.crafterteam.lobbysystem.gui.LotteryMenu;
import de.crafterteam.lobbysystem.gui.NavigatorMenu;
import de.crafterteam.lobbysystem.gui.ProfileMenu;
import de.crafterteam.lobbysystem.gui.VisibilityMenu;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.util.Keys;
import de.crafterteam.lobbysystem.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LobbyListener implements Listener {

    private final LobbySystem plugin;
    private final Map<UUID, Long> jumpCooldown = new HashMap<>();

    public LobbyListener(LobbySystem plugin) {
        this.plugin = plugin;
    }

    private ConfigManager c() {
        return plugin.configs();
    }

    private PluginConfig.Protection prot() {
        return c().cfg().protection;
    }

    private boolean bypass(Player p) {
        return plugin.lobby().isBuilding(p);
    }

    // ---------------------------------------------------------------- Join / Quit

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        plugin.data().preload(event.getUniqueId(), event.getName());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.data().get(player);
        if (!data.name().equals(player.getName())) data.name(player.getName());
        String jm = c().cfg().join.joinMessage;
        event.joinMessage(jm == null || jm.isBlank() ? null : Text.parse(jm, ConfigManager.map("name", player.getName())));

        plugin.initPlayer(player, true);
        plugin.lobby().welcome(player);
        plugin.pack().send(player);
        plugin.data().presence(player.getUniqueId(), c().cfg().proxyServerName);
        plugin.clans().refresh(player.getUniqueId());
        // kurz warten, damit der Online-Status in der DB steht
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) plugin.friends().announce(player, true);
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String qm = c().cfg().join.quitMessage;
        event.quitMessage(qm == null || qm.isBlank() ? null : Text.parse(qm, ConfigManager.map("name", player.getName())));
        plugin.cosmetics().clear(player);
        plugin.cosmetics().quit(player);
        plugin.bars().hide(player);
        plugin.sidebar().remove(player);
        plugin.lobby().quit(player);
        jumpCooldown.remove(player.getUniqueId());
        plugin.data().offline(player.getUniqueId(), c().cfg().proxyServerName);
        plugin.friends().announce(player, false);
        plugin.friends().forget(player.getUniqueId());
        plugin.clans().forget(player.getUniqueId());
        plugin.data().unload(player.getUniqueId());
    }

    // ---------------------------------------------------------------- Hotbar-Items

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (bypass(player)) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            event.setCancelled(true);
            return;
        }
        Action a = event.getAction();
        if (a == Action.PHYSICAL && event.getClickedBlock() != null && jumpPad(player, event.getClickedBlock())) {
            event.setCancelled(true);
            return;
        }
        // Kein Treten auf Farmland, kein Öffnen von Kisten/Türen etc.
        if (a == Action.PHYSICAL || (a == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && event.getClickedBlock().getType().isInteractable())) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
        ItemStack item = event.getItem();
        String tag = Keys.itemTag(item);
        if (tag == null) return;
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            // Linksklick mit Gadget ignorieren
            if (!tag.startsWith("gadget:")) event.setCancelled(true);
            return;
        }
        if (tag.startsWith("gadget:")) {
            plugin.cosmetics().useGadget(player, tag.substring(7), event);
            return;
        }
        event.setCancelled(true);
        switch (tag) {
            case "navigator" -> new NavigatorMenu(plugin, player).open();
            case "visibility" -> new VisibilityMenu(plugin, player).open();
            case "silentlobby" -> toggleSilent(player);
            case "lottery" -> new LotteryMenu(plugin, player).open();
            case "extras" -> new CosmeticsMenu(plugin, player, CosmeticsMenu.lastCategory(player)).open();
            case "profile" -> new ProfileMenu(plugin, player).open();
            case "friends" -> de.crafterteam.lobbysystem.gui.SocialMenu.open(plugin, player, de.crafterteam.lobbysystem.gui.SocialMenu.Tab.FRIENDS);
            case "lobbyswitcher" -> de.crafterteam.lobbysystem.gui.LobbySwitcherMenu.open(plugin, player);
            case "gadget" -> new CosmeticsMenu(plugin, player, de.crafterteam.lobbysystem.cosmetics.Cosmetic.Category.GADGET).open();
            default -> {
            }
        }
    }

    private final Map<UUID, Long> padCooldown = new HashMap<>();

    private boolean jumpPad(Player player, org.bukkit.block.Block plate) {
        PluginConfig.JumpPads jp = c().cfg().jumpPads;
        if (!jp.enabled || !c().has(player, "jumppad")) return false;
        if (!plate.getType().name().equalsIgnoreCase(jp.plate)) return false;
        if (jp.blockBelow != null && !jp.blockBelow.isBlank()
                && !plate.getRelative(org.bukkit.block.BlockFace.DOWN).getType().name().equalsIgnoreCase(jp.blockBelow)) return false;
        long now = System.currentTimeMillis();
        if (padCooldown.getOrDefault(player.getUniqueId(), 0L) > now) return true;
        padCooldown.put(player.getUniqueId(), now + 600);
        Vector v = player.getLocation().getDirection().setY(0);
        if (v.lengthSquared() > 1.0E-4) v.normalize();
        player.setVelocity(v.multiply(jp.forward).setY(jp.upward));
        player.playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 0.9f, 1.2f);
        player.getWorld().spawnParticle(Particle.GUST_EMITTER_SMALL, plate.getLocation().add(0.5, 0.2, 0.5), 1);
        return true;
    }

    private void toggleSilent(Player player) {
        if (!c().has(player, "silentlobby")) {
            c().send(player, "no_permission");
            return;
        }
        PlayerData data = plugin.data().get(player);
        data.silent(!data.silent());
        c().send(player, data.silent() ? "silent_on" : "silent_off");
        player.playSound(player.getLocation(), data.silent() ? Sound.BLOCK_BEACON_DEACTIVATE : Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
        plugin.lobby().updateVisibility();
        plugin.lobby().giveHotbar(player);
    }

    // ---------------------------------------------------------------- Chat

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        PlayerData data = plugin.data().get(sender);
        if (data.silent()) {
            event.viewers().removeIf(v -> v instanceof Player p && !p.equals(sender));
        } else {
            event.viewers().removeIf(v -> v instanceof Player p && !p.equals(sender) && plugin.data().get(p).silent());
        }
        if (!c().cfg().chat.enabled) return;
        String prefix = plugin.sidebar().rank(sender).prefix;
        String format = c().cfg().chat.format;
        event.renderer((source, displayName, message, viewer) -> {
            String before = format.substring(0, Math.max(0, format.indexOf("{message}")));
            String after = format.contains("{message}") ? format.substring(format.indexOf("{message}") + 9) : "";
            Map<String, String> ph = ConfigManager.map("prefix", prefix, "name", source.getName());
            return Text.parse(before, ph).append(message).append(Text.parse(after, ph));
        });
    }

    // ---------------------------------------------------------------- Doppelsprung & Void

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        event.setCancelled(true);
        player.setFlying(false);
        PlayerData data = plugin.data().get(player);
        if (!data.doubleJump() || !c().has(player, "doublejump")) {
            player.setAllowFlight(false);
            return;
        }
        long now = System.currentTimeMillis();
        PluginConfig.DoubleJump dj = c().cfg().doubleJump;
        if (jumpCooldown.getOrDefault(player.getUniqueId(), 0L) > now) return;
        jumpCooldown.put(player.getUniqueId(), now + dj.cooldownTicks * 50L);
        Vector v = player.getLocation().getDirection().setY(0).normalize().multiply(dj.forward).setY(dj.upward);
        player.setVelocity(v);
        player.setAllowFlight(false);
        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.8f, 1.1f);
        player.getWorld().spawnParticle(Particle.GUST, player.getLocation(), 1);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 12, 0.3, 0.05, 0.3, 0.02);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to.getY() < prot().voidY) {
            plugin.lobby().teleportSpawn(player, false);
            return;
        }
        // Doppelsprung wieder freischalten, sobald der Spieler den Boden berührt
        if (!player.getAllowFlight()
                && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR
                && !player.isInsideVehicle() && isOnGround(player)
                && plugin.data().get(player).doubleJump() && c().has(player, "doublejump")) {
            player.setAllowFlight(true);
        }
    }

    private static boolean isOnGround(Player p) {
        return p.getLocation().clone().subtract(0, 0.1, 0).getBlock().getType().isSolid();
    }

    // ---------------------------------------------------------------- Schutz

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && prot().noDamage) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID && event.getEntity() instanceof Player p) {
                plugin.lobby().teleportSpawn(p, false);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (prot().noHunger) {
            event.setCancelled(true);
            event.getEntity().setFoodLevel(20);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (prot().noBlockBreak && !bypass(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (prot().noBlockPlace && !bypass(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (prot().noItemDrop && !bypass(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && prot().noItemPickup && !bypass(p)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!bypass(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p) || bypass(p) || !prot().lockInventory) return;
        if (event.getClickedInventory() == p.getInventory() || event.getClick().isShiftClick()
                || event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (!bypass(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player p && !bypass(p)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (prot().noWeather && event.toWeatherState()) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!prot().noMobSpawning) return;
        switch (event.getSpawnReason()) {
            case NATURAL, JOCKEY, PATROL, RAID, REINFORCEMENTS, VILLAGE_INVASION, TRAP -> event.setCancelled(true);
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeaves(LeavesDecayEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() == org.bukkit.Material.FIRE) event.setCancelled(true);
    }
}
