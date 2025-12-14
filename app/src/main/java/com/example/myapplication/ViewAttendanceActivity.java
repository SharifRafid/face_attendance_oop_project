package com.example.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.models.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for viewing attendance records.
 * Teacher selects class and date to view attendance details.
 */
public class ViewAttendanceActivity extends AppCompatActivity {

    private AttendanceDatabase database;

    private Spinner spinnerClass, spinnerDate;
    private LinearLayout layoutRecords;
    private TextView tvSummary;
    private Button btnDelete;

    private List<BaseClass> classes = new ArrayList<>();
    private List<String> dates = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_attendance);

        database = new AttendanceDatabase(this);

        initViews();
        loadClasses();
    }

    private void initViews() {
        spinnerClass = findViewById(R.id.spinnerClass);
        spinnerDate = findViewById(R.id.spinnerDate);
        layoutRecords = findViewById(R.id.layoutRecords);
        tvSummary = findViewById(R.id.tvSummary);
        btnDelete = findViewById(R.id.btnDelete);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnView).setOnClickListener(v -> viewAttendance());
        btnDelete.setOnClickListener(v -> deleteAttendance());

        spinnerClass.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                loadDates();
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

    private void loadDates() {
        if (classes.isEmpty() || spinnerClass.getSelectedItemPosition() < 0)
            return;
        BaseClass selectedClass = classes.get(spinnerClass.getSelectedItemPosition());
        dates = database.getAttendanceDates(selectedClass.getId());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, dates);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDate.setAdapter(adapter);
    }

    private void viewAttendance() {
        layoutRecords.removeAllViews();
        btnDelete.setVisibility(View.GONE);

        if (classes.isEmpty() || dates.isEmpty()) {
            tvSummary.setText("No attendance records found");
            return;
        }

        BaseClass selectedClass = classes.get(spinnerClass.getSelectedItemPosition());
        String selectedDate = dates.get(spinnerDate.getSelectedItemPosition());

        Attendance attendance = database.getAttendance(selectedClass.getId(), selectedDate);

        if (attendance == null || attendance.getRecords().isEmpty()) {
            tvSummary.setText("No records for this date");
            return;
        }

        tvSummary.setText("Present: " + attendance.getPresentCount() + " / " + attendance.getTotalCount());

        for (AttendanceRecord record : attendance.getRecords()) {
            TextView tv = new TextView(this);
            String status = record.isPresent() ? "✓ Present" : "✗ Absent";
            tv.setText(record.getStudentName() + " (" + record.getStudentIdNumber() + ") - " + status);
            tv.setTextSize(16);
            tv.setPadding(0, 8, 0, 8);
            tv.setTextColor(record.isPresent() ? 0xFF4CAF50 : 0xFFF44336);
            layoutRecords.addView(tv);
        }

        btnDelete.setVisibility(View.VISIBLE);
    }

    private void deleteAttendance() {
        if (classes.isEmpty() || dates.isEmpty())
            return;

        BaseClass selectedClass = classes.get(spinnerClass.getSelectedItemPosition());
        String selectedDate = dates.get(spinnerDate.getSelectedItemPosition());

        Attendance attendance = database.getAttendance(selectedClass.getId(), selectedDate);
        if (attendance != null) {
            database.deleteAttendance(attendance.getId());
            Toast.makeText(this, "Attendance deleted", Toast.LENGTH_SHORT).show();

            // Refresh dates
            loadDates();
            layoutRecords.removeAllViews();
            tvSummary.setText("Select class and date");
            btnDelete.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        database.close();
    }
}
