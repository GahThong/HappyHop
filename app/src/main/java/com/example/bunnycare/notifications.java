package com.example.bunnycare;

public class notifications {

    private String toUserId;
    private String fromUserName;
    private String type;
    private String postText;
    private Object timestamp;

    public notifications() {}

    public String getToUserId() { return toUserId; }
    public String getFromUserName() { return fromUserName; }
    public String getType() { return type; }
    public String getPostText() { return postText; }
    public Object getTimestamp() { return timestamp; }

    public void setToUserId(String toUserId) { this.toUserId = toUserId; }
    public void setFromUserName(String fromUserName) { this.fromUserName = fromUserName; }
    public void setType(String type) { this.type = type; }
    public void setPostText(String postText) { this.postText = postText; }
    public void setTimestamp(Object timestamp) { this.timestamp = timestamp; }
}