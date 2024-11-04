package com.example.detectify.DashboardFeaturesActivity;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.detectify.DatabaseActivity.DatabaseHelper;
import com.example.detectify.R;
import com.example.detectify.UserActivity.DashboardActivity;
import com.example.detectify.UserActivity.LoginActivity;

public class UserProfileActivity extends AppCompatActivity {
    private  static final String TAG = "UserProfileActivity";

    private TextView userProfileName;
    private TextView userProfileEmail;
    private Button EditProfileButton, DeleteProfileButton;
    private ImageButton backButton;
    private int userId;
    private String userFirstName;
    private String userLastName;
    private String userEmail;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_user_profile);

        EditProfileButton = findViewById(R.id.buttonEdit);
        DeleteProfileButton = findViewById(R.id.buttonDelete);
        backButton = findViewById(R.id.backB);
        userProfileName = findViewById(R.id.textView1);
        userProfileEmail = findViewById(R.id.textView2);

        // Inside your activity or method where you need to get the userId
        prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        userFirstName = prefs.getString("firstname", "FirstName");
        userLastName = prefs.getString("lastname", "LastName");
        userId = prefs.getInt("user_id", -1);
        userEmail = prefs.getString("email", "Email");

        userProfileName.setText(userFirstName + " " + userLastName);
        userProfileEmail.setText(userEmail);

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(UserProfileActivity.this, DashboardActivity.class);
                startActivity(intent);
                Toast.makeText(UserProfileActivity.this, "Dashboard", Toast.LENGTH_SHORT).show();
                finish();
            }
        });


        EditProfileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(UserProfileActivity.this, EditProfileActivity.class);
                startActivity(intent);
            }
        });

        DeleteProfileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDeleteConfirmationDialog();
            }
        });
    }

    private void showDeleteConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm Delete");
        builder.setMessage("Please enter your password to confirm deletion:");

        final EditText input = new EditText(this);
        input.setHint("Password");
        builder.setView(input);

        builder.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String password = input.getText().toString().trim();
                if (TextUtils.isEmpty(password)) {
                    Toast.makeText(UserProfileActivity.this, "Password is required", Toast.LENGTH_SHORT).show();
                } else {
                    deleteProfile(password);
                }
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private void deleteProfile(String password) {
        if (userId != -1) {
            // Proceed with deleting user data
            DatabaseHelper dbHelper = new DatabaseHelper(this);

            // Assuming you have a method to validate password
            if (dbHelper.validateUser(userEmail, password)) {
                dbHelper.deleteUserData(userId);
                // Clear user session
                SharedPreferences.Editor editor = prefs.edit();
                editor.clear();
                editor.apply();

                Toast.makeText(UserProfileActivity.this, "Profile deleted successfully", Toast.LENGTH_SHORT).show();

                // Redirect to the login screen
                Intent intent = new Intent(UserProfileActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(UserProfileActivity.this, "Incorrect password", Toast.LENGTH_SHORT).show();
            }
        } else {
            // Handle the case where userId is not found
            Toast.makeText(UserProfileActivity.this, "User not found. Please log in again.", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(UserProfileActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        }
    }
}