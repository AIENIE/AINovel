package com.ainovel.app.adminauth;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AdminAuthCrypto {
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;
    private static final String TOTP_AAD_PREFIX = "ainovel-admin-totp-v1|";
    private final SecureRandom random = new SecureRandom();
    private final PasswordEncoder recoveryCodeEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final Map<String, SecretKeySpec> keys;

    public AdminAuthCrypto(AdminLocalAuthProperties properties, AdminAuthPolicySource policy) {
        if (!policy.requiresTotp()) {
            this.keys = Map.of();
            return;
        }
        this.keys = parseKeys(properties.getEncryptionKeys());
        if (keys.isEmpty()) {
            throw new IllegalStateException("ADMIN_TOTP_ENCRYPTION_KEYS must contain at least one 32-byte key");
        }
        if (!keys.containsKey(properties.getActiveKeyVersion())) {
            throw new IllegalStateException("ADMIN_TOTP_ACTIVE_KEY_VERSION is not present in ADMIN_TOTP_ENCRYPTION_KEYS");
        }
    }

    public EncryptedValue encrypt(String plaintext, String version) {
        SecretKeySpec key = keys.get(version);
        if (key == null) {
            throw new IllegalStateException("Unknown TOTP encryption key version");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD((TOTP_AAD_PREFIX + version).getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedValue(Base64.getEncoder().encodeToString(ciphertext), nonce, version);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt administrator credential", ex);
        }
    }

    public String decrypt(String ciphertext, byte[] nonce, String version) {
        SecretKeySpec key = keys.get(version);
        if (key == null || nonce == null || nonce.length != NONCE_BYTES) {
            throw new IllegalStateException("Unknown TOTP encryption key version");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD((TOTP_AAD_PREFIX + version).getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to decrypt administrator credential", ex);
        }
    }

    public String hashRecoveryCode(String code) {
        return recoveryCodeEncoder.encode(code);
    }

    public boolean matchesRecoveryCode(String code, String hash) {
        return recoveryCodeEncoder.matches(code, hash);
    }

    public String randomBase32(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base32.encode(value);
    }
    public String randomCode() {
        return randomBase32(16);
    }

    private Map<String, SecretKeySpec> parseKeys(String raw) {
        Map<String, SecretKeySpec> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String item : raw.split(",", -1)) {
            int separator = item.indexOf(':');
            if (separator <= 0 || separator != item.lastIndexOf(':')) {
                throw new IllegalStateException("Invalid ADMIN_TOTP_ENCRYPTION_KEYS entry");
            }
            String version = item.substring(0, separator);
            String encoded = item.substring(separator + 1);
            if (!version.matches("[A-Za-z0-9._-]{1,32}") || encoded.isEmpty()) {
                throw new IllegalStateException("Invalid ADMIN_TOTP_ENCRYPTION_KEYS entry");
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(encoded);
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("ADMIN_TOTP_ENCRYPTION_KEYS must use Base64", ex);
            }
            if (bytes.length != 32) {
                throw new IllegalStateException("ADMIN_TOTP_ENCRYPTION_KEYS values must be 32 bytes");
            }
            if (result.putIfAbsent(version, new SecretKeySpec(bytes, "AES")) != null) {
                throw new IllegalStateException("ADMIN_TOTP_ENCRYPTION_KEYS versions must be unique");
            }
        }
        return result;
    }

    public record EncryptedValue(String ciphertext, byte[] nonce, String keyVersion) {
        public EncryptedValue {
            nonce = nonce.clone();
        }

        @Override
        public byte[] nonce() {
            return nonce.clone();
        }
    }

    static final class Base32 {
        private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        static String encode(byte[] bytes) {
            StringBuilder out = new StringBuilder((bytes.length * 8 + 4) / 5);
            int buffer = 0;
            int bits = 0;
            for (byte value : bytes) {
                buffer = (buffer << 8) | (value & 0xff);
                bits += 8;
                while (bits >= 5) {
                    bits -= 5;
                    out.append(ALPHABET[(buffer >> bits) & 31]);
                }
            }
            if (bits > 0) out.append(ALPHABET[(buffer << (5 - bits)) & 31]);
            return out.toString();
        }
    }
}
