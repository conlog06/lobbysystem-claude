package de.crafterteam.lobbysystem.hook;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.util.Text;
import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * LobbySystem-Coins als Vault-Economy: Shop-, Jobs- oder Crate-Plugins können direkt mit den Lobby-Coins arbeiten.
 * Online-Spieler laufen über den Cache (sofort), Offline-Spieler über die Datenbank (kurz blockierend, max. 2 s).
 */
public final class VaultEconomy extends AbstractEconomy {

    private final LobbySystem plugin;

    private VaultEconomy(LobbySystem plugin) {
        this.plugin = plugin;
    }

    public static void register(LobbySystem plugin) {
        Bukkit.getServicesManager().register(net.milkbowl.vault.economy.Economy.class, new VaultEconomy(plugin), plugin, ServicePriority.High);
        plugin.getLogger().info("Vault-Economy registriert – andere Plugins nutzen jetzt die Lobby-Coins.");
    }

    private UUID uuid(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p.getUniqueId();
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) return cached.getUniqueId();
        try {
            return plugin.data().lookup(name).get(2, TimeUnit.SECONDS).map(pr -> pr.uuid()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private long balance(UUID id) {
        if (id == null) return 0;
        try {
            return Math.max(0, plugin.coins().getCoins(id).get(2, TimeUnit.SECONDS));
        } catch (Exception e) {
            return 0;
        }
    }

    private EconomyResponse withdraw(UUID id, double amount) {
        if (id == null) return fail("Spieler unbekannt");
        if (amount < 0) return fail("Negativer Betrag");
        long a = (long) Math.ceil(amount);
        try {
            boolean ok = plugin.coins().removeCoins(id, a, "vault").get(2, TimeUnit.SECONDS);
            return ok ? new EconomyResponse(a, balance(id), EconomyResponse.ResponseType.SUCCESS, null) : fail("Nicht genug Coins");
        } catch (Exception e) {
            return fail("Datenbankfehler");
        }
    }

    private EconomyResponse deposit(UUID id, double amount) {
        if (id == null) return fail("Spieler unbekannt");
        if (amount < 0) return fail("Negativer Betrag");
        long a = (long) Math.floor(amount);
        try {
            boolean ok = plugin.coins().addCoins(id, a, "vault").get(2, TimeUnit.SECONDS);
            return ok ? new EconomyResponse(a, balance(id), EconomyResponse.ResponseType.SUCCESS, null) : fail("Spieler unbekannt");
        } catch (Exception e) {
            return fail("Datenbankfehler");
        }
    }

    private static EconomyResponse fail(String msg) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, msg);
    }

    private static EconomyResponse noBank() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Keine Banken");
    }

    // ---------------------------------------------------------------- Vault

    @Override public boolean isEnabled() { return plugin.isEnabled(); }
    @Override public String getName() { return "LobbySystem"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 0; }
    @Override public String format(double amount) { return Text.formatNumber((long) amount) + " Coins"; }
    @Override public String currencyNamePlural() { return "Coins"; }
    @Override public String currencyNameSingular() { return "Coin"; }

    @Override public boolean hasAccount(String name) { return uuid(name) != null; }
    @Override public boolean hasAccount(String name, String world) { return hasAccount(name); }
    @Override public boolean hasAccount(OfflinePlayer p) { return true; }
    @Override public boolean hasAccount(OfflinePlayer p, String world) { return true; }

    @Override public double getBalance(String name) { return balance(uuid(name)); }
    @Override public double getBalance(String name, String world) { return getBalance(name); }
    @Override public double getBalance(OfflinePlayer p) { return balance(p.getUniqueId()); }
    @Override public double getBalance(OfflinePlayer p, String world) { return getBalance(p); }

    @Override public boolean has(String name, double amount) { return getBalance(name) >= amount; }
    @Override public boolean has(String name, String world, double amount) { return has(name, amount); }
    @Override public boolean has(OfflinePlayer p, double amount) { return getBalance(p) >= amount; }
    @Override public boolean has(OfflinePlayer p, String world, double amount) { return has(p, amount); }

    @Override public EconomyResponse withdrawPlayer(String name, double amount) { return withdraw(uuid(name), amount); }
    @Override public EconomyResponse withdrawPlayer(String name, String world, double amount) { return withdrawPlayer(name, amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer p, double amount) { return withdraw(p.getUniqueId(), amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer p, String world, double amount) { return withdrawPlayer(p, amount); }

    @Override public EconomyResponse depositPlayer(String name, double amount) { return deposit(uuid(name), amount); }
    @Override public EconomyResponse depositPlayer(String name, String world, double amount) { return depositPlayer(name, amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer p, double amount) { return deposit(p.getUniqueId(), amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer p, String world, double amount) { return depositPlayer(p, amount); }

    @Override public boolean createPlayerAccount(String name) { return true; }
    @Override public boolean createPlayerAccount(String name, String world) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer p) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer p, String world) { return true; }

    @Override public EconomyResponse createBank(String name, String player) { return noBank(); }
    @Override public EconomyResponse deleteBank(String name) { return noBank(); }
    @Override public EconomyResponse bankBalance(String name) { return noBank(); }
    @Override public EconomyResponse bankHas(String name, double amount) { return noBank(); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return noBank(); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return noBank(); }
    @Override public EconomyResponse isBankOwner(String name, String playerName) { return noBank(); }
    @Override public EconomyResponse isBankMember(String name, String playerName) { return noBank(); }
    @Override public List<String> getBanks() { return List.of(); }
}
