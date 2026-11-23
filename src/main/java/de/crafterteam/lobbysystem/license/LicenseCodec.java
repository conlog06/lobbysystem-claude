package de.crafterteam.lobbysystem.license;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

/**
 * Lizenzschlüssel = "LS2-" + Base64Url(Payload) + "." + Base64Url(Ed25519-Signatur).
 * Im Plugin steckt NUR der öffentliche Schlüssel → niemand außer dem Besitzer der
 * license-private.key (SujucDerBreite) kann gültige Lizenzen erzeugen.
 * Reine JDK-Klasse, damit sie auch per "java -jar" ohne Server läuft.
 */
public final class LicenseCodec {

    public static final String OWNER = "SujucDerBreite";
    private static final String PREFIX = "LS2-";
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    public record License(String id, String holder, long created, long expires, String createdBy, String key) {
        public boolean expired() {
            return expires > 0 && System.currentTimeMillis() > expires;
        }

        public String expiresText() {
            return expires <= 0 ? "unbegrenzt" : DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                    .withZone(ZoneId.of("Europe/Berlin")).format(Instant.ofEpochMilli(expires));
        }
    }

    private LicenseCodec() {
    }

    public static PublicKey embeddedPublicKey() throws Exception {
        try (InputStream in = LicenseCodec.class.getResourceAsStream("/license/public.key")) {
            if (in == null) throw new IOException("license/public.key fehlt im Plugin");
            return publicKey(new String(in.readAllBytes(), StandardCharsets.UTF_8).trim());
        }
    }

    public static PublicKey publicKey(String b64) throws Exception {
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
    }

    public static PrivateKey privateKey(String b64) throws Exception {
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64)));
    }

    /** Liest "private=…" aus der license-private.key */
    public static PrivateKey readPrivateKeyFile(Path file) throws Exception {
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.startsWith("private=")) return privateKey(line.substring(8).trim());
        }
        throw new IOException("Kein 'private=' Eintrag in " + file.getFileName());
    }

    public static License create(PrivateKey key, String holder, int days, String createdBy) throws Exception {
        String cleanHolder = holder.replace("|", "").trim();
        if (cleanHolder.isEmpty() || cleanHolder.length() > 64) throw new IllegalArgumentException("Ungültiger Inhaber");
        long now = System.currentTimeMillis();
        long expires = days <= 0 ? 0 : now + days * 86_400_000L;
        String id = UUID.randomUUID().toString();
        String payload = String.join("|", "2", id, cleanHolder, Long.toString(now), Long.toString(expires), createdBy);
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(key);
        sig.update(data);
        String licenseKey = PREFIX + ENC.encodeToString(data) + "." + ENC.encodeToString(sig.sign());
        return new License(id, cleanHolder, now, expires, createdBy, licenseKey);
    }

    /** @return Lizenz bei gültiger Signatur (Ablauf separat prüfen), sonst null */
    public static License verify(PublicKey key, String licenseKey) {
        try {
            if (licenseKey == null) return null;
            String k = licenseKey.trim();
            if (!k.startsWith(PREFIX)) return null;
            String[] parts = k.substring(PREFIX.length()).split("\\.");
            if (parts.length != 2) return null;
            byte[] data = DEC.decode(parts[0]);
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(key);
            sig.update(data);
            if (!sig.verify(DEC.decode(parts[1]))) return null;
            String[] f = new String(data, StandardCharsets.UTF_8).split("\\|");
            if (f.length != 6 || !"2".equals(f[0])) return null;
            return new License(f[1], f[2], Long.parseLong(f[3]), Long.parseLong(f[4]), f[5], k);
        } catch (Exception e) {
            return null;
        }
    }

    /** Prüft, ob ein Private-Key zum eingebauten Public-Key passt. */
    public static boolean matches(PrivateKey priv, PublicKey pub) {
        try {
            byte[] test = "lobbysystem-ownercheck".getBytes(StandardCharsets.UTF_8);
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(priv);
            s.update(test);
            byte[] signed = s.sign();
            Signature v = Signature.getInstance("Ed25519");
            v.initVerify(pub);
            v.update(test);
            return v.verify(signed);
        } catch (Exception e) {
            return false;
        }
    }
}
