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
    private static final float RECOGNITION_THRESHOLD = 0.4f; // Lower threshold for Euclidean distance (smaller is
                                                             // better, but we invert logic)

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
                        if (features != null) {
                            callback.onFaceDetected(features);
                        } else {
                            callback.onError("Face detected but landmarks missing");
                        }
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
                        if (features != null) {
                            callback.onFaceDetected(features);
                        } else {
                            callback.onError("Face detected but landmarks missing");
                        }
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
                        if (features != null) {
                            matchFace(features, storedFaces, callback);
                        } else {
                            callback.onFaceNotRecognized();
                        }
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
        float bestScore = Float.MAX_VALUE; // Lower is better for Euclidean distance

        for (FaceData stored : storedFaces) {
            float score = calculateEuclideanDistance(features, stored.features);
            if (score < bestScore) {
                bestScore = score;
                bestMatch = stored.name;
            }
        }

        // Threshold check: if distance is small enough, it's a match
        // We use RECOGNITION_THRESHOLD as the max allowed distance
        if (bestScore <= RECOGNITION_THRESHOLD && bestMatch != null) {
            // Convert distance to a confidence score (0 to 1) for display
            // Simple inversion: 1.0 - (distance / max_expected_distance)
            float confidence = Math.max(0f, 1.0f - (bestScore / 2.0f));
            callback.onFaceRecognized(bestMatch, confidence);
        } else {
            callback.onFaceNotRecognized();
        }
    }

    /**
     * Extracts a feature vector using geometric ratios between landmarks.
     * This is more robust to scale and position than raw coordinates.
     */
    private float[] extractFeatures(Face face) {
        // We need specific landmarks to calculate ratios
        FaceLandmark leftEye = face.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE);
        FaceLandmark nose = face.getLandmark(FaceLandmark.NOSE_BASE);
        FaceLandmark mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT);
        FaceLandmark mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT);
        FaceLandmark mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM);

        // If any essential landmark is missing, we can't compute features
        if (leftEye == null || rightEye == null || nose == null ||
                mouthLeft == null || mouthRight == null || mouthBottom == null) {
            return null;
        }

        PointF pLeftEye = leftEye.getPosition();
        PointF pRightEye = rightEye.getPosition();
        PointF pNose = nose.getPosition();
        PointF pMouthLeft = mouthLeft.getPosition();
        PointF pMouthRight = mouthRight.getPosition();
        PointF pMouthBottom = mouthBottom.getPosition();

        // 1. Calculate Inter-Ocular Distance (IOD) as the reference scale
        float iod = distance(pLeftEye, pRightEye);
        if (iod == 0)
            return null; // Avoid division by zero

        // 2. Calculate other distances
        float eyeToNose = distance(midPoint(pLeftEye, pRightEye), pNose);
        float eyeToMouth = distance(midPoint(pLeftEye, pRightEye), midPoint(pMouthLeft, pMouthRight));
        float noseToMouth = distance(pNose, midPoint(pMouthLeft, pMouthRight));
        float mouthWidth = distance(pMouthLeft, pMouthRight);
        float noseToMouthBottom = distance(pNose, pMouthBottom);

        // New features for stricter matching
        float leftEyeToMouthLeft = distance(pLeftEye, pMouthLeft);
        float rightEyeToMouthRight = distance(pRightEye, pMouthRight);

        // 3. Create feature vector using ratios (Distance / IOD)
        // This makes the features scale-invariant
        float[] features = new float[7];
        features[0] = eyeToNose / iod;
        features[1] = eyeToMouth / iod;
        features[2] = noseToMouth / iod;
        features[3] = mouthWidth / iod;
        features[4] = noseToMouthBottom / iod;
        features[5] = leftEyeToMouthLeft / iod;
        features[6] = rightEyeToMouthRight / iod;

        return features;
    }

    private float distance(PointF p1, PointF p2) {
        return (float) Math.hypot(p1.x - p2.x, p1.y - p2.y);
    }

    private PointF midPoint(PointF p1, PointF p2) {
        return new PointF((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
    }

    /**
     * Calculates Euclidean distance between two feature vectors.
     * Lower value means more similar.
     */
    private float calculateEuclideanDistance(float[] f1, float[] f2) {
        if (f1 == null || f2 == null || f1.length != f2.length)
            return Float.MAX_VALUE;

        float sum = 0;
        for (int i = 0; i < f1.length; i++) {
            float diff = f1[i] - f2[i];
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }

    public void close() {
        detector.close();
    }
}
