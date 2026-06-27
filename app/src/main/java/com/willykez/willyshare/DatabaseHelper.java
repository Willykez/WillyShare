package com.willykez.willyshare;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "willyshare.db";
    private static final int DB_VERSION = 1;
    public static final String TABLE_NAME = "transfer_history";
    public static final String COL_ID = "id";
    public static final String COL_FILE_NAME = "file_name";
    public static final String COL_FILE_SIZE = "file_size";
    public static final String COL_BYTES_TRANS = "bytes_transferred";
    public static final String COL_STATUS = "status";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_IS_SENT = "is_sent";

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_FILE_NAME + " TEXT, " +
                COL_FILE_SIZE + " INTEGER, " +
                COL_BYTES_TRANS + " INTEGER, " +
                COL_STATUS + " TEXT, " +
                COL_TIMESTAMP + " INTEGER, " +
                COL_IS_SENT + " INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public long insertHistory(String fileName, long fileSize, long bytesTransferred,
                              String status, long timestamp, int isSent) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_FILE_NAME, fileName);
        cv.put(COL_FILE_SIZE, fileSize);
        cv.put(COL_BYTES_TRANS, bytesTransferred);
        cv.put(COL_STATUS, status);
        cv.put(COL_TIMESTAMP, timestamp);
        cv.put(COL_IS_SENT, isSent);
        long id = db.insert(TABLE_NAME, null, cv);
        db.close();
        return id;
    }

    public void updateProgress(long id, long bytesTransferred, String status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_BYTES_TRANS, bytesTransferred);
        cv.put(COL_STATUS, status);
        db.update(TABLE_NAME, cv, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
    }

    public List<String[]> getAllHistory() {
        List<String[]> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null, COL_TIMESTAMP + " DESC");
        while (cursor.moveToNext()) {
            list.add(new String[]{
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_FILE_NAME)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_FILE_SIZE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_BYTES_TRANS)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_IS_SENT))
            });
        }
        cursor.close();
        db.close();
        return list;
    }
}
