package de.crafterteam.lobbysystem.gui;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.social.ClanManager;
import de.crafterteam.lobbysystem.social.PartyManager;
import de.crafterteam.lobbysystem.storage.PlayerData;
import de.crafterteam.lobbysystem.storage.PlayerDataManager.Profile;
import de.crafterteam.lobbysystem.util.ItemBuilder;
import de.crafterteam.lobbysystem.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Freunde · Anfragen · Party · Clan · Einstellungen in einem modernen Menü. */
public final class SocialMenu extends Menu {

    public enum Tab { FRIENDS, REQUESTS, PARTY, CLAN, SETTINGS }

    private static final int[] CONTENT = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};

    private final Tab tab;
    private final List<Profile> friends;
    private final List<Profile> requests;
    private final PartyManager.Party party;
    private final ClanManager.Clan clan;
    private int page;

    private SocialMenu(LobbySystem plugin, Player player, Tab tab, List<Profile> friends, List<Profile> requests,
                       PartyManager.Party party, ClanManager.Clan clan) {
        super(plugin, player);
        this.tab = tab;
        this.friends = friends;
        this.requests = requests;
        this.party = party;
        this.clan = clan;
        String title = switch (tab) {
            case FRIENDS -> "<dark_aqua>ꜰʀᴇᴜɴᴅᴇ";
            case REQUESTS -> "<dark_aqua>ᴀɴꜰʀᴀɢᴇɴ";
            case PARTY -> "<light_purple>ᴘᴀʀᴛʏ";
            case CLAN -> "<gold>ᴄʟᴀɴ";
            case SETTINGS -> "<aqua>ᴇɪɴꜱᴛᴇʟʟᴜɴɢᴇɴ";
        };
        create(6, "<dark_gray>»</dark_gray> " + title);
    }

    /** Lädt alle Daten asynchron und öffnet dann das Menü. */
    public static void open(LobbySystem plugin, Player player, Tab tab) {
        var friendsF = plugin.friends().friends(player.getUniqueId());
        var requestsF = plugin.friends().requests(player.getUniqueId());
        var partyF = plugin.parties().party(player.getUniqueId());
        var clanF = plugin.clans().clanOf(player.getUniqueId());
        friendsF.thenCombine(requestsF, (f, r) -> new Object[]{f, r})
                .thenCombine(partyF, (arr, p) -> new Object[]{arr[0], arr[1], p})
                .thenCombine(clanF, (arr, c) -> new Object[]{arr[0], arr[1], arr[2], c})
                .thenAccept(arr -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    @SuppressWarnings("unchecked") List<Profile> f = (List<Profile>) arr[0];
                    @SuppressWarnings("unchecked") List<Profile> r = (List<Profile>) arr[1];
                    new SocialMenu(plugin, player, tab, f, r, (PartyManager.Party) arr[2], (ClanManager.Clan) arr[3]).open();
                }));
    }

    private void reopen(Tab t) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> open(plugin, player, t), 6L);
    }

    @Override
    protected void build() {
        tabs();
        switch (tab) {
            case FRIENDS -> friends();
            case REQUESTS -> requests();
            case PARTY -> party();
            case CLAN -> clan();
            case SETTINGS -> settings();
        }
        border(Material.BLACK_STAINED_GLASS_PANE);
    }

    private void tabs() {
        tab(1, Tab.FRIENDS, new ItemBuilder(Material.PLAYER_HEAD).skull(player), "<dark_aqua>Freunde <gray>(" + friends.size() + ")");
        tab(3, Tab.REQUESTS, new ItemBuilder(Material.WRITABLE_BOOK).amount(Math.max(1, requests.size())), "<aqua>Anfragen <gray>(" + requests.size() + ")");
        tab(5, Tab.PARTY, new ItemBuilder(Material.CAKE), "<light_purple>Party" + (party == null ? "" : " <gray>(" + party.members().size() + ")"));
        tab(7, Tab.CLAN, new ItemBuilder(Material.SHIELD), "<gold>Clan" + (clan == null ? "" : " <dark_gray>[<yellow>" + clan.tag() + "<dark_gray>]"));
        tab(49, Tab.SETTINGS, new ItemBuilder(Material.COMPARATOR), "<aqua>Einstellungen");
    }

    private void tab(int slot, Tab t, ItemBuilder b, String name) {
        boolean active = t == tab;
        set(slot, b.name("<dark_gray>»</dark_gray> " + name)
                .lore(active ? "<dark_gray>↪</dark_gray> <green>Geöffnet" : "<dark_gray>↪</dark_gray> <gray>Klicke zum Öffnen")
                .glow(active).hideFlags().build(), e -> {
            if (active) return;
            click();
            open(plugin, player, t);
        });
    }

    private ItemBuilder head(Profile p) {
        return new ItemBuilder(Material.PLAYER_HEAD).skull(p.uuid(), p.name())
                .name((p.online() ? "<green>" : "<gray>") + Text.escape(p.name()));
    }

    private void paged(List<Profile> list, java.util.function.Function<Profile, ItemBuilder> item, Consumer<ClickData> action) {
        int pages = Math.max(1, (list.size() + CONTENT.length - 1) / CONTENT.length);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < CONTENT.length; i++) {
            int idx = page * CONTENT.length + i;
            if (idx >= list.size()) break;
            Profile p = list.get(idx);
            set(CONTENT[i], item.apply(p).build(), e -> action.accept(new ClickData(p, e.getClick())));
        }
        if (page > 0) set(45, new ItemBuilder(Material.ARROW).name("<gray>« Zurück").build(), e -> {
            page--;
            refresh();
        });
        if (page < pages - 1) set(53, new ItemBuilder(Material.ARROW).name("<gray>Weiter »").build(), e -> {
            page++;
            refresh();
        });
    }

    private record ClickData(Profile profile, ClickType click) {
    }

    // ---------------------------------------------------------------- Tabs

    private void friends() {
        paged(friends, f -> head(f).lore(
                "<dark_gray>↪</dark_gray> " + (f.online() ? "<green>Online" : "<red>Offline"),
                "<dark_gray>↪</dark_gray> " + plugin.friends().where(f), "",
                f.online() ? "<dark_gray>»</dark_gray> <yellow>Linksklick: Nachspringen" : "",
                "<dark_gray>»</dark_gray> <aqua>Rechtsklick: In Party einladen",
                "<dark_gray>»</dark_gray> <red>Shift+Rechtsklick: Entfernen"), data -> {
            Profile f = data.profile();
            player.closeInventory();
            if (data.click() == ClickType.SHIFT_RIGHT) plugin.friends().remove(player, f.name());
            else if (data.click().isRightClick()) plugin.parties().invite(player, f.name());
            else if (f.online()) plugin.friends().jump(player, f.name());
        });
        set(47, new ItemBuilder(Material.NAME_TAG).name("<dark_gray>»</dark_gray> <green>Freund hinzufügen")
                .lore("<dark_gray>↪</dark_gray> <gray>Klicke und gib den Namen ein").build(), e -> suggest("/friend add "));
        set(51, new ItemBuilder(Material.PAPER).name("<dark_gray>»</dark_gray> <gray>Max. Freunde: <white>" + plugin.friends().max(player)).build());
        if (friends.isEmpty()) set(22, new ItemBuilder(Material.STRUCTURE_VOID).name("<gray>Noch keine Freunde").lore("<dark_gray>↪</dark_gray> <gray>/friend add <Name>").build());
    }

    private void requests() {
        paged(requests, r -> head(r).lore("<dark_gray>»</dark_gray> <green>Linksklick: Annehmen", "<dark_gray>»</dark_gray> <red>Rechtsklick: Ablehnen"), data -> {
            if (data.click().isRightClick()) plugin.friends().deny(player, data.profile().name());
            else plugin.friends().accept(player, data.profile().name());
            reopen(Tab.REQUESTS);
        });
        if (requests.isEmpty()) set(22, new ItemBuilder(Material.STRUCTURE_VOID).name("<gray>Keine offenen Anfragen").build());
    }

    private void party() {
        if (party == null) {
            set(22, new ItemBuilder(Material.CAKE).name("<dark_gray>»</dark_gray> <light_purple>Party erstellen")
                    .lore("<dark_gray>↪</dark_gray> <gray>Lade einen Freund ein:", "<dark_gray>↪</dark_gray> <white>/party invite <Name>",
                            "<dark_gray>↪</dark_gray> <gray>oder Rechtsklick auf einen Freund").build(), e -> suggest("/party invite "));
            return;
        }
        boolean leader = party.isLeader(player.getUniqueId());
        paged(party.members(), m -> head(m).lore(
                "<dark_gray>↪</dark_gray> " + (party.isLeader(m.uuid()) ? "<gold>Leader" : "<gray>Mitglied"),
                "<dark_gray>↪</dark_gray> " + plugin.friends().where(m),
                leader && !m.uuid().equals(player.getUniqueId()) ? "<dark_gray>»</dark_gray> <yellow>Links: Leader · <red>Shift+Rechts: Kicken" : ""), data -> {
            if (!leader || data.profile().uuid().equals(player.getUniqueId())) return;
            if (data.click() == ClickType.SHIFT_RIGHT) plugin.parties().kick(player, data.profile().name());
            else if (data.click().isLeftClick()) plugin.parties().promote(player, data.profile().name());
            reopen(Tab.PARTY);
        });
        if (leader) {
            set(46, new ItemBuilder(Material.NAME_TAG).name("<dark_gray>»</dark_gray> <green>Einladen").build(), e -> suggest("/party invite "));
            set(47, new ItemBuilder(Material.ENDER_PEARL).name("<dark_gray>»</dark_gray> <aqua>Party zu mir holen").build(), e -> {
                player.closeInventory();
                plugin.parties().warp(player);
            });
            set(51, new ItemBuilder(Material.BARRIER).name("<dark_gray>»</dark_gray> <red>Party auflösen").build(), e -> {
                player.closeInventory();
                plugin.parties().disband(player);
            });
        }
        set(52, new ItemBuilder(Material.RED_DYE).name("<dark_gray>»</dark_gray> <red>Party verlassen").build(), e -> {
            player.closeInventory();
            plugin.parties().leave(player);
        });
    }

    private void clan() {
        if (clan == null) {
            set(22, new ItemBuilder(Material.WHITE_BANNER).name("<dark_gray>»</dark_gray> <gold>Clan gründen")
                    .lore("<dark_gray>↪</dark_gray> <white>/clan create <Name> <Tag>",
                            "<dark_gray>↪</dark_gray> <gray>Kosten: <yellow>" + Text.formatNumber(plugin.configs().cfg().social.clanCreateCost) + " Coins").build(),
                    e -> suggest("/clan create "));
            return;
        }
        int myRole = clan.role(player.getUniqueId());
        set(4, new ItemBuilder(Material.YELLOW_BANNER).name("<gold>" + Text.escape(clan.name()) + " <dark_gray>[<yellow>" + clan.tag() + "<dark_gray>]")
                .lore("<dark_gray>↪</dark_gray> <gray>Mitglieder: <white>" + clan.members().size() + "<dark_gray>/</dark_gray><white>" + plugin.configs().cfg().social.clanMax,
                        "<dark_gray>↪</dark_gray> <gray>Deine Rolle: <gold>" + ClanManager.roleName(myRole)).build());
        paged(clan.members(), m -> head(m).lore(
                "<dark_gray>↪</dark_gray> <gold>" + ClanManager.roleName(clan.role(m.uuid())),
                "<dark_gray>↪</dark_gray> " + plugin.friends().where(m),
                myRole == ClanManager.LEADER && !m.uuid().equals(player.getUniqueId())
                        ? "<dark_gray>»</dark_gray> <yellow>Links: Befördern · Rechts: Degradieren · <red>Shift+Rechts: Kicken" : ""), data -> {
            Profile m = data.profile();
            if (m.uuid().equals(player.getUniqueId()) || myRole < ClanManager.MODERATOR) return;
            if (data.click() == ClickType.SHIFT_RIGHT) plugin.clans().kick(player, m.name());
            else if (data.click().isRightClick() && myRole == ClanManager.LEADER) plugin.clans().demote(player, m.name());
            else if (data.click().isLeftClick() && myRole == ClanManager.LEADER) plugin.clans().promote(player, m.name());
            reopen(Tab.CLAN);
        });
        if (myRole >= ClanManager.MODERATOR) {
            set(46, new ItemBuilder(Material.NAME_TAG).name("<dark_gray>»</dark_gray> <green>Einladen").build(), e -> suggest("/clan invite "));
        }
        set(47, new ItemBuilder(Material.OAK_SIGN).name("<dark_gray>»</dark_gray> <yellow>Clan-Chat").lore("<dark_gray>↪</dark_gray> <white>/clan chat <Nachricht>").build(), e -> suggest("/clan chat "));
        if (myRole == ClanManager.LEADER) {
            set(51, new ItemBuilder(Material.BARRIER).name("<dark_gray>»</dark_gray> <red>Clan auflösen").lore("<dark_gray>↪</dark_gray> <red>Shift+Klick zum Bestätigen").build(), e -> {
                if (!e.getClick().isShiftClick()) return;
                player.closeInventory();
                plugin.clans().disband(player);
            });
        }
        set(52, new ItemBuilder(Material.RED_DYE).name("<dark_gray>»</dark_gray> <red>Clan verlassen").build(), e -> {
            player.closeInventory();
            plugin.clans().leave(player);
        });
    }

    private void settings() {
        PlayerData d = plugin.data().get(player);
        toggle(20, Material.WRITABLE_BOOK, "Freundschaftsanfragen", d::friendRequests, d::friendRequests);
        toggle(22, Material.CAKE, "Party-Einladungen", d::partyInvites, d::partyInvites);
        toggle(24, Material.BELL, "Online-Benachrichtigungen", d::friendNotify, d::friendNotify);
    }

    private void toggle(int slot, Material mat, String name, BooleanSupplier get, Consumer<Boolean> setter) {
        boolean on = get.getAsBoolean();
        set(slot, new ItemBuilder(mat).name("<dark_gray>-●</dark_gray> <aqua>" + name)
                .lore("<dark_gray>↪</dark_gray> " + (on ? "<green>Aktiviert" : "<red>Deaktiviert"), "", "<dark_gray>»</dark_gray> <gray>Klicke zum Umschalten")
                .glow(on).build(), e -> {
            setter.accept(!get.getAsBoolean());
            plugin.data().save(plugin.data().get(player));
            click();
            refresh();
        });
    }

    private void suggest(String command) {
        player.closeInventory();
        player.sendMessage(Text.parse(plugin.configs().cfg().prefix + "<yellow><click:suggest_command:'" + command + "'>"
                + "<hover:show_text:'Klicke zum Ausfüllen'>[Klicke hier: " + command.trim() + " …]</hover></click>"));
    }
}
