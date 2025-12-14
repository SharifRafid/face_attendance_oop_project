package com.example.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.models.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Main Activity for the Attendance Management System.
 * Handles teacher setup, class/student management, and navigation.
 */
public class MainActivity extends AppCompatActivity {

    private AttendanceDatabase database;
    private CameraDialogHelper cameraHelper;

    private LinearLayout layoutTeacherSetup, layoutDashboard;
    private EditText etTeacherName, etTeacherInitial, etTeacherPin;
    private TextView tvTeacherInfo, tvStatus;

    private Teacher currentTeacher;
    private List<BaseClass> classes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        database = new AttendanceDatabase(this);
        cameraHelper = new CameraDialogHelper(this);

        initViews();
        checkTeacherSetup();
    }

    private void initViews() {
        layoutTeacherSetup = findViewById(R.id.layoutTeacherSetup);
        layoutDashboard = findViewById(R.id.layoutDashboard);

        // Teacher setup views
        etTeacherName = findViewById(R.id.etTeacherName);
        etTeacherInitial = findViewById(R.id.etTeacherInitial);
        etTeacherPin = findViewById(R.id.etTeacherPin);
        findViewById(R.id.btnSaveTeacher).setOnClickListener(v -> saveTeacher());

        // Dashboard views
        tvTeacherInfo = findViewById(R.id.tvTeacherInfo);
        tvStatus = findViewById(R.id.tvStatus);

        findViewById(R.id.btnAddClass).setOnClickListener(v -> showAddClassDialog());
        findViewById(R.id.btnAddStudent).setOnClickListener(v -> showAddStudentDialog());
        findViewById(R.id.btnGiveAttendance).setOnClickListener(v -> 
                startActivity(new Intent(this, GiveAttendanceActivity.class)));
        findViewById(R.id.btnViewAttendance).setOnClickListener(v -> 
                startActivity(new Intent(this, ViewAttendanceActivity.class)));
    }

    private void checkTeacherSetup() {
        currentTeacher = database.getTeacher();
        if (currentTeacher == null) {
            showTeacherSetup();
        } else {
            showDashboard();
        }
    }

    private void showTeacherSetup() {
        layoutTeacherSetup.setVisibility(View.VISIBLE);
        layoutDashboard.setVisibility(View.GONE);
    }

    private void showDashboard() {
        layoutTeacherSetup.setVisibility(View.GONE);
        layoutDashboard.setVisibility(View.VISIBLE);
        tvTeacherInfo.setText("Welcome, " + currentTeacher.getName() + " (" + currentTeacher.getInitial() + ")");
        updateStatus();
    }

    private void saveTeacher() {
        String name = etTeacherName.getText().toString().trim();
        String initial = etTeacherInitial.getText().toString().trim().toUpperCase();
        String pin = etTeacherPin.getText().toString().trim();

        if (name.isEmpty() || initial.isEmpty() || pin.length() != 4) {
            showToast("Please fill all fields correctly");
            return;
        }

        currentTeacher = new Teacher(name, initial, pin);
        database.insertTeacher(currentTeacher);
        showDashboard();
    }

    private void showAddClassDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_class, null);
        EditText etClassName = dialogView.findViewById(R.id.etClassName);
        EditText etSection = dialogView.findViewById(R.id.etSection);
        RadioGroup rgClassType = dialogView.findViewById(R.id.rgClassType);

        new AlertDialog.Builder(this)
                .setTitle("Add Class")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = etClassName.getText().toString().trim();
                    String section = etSection.getText().toString().trim();
                    boolean isLab = rgClassType.getCheckedRadioButtonId() == R.id.rbLab;

                    if (name.isEmpty() || section.isEmpty()) {
                        showToast("Fill all fields");
                        return;
                    }

                    BaseClass baseClass = isLab
                            ? new LabClass(name, section, currentTeacher.getId())
                            : new TheoryClass(name, section, currentTeacher.getId());
                    database.insertClass(baseClass);
                    updateStatus();
                    showToast("Class added!");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddStudentDialog() {
        classes = database.getAllClasses();
        if (classes.isEmpty()) {
            showToast("Add a class first");
            return;
        }

        if (!hasCameraPermission()) return;

        // Show class selection first
        View selectView = LayoutInflater.from(this).inflate(R.layout.dialog_select_class, null);
        Spinner spinnerClass = selectView.findViewById(R.id.spinnerClass);

        List<String> classNames = new ArrayList<>();
        for (BaseClass c : classes) {
            classNames.add(c.toString());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, classNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerClass.setAdapter(adapter);

        new AlertDialog.Builder(this)
                .setTitle("Select Class")
                .setView(selectView)
                .setPositiveButton("Next", (dialog, which) -> {
                    int selectedIndex = spinnerClass.getSelectedItemPosition();
                    if (selectedIndex >= 0) {
                        showCaptureStudentDialog(classes.get(selectedIndex));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCaptureStudentDialog(BaseClass selectedClass) {
        cameraHelper.showCaptureDialog(new CameraDialogHelper.CaptureCallback() {
            @Override
            public void onFaceCaptured(String name, String studentId, String section, float[] features) {
                Student student = new Student(name, studentId, section, selectedClass.getId(), features);
                database.insertStudent(student);
                updateStatus();
                showToast("Student added: " + name);
            }

            @Override
            public void onError(String message) {
                showToast(message);
            }
        });
    }

    private void updateStatus() {
        classes = database.getAllClasses();
        List<Student> students = database.getAllStudents();
        tvStatus.setText("Classes: " + classes.size() + " | Students: " + students.size());
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private boolean hasCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
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
            showToast("Permission granted. Try again.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentTeacher != null) {
            updateStatus();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraHelper.close();
        database.close();
    }
}
