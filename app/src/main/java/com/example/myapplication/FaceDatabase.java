package com.example.myapplication;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple SQLite database helper for storing face data locally.
 */
public class FaceDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "faces.db";
    private static final int DATABASE_VERSION = 3;

    private static final String TABLE_FACES = "faces";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_FEATURES = "features";

    public FaceDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_FACES + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_NAME + " TEXT NOT NULL, " +
                COLUMN_FEATURES + " BLOB NOT NULL)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FACES);
        onCreate(db);
    }

    /**
     * Inserts a new face into the database.
     */
    public long insertFace(FaceData faceData) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, faceData.name);
        values.put(COLUMN_FEATURES, floatArrayToBytes(faceData.features));
        return db.insert(TABLE_FACES, null, values);
    }

    /**
     * Retrieves all stored faces from the database.
     */
    public List<FaceData> getAllFaces() {
        List<FaceData> faces = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_FACES, null, null, null, null, null, null);

        if (cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(COLUMN_NAME);
            int featuresIndex = cursor.getColumnIndex(COLUMN_FEATURES);

            do {
                String name = cursor.getString(nameIndex);
                byte[] featuresBlob = cursor.getBlob(featuresIndex);
                float[] features = bytesToFloatArray(featuresBlob);
                faces.add(new FaceData(name, features));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return faces;
    }

    /**
     * Deletes all faces from the database.
     */
    public void deleteAllFaces() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_FACES, null, null);
    }

    /**
     * Converts float array to byte array for storage.
     */
    private byte[] floatArrayToBytes(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4);
        buffer.order(ByteOrder.nativeOrder());
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    /**
     * Converts byte array back to float array.
     */
    private float[] bytesToFloatArray(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.nativeOrder());
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }
}
