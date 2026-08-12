package com.example.bunnycare;

public class Rabbit {

    private String id;
    private String rabbitName;
    private String breed;
    private String age;
    private String weight;
    private String lastFed;
    private String lastDrink;
    private String imageUrl;
    private String ownerId;
    private String aiSummary;
    private boolean hasQr;

    public Rabbit() {
    }

    public Rabbit(
            String id,
            String rabbitName,
            String breed,
            String age,
            String weight,
            String lastFed,
            String lastDrink,
            String imageUrl,
            String ownerId,
            String aiSummary,
            boolean hasQr
    ) {
        this.id = id;
        this.rabbitName = rabbitName;
        this.breed = breed;
        this.age = age;
        this.weight = weight;
        this.lastFed = lastFed;
        this.lastDrink = lastDrink;
        this.imageUrl = imageUrl;
        this.ownerId = ownerId;
        this.aiSummary = aiSummary;
        this.hasQr = hasQr;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getWeight() {
        return weight;
    }

    public void setWeight(String weight) {
        this.weight = weight;
    }

    public String getLastFed() {
        return lastFed;
    }

    public void setLastFed(String lastFed) {
        this.lastFed = lastFed;
    }

    public String getLastDrink() {
        return lastDrink;
    }

    public void setLastDrink(String lastDrink) {
        this.lastDrink = lastDrink;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public void setAiSummary(String aiSummary) {
        this.aiSummary = aiSummary;
    }

    public boolean isHasQr() {
        return hasQr;
    }

    public void setHasQr(boolean hasQr) {
        this.hasQr = hasQr;
    }

    public boolean isHealthy() {
        return true;
    }

    public String getBreedAgeLabel() {

        String breedText =
                breed == null || breed.trim().isEmpty()
                        ? "Unknown breed"
                        : breed;

        String ageText =
                age == null || age.trim().isEmpty()
                        ? ""
                        : " • " + age;

        return breedText + ageText;
    }
}