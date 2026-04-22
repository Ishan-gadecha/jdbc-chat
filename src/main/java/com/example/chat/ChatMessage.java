package com.example.chat;

public class ChatMessage {
    private int id;
    private String author;
    private String text;
    private String recipient;
    private String timestamp;
    private String mediaData;
    private String mediaMime;

    public ChatMessage() {
    }

    public ChatMessage(int id, String author, String text, String recipient, String timestamp, String mediaData, String mediaMime) {
        this.id = id;
        this.author = author;
        this.text = text;
        this.recipient = recipient;
        this.timestamp = timestamp;
        this.mediaData = mediaData;
        this.mediaMime = mediaMime;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getHandle() {
        return author;
    }

    public void setHandle(String handle) {
        this.author = handle;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getMessage() {
        return text;
    }

    public void setMessage(String message) {
        this.text = message;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getMediaData() {
        return mediaData;
    }

    public void setMediaData(String mediaData) {
        this.mediaData = mediaData;
    }

    public String getMediaMime() {
        return mediaMime;
    }

    public void setMediaMime(String mediaMime) {
        this.mediaMime = mediaMime;
    }
}
