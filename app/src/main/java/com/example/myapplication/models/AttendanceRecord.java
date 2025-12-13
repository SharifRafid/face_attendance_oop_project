package com.example.myapplication.models;

/**
 * Model class representing a single attendance record for a student.
 * Demonstrates composition - used within Attendance class.
 */
public class AttendanceRecord {
    private long id;
    private long studentId;
    private String studentName;
    private String studentIdNumber;
    private boolean present;

    public AttendanceRecord() {}

    public AttendanceRecord(long studentId, boolean present) {
        this.studentId = studentId;
        this.present = present;
    }

    // Getters
    public long getId() { return id; }
    public long getStudentId() { return studentId; }
    public String getStudentName() { return studentName; }
    public String getStudentIdNumber() { return studentIdNumber; }
    public boolean isPresent() { return present; }

    // Setters
    public void setId(long id) { this.id = id; }
    public void setStudentId(long studentId) { this.studentId = studentId; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public void setStudentIdNumber(String studentIdNumber) { this.studentIdNumber = studentIdNumber; }
    public void setPresent(boolean present) { this.present = present; }
}

