package com.example.detectify.UserActivity;

import static com.example.detectify.DashboardFeaturesActivity.SaveDataActivity.exportData;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;


import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
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

    public int getUserId() {
        return userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    private int userId;
    private String userEmail;

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
        logOutButton.setOnClickListener(v -> {
            showLogoutConfirmationDialog();
        });

        backButton.setOnClickListener(v ->  onBackPressed());


        // Handle the back button press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Toast.makeText(DashboardActivity.this, "Dashboard", Toast.LENGTH_SHORT).show();
            }
        });

        // Set click listeners for detect card
        detectCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //detectCard.setEnabled(false);
                Intent intent = new Intent(DashboardActivity.this, DetectActivity.class);
                startActivity(intent);
            }
        });

        visualizeCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, VisualizationActivity.class);
                startActivity(intent);
            }
        });

        // Set click listeners for download card
        downloadCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Call the exportData method
                exportData(DashboardActivity.this);
            }
        });

        userProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, UserProfileActivity.class);
                startActivity(intent);
            }
        });
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
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}