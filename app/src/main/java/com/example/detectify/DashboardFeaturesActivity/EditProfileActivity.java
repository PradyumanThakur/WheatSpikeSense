package com.example.detectify.DashboardFeaturesActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;


import com.example.detectify.DatabaseActivity.DatabaseHelper;
import com.example.detectify.R;
import com.example.detectify.UserActivity.DashboardActivity;
import com.example.detectify.Utility.SecurityUtil;

public class EditProfileActivity extends AppCompatActivity {
    private static final String TAG = "EditProfileActivity";
    private EditText etFirstname, etLastname, etEmail, etOldPassword, etNewPassword, etConfirmNewPassword;
    private Button btnSave;
    private ImageButton backButton;
    private DatabaseHelper databaseHelper;
    private SharedPreferences sharedPreferences;
    private int userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_profile);

        // Initialize views and database helper
        etFirstname = findViewById(R.id.edit_fname);
        etLastname = findViewById(R.id.edit_lname);
        etEmail = findViewById(R.id.editProfileTextEmail);
        etOldPassword = findViewById(R.id.editOld_password);
        etNewPassword = findViewById(R.id.editNew_password);
        etConfirmNewPassword = findViewById(R.id.editNew_confirm);
        backButton = findViewById(R.id.backB);
        btnSave = findViewById(R.id.save_button);


        databaseHelper = new DatabaseHelper(this);
        sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE);
        userId = sharedPreferences.getInt("user_id", -1);

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(EditProfileActivity.this, UserProfileActivity.class);
                startActivity(intent);
                Toast.makeText(EditProfileActivity.this, "User Profile", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        // Set the signup button click listener
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    // btnSignup.setEnabled(false);
                    saveUserData();

                } catch (Exception e) {
                    Log.e(TAG, "Error during saving user data: ", e);
                }
            }
        });
    }

    private void saveUserData() {
        // get user input
        String firstname = etFirstname.getText().toString().trim();
        String lastname = etLastname.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String oldPassword = etOldPassword.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString().trim();
        String confirmNewPassword = etConfirmNewPassword.getText().toString().trim();

        // Validate input
        if (TextUtils.isEmpty(firstname)) {
            etFirstname.setError("First name is required");
            return;
        }
        if (TextUtils.isEmpty(lastname)) {
            etLastname.setError("Last name is required");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            return;
        } else if (!isValidEmail(email)) {
            etEmail.setError("Invalid email address");
            return;
        }
        if (TextUtils.isEmpty(oldPassword)){
            etOldPassword.setError("Old Password is required");
            return;
        }
        if (TextUtils.isEmpty(newPassword)) {
            etNewPassword.setError("New password is required");
            return;
        } else if (!newPassword.equals(confirmNewPassword)) {
            etConfirmNewPassword.setError("Passwords do not match");
            return;
        }

        // Check if old password is correct
        if (!isOldPasswordCorrect(oldPassword)) {
            etOldPassword.setError("Old password is incorrect");
            return;
        }

        // Update the user data in the database
        boolean isUpdated = databaseHelper.updateUser(userId, firstname, lastname, email, newPassword);

        if (isUpdated) {
            // Notify the user of the success
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("firstname", firstname);
            editor.putString("lastname", lastname);
            editor.putString("email", email);
            editor.apply();

            Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(EditProfileActivity.this, UserProfileActivity.class);
            startActivity(intent);
            finish();
        } else {
            // Notify the user of the failure
            Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show();
        }
    }

    // Email validation method
    private boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    // Method to check if old password is correct
    private boolean isOldPasswordCorrect(String oldPassword) {
        // Get the logged-in user's email from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String currentEmail = sharedPreferences.getString("email", null);
        String storedPasswordHash = databaseHelper.getUserPassword(currentEmail);

        if (storedPasswordHash != null) {
            String hashedOldPassword = SecurityUtil.hashPassword(oldPassword);
            return SecurityUtil.slowEquals(hashedOldPassword, storedPasswordHash);
        } else {
            return false; // No stored password found
        }
    }
}