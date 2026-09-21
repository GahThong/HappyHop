package com.example.bunnycare;

import com.google.firebase.Timestamp;

public class posts {

    private String id;
    private String post;
    private String postImage;
    private String posterId;
    private String posterName;
    private String profileImage;

    private int likesCount;
    private int commentCount;

    private Timestamp timestamp;

    private boolean verified;

    public posts() {
    }

    public posts(String id,
                 String post,
                 String postImage,
                 String posterId,
                 String posterName,
                 String profileImage,
                 int likesCount,
                 int commentCount,
                 Timestamp timestamp,
                 boolean verified) {

        this.id = id;
        this.post = post;
        this.postImage = postImage;
        this.posterId = posterId;
        this.posterName = posterName;
        this.profileImage = profileImage;
        this.likesCount = likesCount;
        this.commentCount = commentCount;
        this.timestamp = timestamp;
        this.verified = verified;
    }

    public String getId() {
        return id;
    }

    public String getPost() {
        return post;
    }

    public String getPostImage() {
        return postImage;
    }

    public String getPosterId() {
        return posterId;
    }

    public String getPosterName() {
        return posterName;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public int getLikesCount() {
        return likesCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setPost(String post) {
        this.post = post;
    }

    public void setPostImage(String postImage) {
        this.postImage = postImage;
    }

    public void setPosterId(String posterId) {
        this.posterId = posterId;
    }

    public void setPosterName(String posterName) {
        this.posterName = posterName;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public void setLikesCount(int likesCount) {
        this.likesCount = likesCount;
    }

    public void setCommentCount(int commentCount) {
        this.commentCount = commentCount;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }
}