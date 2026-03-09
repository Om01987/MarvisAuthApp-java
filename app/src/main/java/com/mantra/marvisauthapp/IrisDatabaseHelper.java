package com.mantra.marvisauthapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class IrisDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "MarvisBiometric.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_USERS = "users";
    public static final String COL_ID = "id";
    public static final String COL_NAME = "user_name";

    // We store both the display image (BMP) and the matching template (IIR_K1)
    public static final String COL_LEFT_IMAGE = "left_image";
    public static final String COL_LEFT_TEMPLATE = "left_template";
    public static final String COL_RIGHT_IMAGE = "right_image";
    public static final String COL_RIGHT_TEMPLATE = "right_template";

    public IrisDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_USERS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_NAME + " TEXT NOT NULL, " +
                COL_LEFT_IMAGE + " BLOB, " +
                COL_LEFT_TEMPLATE + " BLOB, " +
                COL_RIGHT_IMAGE + " BLOB, " +
                COL_RIGHT_TEMPLATE + " BLOB)";
        db.execSQL(createTable);
        Log.d("DB", "Database created successfully.");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    // Helper method to insert a new user
    public long insertUser(String name, byte[] leftImg, byte[] leftTemp, byte[] rightImg, byte[] rightTemp) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NAME, name);
        if (leftImg != null) values.put(COL_LEFT_IMAGE, leftImg);
        if (leftTemp != null) values.put(COL_LEFT_TEMPLATE, leftTemp);
        if (rightImg != null) values.put(COL_RIGHT_IMAGE, rightImg);
        if (rightTemp != null) values.put(COL_RIGHT_TEMPLATE, rightTemp);

        long id = db.insert(TABLE_USERS, null, values);
        db.close();
        return id;
    }

    // Fetch all users
    public Cursor getAllUsers() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_USERS + " ORDER BY " + COL_ID + " DESC", null);
    }

    // Delete a specific user
    public boolean deleteUser(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_USERS, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        return result > 0;
    }

    // Update user name
    public boolean updateUserName(int id, String newName) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_NAME, newName);
        int result = db.update(TABLE_USERS, cv, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        return result > 0;
    }
}