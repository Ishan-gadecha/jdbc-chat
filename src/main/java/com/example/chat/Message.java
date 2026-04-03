package com.example.chat;

import java.time.LocalDateTime;

public final class Message {
    private final long id;
    private final String author;
    private final String recipient;
    private final String text;
    private final LocalDateTime timestamp;

    public Message(long id, String author, String recipient, String text, LocalDateTime timestamp) {
        this.id = id;
        this.author = author;
        this.recipient = recipient;
        this.text = text;
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

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
