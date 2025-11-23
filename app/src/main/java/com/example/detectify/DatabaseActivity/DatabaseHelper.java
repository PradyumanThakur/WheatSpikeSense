package com.example.detectify.DatabaseActivity;

import static com.example.detectify.Utility.SecurityUtil.hashPassword;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseHelper";

    private static final String DATABASE_NAME = "detectify.db";
    private static final int DATABASE_VERSION = 11;

    // Table names
    public static final String TABLE_USERS = "users";
    public static final String TABLE_SESSIONS = "sessions";
    public static final String TABLE_POT_DATA = "potData";

    // Users table columns
    public static final String COLUMN_USER_ID = "user_id";
    public static final String COLUMN_FIRSTNAME = "firstname";
    public static final String COLUMN_LASTNAME = "lastname";
    public static final String COLUMN_EMAIL = "email";
    public static final String COLUMN_PASSWORD = "password";
    private static final String COLUMN_CREATED_AT = "created_at";
    private static final String COLUMN_UPDATED_AT = "updated_at";

    // Sessions table columns
    public static final String COLUMN_SESSION_ID = "session_id";
    public static final String COLUMN_SESSION_NUMBER = "session_number";
    private static final String COLUMN_START_TIME = "start_time";
    private static final String COLUMN_END_TIME = "end_time";
    private static final String COLUMN_STATUS = "status";

    // Pots table columns
    private static final String COLUMN_POT_DATA_ID = "pot_data_id";
    public static final String COLUMN_POT_ID = "pot_id";
    public static final String COLUMN_WHEAT_SPIKES = "wheat_spikes";

    // Table creation SQL statements
    private static final String CREATE_TABLE_USERS  =
            "CREATE TABLE " + TABLE_USERS + " (" +
                    COLUMN_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_FIRSTNAME + " TEXT NOT NULL, " +
                    COLUMN_LASTNAME + " TEXT NOT NULL, " +
                    COLUMN_EMAIL + " TEXT NOT NULL, " +
                    COLUMN_PASSWORD + " TEXT NOT NULL, " +
                    COLUMN_CREATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    COLUMN_UPDATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE (" + COLUMN_EMAIL + "));";

    private static final String CREATE_TRIGGER_USERS_UPDATED_AT =
            "CREATE TRIGGER users_updated_at " +
                    "AFTER UPDATE ON " + TABLE_USERS + " " +
                    "FOR EACH ROW " +
                    "BEGIN " +
                    "UPDATE " + TABLE_USERS + " SET " + COLUMN_UPDATED_AT + " = CURRENT_TIMESTAMP WHERE " +
                    COLUMN_USER_ID + " = old." + COLUMN_USER_ID + ";" +
                    "END;";

    private static final String CREATE_TABLE_SESSIONS =
            "CREATE TABLE " + TABLE_SESSIONS + " (" +
                    COLUMN_SESSION_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_USER_ID + " INTEGER, " +
                    COLUMN_SESSION_NUMBER + " INTEGER," +
                    COLUMN_START_TIME + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    COLUMN_END_TIME + " TIMESTAMP, " +
                    COLUMN_STATUS + " TEXT, " +
                    "FOREIGN KEY(" + COLUMN_USER_ID + ") REFERENCES " + TABLE_USERS + "(" + COLUMN_USER_ID + "));";


    private static final String CREATE_TABLE_POT_DATA =
            "CREATE TABLE " + TABLE_POT_DATA + " (" +
                    COLUMN_POT_DATA_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_SESSION_ID + " INTEGER, " +
                    COLUMN_POT_ID + " TEXT, " +
                    COLUMN_WHEAT_SPIKES + " INTEGER, " +
                    "FOREIGN KEY(" + COLUMN_SESSION_ID + ") REFERENCES " + TABLE_SESSIONS + "(" + COLUMN_SESSION_ID + "));";


    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Enable foreign key constraints
        db.execSQL("PRAGMA foreign_keys = ON;");
        // Create tables
        db.execSQL(CREATE_TABLE_USERS);
        db.execSQL(CREATE_TRIGGER_USERS_UPDATED_AT);
        db.execSQL(CREATE_TABLE_SESSIONS);
        db.execSQL(CREATE_TABLE_POT_DATA);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Handle database upgrades if needed. For simplicity, we'll drop and recreate the table.
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_POT_DATA);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SESSIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    // User management methods
    // Method to add a new user
    public long addUser(String firstname, String lastname, String email, String password) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            String hashedPassword = hashPassword(password);

            ContentValues values = new ContentValues();
            values.put(COLUMN_FIRSTNAME, firstname);
            values.put(COLUMN_LASTNAME, lastname);
            values.put(COLUMN_EMAIL, email);
            values.put(COLUMN_PASSWORD, hashedPassword);

            db.insert(TABLE_USERS, null, values);
        } catch (Exception e) {
            Log.e(TAG, "Error adding user to database: ", e);
        } finally {
            if (db != null) db.close();
        }
        return 0;
    }

    // Method to check if an email is already registered
    public boolean checkUserExists(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_USERS + " WHERE " + COLUMN_EMAIL + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{email});

        boolean exists = cursor.getCount() > 0;
        cursor.close();
        db.close();

        return exists;
    }

    // Method to validate user login with hashed password
    public boolean validateUser(String email, String password) {
        // Query your database to get the stored password hash for the given email
        SQLiteDatabase db = this.getReadableDatabase();

        // 1. Fetch the stored hashed password based on email
        String[] columns = {DatabaseHelper.COLUMN_PASSWORD};
        String selection = DatabaseHelper.COLUMN_EMAIL + " = ?";
        String[] selectionArgs = {email};

        Cursor cursor = db.query(DatabaseHelper.TABLE_USERS, columns, selection, selectionArgs, null, null, null);
        if (cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_PASSWORD);
            if (columnIndex >= 0) {
                String storedPasswordHash = cursor.getString(columnIndex);  //Get the stored hashed password
                cursor.close();
                db.close();

                // 2. Hash the provided password
                String providedPasswordHash = hashPassword(password);

                // 3. Compare the hashes using slowEquals
                return slowEquals(providedPasswordHash, storedPasswordHash);

            } else {
                // Handle the case where the column doesn't exist (log an error, etc.)
                System.err.println("Error: Password column not found in database.");
                cursor.close();
                db.close();
                return false;
            }
        } else {
            // No user found with the provided email
            cursor.close();
            db.close();
            return false; // User not found
        }
    }

    // Constant-time comparison function to prevent timing attacks
    private static boolean slowEquals(String a, String b) {
        int diff = a.length() ^ b.length();
        for (int i = 0; i < a.length() && i < b.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    public boolean updateUser(int userId, String firstname, String lastname, String email, String newPassword) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_FIRSTNAME, firstname);
        values.put(COLUMN_LASTNAME, lastname);
        values.put(COLUMN_EMAIL, email);

        // Hash the new password and update it if provided
        if (!TextUtils.isEmpty(newPassword)) {
            String hashedPassword = hashPassword(newPassword);
            values.put(COLUMN_PASSWORD, hashedPassword);
        }

        int rowsAffected = db.update(TABLE_USERS, values, COLUMN_USER_ID + " = ?", new String[]{String.valueOf(userId)});
        db.close();

        return rowsAffected > 0;
    }

    // Method to delete user data from the database
    public void deleteUserData(int userId) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Begin a transaction to ensure data integrity
        db.beginTransaction();
        try {
            // Delete all records associated with the user in other tables
            db.delete(TABLE_POT_DATA, COLUMN_SESSION_ID + " IN (SELECT " + COLUMN_SESSION_ID + " FROM " + TABLE_SESSIONS +
                    " WHERE " + COLUMN_USER_ID + " = ?)", new String[]{String.valueOf(userId)});
            db.delete(TABLE_SESSIONS, COLUMN_USER_ID + " = ?", new String[]{String.valueOf(userId)});

            // Finally, delete the user record from the users table
            db.delete(TABLE_USERS, COLUMN_USER_ID + " = ?", new String[]{String.valueOf(userId)});

            // Set the transaction as successful
            db.setTransactionSuccessful();
        } catch (Exception e) {
            e.printStackTrace();
            // Handle the exception, perhaps log it
            Log.e(TAG, "Error deleting user data: ", e);
        } finally {
            // End the transaction
            db.endTransaction();
            db.close();
        }
    }

    // Method that retrives the user ID based on the email address
    public int getUserIdByEmail(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT " + COLUMN_USER_ID + " FROM " + TABLE_USERS + " WHERE email = ?";
        Cursor cursor = db.rawQuery(query, new String[]{email});

        int userId = -1; // Default value if user not found
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                userId = cursor.getInt(cursor.getColumnIndex(COLUMN_USER_ID));
                Log.d(TAG, "User ID for email " + email + ": " + userId);
            }
            cursor.close();
        }

        return userId;
    }

    // Method to get user details by email
    public String[] getUserDetails(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_FIRSTNAME, COLUMN_LASTNAME};
        String selection = COLUMN_EMAIL + " = ?";
        String[] selectionArgs = {email};

        Cursor cursor = db.query(TABLE_USERS, columns, selection, selectionArgs, null, null, null);
        if (cursor.moveToFirst()) {
            String firstName = cursor.getString(cursor.getColumnIndex(COLUMN_FIRSTNAME));
            String lastName = cursor.getString(cursor.getColumnIndex(COLUMN_LASTNAME));
            cursor.close();
            db.close();
            return new String[]{firstName, lastName};
        } else {
            cursor.close();
            db.close();
            return null;
        }
    }

    // Method to retrieve all users
    public List<String> getAllUsers() {
        List<String> userList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null, null, null, null, null, null);

        if (cursor.moveToFirst()) {
            do {
                String firstname = cursor.getString(cursor.getColumnIndex(COLUMN_FIRSTNAME));
                String lastname = cursor.getString(cursor.getColumnIndex(COLUMN_LASTNAME));
                String email = cursor.getString(cursor.getColumnIndex(COLUMN_EMAIL));
                userList.add(firstname + " " + lastname + " (" + email + ")");
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return userList;
    }

    // Method to delete a user by email
    public void deleteUser(String email) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_USERS, COLUMN_EMAIL + " = ?", new String[]{email});
        db.close();
    }

    // Method to retrieve user data (e.g., for a specific user)
    public Cursor getUserData(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_USERS, null, COLUMN_EMAIL + " = ?", new String[]{email}, null, null, null);
    }

    public String getUserPassword(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, new String[]{COLUMN_PASSWORD}, COLUMN_EMAIL + " = ?", new String[]{email}, null, null, null);

        if (cursor.moveToFirst()) {
            String passwordHash = cursor.getString(cursor.getColumnIndex(COLUMN_PASSWORD));
            cursor.close();
            db.close();
            return passwordHash; // This is the hashed password
        } else {
            cursor.close();
            db.close();
            return null;
        }
    }
}