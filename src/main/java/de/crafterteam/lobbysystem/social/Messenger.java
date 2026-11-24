package de.crafterteam.lobbysystem.social;

import de.crafterteam.lobbysystem.LobbySystem;
import de.crafterteam.lobbysystem.lobby.LobbyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Nachrichten & Serverwechsel über den Proxy (BungeeCord-Channel – funktioniert auch mit Velocity):
 * "MessageRaw" erreicht Spieler auf jedem Server, "ConnectOther" verschiebt andere Spieler.
 */
public final class Messenger {

    private final LobbySystem plugin;

    public Messenger(LobbySystem plugin) {
        this.plugin = plugin;
    }

    /** Sendet an einen Spieler – lokal direkt, sonst über den Proxy. */
    public void send(String playerName, Component message) {
        Player local = Bukkit.getPlayerExact(playerName);
        if (local != null) {
            local.sendMessage(message);
            return;
        }
        Player carrier = carrier();
        if (carrier == null) return;
        write(carrier, "MessageRaw", playerName, GsonComponentSerializer.gson().serialize(message));
    }

    public void connectOther(String playerName, String server) {
        Player local = Bukkit.getPlayerExact(playerName);
        if (local != null) {
            plugin.lobby().connect(local, server);
            return;
        }
        Player carrier = carrier();
        if (carrier != null) write(carrier, "ConnectOther", playerName, server);
    }

    private Player carrier() {
        return Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
    }

    private void write(Player via, String... parts) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
            for (String p : parts) out.writeUTF(p);
            via.sendPluginMessage(plugin, LobbyManager.BUNGEE_CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Proxy-Nachricht fehlgeschlagen: " + e.getMessage());
        }
    }

    /** Aus einem async-Kontext sicher im Main-Thread ausführen. */
    public void sync(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }
}
