package de.crafterteam.lobbysystem.license;

import de.crafterteam.lobbysystem.LobbySystem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class LicenseManager {

    private final LobbySystem plugin;
    private PublicKey publicKey;
    private PrivateKey ownerKey;
    private LicenseCodec.License active;

    public LicenseManager(LobbySystem plugin) {
        this.plugin = plugin;
    }

    /** @return true, wenn das Plugin starten darf */
    public boolean check(String configuredKey) {
        try {
            publicKey = LicenseCodec.embeddedPublicKey();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Lizenz-Schlüssel des Plugins beschädigt!", e);
            return false;
        }
        Path ownerFile = plugin.getDataFolder().toPath().resolve("license-private.key");
        if (Files.exists(ownerFile)) {
            try {
                PrivateKey key = LicenseCodec.readPrivateKeyFile(ownerFile);
                if (LicenseCodec.matches(key, publicKey)) {
                    ownerKey = key;
                    plugin.getLogger().info("Owner-Modus aktiv – Lizenzen können mit /license create erstellt werden.");
                } else {
                    plugin.getLogger().warning("license-private.key passt nicht zu diesem Plugin und wird ignoriert.");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "license-private.key konnte nicht gelesen werden.", e);
            }
        }
        active = LicenseCodec.verify(publicKey, configuredKey);
        if (active != null && !active.expired()) {
            plugin.getLogger().info("Lizenz gültig – Inhaber: " + active.holder() + " (Ablauf: " + active.expiresText() + ")");
            return true;
        }
        if (ownerMode()) return true;
        if (active != null) {
            plugin.getLogger().severe("Deine Lizenz ist am " + active.expiresText() + " abgelaufen!");
        } else {
            plugin.getLogger().severe("Keine gültige Lizenz in der config.json eingetragen (\"license\")!");
        }
        plugin.getLogger().severe("Lizenzen erhältst du ausschließlich von " + LicenseCodec.OWNER + ".");
        return false;
    }

    public boolean ownerMode() {
        return ownerKey != null;
    }

    public PublicKey publicKey() {
        return publicKey;
    }

    public LicenseCodec.License active() {
        return active;
    }

    public CompletableFuture<LicenseCodec.License> create(String holder, int days, String createdBy) {
        if (ownerKey == null) return CompletableFuture.failedFuture(new IllegalStateException("Kein Owner-Schlüssel"));
        final LicenseCodec.License license;
        try {
            license = LicenseCodec.create(ownerKey, holder, days, createdBy);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
        return plugin.database().submit(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "insert into ls_licenses (id, holder, created, expires, created_by, license_key) values (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, license.id());
                ps.setString(2, license.holder());
                ps.setLong(3, license.created());
                ps.setLong(4, license.expires());
                ps.setString(5, license.createdBy());
                ps.setString(6, license.key());
                ps.executeUpdate();
            }
            return license;
        });
    }

    public CompletableFuture<List<LicenseCodec.License>> list() {
        return plugin.database().submit(con -> {
            List<LicenseCodec.License> out = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement("select * from ls_licenses order by created desc");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new LicenseCodec.License(rs.getString("id"), rs.getString("holder"), rs.getLong("created"),
                            rs.getLong("expires"), rs.getString("created_by"), rs.getString("license_key")));
                }
            }
            return out;
        });
    }
}
