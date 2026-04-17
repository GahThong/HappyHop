package com.example.bunnycare;

public class posts {

    private String id;

    private String post;
    private String postImage;
    private String posterId;
    private String posterName;

    private int likesCount;
    private int commentCount;

    public posts() {}

    public posts(String id, String post, String postImage, String posterId,
                 String posterName, int likesCount, int commentCount) {
        this.id = id;
        this.post = post;
        this.postImage = postImage;
        this.posterId = posterId;
        this.posterName = posterName;
        this.likesCount = likesCount;
        this.commentCount = commentCount;
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

    public int getLikesCount() {
        return likesCount;
    }

    public int getCommentCount() {
        return commentCount;
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

    public void setLikesCount(int likesCount) {
        this.likesCount = likesCount;
    }

    public void setCommentCount(int commentCount) { //
        this.commentCount = commentCount;
    }
}