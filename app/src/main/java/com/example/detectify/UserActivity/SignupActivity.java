package com.example.detectify.UserActivity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.detectify.DatabaseActivity.DatabaseHelper;
import com.example.detectify.R;

import java.io.IOException;

public class SignupActivity extends AppCompatActivity {
    private static final String TAG = "SignupActivity";

    private EditText etFirstname, etLastname, etEmail, etPassword, etConfirmPassword;
    private Button btnSignup;
    private TextView loginText;
    private DatabaseHelper databaseHelper;
    private OnBackPressedCallback backPressedCallback;


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
        btnSignup = findViewById(R.id.signup_button);
        loginText = findViewById(R.id.loginRedirectText);

        databaseHelper = new DatabaseHelper(this);

        // Set the signup button click listener
        btnSignup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    registerUser();
                } catch (Exception e) {
                    Log.e(TAG, "Error during user registration: ", e);
                }
            }
        });

        // Set the login text click listener
        loginText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    // Redirect to the login page
                    Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
                    startActivity(intent);
                    finish();
                } catch (Exception e) {
                    Log.e(TAG, "Error while redirecting to login page: ", e);
                }
            }
        });

        // Handle the back button press
        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitConfirmationDialog();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
    }

    private void registerUser() {
        try {
            // get user input
            String firstname = etFirstname.getText().toString().trim();
            String lastname = etLastname.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();

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
            if (TextUtils.isEmpty(password)) {
                etPassword.setError("Password is required");
                return;
            }
            if (TextUtils.isEmpty(confirmPassword)) {
                etConfirmPassword.setError("Confirm password is required");
                return;
            } else if (!password.equals(confirmPassword)) {
                etConfirmPassword.setError("Passwords do not match");
                return;
            }

            // Check if email already exists
            if (databaseHelper.checkUserExists(email)) {
                Toast.makeText(this, "Email already exists", Toast.LENGTH_SHORT).show();
                return;
            }

            // Add user to database
            databaseHelper.addUser(firstname, lastname, email, password);
            Toast.makeText(this, "User registered successfully", Toast.LENGTH_SHORT).show();

            // Create account using AccountManager asynchronously
            final String accountType = "com.example.detectify"; // Use your app's account type

            AccountManager accountManager = AccountManager.get(this);
            final Account account = new Account(email, accountType); // Use your app's account type
            final String authTokenType = "com.example.detectify.authtoken"; // Use your auth token type

            accountManager.addAccount(accountType, authTokenType, null, null, this, new AccountManagerCallback<Bundle>() {
                @Override
                public void run(AccountManagerFuture<Bundle> future) {
                    try {
                        Bundle result = future.getResult();
                        if (result.containsKey(AccountManager.KEY_ACCOUNT_NAME) && result.containsKey(AccountManager.KEY_ACCOUNT_TYPE)) {
                            // Account created successfully
                            Log.d(TAG, "Account created successfully");

                            // Redirect to login activity
                            Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            // Account creation failed
                            Log.e(TAG, "Account creation failed");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(SignupActivity.this, "Account creation failed", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    } catch (OperationCanceledException | IOException | AuthenticatorException e) {
                        // Handle exceptions
                        Log.e(TAG, "Error during account creation: ", e);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(SignupActivity.this, "Account creation failed", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }, null);
        } catch (Exception e) {
            Log.e(TAG, "Error during user registration: ", e);
            Toast.makeText(this, "Registration failed", Toast.LENGTH_SHORT).show();
        }
    }

    // Email validation method
    private boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void showExitConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    finishAffinity(); // Close all activities
                })
                .setNegativeButton("No", null)
                .show();
    }
}