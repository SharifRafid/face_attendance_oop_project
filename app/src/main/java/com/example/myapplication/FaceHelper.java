package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.List;

/**
 * Helper class that abstracts all face detection and recognition logic.
 * Provides simple callbacks for face operations.
 */
public class FaceHelper {

    public interface FaceDetectionCallback {
        void onFaceDetected(float[] features);
        void onNoFaceDetected();
        void onError(String message);
    }

    public interface FaceRecognitionCallback {
        void onFaceRecognized(String name, float confidence);
        void onFaceNotRecognized();
        void onNoFaceDetected();
        void onError(String message);
    }

    private final FaceDetector detector;
    private static final float RECOGNITION_THRESHOLD = 0.6f;

    public FaceHelper() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build();
        detector = FaceDetection.getClient(options);
    }

    /**
     * Detects a face from a Bitmap and extracts features.
     */
    public void detectFace(Bitmap bitmap, FaceDetectionCallback callback) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.isEmpty()) {
                        callback.onNoFaceDetected();
                    } else {
                        float[] features = extractFeatures(faces.get(0));
                        callback.onFaceDetected(features);
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Detects a face from ImageProxy (for CameraX) and extracts features.
     */
    @androidx.camera.core.ExperimentalGetImage
    public void detectFace(ImageProxy imageProxy, FaceDetectionCallback callback) {
        if (imageProxy.getImage() == null) {
            callback.onError("No image available");
            imageProxy.close();
            return;
        }
        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.isEmpty()) {
                        callback.onNoFaceDetected();
                    } else {
                        float[] features = extractFeatures(faces.get(0));
                        callback.onFaceDetected(features);
                    }
                    imageProxy.close();
                })
                .addOnFailureListener(e -> {
                    callback.onError(e.getMessage());
                    imageProxy.close();
                });
    }

    /**
     * Recognizes a face by comparing against stored face data.
     */
    public void recognizeFace(Bitmap bitmap, List<FaceData> storedFaces, FaceRecognitionCallback callback) {
        detectFace(bitmap, new FaceDetectionCallback() {
            @Override
            public void onFaceDetected(float[] features) {
                matchFace(features, storedFaces, callback);
            }

            @Override
            public void onNoFaceDetected() {
                callback.onNoFaceDetected();
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Recognizes a face from ImageProxy by comparing against stored face data.
     */
    @androidx.camera.core.ExperimentalGetImage
    public void recognizeFace(ImageProxy imageProxy, List<FaceData> storedFaces, FaceRecognitionCallback callback) {
        if (imageProxy.getImage() == null) {
            callback.onError("No image available");
            imageProxy.close();
            return;
        }
        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.isEmpty()) {
                        callback.onNoFaceDetected();
                    } else {
                        float[] features = extractFeatures(faces.get(0));
                        matchFace(features, storedFaces, callback);
                    }
                    imageProxy.close();
                })
                .addOnFailureListener(e -> {
                    callback.onError(e.getMessage());
                    imageProxy.close();
                });
    }

    private void matchFace(float[] features, List<FaceData> storedFaces, FaceRecognitionCallback callback) {
        if (storedFaces.isEmpty()) {
            callback.onFaceNotRecognized();
            return;
        }

        String bestMatch = null;
        float bestSimilarity = 0;

        for (FaceData stored : storedFaces) {
            float similarity = calculateSimilarity(features, stored.features);
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = stored.name;
            }
        }

        if (bestSimilarity >= RECOGNITION_THRESHOLD && bestMatch != null) {
            callback.onFaceRecognized(bestMatch, bestSimilarity);
        } else {
            callback.onFaceNotRecognized();
        }
    }

    /**
     * Extracts a feature vector from face landmarks and contours.
     * Uses normalized distances between facial landmarks as features.
     */
    private float[] extractFeatures(Face face) {
        float[] features = new float[50];
        int index = 0;

        // Get bounding box dimensions for normalization
        float faceWidth = face.getBoundingBox().width();
        float faceHeight = face.getBoundingBox().height();
        if (faceWidth == 0) faceWidth = 1;
        if (faceHeight == 0) faceHeight = 1;

        // Extract landmark positions (normalized)
        int[] landmarkTypes = {
                FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE,
                FaceLandmark.NOSE_BASE, FaceLandmark.MOUTH_LEFT,
                FaceLandmark.MOUTH_RIGHT, FaceLandmark.MOUTH_BOTTOM,
                FaceLandmark.LEFT_CHEEK, FaceLandmark.RIGHT_CHEEK,
                FaceLandmark.LEFT_EAR, FaceLandmark.RIGHT_EAR
        };

        float centerX = face.getBoundingBox().centerX();
        float centerY = face.getBoundingBox().centerY();

        for (int type : landmarkTypes) {
            FaceLandmark landmark = face.getLandmark(type);
            if (landmark != null && index < 48) {
                features[index++] = (landmark.getPosition().x - centerX) / faceWidth;
                features[index++] = (landmark.getPosition().y - centerY) / faceHeight;
            } else if (index < 48) {
                features[index++] = 0;
                features[index++] = 0;
            }
        }

        // Add face angles as features
        features[index++] = face.getHeadEulerAngleX() / 45f;  // Normalized pitch
        features[index++] = face.getHeadEulerAngleY() / 45f;  // Normalized yaw

        // Add some contour-based features for better accuracy
        FaceContour faceOval = face.getContour(FaceContour.FACE);
        if (faceOval != null) {
            List<PointF> points = faceOval.getPoints();
            if (points.size() >= 10) {
                // Sample a few points from the face contour
                for (int i = 0; i < 10 && index < 50; i++) {
                    int sampleIndex = i * points.size() / 10;
                    PointF point = points.get(sampleIndex);
                    features[index++] = (point.x - centerX) / faceWidth;
                }
            }
        }

        return features;
    }

    /**
     * Calculates cosine similarity between two feature vectors.
     */
    private float calculateSimilarity(float[] features1, float[] features2) {
        if (features1.length != features2.length) return 0;

        float dotProduct = 0;
        float norm1 = 0;
        float norm2 = 0;

        for (int i = 0; i < features1.length; i++) {
            dotProduct += features1[i] * features2[i];
            norm1 += features1[i] * features1[i];
            norm2 += features2[i] * features2[i];
        }

        if (norm1 == 0 || norm2 == 0) return 0;
        return (dotProduct / (float) (Math.sqrt(norm1) * Math.sqrt(norm2)) + 1) / 2;
    }

    public void close() {
        detector.close();
    }
}

