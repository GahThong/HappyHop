package com.example.bunnycare;

public class Rabbit {

    private String id;
    private String ownerId;
    private String rabbitName;
    private String breed;
    private String age;          // display string, e.g. "1.5 yrs" or "8 months"
    private String imageUrl;
    private String lastFedFood;
    private String lastDrink;
    private String qrImage;
    private boolean hasQr;

    // Drives the status dot color in the list: true = green (healthy/ok),
    // false = red (needs attention). Defaults to true so newly added
    // rabbits show green until flagged otherwise.
    private boolean healthy = true;

    public Rabbit() {
        // Required empty constructor for Firestore
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getRabbitName() {
        return rabbitName;
    }

    public void setRabbitName(String rabbitName) {
        this.rabbitName = rabbitName;
    }

    public String getBreed() {
        return breed;
    }

    public void setBreed(String breed) {
        this.breed = breed;
    }

    public String getAge() {
        return age;
    }

    public void setAge(String age) {
        this.age = age;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getLastFedFood() {
        return lastFedFood;
    }

    public void setLastFedFood(String lastFedFood) {
        this.lastFedFood = lastFedFood;
    }

    public String getLastDrink() {
        return lastDrink;
    }

    public void setLastDrink(String lastDrink) {
        this.lastDrink = lastDrink;
    }

    public String getQrImage() {
        return qrImage;
    }

    public void setQrImage(String qrImage) {
        this.qrImage = qrImage;
    }

    public boolean isHasQr() {
        return hasQr;
    }

    public void setHasQr(boolean hasQr) {
        this.hasQr = hasQr;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    // Convenience for the list card: "Holland Lop · 1.5 yrs"
    public String getBreedAgeLabel() {
        boolean hasBreed = breed != null && !breed.isEmpty();
        boolean hasAge = age != null && !age.isEmpty();

        if (hasBreed && hasAge) {
            return breed + " · " + age;
        } else if (hasBreed) {
            return breed;
        } else if (hasAge) {
            return age;
        } else {
            return "";
        }
    }
}