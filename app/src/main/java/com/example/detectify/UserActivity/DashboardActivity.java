package com.example.detectify.UserActivity;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;


import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.appcompat.app.AppCompatActivity;


import com.example.detectify.DashboardFeaturesActivity.DetectActivity;
import com.example.detectify.DashboardFeaturesActivity.SaveDataActivity;
import com.example.detectify.DashboardFeaturesActivity.UserProfileActivity;
import com.example.detectify.DashboardFeaturesActivity.VisualizationActivity;
import com.example.detectify.R;

public class DashboardActivity extends AppCompatActivity {
    private static final String TAG = "DashboardActivity";

    private TextView welcomeTextView;
    private ImageButton logOutButton, backButton;
    private View detectCard, visualizeCard, userProfile, downloadCard;
    private String userFirstName;
    private String userLastName;
    private int userId;
    private String userEmail;
    private OnBackPressedCallback backPressedCallback;
    private boolean doubleBackToExitPressedOnce = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Initialize views
        welcomeTextView = findViewById(R.id.textView3);
        logOutButton = findViewById(R.id.logOutB);
        backButton = findViewById(R.id.backB);
        detectCard = findViewById(R.id.detectCard);
        visualizeCard = findViewById(R.id.interestsCard);
        downloadCard = findViewById(R.id.downloadCard);
        userProfile = findViewById(R.id.settingsCard);

        // Retrieve user details from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        userFirstName = prefs.getString("firstname", "FirstName");
        userLastName = prefs.getString("lastname", "LastName");
        userId = prefs.getInt("user_id", -1);
        userEmail = prefs.getString("email", "Email");


        // Set welcome message
        welcomeTextView.setText(userFirstName + " " + userLastName);

        // Set click listeners for buttons
        logOutButton.setOnClickListener(v -> showLogoutConfirmationDialog());

        // Handle the user-defined back button press
        backButton.setOnClickListener(v -> handleBackButton());

        // Handle the mobile back button press
        OnBackPressedDispatcher onBackPressedDispatcher = getOnBackPressedDispatcher();
        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackButton();
            }
        };
        onBackPressedDispatcher.addCallback(this, backPressedCallback);

        // Set click listeners for detect card
        detectCard.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, DetectActivity.class);
            startActivity(intent);
            saveLastActivity(DetectActivity.class.getName());
        });

        visualizeCard.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, VisualizationActivity.class);
            startActivity(intent);
            saveLastActivity(VisualizationActivity.class.getName());
        });

        // Set click listeners for download card
        downloadCard.setOnClickListener(v -> {
            SaveDataActivity.exportData(DashboardActivity.this);
            saveLastActivity(SaveDataActivity.class.getName());

        });

        userProfile.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, UserProfileActivity.class);
            startActivity(intent);
            saveLastActivity(UserProfileActivity.class.getName());
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Enable the back pressed callback when the activity is in the foreground
        if (backPressedCallback != null) {
            backPressedCallback.setEnabled(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Disable the back pressed callback when the activity is in the background
        if (backPressedCallback != null) {
            backPressedCallback.setEnabled(false);
        }
    }

    private void handleBackButton() {
        if (doubleBackToExitPressedOnce) {
            moveTaskToBack(true);  // Close all activities
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
    }

    // Show logout confirmation dialog
    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes", (dialog, which) -> logOut())
                .setNegativeButton("No", null)
                .show();
    }

    private void logOut() {
        // Clear SharedPreferences and redirect to LoginActivity
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();

        // Show logout message
        Toast.makeText(DashboardActivity.this, "Successfully logged out", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
        // Clear the activity stack
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void saveLastActivity(String activityName) {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("last_activity", activityName);
        editor.apply();
    }

    private void navigateToActivity(Class<?> activityClass) {
        saveLastActivity(activityClass.getName());
        Intent intent = new Intent(DashboardActivity.this, activityClass);
        startActivity(intent);
    }
}