package com.podpiski.manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "podpiski.db";
    private static final int DB_VERSION = 4;

    private static final String T_FOLDERS = "folders";
    private static final String F_ID = "id", F_NAME = "name", F_POSITION = "position";

    private static final String T_SUBS = "subscriptions";
    private static final String S_ID = "id", S_NAME = "name", S_LINK = "link",
            S_FOLDER_ID = "folder_id", S_POSITION = "position", S_CREATED = "created_at";

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_FOLDERS + " (" +
                F_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                F_NAME + " TEXT NOT NULL UNIQUE, " +
                F_POSITION + " INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TABLE " + T_SUBS + " (" +
                S_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                S_NAME + " TEXT NOT NULL, " +
                S_LINK + " TEXT NOT NULL, " +
                S_FOLDER_ID + " INTEGER NOT NULL DEFAULT -1, " +
                S_POSITION + " INTEGER NOT NULL DEFAULT 0, " +
                S_CREATED + " TEXT NOT NULL, " +
                "UNIQUE(" + S_NAME + ", " + S_FOLDER_ID + "), " +
                "UNIQUE(" + S_LINK + ", " + S_FOLDER_ID + "))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 4) {
            db.execSQL("DROP TABLE IF EXISTS " + T_SUBS);
            db.execSQL("DROP TABLE IF EXISTS " + T_FOLDERS);
            onCreate(db);
        }
    }

    // ==================== FOLDERS ====================

    public long addFolder(String name) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(F_NAME, name.trim());
        cv.put(F_POSITION, getNextPos(T_FOLDERS, F_POSITION, null));
        try {
            return db.insertOrThrow(T_FOLDERS, null, cv);
        } catch (Exception e) {
            return -1;
        }
    }

    public boolean updateFolder(long id, String name) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(F_NAME, name.trim());
        return db.update(T_FOLDERS, cv, F_ID + "=?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean deleteFolder(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(T_SUBS, S_FOLDER_ID + "=?", new String[]{String.valueOf(id)});
        return db.delete(T_FOLDERS, F_ID + "=?", new String[]{String.valueOf(id)}) > 0;
    }

    public List<Folder> getAllFolders() {
        List<Folder> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_FOLDERS, null, null, null, null, null, F_POSITION + " ASC");
        if (c != null && c.moveToFirst()) {
            do {
                list.add(cursorToFolder(c));
            } while (c.moveToNext());
            c.close();
        }
        return list;
    }

    public boolean folderNameExists(String name, long excludeId) {
        SQLiteDatabase db = getReadableDatabase();
        String selection = F_NAME + "=? AND " + F_ID + "!=?";
        Cursor c = db.query(T_FOLDERS, new String[]{F_ID},
                selection, new String[]{name.trim(), String.valueOf(excludeId)}, null, null, null);
        boolean exists = c != null && c.moveToFirst();
        if (c != null) c.close();
        return exists;
    }

    public int getSubscriptionCountInFolder(long folderId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + T_SUBS + " WHERE " + S_FOLDER_ID + "=?",
                new String[]{String.valueOf(folderId)});
        int count = 0;
        if (c != null && c.moveToFirst()) {
            count = c.getInt(0);
            c.close();
        }
        return count;
    }

    public void updateFolderPositions(List<Folder> folders) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < folders.size(); i++) {
                ContentValues cv = new ContentValues();
                cv.put(F_POSITION, i);
                db.update(T_FOLDERS, cv, F_ID + "=?", new String[]{String.valueOf(folders.get(i).getId())});
                folders.get(i).setPosition(i);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void updateFolderPosition(long id, int position) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(F_POSITION, position);
        db.update(T_FOLDERS, cv, F_ID + "=?", new String[]{String.valueOf(id)});
    }

    public String getFolderName(long folderId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_FOLDERS, new String[]{F_NAME}, F_ID + "=?", new String[]{String.valueOf(folderId)}, null, null, null);
        String name = "";
        if (c != null && c.moveToFirst()) {
            name = c.getString(0);
            c.close();
        }
        return name;
    }

    // ==================== SUBSCRIPTIONS ====================

    public long addSubscription(String name, String link, long folderId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(S_NAME, name.trim());
        cv.put(S_LINK, link.trim());
        cv.put(S_FOLDER_ID, folderId);
        cv.put(S_POSITION, getNextPos(T_SUBS, S_POSITION, S_FOLDER_ID + "=" + folderId));
        cv.put(S_CREATED, getTimestamp());
        try {
            return db.insertOrThrow(T_SUBS, null, cv);
        } catch (Exception e) {
            return -1;
        }
    }
    
    // Добавление подписки с указанной позицией (для вставки в начало)
    public long addSubscriptionAtPosition(String name, String link, long folderId, int position) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(S_NAME, name.trim());
        cv.put(S_LINK, link.trim());
        cv.put(S_FOLDER_ID, folderId);
        cv.put(S_POSITION, position);
        cv.put(S_CREATED, getTimestamp());
        try {
            return db.insertOrThrow(T_SUBS, null, cv);
        } catch (Exception e) {
            return -1;
        }
    }

    public boolean updateSubscription(long id, String name, String link) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(S_NAME, name.trim());
        cv.put(S_LINK, link.trim());
        return db.update(T_SUBS, cv, S_ID + "=?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean deleteSubscription(long id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(T_SUBS, S_ID + "=?", new String[]{String.valueOf(id)}) > 0;
    }

    public List<Subscription> getSubscriptionsByFolder(long folderId) {
        List<Subscription> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_SUBS, null, S_FOLDER_ID + "=?",
                new String[]{String.valueOf(folderId)}, null, null, S_POSITION + " ASC");
        if (c != null && c.moveToFirst()) {
            do {
                list.add(cursorToSub(c));
            } while (c.moveToNext());
            c.close();
        }
        return list;
    }

    public List<Subscription> getAllSubscriptions() {
        List<Subscription> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_SUBS, null, null, null, null, null, S_CREATED + " DESC");
        if (c != null && c.moveToFirst()) {
            do {
                list.add(cursorToSub(c));
            } while (c.moveToNext());
            c.close();
        }
        return list;
    }

    public List<Subscription> searchSubscriptions(String query) {
        List<Subscription> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String q = "%" + query.trim() + "%";
        Cursor c = db.query(T_SUBS, null,
                "(" + S_NAME + " LIKE ? OR " + S_LINK + " LIKE ?)",
                new String[]{q, q}, null, null, S_CREATED + " DESC");
        if (c != null && c.moveToFirst()) {
            do {
                list.add(cursorToSub(c));
            } while (c.moveToNext());
            c.close();
        }
        return list;
    }

    public DuplicateInfo checkNameDuplicateGlobal(String name, long excludeId) {
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT s." + S_ID + " as sub_id, s." + S_NAME + " as sub_name, " +
                       "s." + S_FOLDER_ID + " as folder_id, f." + F_NAME + " as folder_name " +
                       " FROM " + T_SUBS + " s" +
                       " LEFT JOIN " + T_FOLDERS + " f ON s." + S_FOLDER_ID + " = f." + F_ID +
                       " WHERE s." + S_NAME + " = ? AND s." + S_ID + " != ?";
        Cursor c = db.rawQuery(query, new String[]{name.trim(), String.valueOf(excludeId)});
        DuplicateInfo info = null;
        if (c != null && c.moveToFirst()) {
            info = new DuplicateInfo();
            info.type = "name";
            info.value = name;
            info.existingId = c.getLong(c.getColumnIndexOrThrow("sub_id"));
            info.existingName = c.getString(c.getColumnIndexOrThrow("sub_name"));
            info.existingFolderId = c.getLong(c.getColumnIndexOrThrow("folder_id"));
            info.existingFolderName = c.getString(c.getColumnIndexOrThrow("folder_name"));
            if (info.existingFolderName == null) info.existingFolderName = "Без папки";
            c.close();
        } else if (c != null) {
            c.close();
        }
        return info;
    }

    public DuplicateInfo checkLinkDuplicateGlobal(String link, long excludeId) {
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT s." + S_ID + " as sub_id, s." + S_NAME + " as sub_name, " +
                       "s." + S_FOLDER_ID + " as folder_id, f." + F_NAME + " as folder_name " +
                       " FROM " + T_SUBS + " s" +
                       " LEFT JOIN " + T_FOLDERS + " f ON s." + S_FOLDER_ID + " = f." + F_ID +
                       " WHERE s." + S_LINK + " = ? AND s." + S_ID + " != ?";
        Cursor c = db.rawQuery(query, new String[]{link.trim(), String.valueOf(excludeId)});
        DuplicateInfo info = null;
        if (c != null && c.moveToFirst()) {
            info = new DuplicateInfo();
            info.type = "link";
            info.value = link;
            info.existingId = c.getLong(c.getColumnIndexOrThrow("sub_id"));
            info.existingName = c.getString(c.getColumnIndexOrThrow("sub_name"));
            info.existingFolderId = c.getLong(c.getColumnIndexOrThrow("folder_id"));
            info.existingFolderName = c.getString(c.getColumnIndexOrThrow("folder_name"));
            if (info.existingFolderName == null) info.existingFolderName = "Без папки";
            c.close();
        } else if (c != null) {
            c.close();
        }
        return info;
    }

    public void updateSubscriptionPosition(long id, int position) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(S_POSITION, position);
        db.update(T_SUBS, cv, S_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void shiftSubscriptionsForward(long folderId, int fromPosition) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE " + T_SUBS + " SET " + S_POSITION + " = " + S_POSITION + " + 1 " +
                   "WHERE " + S_FOLDER_ID + " = ? AND " + S_POSITION + " >= ?",
                   new Object[]{folderId, fromPosition});
    }

    public void updateSubPositions(List<Subscription> subs) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < subs.size(); i++) {
                ContentValues cv = new ContentValues();
                cv.put(S_POSITION, i);
                db.update(T_SUBS, cv, S_ID + "=?", new String[]{String.valueOf(subs.get(i).getId())});
                subs.get(i).setPosition(i);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void updateSubscriptionCreatedAt(long id, String createdAt) {
        if (createdAt == null || createdAt.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(S_CREATED, createdAt);
        db.update(T_SUBS, cv, S_ID + "=?", new String[]{String.valueOf(id)});
    }

    public boolean moveSubscriptionToFolder(long subscriptionId, long newFolderId) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.query(T_SUBS, new String[]{S_FOLDER_ID, S_POSITION}, S_ID + "=?", 
                new String[]{String.valueOf(subscriptionId)}, null, null, null);
        if (c == null || !c.moveToFirst()) {
            if (c != null) c.close();
            return false;
        }
        long oldFolderId = c.getLong(0);
        int oldPosition = c.getInt(1);
        c.close();

        // Обновляем позиции в старой папке
        db.execSQL("UPDATE " + T_SUBS + " SET " + S_POSITION + " = " + S_POSITION + " - 1 " +
                   "WHERE " + S_FOLDER_ID + " = ? AND " + S_POSITION + " > ?",
                   new Object[]{oldFolderId, oldPosition});

        // Получаем новую позицию (в конец)
        Cursor countCursor = db.rawQuery("SELECT COUNT(*) FROM " + T_SUBS + " WHERE " + S_FOLDER_ID + " = ?",
                new String[]{String.valueOf(newFolderId)});
        int newPosition = 0;
        if (countCursor != null && countCursor.moveToFirst()) {
            newPosition = countCursor.getInt(0);
            countCursor.close();
        }

        ContentValues cv = new ContentValues();
        cv.put(S_FOLDER_ID, newFolderId);
        cv.put(S_POSITION, newPosition);
        int rows = db.update(T_SUBS, cv, S_ID + "=?", new String[]{String.valueOf(subscriptionId)});
        return rows > 0;
    }

    public List<Subscription> findDuplicates() {
        List<Subscription> dups = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT * FROM " + T_SUBS + " WHERE " + S_LINK + " IN " +
                        "(SELECT " + S_LINK + " FROM " + T_SUBS + " GROUP BY " + S_LINK + " HAVING COUNT(*) > 1) " +
                        " ORDER BY " + S_LINK + ", " + S_CREATED, null);
        if (c != null && c.moveToFirst()) {
            do {
                dups.add(cursorToSub(c));
            } while (c.moveToNext());
            c.close();
        }
        return dups;
    }

    public int deleteDuplicates() {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(T_SUBS, S_ID + " NOT IN " +
                "(SELECT MIN(" + S_ID + ") FROM " + T_SUBS + " GROUP BY " + S_LINK + ")", null);
    }

    public void deleteAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(T_SUBS, null, null);
        db.delete(T_FOLDERS, null, null);
    }

    public List<Folder> getAllFoldersIncludingDeleted() {
        return getAllFolders();
    }

    public List<Subscription> getAllSubscriptionsIncludingDeleted() {
        return getAllSubscriptions();
    }

    private Folder cursorToFolder(Cursor c) {
        return new Folder(
                c.getLong(c.getColumnIndexOrThrow(F_ID)),
                c.getString(c.getColumnIndexOrThrow(F_NAME)),
                c.getInt(c.getColumnIndexOrThrow(F_POSITION)));
    }

    private Subscription cursorToSub(Cursor c) {
        Subscription s = new Subscription();
        s.setId(c.getLong(c.getColumnIndexOrThrow(S_ID)));
        s.setName(c.getString(c.getColumnIndexOrThrow(S_NAME)));
        s.setLink(c.getString(c.getColumnIndexOrThrow(S_LINK)));
        s.setFolderId(c.getLong(c.getColumnIndexOrThrow(S_FOLDER_ID)));
        s.setPosition(c.getInt(c.getColumnIndexOrThrow(S_POSITION)));
        s.setCreatedAt(c.getString(c.getColumnIndexOrThrow(S_CREATED)));
        return s;
    }

    private int getNextPos(String table, String col, String where) {
        SQLiteDatabase db = getReadableDatabase();
        String q = where != null ? " WHERE " + where : "";
        Cursor c = db.rawQuery("SELECT MAX(" + col + ") FROM " + table + q, null);
        int pos = 0;
        if (c != null && c.moveToFirst()) {
            pos = c.getInt(0) + 1;
            c.close();
        }
        return pos;
    }

    private String getTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    public static class DuplicateInfo {
        public String type;
        public String value;
        public long existingId;
        public long existingFolderId;
        public String existingFolderName;
        public String existingName;
    }
}