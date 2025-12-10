package com.example.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // Stored face data list
    private final List<FaceData> storedFaces = new ArrayList<>();
    
    // Face helper for all detection logic
    private FaceHelper faceHelper;
    
    // Database for storing faces locally
    private FaceDatabase faceDatabase;
    
    private TextView tvStatus;
    private ExecutorService cameraExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        faceHelper = new FaceHelper();
        cameraExecutor = Executors.newSingleThreadExecutor();
        
        // Initialize database and load stored faces
        faceDatabase = new FaceDatabase(this);
        storedFaces.addAll(faceDatabase.getAllFaces());
        
        tvStatus = findViewById(R.id.tvStatus);
        tvStatus.setText("Stored faces: " + storedFaces.size());
        
        findViewById(R.id.btnDetectFace).setOnClickListener(v -> {
            if (checkCameraPermission()) showCaptureDialog();
        });
        
        findViewById(R.id.btnRecognizeFace).setOnClickListener(v -> {
            if (checkCameraPermission()) showRecognizeDialog();
        });
    }

    private boolean checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.CAMERA}, 100);
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
            Toast.makeText(this, "Permission granted. Try again.", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== CAPTURE FACE DIALOG ====================
    
    private void showCaptureDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_capture_face, null);
        PreviewView previewView = dialogView.findViewById(R.id.previewView);
        EditText etName = dialogView.findViewById(R.id.etName);
        Button btnCapture = dialogView.findViewById(R.id.btnCapture);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add Face")
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
            future.addListener(() -> {
                try {
                    ProcessCameraProvider provider = future.get();
                    Preview preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());
                    
                    provider.unbindAll();
                    provider.bindToLifecycle((LifecycleOwner) this, 
                            CameraSelector.DEFAULT_FRONT_CAMERA, preview);
                } catch (Exception e) {
                    Toast.makeText(this, "Camera error", Toast.LENGTH_SHORT).show();
                }
            }, ContextCompat.getMainExecutor(this));
        });

        btnCapture.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Enter a name", Toast.LENGTH_SHORT).show();
                return;
            }

            // Get bitmap on main thread before processing
            Bitmap bitmap = previewView.getBitmap();
            if (bitmap == null) {
                Toast.makeText(this, "Could not capture image", Toast.LENGTH_SHORT).show();
                return;
            }

            faceHelper.detectFace(bitmap, new FaceHelper.FaceDetectionCallback() {
                @Override
                public void onFaceDetected(float[] features) {
                    FaceData faceData = new FaceData(name, features);
                    storedFaces.add(faceData);
                    faceDatabase.insertFace(faceData);
                    runOnUiThread(() -> {
                        tvStatus.setText("Stored faces: " + storedFaces.size());
                        Toast.makeText(MainActivity.this, 
                                "Face saved: " + name, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    });
                }

                @Override
                public void onNoFaceDetected() {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, 
                            "No face detected", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, 
                            "Error: " + message, Toast.LENGTH_SHORT).show());
                }
            });
        });

        dialog.setOnDismissListener(d -> {
            try {
                ProcessCameraProvider.getInstance(this).get().unbindAll();
            } catch (Exception ignored) {}
        });

        dialog.show();
    }

    // ==================== RECOGNIZE FACE DIALOG ====================
    
    private void showRecognizeDialog() {
        if (storedFaces.isEmpty()) {
            Toast.makeText(this, "No faces stored yet", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_recognize_face, null);
        PreviewView previewView = dialogView.findViewById(R.id.previewView);
        TextView tvResult = dialogView.findViewById(R.id.tvResult);
        Button btnClose = dialogView.findViewById(R.id.btnClose);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Recognize Face")
                .setView(dialogView)
                .create();

        final boolean[] isProcessing = {false};

        dialog.setOnShowListener(d -> {
            ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
            future.addListener(() -> {
                try {
                    ProcessCameraProvider provider = future.get();
                    Preview preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());
                    
                    ImageAnalysis analysis = new ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build();
                    
                    analysis.setAnalyzer(cameraExecutor, image -> {
                        if (isProcessing[0]) {
                            image.close();
                            return;
                        }
                        isProcessing[0] = true;
                        image.close();
                        
                        // Get bitmap on main thread
                        runOnUiThread(() -> {
                            Bitmap bitmap = previewView.getBitmap();
                            if (bitmap != null) {
                                faceHelper.recognizeFace(bitmap, storedFaces, 
                                        new FaceHelper.FaceRecognitionCallback() {
                                    @Override
                                    public void onFaceRecognized(String name, float confidence) {
                                        runOnUiThread(() -> tvResult.setText("Recognized: " + name));
                                        isProcessing[0] = false;
                                    }

                                    @Override
                                    public void onFaceNotRecognized() {
                                        runOnUiThread(() -> tvResult.setText("Unknown face"));
                                        isProcessing[0] = false;
                                    }

                                    @Override
                                    public void onNoFaceDetected() {
                                        runOnUiThread(() -> tvResult.setText("No face detected"));
                                        isProcessing[0] = false;
                                    }

                                    @Override
                                    public void onError(String message) {
                                        isProcessing[0] = false;
                                    }
                                });
                            } else {
                                isProcessing[0] = false;
                            }
                        });
                    });
                    
                    provider.unbindAll();
                    provider.bindToLifecycle((LifecycleOwner) this, 
                            CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis);
                } catch (Exception e) {
                    Toast.makeText(this, "Camera error", Toast.LENGTH_SHORT).show();
                }
            }, ContextCompat.getMainExecutor(this));
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.setOnDismissListener(d -> {
            try {
                ProcessCameraProvider.getInstance(this).get().unbindAll();
            } catch (Exception ignored) {}
        });

        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        faceHelper.close();
        cameraExecutor.shutdown();
        faceDatabase.close();
    }
}
