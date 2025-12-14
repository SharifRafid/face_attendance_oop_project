package com.example.myapplication;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.myapplication.models.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Database helper class for managing all attendance-related data.
 * Isolated from UI logic for better separation of concerns.
 */
public class AttendanceDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "attendance.db";
    private static final int DATABASE_VERSION = 1;

    // Table names
    private static final String TABLE_TEACHERS = "teachers";
    private static final String TABLE_CLASSES = "classes";
    private static final String TABLE_STUDENTS = "students";
    private static final String TABLE_ATTENDANCE = "attendance";
    private static final String TABLE_ATTENDANCE_RECORDS = "attendance_records";

    public AttendanceDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Teachers table
        db.execSQL("CREATE TABLE " + TABLE_TEACHERS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "initial TEXT NOT NULL, " +
                "pin TEXT NOT NULL)");

        // Classes table
        db.execSQL("CREATE TABLE " + TABLE_CLASSES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "section TEXT NOT NULL, " +
                "class_type TEXT NOT NULL, " +
                "teacher_id INTEGER, " +
                "FOREIGN KEY(teacher_id) REFERENCES " + TABLE_TEACHERS + "(id))");

        // Students table
        db.execSQL("CREATE TABLE " + TABLE_STUDENTS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "student_id TEXT NOT NULL, " +
                "section TEXT NOT NULL, " +
                "class_id INTEGER, " +
                "face_features BLOB, " +
                "FOREIGN KEY(class_id) REFERENCES " + TABLE_CLASSES + "(id))");

        // Attendance table
        db.execSQL("CREATE TABLE " + TABLE_ATTENDANCE + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "class_id INTEGER NOT NULL, " +
                "date TEXT NOT NULL, " +
                "FOREIGN KEY(class_id) REFERENCES " + TABLE_CLASSES + "(id))");

        // Attendance records table
        db.execSQL("CREATE TABLE " + TABLE_ATTENDANCE_RECORDS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "attendance_id INTEGER NOT NULL, " +
                "student_id INTEGER NOT NULL, " +
                "present INTEGER NOT NULL, " +
                "FOREIGN KEY(attendance_id) REFERENCES " + TABLE_ATTENDANCE + "(id), " +
                "FOREIGN KEY(student_id) REFERENCES " + TABLE_STUDENTS + "(id))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ATTENDANCE_RECORDS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ATTENDANCE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_STUDENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CLASSES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TEACHERS);
        onCreate(db);
    }

    // ==================== TEACHER OPERATIONS ====================

    public long insertTeacher(Teacher teacher) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", teacher.getName());
        values.put("initial", teacher.getInitial());
        values.put("pin", teacher.getPin());
        return db.insert(TABLE_TEACHERS, null, values);
    }

    public Teacher getTeacher() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_TEACHERS, null, null, null, null, null, "id DESC", "1");
        Teacher teacher = null;
        if (cursor.moveToFirst()) {
            teacher = new Teacher();
            teacher.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
            teacher.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            teacher.setInitial(cursor.getString(cursor.getColumnIndexOrThrow("initial")));
            teacher.setPin(cursor.getString(cursor.getColumnIndexOrThrow("pin")));
        }
        cursor.close();
        return teacher;
    }

    // ==================== CLASS OPERATIONS ====================

    public long insertClass(BaseClass baseClass) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", baseClass.getName());
        values.put("section", baseClass.getSection());
        values.put("class_type", baseClass.getClassType());
        values.put("teacher_id", baseClass.getTeacherId());
        return db.insert(TABLE_CLASSES, null, values);
    }

    public List<BaseClass> getAllClasses() {
        List<BaseClass> classes = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_CLASSES, null, null, null, null, null, null);

        while (cursor.moveToNext()) {
            String type = cursor.getString(cursor.getColumnIndexOrThrow("class_type"));
            BaseClass baseClass = "Lab".equals(type) ? new LabClass() : new TheoryClass();
            baseClass.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
            baseClass.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            baseClass.setSection(cursor.getString(cursor.getColumnIndexOrThrow("section")));
            baseClass.setTeacherId(cursor.getLong(cursor.getColumnIndexOrThrow("teacher_id")));
            classes.add(baseClass);
        }
        cursor.close();
        return classes;
    }

    public BaseClass getClassById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_CLASSES, null, "id=?", new String[] { String.valueOf(id) }, null, null, null);
        BaseClass baseClass = null;
        if (cursor.moveToFirst()) {
            String type = cursor.getString(cursor.getColumnIndexOrThrow("class_type"));
            baseClass = "Lab".equals(type) ? new LabClass() : new TheoryClass();
            baseClass.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
            baseClass.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            baseClass.setSection(cursor.getString(cursor.getColumnIndexOrThrow("section")));
            baseClass.setTeacherId(cursor.getLong(cursor.getColumnIndexOrThrow("teacher_id")));
        }
        cursor.close();
        return baseClass;
    }

    // ==================== STUDENT OPERATIONS ====================

    public long insertStudent(Student student) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", student.getName());
        values.put("student_id", student.getStudentId());
        values.put("section", student.getSection());
        values.put("class_id", student.getClassId());
        if (student.getFaceFeatures() != null) {
            values.put("face_features", floatArrayToBytes(student.getFaceFeatures()));
        }
        return db.insert(TABLE_STUDENTS, null, values);
    }

    public List<Student> getStudentsByClass(long classId) {
        List<Student> students = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_STUDENTS, null, "class_id=?",
                new String[] { String.valueOf(classId) }, null, null, null);

        while (cursor.moveToNext()) {
            Student student = new Student();
            student.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
            student.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            student.setStudentId(cursor.getString(cursor.getColumnIndexOrThrow("student_id")));
            student.setSection(cursor.getString(cursor.getColumnIndexOrThrow("section")));
            student.setClassId(cursor.getLong(cursor.getColumnIndexOrThrow("class_id")));
            byte[] features = cursor.getBlob(cursor.getColumnIndexOrThrow("face_features"));
            if (features != null) {
                student.setFaceFeatures(bytesToFloatArray(features));
            }
            students.add(student);
        }
        cursor.close();
        return students;
    }

    public List<Student> getAllStudents() {
        List<Student> students = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_STUDENTS, null, null, null, null, null, null);

        while (cursor.moveToNext()) {
            Student student = new Student();
            student.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
            student.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            student.setStudentId(cursor.getString(cursor.getColumnIndexOrThrow("student_id")));
            student.setSection(cursor.getString(cursor.getColumnIndexOrThrow("section")));
            student.setClassId(cursor.getLong(cursor.getColumnIndexOrThrow("class_id")));
            byte[] features = cursor.getBlob(cursor.getColumnIndexOrThrow("face_features"));
            if (features != null) {
                student.setFaceFeatures(bytesToFloatArray(features));
            }
            students.add(student);
        }
        cursor.close();
        return students;
    }

    // ==================== ATTENDANCE OPERATIONS ====================

    public long insertAttendance(Attendance attendance) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("class_id", attendance.getClassId());
        values.put("date", attendance.getDate());
        long attendanceId = db.insert(TABLE_ATTENDANCE, null, values);

        // Insert attendance records
        for (AttendanceRecord record : attendance.getRecords()) {
            ContentValues recordValues = new ContentValues();
            recordValues.put("attendance_id", attendanceId);
            recordValues.put("student_id", record.getStudentId());
            recordValues.put("present", record.isPresent() ? 1 : 0);
            db.insert(TABLE_ATTENDANCE_RECORDS, null, recordValues);
        }
        return attendanceId;
    }

    public Attendance getAttendance(long classId, String date) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_ATTENDANCE, null, "class_id=? AND date=?",
                new String[] { String.valueOf(classId), date }, null, null, null);

        Attendance attendance = null;
        if (cursor.moveToFirst()) {
            attendance = new Attendance();
            attendance.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
            attendance.setClassId(cursor.getLong(cursor.getColumnIndexOrThrow("class_id")));
            attendance.setDate(cursor.getString(cursor.getColumnIndexOrThrow("date")));

            // Get class name
            BaseClass baseClass = getClassById(classId);
            if (baseClass != null) {
                attendance.setClassName(baseClass.getName() + " - " + baseClass.getSection());
            }

            // Get attendance records
            attendance.setRecords(getAttendanceRecords(attendance.getId()));
        }
        cursor.close();
        return attendance;
    }

    public void deleteAttendance(long attendanceId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_ATTENDANCE_RECORDS, "attendance_id=?", new String[] { String.valueOf(attendanceId) });
        db.delete(TABLE_ATTENDANCE, "id=?", new String[] { String.valueOf(attendanceId) });
    }

    private List<AttendanceRecord> getAttendanceRecords(long attendanceId) {
        List<AttendanceRecord> records = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String query = "SELECT ar.*, s.name as student_name, s.student_id as student_id_number " +
                "FROM " + TABLE_ATTENDANCE_RECORDS + " ar " +
                "JOIN " + TABLE_STUDENTS + " s ON ar.student_id = s.id " +
                "WHERE ar.attendance_id = ?";

        Cursor cursor = db.rawQuery(query, new String[] { String.valueOf(attendanceId) });

        while (cursor.moveToNext()) {
            AttendanceRecord record = new AttendanceRecord();
            record.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
            record.setStudentId(cursor.getLong(cursor.getColumnIndexOrThrow("student_id")));
            record.setStudentName(cursor.getString(cursor.getColumnIndexOrThrow("student_name")));
            record.setStudentIdNumber(cursor.getString(cursor.getColumnIndexOrThrow("student_id_number")));
            record.setPresent(cursor.getInt(cursor.getColumnIndexOrThrow("present")) == 1);
            records.add(record);
        }
        cursor.close();
        return records;
    }

    public List<String> getAttendanceDates(long classId) {
        List<String> dates = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_ATTENDANCE, new String[] { "date" }, "class_id=?",
                new String[] { String.valueOf(classId) }, null, null, "date DESC");

        while (cursor.moveToNext()) {
            dates.add(cursor.getString(0));
        }
        cursor.close();
        return dates;
    }

    // ==================== UTILITY METHODS ====================

    private byte[] floatArrayToBytes(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4);
        buffer.order(ByteOrder.nativeOrder());
        for (float f : floats)
            buffer.putFloat(f);
        return buffer.array();
    }

    private float[] bytesToFloatArray(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.nativeOrder());
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++)
            floats[i] = buffer.getFloat();
        return floats;
    }
}
