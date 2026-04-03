package com.example.chat;

import java.time.LocalDateTime;

public final class Message {
    private final long id;
    private final String author;
    private final String recipient;
    private final String text;
    private final String mediaMime;
    private final String mediaData;
    private final LocalDateTime timestamp;

    public Message(long id, String author, String recipient, String text, String mediaMime, String mediaData, LocalDateTime timestamp) {
        this.id = id;
        this.author = author;
        this.recipient = recipient;
        this.text = text;
        this.mediaMime = mediaMime;
        this.mediaData = mediaData;
        this.timestamp = timestamp;
    }

    public long getId() {
        return id;
    }

    public String getAuthor() {
        return author;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getText() {
        return text;
    }

    public String getMediaMime() {
        return mediaMime;
    }

    public String getMediaData() {
        return mediaData;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
