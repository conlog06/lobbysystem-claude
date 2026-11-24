package de.crafterteam.lobbysystem.command;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.ConfigManager;
import de.crafterteam.lobbysystem.gui.SocialMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /friend, /party, /clan */
public final class SocialCommands implements TabExecutor {

    private final LobbySystem plugin;

    public SocialCommands(LobbySystem plugin) {
        this.plugin = plugin;
    }

    private ConfigManager c() {
        return plugin.configs();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            c().send(sender, "only_players");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "friend" -> friend(p, args);
            case "party" -> party(p, args);
            case "clan" -> clan(p, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static String rest(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    private boolean need(Player p, String[] args, int n, String prefix, String help) {
        if (args.length >= n) return true;
        c().sendP(p, prefix, help);
        return false;
    }

    // ---------------------------------------------------------------- /friend

    private void friend(Player p, String[] args) {
        if (!c().cfg().social.friends) return;
        var f = plugin.friends();
        if (args.length == 0) {
            SocialMenu.open(plugin, p, SocialMenu.Tab.FRIENDS);
            return;
        }
        String P = "friend_prefix", H = "friend_help";
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> { if (need(p, args, 2, P, H)) f.add(p, args[1]); }
            case "remove", "del" -> { if (need(p, args, 2, P, H)) f.remove(p, args[1]); }
            case "accept" -> { if (need(p, args, 2, P, H)) f.accept(p, args[1]); }
            case "deny" -> { if (need(p, args, 2, P, H)) f.deny(p, args[1]); }
            case "list" -> f.list(p);
            case "requests" -> f.listRequests(p);
            case "jump" -> { if (need(p, args, 2, P, H)) f.jump(p, args[1]); }
            case "msg" -> { if (need(p, args, 3, P, H)) f.message(p, args[1], rest(args, 2)); }
            case "toggle" -> f.toggle(p);
            default -> {
                // "/friend Name" = hinzufügen
                if (args.length == 1 && args[0].matches("[A-Za-z0-9_]{3,16}")) f.add(p, args[0]);
                else c().sendP(p, P, H);
            }
        }
    }

    // ---------------------------------------------------------------- /party

    private void party(Player p, String[] args) {
        if (!c().cfg().social.party) return;
        if (!c().has(p, "party")) {
            c().send(p, "no_permission");
            return;
        }
        var pm = plugin.parties();
        if (args.length == 0) {
            SocialMenu.open(plugin, p, SocialMenu.Tab.PARTY);
            return;
        }
        String P = "party_prefix", H = "party_help";
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "invite" -> { if (need(p, args, 2, P, H)) pm.invite(p, args[1]); }
            case "accept", "join" -> { if (need(p, args, 2, P, H)) pm.accept(p, args[1]); }
            case "deny" -> { if (need(p, args, 2, P, H)) pm.deny(p, args[1]); }
            case "leave" -> pm.leave(p);
            case "kick" -> { if (need(p, args, 2, P, H)) pm.kick(p, args[1]); }
            case "promote" -> { if (need(p, args, 2, P, H)) pm.promote(p, args[1]); }
            case "disband" -> pm.disband(p);
            case "list" -> pm.list(p);
            case "warp" -> pm.warp(p);
            case "chat" -> { if (need(p, args, 2, P, H)) pm.chat(p, rest(args, 1)); }
            default -> {
                if (args.length == 1 && args[0].matches("[A-Za-z0-9_]{3,16}")) pm.invite(p, args[0]);
                else c().sendP(p, P, H);
            }
        }
    }

    // ---------------------------------------------------------------- /clan

    private void clan(Player p, String[] args) {
        if (!c().cfg().social.clans) return;
        var cm = plugin.clans();
        if (args.length == 0) {
            SocialMenu.open(plugin, p, SocialMenu.Tab.CLAN);
            return;
        }
        String P = "clan_prefix", H = "clan_help";
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (!c().has(p, "clan_create")) {
                    c().send(p, "no_permission");
                    return;
                }
                if (need(p, args, 3, P, H)) cm.create(p, args[1], args[2]);
            }
            case "invite" -> { if (need(p, args, 2, P, H)) cm.invite(p, args[1]); }
            case "accept", "join" -> { if (need(p, args, 2, P, H)) cm.accept(p, args[1]); }
            case "deny" -> { if (need(p, args, 2, P, H)) cm.deny(p, args[1]); }
            case "leave" -> cm.leave(p);
            case "kick" -> { if (need(p, args, 2, P, H)) cm.kick(p, args[1]); }
            case "promote" -> { if (need(p, args, 2, P, H)) cm.promote(p, args[1]); }
            case "demote" -> { if (need(p, args, 2, P, H)) cm.demote(p, args[1]); }
            case "disband" -> cm.disband(p);
            case "info" -> cm.info(p, args.length >= 2 ? args[1] : null);
            case "list", "members" -> cm.info(p, null);
            case "chat" -> { if (need(p, args, 2, P, H)) cm.chat(p, rest(args, 1)); }
            default -> c().sendP(p, P, H);
        }
    }

    // ---------------------------------------------------------------- Tab

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return switch (name) {
                case "friend" -> Cmds.filter(List.of("add", "remove", "accept", "deny", "list", "requests", "jump", "msg", "toggle"), args[0]);
                case "party" -> Cmds.filter(List.of("invite", "accept", "deny", "leave", "kick", "promote", "disband", "list", "warp", "chat"), args[0]);
                case "clan" -> Cmds.filter(List.of("create", "invite", "accept", "deny", "leave", "kick", "promote", "demote", "disband", "info", "list", "chat"), args[0]);
                default -> List.of();
            };
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("chat") && !args[0].equalsIgnoreCase("create")) return Cmds.players(args[1]);
        return List.of();
    }
}
