package com.example.chat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class PasswordUtil {
    private PasswordUtil() {
    }

    public static String hash(String handle, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(handle.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(password.getBytes(StandardCharsets.UTF_8));
            byte[] hashed = digest.digest();
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError("SHA-256 is required", ex);
        }
    }
}
