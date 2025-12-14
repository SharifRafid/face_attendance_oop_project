package com.example.myapplication.models;

/**
 * Abstract base class for School Classes.
 * Demonstrates abstraction - defines common behavior for all class types.
 */
public abstract class BaseClass {
    protected long id;
    protected String name;
    protected String section;
    protected long teacherId;

    public BaseClass() {}

    public BaseClass(String name, String section, long teacherId) {
        this.name = name;
        this.section = section;
        this.teacherId = teacherId;
    }

    // Abstract method - subclasses must implement
    public abstract String getClassType();

    // Getters
    public long getId() { return id; }
    public String getName() { return name; }
    public String getSection() { return section; }
    public long getTeacherId() { return teacherId; }

    // Setters
    public void setId(long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setSection(String section) { this.section = section; }
    public void setTeacherId(long teacherId) { this.teacherId = teacherId; }

    @Override
    public String toString() {
        return name + " - " + section + " (" + getClassType() + ")";
    }
}

