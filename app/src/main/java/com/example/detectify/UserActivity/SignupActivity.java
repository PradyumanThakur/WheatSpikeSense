package com.example.detectify.UserActivity;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.detectify.DatabaseActivity.DatabaseHelper;
import com.example.detectify.R;

public class SignupActivity extends AppCompatActivity {
    private static final String TAG = "SignupActivity";

    private EditText etFirstname, etLastname, etEmail, etPassword, etConfirmPassword;
    private Button btnSignup;
    private TextView loginText;
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_signup);

        // Initialize views and database helper
        etFirstname = findViewById(R.id.signup_fname);
        etLastname = findViewById(R.id.signup_lname);
        etEmail = findViewById(R.id.editTextEmail);
        etPassword = findViewById(R.id.signup_password);
        etConfirmPassword = findViewById(R.id.signup_confirm);
        btnSignup = findViewById(R.id.signup_button);loginText = findViewById(R.id.loginRedirectText);

        databaseHelper = new DatabaseHelper(this);

        btnSignup.setOnClickListener(view -> registerUser());

        loginText.setOnClickListener(v -> {
            // Redirect to the login page
            Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });

        // Handle the back button press
        OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitConfirmationDialog();}
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
    }

    private void registerUser() {
        // get user input
        String firstname = etFirstname.getText().toString().trim();
        String lastname = etLastname.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // --- Input Validation (This part is good) ---
        if (!validateInput(firstname, lastname, email, password, confirmPassword)) {
            return; // Stop if validation fails
        }

        // --- Database Interaction ---
        try {
            // Check if email already exists
            if (databaseHelper.checkUserExists(email)) {
                Toast.makeText(this, "Email alreadyexists", Toast.LENGTH_SHORT).show();
                return;
            }

            // Add user to the local database
            long userId = databaseHelper.addUser(firstname, lastname, email, password);

            if (userId != -1) {
                Toast.makeText(this, "User registered successfully", Toast.LENGTH_SHORT).show();

                // Registration successful, redirect to LoginActivity to let the user log in.
                // This is the standard and simplest flow.
                Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
                // Pass the email to pre-fill the login formfor a better user experience
                intent.putExtra("EMAIL", email);
                startActivity(intent);
                finish(); // Finish SignupActivity so the user can't go back to it
            } else {
                Toast.makeText(this, "Registration failed. Please try again.", Toast.LENGTH_SHORT).show();}
        } catch (Exception e) {
            Log.e(TAG, "Error during user registration: ", e);
            Toast.makeText(this, "An error occurred during registration.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean validateInput(String fname, String lname, String email, String pass, String cpass) {
        if (TextUtils.isEmpty(fname)) {
            etFirstname.setError("First name is required");
            return false;
        }
        if (TextUtils.isEmpty(lname)) {
            etLastname.setError("Last name isrequired");
            return false;
        }
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            return false;
        } else if (!isValidEmail(email)) {
            etEmail.setError("Invalid email address");
            return false;
        }
        if (TextUtils.isEmpty(pass)) {
            etPassword.setError("Password is required");
            return false;
        }
        if (TextUtils.isEmpty(cpass)) {
            etConfirmPassword.setError("Confirm password is required");
            return false;} else if (!pass.equals(cpass)) {
            etConfirmPassword.setError("Passwords do not match");
            return false;
        }
        return true;
    }

    private boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email)&& Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void showExitConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton("Yes", (dialog, which) -> finishAffinity())
                .setNegativeButton("No", null)
                .show();
    }
}