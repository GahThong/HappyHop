package com.example.bunnycare;

public class User {

    private String username;
    private String email;
    private String breed;
    private String status;
    private String role;

    // REQUIRED for Firebase
    public User() {}

    // FULL constructor (use this only)
    public User(String username, String email, String breed, String status, String role) {
        this.username = username;
        this.email = email;
        this.breed = breed;
        this.status = status;
        this.role = role;
    }

    // Getters
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getBreed() { return breed; }
    public String getStatus() { return status; }
    public String getRole() { return role; }

    // Setters
    public void setUsername(String username) { this.username = username; }
    public void setEmail(String email) { this.email = email; }
    public void setBreed(String breed) { this.breed = breed; }
    public void setStatus(String status) { this.status = status; }
    public void setRole(String role) { this.role = role; }
}