package com.example.chat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Instant;

public final class TotpUtil {
    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;

    private TotpUtil() {
    }

    public static boolean isValidCode(String secretBase32, String code) {
        if (secretBase32 == null || secretBase32.isBlank() || code == null || !code.matches("\\d{6}")) {
            return false;
        }
        long counter = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        for (int offset = -1; offset <= 1; offset++) {
            String expected = generateCode(secretBase32, counter + offset);
            if (expected != null && expected.equals(code)) {
                return true;
            }
        }
        return false;
    }

    private static String generateCode(String secretBase32, long counter) {
        try {
            byte[] key = decodeBase32(secretBase32);
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));

            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return String.format("%0" + CODE_DIGITS + "d", otp);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            return null;
        }
    }

    private static byte[] decodeBase32(String input) {
        String normalized = input.trim().replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer buffer = ByteBuffer.allocate(normalized.length() * 5 / 8 + 1);

        int bits = 0;
        int value = 0;
        for (char c : normalized.toCharArray()) {
            int idx = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);
            if (idx < 0) {
                throw new IllegalArgumentException("Invalid Base32 secret");
            }
            value = (value << 5) | idx;
            bits += 5;
            if (bits >= 8) {
                buffer.put((byte) ((value >> (bits - 8)) & 0xFF));
                bits -= 8;
            }
        }

        buffer.flip();
        byte[] out = new byte[buffer.remaining()];
        buffer.get(out);
        return out;
    }
}
