package com.example.myapplication;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.myapplication.models.Student;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Helper class that manages camera dialogs for face capture and recognition.
 * Abstracts all camera setup and dialog management from the main activity.
 */
public class CameraDialogHelper {

    public interface CaptureCallback {
        void onFaceCaptured(String name, String studentId, String section, float[] features);
        void onError(String message);
    }

    public interface RecognizeCallback {
        void onResult(Student student);
    }

    private final AppCompatActivity activity;
    private final FaceHelper faceHelper;
    private final ExecutorService cameraExecutor;

    public CameraDialogHelper(AppCompatActivity activity) {
        this.activity = activity;
        this.faceHelper = new FaceHelper();
        this.cameraExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * Shows the capture face dialog for adding a new student.
     */
    public void showCaptureDialog(CaptureCallback callback) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_capture_face, null);
        PreviewView previewView = dialogView.findViewById(R.id.previewView);
        EditText etName = dialogView.findViewById(R.id.etName);
        EditText etStudentId = dialogView.findViewById(R.id.etStudentId);
        EditText etSection = dialogView.findViewById(R.id.etSection);
        Button btnCapture = dialogView.findViewById(R.id.btnCapture);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Add Student")
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> startCameraPreview(previewView));

        btnCapture.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String studentId = etStudentId.getText().toString().trim();
            String section = etSection.getText().toString().trim();

            if (name.isEmpty() || studentId.isEmpty() || section.isEmpty()) {
                callback.onError("Fill all fields");
                return;
            }

            Bitmap bitmap = previewView.getBitmap();
            if (bitmap == null) {
                callback.onError("Could not capture image");
                return;
            }

            faceHelper.detectFace(bitmap, new FaceHelper.FaceDetectionCallback() {
                @Override
                public void onFaceDetected(float[] features) {
                    activity.runOnUiThread(() -> {
                        callback.onFaceCaptured(name, studentId, section, features);
                        dialog.dismiss();
                    });
                }

                @Override
                public void onNoFaceDetected() {
                    activity.runOnUiThread(() -> callback.onError("No face detected"));
                }

                @Override
                public void onError(String message) {
                    activity.runOnUiThread(() -> callback.onError(message));
                }
            });
        });

        dialog.setOnDismissListener(d -> stopCamera());
        dialog.show();
    }

    /**
     * Shows the recognize face dialog with continuous face recognition.
     */
    public void showRecognizeDialog(List<Student> students, RecognizeCallback callback) {
        // Convert Student list to FaceData format for recognition
        List<FaceData> storedFaces = new ArrayList<>();
        for (Student s : students) {
            if (s.getFaceFeatures() != null) {
                FaceData fd = new FaceData(s.getName(), s.getFaceFeatures());
                fd.studentId = s.getId();
                storedFaces.add(fd);
            }
        }

        if (storedFaces.isEmpty()) {
            callback.onResult(null);
            return;
        }

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_recognize_face, null);
        PreviewView previewView = dialogView.findViewById(R.id.previewView);
        TextView tvResult = dialogView.findViewById(R.id.tvResult);
        Button btnClose = dialogView.findViewById(R.id.btnClose);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Recognize Face")
                .setView(dialogView)
                .create();

        final boolean[] isProcessing = {false};

        dialog.setOnShowListener(d -> startCameraWithAnalysis(previewView, image -> {
            if (isProcessing[0]) {
                image.close();
                return;
            }
            isProcessing[0] = true;
            image.close();

            activity.runOnUiThread(() -> {
                Bitmap bitmap = previewView.getBitmap();
                if (bitmap != null) {
                    faceHelper.recognizeFace(bitmap, storedFaces, new FaceHelper.FaceRecognitionCallback() {
                        @Override
                        public void onFaceRecognized(String name, float confidence) {
                            activity.runOnUiThread(() -> {
                                tvResult.setText("Recognized: " + name);
                                // Find matching student
                                for (Student s : students) {
                                    if (s.getName().equals(name)) {
                                        callback.onResult(s);
                                        break;
                                    }
                                }
                            });
                            isProcessing[0] = false;
                        }

                        @Override
                        public void onFaceNotRecognized() {
                            activity.runOnUiThread(() -> tvResult.setText("Unknown face"));
                            isProcessing[0] = false;
                        }

                        @Override
                        public void onNoFaceDetected() {
                            activity.runOnUiThread(() -> tvResult.setText("No face detected"));
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
        }));

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> stopCamera());
        dialog.show();
    }

    /**
     * Starts continuous attendance taking with auto-recognition.
     */
    public void startAttendanceCamera(PreviewView previewView, List<Student> students, 
                                       AttendanceCallback callback) {
        List<FaceData> storedFaces = new ArrayList<>();
        for (Student s : students) {
            if (s.getFaceFeatures() != null) {
                FaceData fd = new FaceData(s.getName(), s.getFaceFeatures());
                fd.studentId = s.getId();
                storedFaces.add(fd);
            }
        }

        final boolean[] isProcessing = {false};

        startCameraWithAnalysis(previewView, image -> {
            if (isProcessing[0]) {
                image.close();
                return;
            }
            isProcessing[0] = true;
            image.close();

            activity.runOnUiThread(() -> {
                Bitmap bitmap = previewView.getBitmap();
                if (bitmap != null) {
                    faceHelper.recognizeFace(bitmap, storedFaces, new FaceHelper.FaceRecognitionCallback() {
                        @Override
                        public void onFaceRecognized(String name, float confidence) {
                            activity.runOnUiThread(() -> {
                                for (Student s : students) {
                                    if (s.getName().equals(name)) {
                                        callback.onStudentRecognized(s);
                                        break;
                                    }
                                }
                            });
                            isProcessing[0] = false;
                        }

                        @Override
                        public void onFaceNotRecognized() {
                            isProcessing[0] = false;
                        }

                        @Override
                        public void onNoFaceDetected() {
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
    }

    public interface AttendanceCallback {
        void onStudentRecognized(Student student);
    }

    private void startCameraPreview(PreviewView previewView) {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(activity);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                provider.unbindAll();
                provider.bindToLifecycle(activity, CameraSelector.DEFAULT_FRONT_CAMERA, preview);
            } catch (Exception ignored) {}
        }, ContextCompat.getMainExecutor(activity));
    }

    private void startCameraWithAnalysis(PreviewView previewView, ImageAnalysis.Analyzer analyzer) {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(activity);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, analyzer);

                provider.unbindAll();
                provider.bindToLifecycle(activity, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis);
            } catch (Exception ignored) {}
        }, ContextCompat.getMainExecutor(activity));
    }

    public void stopCamera() {
        try {
            ProcessCameraProvider.getInstance(activity).get().unbindAll();
        } catch (Exception ignored) {}
    }

    public void close() {
        faceHelper.close();
        cameraExecutor.shutdown();
    }
}
