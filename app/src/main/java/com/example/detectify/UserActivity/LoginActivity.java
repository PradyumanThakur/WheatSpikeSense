package com.example.detectify.UserActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;


import com.example.detectify.DatabaseActivity.DatabaseHelper;
import com.example.detectify.R;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView signupText;
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.loginEmail);
        etPassword = findViewById(R.id.loginPassword);
        btnLogin = findViewById(R.id.buttonLogin);
        signupText = findViewById(R.id.signupRedirectText);

        databaseHelper = new DatabaseHelper(this);

        // Check if user is already logged in
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);

        if (isLoggedIn) {
            // User is already logged in, redirect to dashboard
            Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
            startActivity(intent);
            finish();
            return; // Important: Stop further execution of onCreate
        }

        // Set the login button click listener
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loginUser();
            }
        });

        // Set the signup text click listener
        signupText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    // Redirect to the signup page
                    Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
                    startActivity(intent);
                    finish();
                } catch (Exception e) {
                    Log.e(TAG, "Error while redirecting to signup page: ", e);
                }
            }
        });
    }

    private void loginUser() {
        try {
            // Get user input
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            // Validate input
            if (TextUtils.isEmpty(email)) {
                etEmail.setError("Email is required");
                //Toast.makeText(this, "Email is required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (TextUtils.isEmpty(password)) {
                etPassword.setError("Password is required");
                //Toast.makeText(this, "Password is required", Toast.LENGTH_SHORT).show();
                return;
            }

            // Check user credentials
            boolean isValidUser = databaseHelper.validateUser(email, password);

            if (isValidUser) {
                // Retrieve user details for storing in SharedPreferences
                String[] userDetails = databaseHelper.getUserDetails(email);

                if (userDetails != null) {
                    String firstName = userDetails[0];
                    String lastName = userDetails[1];
                    int userId = databaseHelper.getUserIdByEmail(email); // Retrieve the user ID


                    // Save user details in SharedPreferences
                    SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("firstname", firstName);
                    editor.putString("lastname", lastName);
                    editor.putString("email", email);
                    editor.putInt("user_id", userId); // Save user ID
                    editor.apply();

                    Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show();

                    // Redirect to dashboard
                    Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
                    startActivity(intent);
                    finish();

                } else {
                    Toast.makeText(LoginActivity.this, "Error retrieving user details", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show();
                clearFields();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during login: ", e);
            Toast.makeText(this, "Login failed. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearFields() {
        etEmail.setText("");
        etPassword.setText("");
    }
}