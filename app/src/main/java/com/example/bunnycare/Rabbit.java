package com.example.bunnycare;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

public class Rabbit {

    private String id;
    private String rabbitName;
    private String imageUrl;
    private boolean hasQr;

    public Rabbit() {}

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

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isHasQr() {
        return hasQr;
    }

    public void setHasQr(boolean hasQr) {
        this.hasQr = hasQr;
    }
}