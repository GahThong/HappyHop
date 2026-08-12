package com.example.bunnycare;

public class WeightEntry {
    public double value;
    public long timestamp;

    public WeightEntry() {}

    public WeightEntry(double value, long timestamp) {
        this.value = value;
        this.timestamp = timestamp;
    }
}