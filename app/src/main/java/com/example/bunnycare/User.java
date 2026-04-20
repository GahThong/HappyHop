package com.example.bunnycare;

public class User {

    private String username;
    private String email;
    private String breed;
    private String role;

    public User() {}

    public User(String username, String email, String breed, String role) {
        this.username = username;
        this.email = email;
        this.breed = breed;
        this.role = role;
    }

    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getBreed() { return breed; }
    public String getRole() { return role; }

    // Setters
    public void setUsername(String username) { this.username = username; }
    public void setEmail(String email) { this.email = email; }
    public void setBreed(String breed) { this.breed = breed; }
    public void setRole(String role) { this.role = role; }
}