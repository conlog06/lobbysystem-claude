package de.crafterteam.lobbysystem.command;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.config.ConfigManager;
import de.crafterteam.lobbysystem.license.LicenseCodec;
import de.crafterteam.lobbysystem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * /license – ausschließlich für den Owner SujucDerBreite (Name + Owner-Schlüssel auf dem Server).
 */
public final class LicenseCommand implements TabExecutor {

    private final LobbySystem plugin;

    public LicenseCommand(LobbySystem plugin) {
        this.plugin = plugin;
    }

    private boolean isOwner(CommandSender sender) {
        return sender instanceof Player p
                && p.getName().equals(LicenseCodec.OWNER)
                && plugin.licenses().ownerMode();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        ConfigManager c = plugin.configs();
        if (!isOwner(sender)) {
            c.send(sender, "license_owner_only");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Text.parse(c.cfg().prefix + "<gray>/license create <Inhaber> [Tage]  <dark_gray>|</dark_gray> <gray>/license list <dark_gray>|</dark_gray> <gray>/license verify <Lizenz>"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length < 2) {
                    c.send(sender, "usage", "usage", "/license create <Inhaber> [Tage]");
                    return true;
                }
                int days = 0;
                if (args.length >= 3) {
                    try {
                        days = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        c.send(sender, "invalid_number");
                        return true;
                    }
                }
                plugin.licenses().create(args[1], days, sender.getName()).whenComplete((l, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (err != null) {
                        sender.sendMessage(Text.parse(c.cfg().prefix + "<red>Fehler: " + Text.escape(String.valueOf(err.getMessage()))));
                        return;
                    }
                    c.send(sender, "license_created", "holder", l.holder(), "expires", l.expiresText(), "raw_key", l.key());
                    plugin.getLogger().info("Neue Lizenz für " + l.holder() + ": " + l.key());
                }));
            }
            case "list" -> plugin.licenses().list().whenComplete((list, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (err != null || list == null) {
                    sender.sendMessage(Text.parse(c.cfg().prefix + "<red>Lizenzen konnten nicht geladen werden."));
                    return;
                }
                sender.sendMessage(Text.parse(c.cfg().prefix + "<gray>Erstellte Lizenzen: <white>" + list.size()));
                for (LicenseCodec.License l : list) {
                    String state = l.expired() ? "<red>abgelaufen" : "<green>aktiv";
                    sender.sendMessage(Text.parse("<dark_gray> » </dark_gray><white>" + Text.escape(l.holder()) + "</white> <dark_gray>|</dark_gray> "
                            + state + " <dark_gray>|</dark_gray> <gray>" + l.expiresText() + " <yellow><click:copy_to_clipboard:'" + l.key()
                            + "'><hover:show_text:'Kopieren'>[Kopieren]</hover></click>"));
                }
            }));
            case "verify", "info" -> {
                if (args.length < 2) {
                    c.send(sender, "usage", "usage", "/license verify <Lizenz>");
                    return true;
                }
                LicenseCodec.License l = LicenseCodec.verify(plugin.licenses().publicKey(), args[1]);
                if (l == null) {
                    sender.sendMessage(Text.parse(c.cfg().prefix + "<red>Diese Lizenz ist ungültig."));
                } else {
                    sender.sendMessage(Text.parse(c.cfg().prefix + (l.expired() ? "<red>Abgelaufen" : "<green>Gültig")
                            + " <dark_gray>|</dark_gray> <gray>Inhaber: <white>" + Text.escape(l.holder())
                            + "</white> <dark_gray>|</dark_gray> <gray>Ablauf: <white>" + l.expiresText()));
                }
            }
            default -> c.send(sender, "usage", "usage", "/license <create|list|verify>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!isOwner(sender)) return List.of();
        if (args.length == 1) return Cmds.filter(List.of("create", "list", "verify"), args[0]);
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) return Cmds.filter(List.of("30", "90", "365", "0"), args[2]);
        return List.of();
    }
}
