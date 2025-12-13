package com.example.myapplication.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Model class representing an Attendance session.
 * Demonstrates composition - contains a list of AttendanceRecords.
 */
public class Attendance {
    private long id;
    private long classId;
    private String className;
    private String date; // Format: yyyy-MM-dd
    private List<AttendanceRecord> records;

    public Attendance() {
        this.records = new ArrayList<>();
    }

    public Attendance(long classId, String date) {
        this.classId = classId;
        this.date = date;
        this.records = new ArrayList<>();
    }

    // Getters
    public long getId() { return id; }
    public long getClassId() { return classId; }
    public String getClassName() { return className; }
    public String getDate() { return date; }
    public List<AttendanceRecord> getRecords() { return records; }

    // Setters
    public void setId(long id) { this.id = id; }
    public void setClassId(long classId) { this.classId = classId; }
    public void setClassName(String className) { this.className = className; }
    public void setDate(String date) { this.date = date; }
    public void setRecords(List<AttendanceRecord> records) { this.records = records; }

    // Helper methods
    public void addRecord(AttendanceRecord record) {
        this.records.add(record);
    }

    public int getPresentCount() {
        int count = 0;
        for (AttendanceRecord record : records) {
            if (record.isPresent()) count++;
        }
        return count;
    }

    public int getTotalCount() {
        return records.size();
    }
}

