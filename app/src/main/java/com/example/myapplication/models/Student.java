package com.example.myapplication.models;

/**
 * Model class representing a Student entity.
 * Demonstrates encapsulation with private fields and public accessors.
 */
public class Student {
    private long id;
    private String name;
    private String studentId;
    private String section;
    private long classId;
    private float[] faceFeatures;

    public Student() {}

    public Student(String name, String studentId, String section, long classId, float[] faceFeatures) {
        this.name = name;
        this.studentId = studentId;
        this.section = section;
        this.classId = classId;
        this.faceFeatures = faceFeatures;
    }

    // Getters
    public long getId() { return id; }
    public String getName() { return name; }
    public String getStudentId() { return studentId; }
    public String getSection() { return section; }
    public long getClassId() { return classId; }
    public float[] getFaceFeatures() { return faceFeatures; }

    // Setters
    public void setId(long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public void setSection(String section) { this.section = section; }
    public void setClassId(long classId) { this.classId = classId; }
    public void setFaceFeatures(float[] faceFeatures) { this.faceFeatures = faceFeatures; }

    @Override
    public String toString() {
        return name + " (" + studentId + ")";
    }
}

