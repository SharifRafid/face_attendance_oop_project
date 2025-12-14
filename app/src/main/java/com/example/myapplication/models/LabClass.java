package com.example.myapplication.models;

/**
 * LabClass extends SchoolClass.
 * Demonstrates inheritance - inherits common properties from SchoolClass.
 */

public class LabClass extends BaseClass {

    public LabClass() {
        super();
    }

    public LabClass(String name, String section, long teacherId) {
        super(name, section, teacherId);
    }

    @Override
    public String getClassType() {
        return "Lab";
    }
}

