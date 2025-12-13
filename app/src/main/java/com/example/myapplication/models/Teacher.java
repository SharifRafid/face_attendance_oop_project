package com.example.myapplication.models;

/**
 * Model class representing a Teacher entity.
 * Demonstrates encapsulation with private fields and public accessors.
 */
public class Teacher {
    private long id;
    private String name;
    private String initial;
    private String pin;

    public Teacher() {}

    public Teacher(String name, String initial, String pin) {
        this.name = name;
        this.initial = initial;
        this.pin = pin;
    }

    // Getters
    public long getId() { return id; }
    public String getName() { return name; }
    public String getInitial() { return initial; }
    public String getPin() { return pin; }

    // Setters
    public void setId(long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setInitial(String initial) { this.initial = initial; }
    public void setPin(String pin) { this.pin = pin; }

    @Override
    public String toString() {
        return name + " (" + initial + ")";
    }
}

