package de.crafterteam.lobbysystem.license;

import java.nio.file.Path;
import java.security.PrivateKey;

/**
 * Offline-Werkzeug ohne Server:
 *   java -jar LobbySystem.jar create <Inhaber> [Tage] [Pfad zur license-private.key]
 *   java -jar LobbySystem.jar verify <Lizenz>
 */
public final class LicenseTool {

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && args[0].equalsIgnoreCase("create")) {
            int days = args.length >= 3 ? Integer.parseInt(args[2]) : 0;
            Path keyFile = Path.of(args.length >= 4 ? args[3] : "license-private.key");
            PrivateKey key = LicenseCodec.readPrivateKeyFile(keyFile);
            if (!LicenseCodec.matches(key, LicenseCodec.embeddedPublicKey())) {
                System.err.println("Der Private-Key passt nicht zu diesem Plugin!");
                System.exit(1);
            }
            LicenseCodec.License l = LicenseCodec.create(key, args[1], days, LicenseCodec.OWNER);
            System.out.println("Inhaber: " + l.holder());
            System.out.println("Ablauf:  " + l.expiresText());
            System.out.println("Lizenz:  " + l.key());
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("verify")) {
            LicenseCodec.License l = LicenseCodec.verify(LicenseCodec.embeddedPublicKey(), args[1]);
            if (l == null) {
                System.out.println("UNGÜLTIG");
            } else {
                System.out.println((l.expired() ? "ABGELAUFEN" : "GÜLTIG") + " – Inhaber: " + l.holder() + ", Ablauf: " + l.expiresText());
            }
            return;
        }
        System.out.println("LobbySystem Lizenz-Tool");
        System.out.println("  java -jar LobbySystem.jar create <Inhaber> [Tage, 0 = unbegrenzt] [license-private.key]");
        System.out.println("  java -jar LobbySystem.jar verify <Lizenz>");
    }
}
