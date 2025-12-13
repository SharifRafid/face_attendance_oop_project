package com.example.myapplication;

/**
 * Simple data class to store a person's name and their facial features.
 */
public class FaceData {
    public String name;
    public float[] features;
    public long studentId; // Optional: for linking to Student model

    public FaceData(String name, float[] features) {
        this.name = name;
        this.features = features;
    }
}
