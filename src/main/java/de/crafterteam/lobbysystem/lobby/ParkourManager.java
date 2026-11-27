package de.crafterteam.lobbysystem.lobby;

import com.google.gson.reflect.TypeToken;
import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.ConfigManager;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import de.crafterteam.lobbysystem.util.Keys;
import de.crafterteam.lobbysystem.util.SLoc;
import de.crafterteam.lobbysystem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Parkour in der Lobby: Start, Checkpoints & Ziel sind Blöcke (am besten Druckplatten), Zeit in der Actionbar,
 * Bestzeiten in der Datenbank (ls_parkour_times) inkl. Bestenliste. Doppelsprung & Fliegen sind während des Laufs aus.
 */
public final class ParkourManager implements Listener, TabExecutor {

    public static final class Course {
        public String display = "<aqua>Parkour";
        public SLoc start;
        public List<SLoc> checkpoints = new ArrayList<>();
        public SLoc finish;
        /** Coins für den ersten Abschluss */
        public long reward = 250;
        /** Coins für eine neue persönliche Bestzeit */
        public long rewardBest = 25;
        public transient double minY;
    }

    private enum Kind { START, CHECKPOINT, FINISH }

    private record Marker(String course, Kind kind, int index) {
    }

    private static final class Run {
        final String course;
        final long started = System.currentTimeMillis();
        int reached;
        Location respawn;

        Run(String course, Location respawn) {
            this.course = course;
            this.respawn = respawn;
        }

        long time() {
            return System.currentTimeMillis() - started;
        }
    }

    private final LobbySystem plugin;
    private final Path file;
    private Map<String, Course> courses = new LinkedHashMap<>();
    private final Map<String, Marker> markers = new HashMap<>();
    private final Map<UUID, Run> runs = new HashMap<>();
    private final List<TextDisplay> holograms = new ArrayList<>();
    /** eigene Teleports (Checkpoint/Neustart) beenden den Lauf nicht */
    private final java.util.Set<UUID> ownTeleport = new java.util.HashSet<>();

    public ParkourManager(LobbySystem plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("parkour.json");
        load();
    }

    // ---------------------------------------------------------------- Laden & Speichern

    public void load() {
        try {
            if (Files.exists(file)) {
                Map<String, Course> read = ConfigManager.GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                        new TypeToken<LinkedHashMap<String, Course>>() {}.getType());
                if (read != null) courses = read;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "parkour.json konnte nicht gelesen werden", e);
        }
        index();
    }

    private void save() {
        try {
            Files.writeString(file, ConfigManager.GSON.toJson(courses), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "parkour.json konnte nicht gespeichert werden", e);
        }
        index();
    }

    private static String key(Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    private static String key(SLoc s) {
        return s.world + ":" + (int) Math.floor(s.x) + ":" + (int) Math.floor(s.y) + ":" + (int) Math.floor(s.z);
    }

    /** Block-Lookup aufbauen (O(1) beim Laufen statt alle Kurse zu prüfen) + Hologramme. */
    private void index() {
        markers.clear();
        for (Map.Entry<String, Course> e : courses.entrySet()) {
            Course c = e.getValue();
            double min = Double.MAX_VALUE;
            if (c.start != null) {
                markers.put(key(c.start), new Marker(e.getKey(), Kind.START, 0));
                min = Math.min(min, c.start.y);
            }
            for (int i = 0; i < c.checkpoints.size(); i++) {
                markers.put(key(c.checkpoints.get(i)), new Marker(e.getKey(), Kind.CHECKPOINT, i));
                min = Math.min(min, c.checkpoints.get(i).y);
            }
            if (c.finish != null) {
                markers.put(key(c.finish), new Marker(e.getKey(), Kind.FINISH, 0));
                min = Math.min(min, c.finish.y);
            }
            c.minY = min == Double.MAX_VALUE ? -64 : min;
        }
        respawnHolograms();
    }

    private void respawnHolograms() {
        despawn();
        if (!plugin.configs().cfg().parkour.holograms) return;
        for (Map.Entry<String, Course> e : courses.entrySet()) {
            Course c = e.getValue();
            if (c.start != null) hologram(c.start, c.display + "\n<gray>ᴘᴀʀᴋᴏᴜʀ ꜱᴛᴀʀᴛ");
            for (int i = 0; i < c.checkpoints.size(); i++) hologram(c.checkpoints.get(i), "<yellow>Checkpoint <white>#" + (i + 1));
            if (c.finish != null) hologram(c.finish, "<green><b>ZIEL</b>\n" + c.display);
        }
    }

    private void hologram(SLoc s, String text) {
        Location l = s.toLocation();
        if (l == null || !l.isWorldLoaded()) return;
        l = l.getBlock().getLocation().add(0.5, 1.6, 0.5);
        holograms.add(l.getWorld().spawn(l, TextDisplay.class, d -> {
            d.text(Text.parse(text));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.setShadowed(true);
            d.getPersistentDataContainer().set(Keys.GADGET, PersistentDataType.STRING, "parkour");
        }));
    }

    public void despawn() {
        holograms.forEach(org.bukkit.entity.Entity::remove);
        holograms.clear();
    }

    public void shutdown() {
        despawn();
        for (UUID id : new ArrayList<>(runs.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) stop(p, false);
        }
    }

    public Map<String, Course> courses() {
        return courses;
    }

    public boolean running(Player p) {
        return runs.containsKey(p.getUniqueId());
    }

    // ---------------------------------------------------------------- Ablauf

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) return;
        Player p = event.getPlayer();
        Run run = runs.get(p.getUniqueId());
        if (run != null) {
            Course c = courses.get(run.course);
            if (c == null) {
                stop(p, true);
                return;
            }
            if (to.getY() < c.minY - plugin.configs().cfg().parkour.fallDistance) {
                toCheckpoint(p, run);
                return;
            }
            if (run.time() > plugin.configs().cfg().parkour.maxMinutes * 60_000L) {
                send(p, "parkour_timeout");
                stop(p, true);
                return;
            }
        }
        if (markers.isEmpty()) return;
        Marker m = markers.get(key(to));
        if (m == null) return;
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || plugin.lobby().isBuilding(p)) return;
        Course c = courses.get(m.course());
        switch (m.kind()) {
            case START -> start(p, m.course(), c);
            case CHECKPOINT -> {
                if (run == null || !run.course.equals(m.course())) return;
                if (m.index() == run.reached) {
                    run.reached++;
                    run.respawn = p.getLocation().getBlock().getLocation().add(0.5, 0.1, 0.5);
                    run.respawn.setYaw(p.getLocation().getYaw());
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
                    send(p, "parkour_checkpoint", "number", Integer.toString(run.reached), "total", Integer.toString(c.checkpoints.size()),
                            "time", format(run.time()));
                } else if (m.index() > run.reached) {
                    send(p, "parkour_skipped", "number", Integer.toString(run.reached + 1));
                }
            }
            case FINISH -> {
                if (run == null || !run.course.equals(m.course())) return;
                if (run.reached < c.checkpoints.size()) {
                    send(p, "parkour_skipped", "number", Integer.toString(run.reached + 1));
                    return;
                }
                finish(p, run, c);
            }
        }
    }

    private void start(Player p, String id, Course c) {
        Run old = runs.get(p.getUniqueId());
        if (old != null && old.course.equals(id) && old.time() < 1500) return; // nicht bei jedem Schritt auf der Platte neu starten
        if (p.isFlying() || p.isGliding()) {
            send(p, "parkour_no_fly");
            return;
        }
        Location respawn = p.getLocation().getBlock().getLocation().add(0.5, 0.1, 0.5);
        respawn.setYaw(p.getLocation().getYaw());
        runs.put(p.getUniqueId(), new Run(id, respawn));
        p.setAllowFlight(false);
        p.setFlying(false);
        if (old == null) {
            var inv = p.getInventory();
            inv.clear();
            inv.setItem(3, new ItemBuilder(Material.LIGHT_WEIGHTED_PRESSURE_PLATE).name("<yellow>Zum Checkpoint").tag("parkour_cp").build());
            inv.setItem(4, new ItemBuilder(Material.SLIME_BALL).name("<aqua>Neu starten").tag("parkour_restart").build());
            inv.setItem(5, new ItemBuilder(Material.RED_BED).name("<red>Parkour verlassen").tag("parkour_leave").build());
            inv.setHeldItemSlot(4);
            plugin.cosmetics().hidePet(p, true);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        send(p, old == null ? "parkour_start" : "parkour_restart", "course", c.display);
    }

    private void toCheckpoint(Player p, Run run) {
        ownTeleport.add(p.getUniqueId());
        p.setFallDistance(0);
        p.teleport(run.respawn);
        ownTeleport.remove(p.getUniqueId());
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.4f);
    }

    /** Lauf beenden (ohne Wertung). */
    public void stop(Player p, boolean message) {
        if (runs.remove(p.getUniqueId()) == null) return;
        p.sendActionBar(net.kyori.adventure.text.Component.empty());
        if (message) send(p, "parkour_left");
        plugin.lobby().giveHotbar(p);
        plugin.cosmetics().hidePet(p, false);
    }

    private void finish(Player p, Run run, Course c) {
        long time = run.time();
        runs.remove(p.getUniqueId());
        plugin.lobby().giveHotbar(p);
        plugin.cosmetics().hidePet(p, false);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        plugin.lobby().spawnFirework(p.getLocation(), false);
        UUID id = p.getUniqueId();
        String name = p.getName(), course = run.course;
        plugin.database().submit(con -> {
            long best = -1;
            try (PreparedStatement ps = con.prepareStatement("select best_ms from ls_parkour_times where uuid = ? and course = ?")) {
                ps.setString(1, id.toString());
                ps.setString(2, course);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) best = rs.getLong(1);
                }
            }
            long record;
            try (PreparedStatement ps = con.prepareStatement("select min(best_ms) from ls_parkour_times where course = ?")) {
                ps.setString(1, course);
                try (ResultSet rs = ps.executeQuery()) {
                    record = rs.next() ? rs.getLong(1) : 0;
                }
            }
            long now = System.currentTimeMillis();
            if (best < 0) {
                try (PreparedStatement ps = con.prepareStatement("insert into ls_parkour_times (uuid, course, name, best_ms, runs, updated) values (?, ?, ?, ?, 1, ?)")) {
                    ps.setString(1, id.toString());
                    ps.setString(2, course);
                    ps.setString(3, name);
                    ps.setLong(4, time);
                    ps.setLong(5, now);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = con.prepareStatement("update ls_parkour_times set name = ?, runs = runs + 1, updated = ?, "
                        + "best_ms = case when best_ms > ? then ? else best_ms end where uuid = ? and course = ?")) {
                    ps.setString(1, name);
                    ps.setLong(2, now);
                    ps.setLong(3, time);
                    ps.setLong(4, time);
                    ps.setString(5, id.toString());
                    ps.setString(6, course);
                    ps.executeUpdate();
                }
            }
            int rank;
            try (PreparedStatement ps = con.prepareStatement("select count(*) from ls_parkour_times where course = ? and best_ms < ?")) {
                ps.setString(1, course);
                ps.setLong(2, Math.min(time, best < 0 ? time : best));
                try (ResultSet rs = ps.executeQuery()) {
                    rank = (rs.next() ? rs.getInt(1) : 0) + 1;
                }
            }
            return new long[]{best, record, rank};
        }).thenAccept(r -> Bukkit.getScheduler().runTask(plugin, () -> {
            long best = r[0], record = r[1];
            boolean first = best < 0, newBest = !first && time < best;
            send(p, "parkour_finish", "course", c.display, "time", format(time), "rank", Long.toString(r[2]));
            if (first) {
                if (c.reward > 0) plugin.coins().change(id, c.reward, "parkour");
                send(p, "parkour_first", "coins", Text.formatNumber(c.reward));
            } else if (newBest) {
                if (c.rewardBest > 0) plugin.coins().change(id, c.rewardBest, "parkour");
                send(p, "parkour_best", "old", format(best), "coins", Text.formatNumber(c.rewardBest));
            } else {
                send(p, "parkour_no_best", "best", format(best));
            }
            if ((record <= 0 || time < record) && (first || newBest)) {
                Bukkit.broadcast(plugin.configs().msg("parkour_record", "name", Text.escape(p.getName()), "course", c.display, "time", format(time)));
            }
        }));
    }

    /** Actionbar mit laufender Zeit – alle 2 Ticks vom Cosmetics-Tick aus. */
    public void tick() {
        if (runs.isEmpty()) return;
        for (Map.Entry<UUID, Run> e : runs.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            Run run = e.getValue();
            Course c = courses.get(run.course);
            int total = c == null ? 0 : c.checkpoints.size();
            p.sendActionBar(Text.parse("<gray>⏱ <white>" + format(run.time()) + " <dark_gray>|</dark_gray> <yellow>Checkpoint " + run.reached + "/" + total));
        }
    }

    public static String format(long ms) {
        return String.format(Locale.ROOT, "%d:%02d.%03d", ms / 60_000, (ms / 1000) % 60, ms % 1000);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String tag = Keys.itemTag(event.getItem());
        if (tag == null || !tag.startsWith("parkour_")) return;
        event.setCancelled(true);
        Player p = event.getPlayer();
        Run run = runs.get(p.getUniqueId());
        if (run == null) {
            plugin.lobby().giveHotbar(p);
            return;
        }
        switch (tag) {
            case "parkour_cp" -> toCheckpoint(p, run);
            case "parkour_restart" -> {
                Course c = courses.get(run.course);
                if (c == null || c.start == null) return;
                runs.remove(p.getUniqueId());
                Location l = c.start.toLocation();
                if (l == null) return;
                ownTeleport.add(p.getUniqueId());
                p.teleport(l.getBlock().getLocation().add(0.5, 0.1, 0.5).setDirection(p.getLocation().getDirection()));
                ownTeleport.remove(p.getUniqueId());
                Location respawn = l.getBlock().getLocation().add(0.5, 0.1, 0.5);
                respawn.setYaw(p.getLocation().getYaw());
                runs.put(p.getUniqueId(), new Run(run.course, respawn));
                send(p, "parkour_restart", "course", c.display);
            }
            case "parkour_leave" -> stop(p, true);
            default -> {
            }
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (!runs.containsKey(p.getUniqueId()) || ownTeleport.contains(p.getUniqueId())) return;
        if (event.getFrom().getWorld() != event.getTo().getWorld() || event.getFrom().distanceSquared(event.getTo()) > 16) {
            Bukkit.getScheduler().runTask(plugin, () -> stop(p, true));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        runs.remove(event.getPlayer().getUniqueId());
    }

    private void send(Player p, String key, String... ph) {
        plugin.configs().send(p, key, ph);
    }

    // ---------------------------------------------------------------- Befehl

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = plugin.configs().has(sender, "parkour_admin");
        Player p = sender instanceof Player pl ? pl : null;
        switch (sub) {
            case "list" -> {
                if (courses.isEmpty()) {
                    plugin.configs().send(sender, "parkour_none");
                    return true;
                }
                plugin.configs().send(sender, "parkour_list", "courses", String.join("<gray>, </gray>", courses.entrySet().stream()
                        .map(e -> e.getValue().display + " <dark_gray>(" + e.getKey() + ")</dark_gray>").toList()));
            }
            case "leave", "verlassen" -> {
                if (p != null) stop(p, true);
            }
            case "top", "bestenliste" -> {
                String id = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : courses.keySet().stream().findFirst().orElse(null);
                if (id == null || !courses.containsKey(id)) {
                    plugin.configs().send(sender, "parkour_unknown", "course", args.length > 1 ? args[1] : "-");
                    return true;
                }
                top(id, 10).thenAccept(list -> Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.configs().send(sender, "parkour_top_header", "course", courses.get(id).display);
                    if (list.isEmpty()) plugin.configs().send(sender, "parkour_top_empty");
                    for (int i = 0; i < list.size(); i++) {
                        sender.sendMessage(Text.parse(" <gold>" + (i + 1) + ". <white>" + Text.escape(list.get(i)[0]) + " <dark_gray>»</dark_gray> <aqua>" + list.get(i)[1]));
                    }
                }));
            }
            case "create", "setstart", "addcp", "removecp", "setfinish", "delete", "setreward", "setname" -> {
                if (!admin) {
                    plugin.configs().send(sender, "no_permission");
                    return true;
                }
                if (p == null || args.length < 2) {
                    plugin.configs().send(sender, "parkour_admin_help");
                    return true;
                }
                admin(p, sub, args[1].toLowerCase(Locale.ROOT), args);
            }
            default -> plugin.configs().send(sender, admin ? "parkour_admin_help" : "parkour_help");
        }
        return true;
    }

    private void admin(Player p, String sub, String id, String[] args) {
        Course c = courses.get(id);
        if (!sub.equals("create") && c == null) {
            send(p, "parkour_unknown", "course", id);
            return;
        }
        Block b = p.getLocation().getBlock();
        SLoc here = SLoc.of(b.getLocation());
        switch (sub) {
            case "create" -> {
                if (c != null) {
                    send(p, "parkour_exists", "course", id);
                    return;
                }
                c = new Course();
                c.display = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "<aqua>" + id;
                c.start = here;
                courses.put(id, c);
            }
            case "setstart" -> c.start = here;
            case "addcp" -> c.checkpoints.add(here);
            case "removecp" -> {
                if (!c.checkpoints.isEmpty()) c.checkpoints.remove(c.checkpoints.size() - 1);
            }
            case "setfinish" -> c.finish = here;
            case "delete" -> courses.remove(id);
            case "setreward" -> {
                try {
                    c.reward = Long.parseLong(args[2]);
                    if (args.length > 3) c.rewardBest = Long.parseLong(args[3]);
                } catch (Exception e) {
                    send(p, "parkour_admin_help");
                    return;
                }
            }
            case "setname" -> {
                if (args.length < 3) return;
                c.display = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            }
            default -> {
            }
        }
        save();
        send(p, "parkour_saved", "course", id, "action", sub, "checkpoints", c == null ? "0" : Integer.toString(c.checkpoints.size()));
    }

    /** Top-Zeiten: [Name, Zeit] */
    public java.util.concurrent.CompletableFuture<List<String[]>> top(String course, int limit) {
        return plugin.database().submit(con -> {
            List<String[]> out = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement("select name, best_ms from ls_parkour_times where course = ? order by best_ms asc limit " + limit)) {
                ps.setString(1, course);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new String[]{rs.getString(1), format(rs.getLong(2))});
                }
            }
            return out;
        });
    }

    /** Persönliche Bestzeiten: Kurs → ms */
    public java.util.concurrent.CompletableFuture<Map<String, Long>> personal(UUID id) {
        return plugin.database().submit(con -> {
            Map<String, Long> out = new HashMap<>();
            try (PreparedStatement ps = con.prepareStatement("select course, best_ms from ls_parkour_times where uuid = ?")) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.put(rs.getString(1), rs.getLong(2));
                }
            }
            return out;
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        List<String> subs = new ArrayList<>(List.of("list", "top", "leave"));
        if (plugin.configs().has(sender, "parkour_admin")) subs.addAll(List.of("create", "setstart", "addcp", "removecp", "setfinish", "setreward", "setname", "delete"));
        if (args.length == 1) return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && !args[0].equalsIgnoreCase("create") && !args[0].equalsIgnoreCase("leave"))
            return courses.keySet().stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
