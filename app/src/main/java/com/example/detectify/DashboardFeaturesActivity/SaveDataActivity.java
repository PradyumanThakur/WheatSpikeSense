package com.example.detectify.DashboardFeaturesActivity;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SaveDataActivity extends AppCompatActivity {
    private static final String TAG = "SaveDataActivity";

    private static final String PREFS_NAME = "user_prefs";
    private static final String PREF_USER_ID = "user_id";
    private static final String PREF_EMAIL = "email";

    private static final int REQUEST_CODE_WRITE_EXTERNAL_STORAGE = 1;
    private static Context mContext;

    public static void exportData(Context context) {
        mContext = context;
        // Check for storage permission (if needed)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted, request it
                ActivityCompat.requestPermissions((AppCompatActivity) context, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_WRITE_EXTERNAL_STORAGE);
                return; // Exit the method if permission is not granted yet
            }
        }

        // Proceed with exporting data if permission is already granted
        exportDataInternal(context);
    }

    private static void exportDataInternal(Context context) {
        // Retrieve user_id and email from SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int userId = prefs.getInt(PREF_USER_ID, -1);
        String email = prefs.getString(PREF_EMAIL, null);

        if (userId == -1 || email == null) {
            Log.e(TAG, "User ID or Email not found in SharedPreferences.");
            Toast.makeText(context, "User data not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Format the email and file name
        String formattedEmail = email.replace(".", "_");
        String fileName = String.format("%d_%s_%s.csv", userId, formattedEmail, new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()));

        // Use MediaStore to create the file
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/wheatSpikeSenseData");
            Log.d(TAG, "Environment.DIRECTORY_DOWNLOADS: " + Environment.DIRECTORY_DOWNLOADS);
        }

        Uri uri = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        }

        if (uri != null) {
            try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
                // Write CSV header and data rows to outputStream
                writeCsvData(context, outputStream, userId);

                Log.d(TAG, "User data exported to CSV: " + uri);
                Toast.makeText(context, "Data saved to: Downloads/wheatSpikeSenseData/" + fileName, Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                Log.e(TAG, "Error exporting user data to CSV: ", e);
                Toast.makeText(context, "Error saving data", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.e(TAG, "Failed to create file using MediaStore.");
            Toast.makeText(context, "Error saving data", Toast.LENGTH_SHORT).show();
        }
    }

    private static void writeCsvData(Context context, OutputStream outputStream, int userId) throws IOException {
        // Open SQLiteDatabase
        SQLiteDatabase db = context.openOrCreateDatabase("detectify.db", Context.MODE_PRIVATE, null);

        String query =
                "SELECT s.user_id, u.email, s.session_number, p.pot_id, p.wheat_spikes, s.start_time, s.end_time " +
                        "FROM sessions s " +
                        "LEFT JOIN potData p ON s.session_id = p.session_id " +
                        "INNER JOIN users u ON s.user_id = u.user_id " +
                        "WHERE s.user_id = ? " +
                        "ORDER BY s.session_id ASC, p.pot_id ASC";

        try (Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(userId)})) {
            // Write CSV header
            outputStream.write("user_id,email,session_number,pot_id,wheat_spikes,start_time,end_time\n".getBytes());

            // Write data rows
            while (cursor.moveToNext()) {
                int userIdColumn = cursor.getInt(cursor.getColumnIndex("user_id"));
                String emailColumn = cursor.getString(cursor.getColumnIndex("email"));
                int sessionNumber = cursor.getInt(cursor.getColumnIndex("session_number"));
                String potId = cursor.getString(cursor.getColumnIndex("pot_id"));
                int wheatSpikes = cursor.getInt(cursor.getColumnIndex("wheat_spikes"));
                String startTime = cursor.getString(cursor.getColumnIndex("start_time"));
                String endTime = cursor.getString(cursor.getColumnIndex("end_time"));

                // Write each row to CSV
                String row = userIdColumn + "," +
                        (emailColumn != null ? emailColumn + "," : "NULL,") +
                        sessionNumber + "," +
                        (potId != null ? potId + "," : "NULL,") +
                        (cursor.isNull(cursor.getColumnIndex("wheat_spikes")) ? "NULL," : wheatSpikes + ",") +
                        (startTime != null ? startTime + "," : "NULL,") +
                        (endTime != null ? endTime + "\n" : "NULL\n");

                outputStream.write(row.getBytes());
            }
        } finally {
            db.close();
        }
    }

    // Handle permission request result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with exporting data
                exportDataInternal(mContext); // Call exportData again
            } else {
                // Permission denied, handle accordingly (e.g., show a message)
                Toast.makeText(this, "Storage permission denied. Cannot export data.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}