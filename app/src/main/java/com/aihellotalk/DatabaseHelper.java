package com.aihellotalk;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "chat_history.db";
    private static final int DB_VERSION = 1;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE chats (" +
                "id TEXT PRIMARY KEY, " +
                "name TEXT NOT NULL, " +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "chat_id TEXT NOT NULL, " +
                "role TEXT NOT NULL, " +
                "content TEXT NOT NULL, " +
                "timestamp INTEGER NOT NULL, " +
                "FOREIGN KEY (chat_id) REFERENCES chats(id))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS messages");
        db.execSQL("DROP TABLE IF EXISTS chats");
        onCreate(db);
    }
}
