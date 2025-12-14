package com.example.myapplication;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication.models.*;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Activity for taking attendance using face recognition.
 * Teacher selects class and date, then camera auto-recognizes students.
 */
public class GiveAttendanceActivity extends AppCompatActivity {

    private AttendanceDatabase database;
    private CameraDialogHelper cameraHelper;

    private Spinner spinnerClass;
    private TextView tvDate, tvAttendanceStatus;
    private PreviewView previewView;
    private Button btnStartCamera, btnSave;
    private View layoutCamera;

    private List<BaseClass> classes = new ArrayList<>();
    private List<Student> students = new ArrayList<>();
    private Set<Long> presentStudents = new HashSet<>();
    private String selectedDate;
    private boolean cameraStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_give_attendance);

        database = new AttendanceDatabase(this);
        cameraHelper = new CameraDialogHelper(this);

        initViews();
        loadClasses();
        setCurrentDate();
    }

    private void initViews() {
        spinnerClass = findViewById(R.id.spinnerClass);
        tvDate = findViewById(R.id.tvDate);
        tvAttendanceStatus = findViewById(R.id.tvAttendanceStatus);
        previewView = findViewById(R.id.previewView);
        btnStartCamera = findViewById(R.id.btnStartCamera);
        btnSave = findViewById(R.id.btnSave);
        layoutCamera = findViewById(R.id.layoutCamera);

        tvDate.setOnClickListener(v -> showDatePicker());
        btnStartCamera.setOnClickListener(v -> startAttendance());
        btnSave.setOnClickListener(v -> saveAttendance());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        spinnerClass.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                loadStudentsForClass();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void loadClasses() {
        classes = database.getAllClasses();
        List<String> classNames = new ArrayList<>();
        for (BaseClass c : classes) {
            classNames.add(c.toString());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, classNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerClass.setAdapter(adapter);
    }

    private void loadStudentsForClass() {
        if (classes.isEmpty() || spinnerClass.getSelectedItemPosition() < 0)
            return;
        BaseClass selectedClass = classes.get(spinnerClass.getSelectedItemPosition());
        students = database.getStudentsByClass(selectedClass.getId());
        presentStudents.clear();
        updateStatus();
    }

    private void setCurrentDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        selectedDate = sdf.format(new Date());
        tvDate.setText(selectedDate);
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            tvDate.setText(selectedDate);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void startAttendance() {
        if (classes.isEmpty()) {
            Toast.makeText(this, "No classes available", Toast.LENGTH_SHORT).show();
            return;
        }
        if (students.isEmpty()) {
            Toast.makeText(this, "No students in this class", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!hasCameraPermission())
            return;

        layoutCamera.setVisibility(View.VISIBLE);
        btnStartCamera.setVisibility(View.GONE);
        cameraStarted = true;

        cameraHelper.startAttendanceCamera(previewView, students, student -> {
            if (!presentStudents.contains(student.getId())) {
                presentStudents.add(student.getId());
                updateStatus();
                Toast.makeText(this, "Present: " + student.getName(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateStatus() {
        tvAttendanceStatus.setText("Present: " + presentStudents.size() + " / " + students.size());
    }

    private void saveAttendance() {
        if (classes.isEmpty() || students.isEmpty()) {
            Toast.makeText(this, "No data to save", Toast.LENGTH_SHORT).show();
            return;
        }

        BaseClass selectedClass = classes.get(spinnerClass.getSelectedItemPosition());

        // Check if attendance already exists
        if (database.getAttendance(selectedClass.getId(), selectedDate) != null) {
            Toast.makeText(this, "Attendance already exists for this date", Toast.LENGTH_SHORT).show();
            return;
        }

        Attendance attendance = new Attendance(selectedClass.getId(), selectedDate);

        for (Student s : students) {
            AttendanceRecord record = new AttendanceRecord(s.getId(), presentStudents.contains(s.getId()));
            attendance.addRecord(record);
        }

        database.insertAttendance(attendance);
        Toast.makeText(this, "Attendance saved!", Toast.LENGTH_SHORT).show();

        if (cameraStarted) {
            cameraHelper.stopCamera();
        }
        finish();
    }

    private boolean hasCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.CAMERA }, 100);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startAttendance();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraHelper.close();
        database.close();
    }
}
