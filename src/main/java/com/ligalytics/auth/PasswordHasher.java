package com.ligalytics.auth;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.stereotype.Component;

/**
 * Hash de contraseñas con PBKDF2-HMAC-SHA256 (JDK, sin dependencias), sal aleatoria por usuario y 210 000
 * iteraciones (recomendación de OWASP). Formato guardado: {@code pbkdf2$iteraciones$sal$hash}.
 */
@Component
public class PasswordHasher {

    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final SecureRandom random = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derived);
    }

    public boolean matches(String password, String stored) {
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
            return false;
        }
        byte[] expected = Base64.getDecoder().decode(parts[3]);
        byte[] actual = derive(password, Base64.getDecoder().decode(parts[2]), Integer.parseInt(parts[1]));
        return MessageDigest.isEqual(expected, actual);
    }

    /** SHA-256 en hexadecimal (para guardar los tokens de sesión). */
    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS))
                    .getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo calcular el hash de la contraseña", ex);
        }
    }
}
