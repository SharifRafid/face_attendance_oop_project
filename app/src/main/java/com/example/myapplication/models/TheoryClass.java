package com.example.myapplication.models;

/**
 * TheoryClass extends SchoolClass.
 * Demonstrates inheritance - inherits common properties from SchoolClass.
 */
public class TheoryClass extends SchoolClass {

    public TheoryClass() {
        super();
    }

    public TheoryClass(String name, String section, long teacherId) {
        super(name, section, teacherId);
    }

    @Override
    public String getClassType() {
        return "Theory";
    }
}

