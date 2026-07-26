package com.ainovel.app.adminauth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;

public final class TotpService {
    private TotpService() {
    }

    public static boolean verify(String secret, String code, long nowSeconds, long lastAccepted, int digits, int period) {
        if (code == null || !code.matches("\\d{" + digits + "}")) {
            return false;
        }
        long current = Math.floorDiv(nowSeconds, period);
        for (long candidate = current - 1; candidate <= current + 1; candidate++) {
            if (candidate <= lastAccepted) {
                continue;
            }
            String expected = code(secret, candidate, digits);
            if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.US_ASCII))) {
                return true;
            }
        }
        return false;
    }

    public static long matchingTimestep(String secret, String code, long nowSeconds, long lastAccepted, int digits, int period) {
        if (code == null || !code.matches("\\d{" + digits + "}")) {
            return -1;
        }
        long current = Math.floorDiv(nowSeconds, period);
        for (long candidate = current - 1; candidate <= current + 1; candidate++) {
            if (candidate <= lastAccepted) {
                continue;
            }
            if (MessageDigest.isEqual(
                    code(secret, candidate, digits).getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII)
            )) {
                return candidate;
            }
        }
        return -1;
    }

    public static String code(String secret, long timestep, int digits) {
        try {
            byte[] key = decode(secret);
            byte[] message = ByteBuffer.allocate(8).putLong(timestep).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(message);
            int offset = hash[hash.length - 1] & 0xf;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int mod = (int) Math.pow(10, digits);
            return String.format(Locale.ROOT, "%0" + digits + "d", binary % mod);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to calculate TOTP", ex);
        }
    }

    private static byte[] decode(String value) {
        String normalized = value.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        return decodeBase32(normalized);
    }

    static byte[] decodeBase32(String value) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char ch : value.toCharArray()) {
            int index = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(ch);
            if (index < 0) {
                throw new IllegalArgumentException("Invalid Base32 secret");
            }
            buffer = (buffer << 5) | index;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 0xff);
            }
        }
        return out.toByteArray();
    }
}
