package com.example.chat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class StoragePaths {
    private StoragePaths() {
    }

    public static Path resolveDbPath() {
        String explicit = System.getenv("CHAT_DB_PATH");
        if (explicit != null && !explicit.isBlank()) {
            return Paths.get(explicit.trim());
        }

        String renderDisk = System.getenv("RENDER_DISK_PATH");
        if (renderDisk != null && !renderDisk.isBlank()) {
            return Paths.get(renderDisk.trim(), "chat.db");
        }

        Path varData = Paths.get("/var/data");
        if (Files.isDirectory(varData) && Files.isWritable(varData)) {
            return varData.resolve("chat.db");
        }

        return Paths.get(System.getProperty("user.home"), ".advanced-chat", "chat.db");
    }
}
