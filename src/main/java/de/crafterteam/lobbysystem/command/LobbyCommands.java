package de.crafterteam.lobbysystem.command;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.ConfigManager;
import de.crafterteam.lobbysystem.npc.NpcManager;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.util.SLoc;
import de.crafterteam.lobbysystem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** /build, /lobbycoins, /setreward, /setsettings, /setspawn, /setwarp, /warp, /spawn, /tpcoord, /lobbysystem */
public final class LobbyCommands implements TabExecutor {

    private final LobbySystem plugin;

    public LobbyCommands(LobbySystem plugin) {
        this.plugin = plugin;
    }

    private ConfigManager c() {
        return plugin.configs();
    }

    private boolean check(CommandSender sender, String perm) {
        if (c().has(sender, perm)) return true;
        c().send(sender, "no_permission");
        return false;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player p) return p;
        c().send(sender, "only_players");
        return null;
    }

    private void usage(CommandSender sender, String usage) {
        c().send(sender, "usage", "usage", usage);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "build" -> build(sender, args);
            case "lobbycoins" -> coins(sender, args);
            case "setreward" -> npc(sender, args, NpcManager.Type.REWARD, "setreward", "reward_set", "reward_removed");
            case "setsettings" -> npc(sender, args, NpcManager.Type.SETTINGS, "setsettings", "settings_set", "settings_removed");
            case "setspawn" -> setSpawn(sender);
            case "setwarp" -> setWarp(sender, args);
            case "warp" -> warp(sender, args);
            case "spawn" -> {
                Player p = player(sender);
                if (p != null) plugin.lobby().teleportSpawn(p, true);
            }
            case "tpcoord" -> tpCoord(sender, args);
            case "pay" -> pay(sender, args);
            case "cookie" -> {
                Player p = player(sender);
                if (p == null || !check(sender, "cookie")) break;
                if (!c().cfg().cookieClicker.enabled) break;
                new de.crafterteam.lobbysystem.gui.CookieMenu(plugin, p).open();
            }
            case "level" -> level(sender, args);
            case "setleaderboard" -> setLeaderboard(sender, args);
            case "lobbyswitcher" -> {
                Player p = player(sender);
                if (p != null) de.crafterteam.lobbysystem.gui.LobbySwitcherMenu.open(plugin, p);
            }
            case "lobbysystem" -> admin(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- /build

    private void build(CommandSender sender, String[] args) {
        if (args.length >= 1) {
            if (!check(sender, "build_others")) return;
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                c().send(sender, "player_not_found", "name", args[0]);
                return;
            }
            boolean on = plugin.lobby().toggleBuild(target);
            c().send(target, on ? "build_on" : "build_off");
            c().send(sender, "build_other", "name", target.getName(), "state", c().raw(on ? "state_on" : "state_off"));
            return;
        }
        Player p = player(sender);
        if (p == null || !check(sender, "build")) return;
        boolean on = plugin.lobby().toggleBuild(p);
        c().send(p, on ? "build_on" : "build_off");
    }

    // ---------------------------------------------------------------- /lobbycoins

    private void coins(CommandSender sender, String[] args) {
        String u = "/lobbycoins <add|remove|set|get> <Spieler> [Coins]";
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("get"))) {
            Player p = player(sender);
            if (p == null) return;
            c().send(p, "coins_get_self", "amount", Text.formatNumber(plugin.data().get(p).coins()));
            return;
        }
        String mode = args[0].toLowerCase(Locale.ROOT);
        if (mode.equals("get")) {
            if (!check(sender, "coins_get_others")) return;
            String name = args[1];
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) {
                c().send(sender, "coins_get", "name", online.getName(), "amount", Text.formatNumber(plugin.data().get(online).coins()));
                return;
            }
            plugin.data().offlineCoins(name).whenComplete((res, err) -> sync(() -> {
                if (err != null || res == null || res.isEmpty()) c().send(sender, "player_not_found", "name", name);
                else c().send(sender, "coins_get", "name", res.get().name(), "amount", Text.formatNumber(res.get().coins()));
            }));
            return;
        }
        if (!mode.equals("add") && !mode.equals("remove") && !mode.equals("set")) {
            usage(sender, u);
            return;
        }
        if (!check(sender, "coins")) return;
        if (args.length < 3) {
            usage(sender, u);
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
            if (amount < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            c().send(sender, "invalid_number");
            return;
        }
        String name = args[1];
        String key = "coins_" + mode;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            PlayerData d = plugin.data().get(online);
            switch (mode) {
                case "add" -> plugin.coins().addCoins(online.getUniqueId(), amount, "command");
                case "remove" -> plugin.coins().change(online.getUniqueId(), -amount, "command");
                default -> plugin.coins().setCoins(online.getUniqueId(), amount, "command");
            }
            plugin.data().save(d);
            c().send(sender, key, "name", online.getName(), "amount", Text.formatNumber(amount));
            return;
        }
        long finalAmount = amount;
        plugin.data().offlineModify(name, mode, amount).whenComplete((res, err) -> sync(() -> {
            Optional<?> r = res;
            if (err != null || r == null || r.isEmpty()) c().send(sender, "player_not_found", "name", name);
            else c().send(sender, key, "name", res.get().name(), "amount", Text.formatNumber(finalAmount));
        }));
    }

    // ---------------------------------------------------------------- /pay & Bestenlisten

    private void pay(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null || !check(sender, "pay")) return;
        if (args.length < 2) {
            usage(sender, "/pay <Spieler> <Coins>");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[1]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            c().send(p, "invalid_number");
            return;
        }
        if (args[0].equalsIgnoreCase(p.getName())) {
            c().send(p, "pay_self");
            return;
        }
        if (plugin.data().get(p).coins() < amount) {
            c().send(p, "pay_not_enough");
            return;
        }
        plugin.data().lookup(args[0]).thenAccept(opt -> sync(() -> {
            if (opt.isEmpty()) {
                c().send(p, "player_not_found", "name", args[0]);
                return;
            }
            var target = opt.get();
            plugin.coins().transfer(p.getUniqueId(), target.uuid(), amount, "pay").thenAccept(ok -> sync(() -> {
                if (!ok) {
                    c().send(p, "pay_not_enough");
                    return;
                }
                String amt = Text.formatNumber(amount);
                c().send(p, "pay_sent", "name", target.name(), "amount", amt);
                plugin.messenger().send(target.name(), c().msg("pay_received", "name", p.getName(), "amount", amt));
            }));
        }));
    }

    private void level(CommandSender sender, String[] args) {
        Player target = args.length >= 1 ? Bukkit.getPlayerExact(args[0]) : (sender instanceof Player p ? p : null);
        if (target == null) {
            c().send(sender, "player_not_found", "name", args.length >= 1 ? args[0] : "-");
            return;
        }
        long xp = plugin.data().get(target).xp();
        var lv = plugin.levels();
        int level = lv.level(xp);
        c().send(sender, "level_info", "name", target.getName(), "level", Integer.toString(level),
                "xp", Text.formatNumber(lv.progress(xp)), "needed", Text.formatNumber(lv.needed(level)), "bar", lv.bar(xp, 30));
    }

    private void setLeaderboard(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null || !check(sender, "setleaderboard")) return;
        boolean remove = args.length >= 2 && args[0].equalsIgnoreCase("remove");
        var type = args.length == 0 ? null : plugin.leaderboards().board(remove ? args[1] : args[0]);
        if (type == null) {
            usage(sender, "/setleaderboard [remove] <coins|playtime|streak|level|cookies|friends|<spiel>_<wins|kills|deaths|games|level>>");
            c().send(p, "leaderboard_types");
            return;
        }
        plugin.leaderboards().set(type, remove ? null : p.getLocation());
        c().send(p, remove ? "leaderboard_removed" : "leaderboard_set", "type", type.key());
    }

    // ---------------------------------------------------------------- NPCs, Spawn, Warps

    private void npc(CommandSender sender, String[] args, NpcManager.Type type, String perm, String setMsg, String removeMsg) {
        Player p = player(sender);
        if (p == null || !check(sender, perm)) return;
        if (args.length >= 1 && args[0].equalsIgnoreCase("remove")) {
            plugin.npcs().set(type, null);
            c().send(p, removeMsg);
            return;
        }
        Location loc = p.getLocation();
        loc.setPitch(0);
        plugin.npcs().set(type, loc);
        c().send(p, setMsg);
    }

    private void setSpawn(CommandSender sender) {
        Player p = player(sender);
        if (p == null || !check(sender, "setspawn")) return;
        c().locations().spawn = SLoc.of(p.getLocation());
        c().saveLocations();
        p.getWorld().setSpawnLocation(p.getLocation());
        c().send(p, "spawn_set");
    }

    private void setWarp(CommandSender sender, String[] args) {
        if (!check(sender, "setwarp")) return;
        String u = "/setwarp <Name> | /setwarp remove <Name> | /setwarp list";
        if (args.length == 0) {
            usage(sender, u);
            return;
        }
        if (args[0].equalsIgnoreCase("list")) {
            c().send(sender, "warp_list", "warps", c().warps().isEmpty() ? "-" : String.join(", ", c().warps().keySet()));
            return;
        }
        if (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("delete")) {
            if (args.length < 2) {
                usage(sender, u);
                return;
            }
            String name = args[1].toLowerCase(Locale.ROOT);
            if (c().warps().remove(name) == null) {
                c().send(sender, "warp_not_found", "warp", name);
                return;
            }
            c().saveWarps();
            c().send(sender, "warp_removed", "warp", name);
            return;
        }
        Player p = player(sender);
        if (p == null) return;
        String name = args[0].toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_\\-]{1,32}")) {
            usage(sender, u);
            return;
        }
        c().warps().put(name, SLoc.of(p.getLocation()));
        c().saveWarps();
        c().send(p, "warp_set", "warp", name);
    }

    private void warp(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null || !check(sender, "warp")) return;
        if (args.length == 0) {
            c().send(p, "warp_list", "warps", c().warps().isEmpty() ? "-" : String.join(", ", c().warps().keySet()));
            return;
        }
        plugin.lobby().runTarget(p, "warp:" + args[0]);
    }

    // ---------------------------------------------------------------- /tpcoord

    private void tpCoord(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null || !check(sender, "tpcoord")) return;
        if (args.length < 3) {
            usage(sender, "/tpcoord <x> <y> <z> [Welt] [Yaw] [Pitch]");
            return;
        }
        Location base = p.getLocation();
        try {
            double x = coord(args[0], base.getX());
            double y = coord(args[1], base.getY());
            double z = coord(args[2], base.getZ());
            World world = base.getWorld();
            if (args.length >= 4) {
                world = Bukkit.getWorld(args[3]);
                if (world == null) {
                    c().send(p, "world_not_found", "world", args[3]);
                    return;
                }
            }
            float yaw = args.length >= 5 ? Float.parseFloat(args[4]) : base.getYaw();
            float pitch = args.length >= 6 ? Float.parseFloat(args[5]) : base.getPitch();
            Location target = new Location(world, x, y, z, yaw, pitch);
            plugin.lobby().teleport(p, target, true);
            c().send(p, "tpcoord_done", "x", fmt(x), "y", fmt(y), "z", fmt(z), "world", world.getName());
        } catch (NumberFormatException e) {
            c().send(p, "invalid_number");
        }
    }

    /** Unterstützt relative Koordinaten (~, ~5). */
    private static double coord(String s, double current) {
        if (s.startsWith("~")) return current + (s.length() > 1 ? Double.parseDouble(s.substring(1)) : 0);
        return Double.parseDouble(s);
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    // ---------------------------------------------------------------- /lobbysystem

    private void admin(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!check(sender, "reload")) return;
            plugin.reload();
            c().send(sender, "reloaded");
            return;
        }
        var lic = plugin.licenses().active();
        sender.sendMessage(Text.parse(c().cfg().prefix + "<gray>LobbySystem <white>v" + plugin.getPluginMeta().getVersion()
                + "</white> <dark_gray>|</dark_gray> <gray>Paper 1.21.10 – 26.3 <dark_gray>|</dark_gray> <gray>by <gold>" + "SujucDerBreite"));
        sender.sendMessage(Text.parse(c().cfg().prefix + "<gray>Lizenz: " + (lic == null ? (plugin.licenses().ownerMode() ? "<gold>Owner" : "<red>keine")
                : "<white>" + Text.escape(lic.holder()) + " <dark_gray>(" + lic.expiresText() + ")")
                + " <dark_gray>|</dark_gray> <gray>Speicher: <white>" + (plugin.database().isMySql() ? "MySQL" : "SQLite")));
    }

    private void sync(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    // ---------------------------------------------------------------- Tab-Completion

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "build" -> {
                if (args.length == 1 && c().has(sender, "build_others")) return Cmds.players(args[0]);
            }
            case "lobbycoins" -> {
                if (args.length == 1) return Cmds.filter(List.of("add", "remove", "set", "get"), args[0]);
                if (args.length == 2) return Cmds.players(args[1]);
                if (args.length == 3 && !args[0].equalsIgnoreCase("get")) return Cmds.filter(List.of("100", "1000", "10000"), args[2]);
            }
            case "setreward", "setsettings" -> {
                if (args.length == 1) return Cmds.filter(List.of("remove"), args[0]);
            }
            case "setwarp" -> {
                if (args.length == 1) {
                    List<String> l = new ArrayList<>(List.of("remove", "list"));
                    return Cmds.filter(l, args[0]);
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("remove")) return Cmds.filter(new ArrayList<>(c().warps().keySet()), args[1]);
            }
            case "warp" -> {
                if (args.length == 1) return Cmds.filter(new ArrayList<>(c().warps().keySet()), args[0]);
            }
            case "tpcoord" -> {
                if (args.length >= 1 && args.length <= 3) return List.of("~");
                if (args.length == 4) return Cmds.filter(Bukkit.getWorlds().stream().map(World::getName).toList(), args[3]);
            }
            case "lobbysystem" -> {
                if (args.length == 1) return Cmds.filter(List.of("reload", "info"), args[0]);
            }
            case "pay" -> {
                if (args.length == 1) return Cmds.players(args[0]);
                if (args.length == 2) return Cmds.filter(List.of("10", "100", "1000"), args[1]);
            }
            case "setleaderboard" -> {
                List<String> types = List.of("coins", "playtime", "streak", "level", "cookies", "friends", "missilewars_wins", "missilewars_kills", "missilewars_level", "bedwars_wins");
                if (args.length == 1) {
                    List<String> l = new ArrayList<>(types);
                    l.add("remove");
                    return Cmds.filter(l, args[0]);
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("remove")) return Cmds.filter(types, args[1]);
            }
            default -> {
            }
        }
        return List.of();
    }
}
