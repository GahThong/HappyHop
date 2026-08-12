package com.example.bunnycare;

import com.google.firebase.Timestamp;

public class AppNotification {

    private String id;
    private String message;
    private String recipientId;
    private boolean read;
    private Timestamp timestamp;

    public AppNotification() {
        // Required empty constructor for Firestore
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }
}