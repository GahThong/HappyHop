package com.example.bunnycare;

public class posts {
    private String post;
    private String postImage;
    private String posterId;
    private String posterName;
    private int likesCount;

    public posts(String post, String postImage, String posterId, String posterName, int likesCount) {
        this.post = post;
        this.postImage = postImage;
        this.posterId = posterId;
        this.posterName = posterName;
        this.likesCount = likesCount;
    }

    public posts() {}

    public String getPost() { return post; }
    public String getPostImage() { return postImage; }
    public String getPosterId() { return posterId; }
    public String getPosterName() { return posterName; }
    public int getLikesCount() { return likesCount; }

    public void setPost(String post) { this.post = post; }
    public void setPostImage(String postImage) { this.postImage = postImage; }
    public void setPosterId(String posterId) { this.posterId = posterId; }
    public void setPosterName(String posterName) { this.posterName = posterName; }
    public void setLikesCount(int likesCount) { this.likesCount = likesCount; }
}