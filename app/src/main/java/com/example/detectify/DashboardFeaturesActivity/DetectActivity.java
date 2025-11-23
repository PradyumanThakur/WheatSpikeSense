package com.example.detectify.DashboardFeaturesActivity;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import com.example.detectify.CameraActivity.CameraProcess;
import com.example.detectify.CameraActivity.FullScreenAnalyse;
import com.example.detectify.DatabaseActivity.DatabaseHelper;
import com.example.detectify.Detector.Yolov5TFLiteDetector;
import com.example.detectify.R;
import com.example.detectify.UserActivity.DashboardActivity;
import com.google.common.util.concurrent.ListenableFuture;

import android.database.sqlite.SQLiteDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class DetectActivity extends AppCompatActivity {
    private static final String TAG = "DetectActivity";

    private PreviewView cameraPreviewMatch;
    private ImageView boxLabelCanvas;
    private Spinner modelSpinner;
    private Spinner delegateSpinner;
    private TextView inferenceTimeTextView;
    private TextView frameSizeTextView;
    private TextView objectCountsTextView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private Yolov5TFLiteDetector yolov5TFLiteDetector;

    private CameraProcess cameraProcess = new CameraProcess();

    private Button detectThresholdIncrementButton;
    private Button detectThresholdDecrementButton;
    private TextView detectThresholdTextView;
    private ImageButton closeButton;

    private FullScreenAnalyse fullScreenAnalyse;

    private DatabaseHelper dbHelper;
    private long sessionId; // To keep track of the current session

    /**
     * Get screen rotation angle. 0 indicates landscape orientation.
     */
    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

    /**
     * Initialize model
     *
     * @param modelName
     */
    private void initModel(String modelName) {
        try {
            this.yolov5TFLiteDetector = new Yolov5TFLiteDetector();
            this.yolov5TFLiteDetector.setModelFile(modelName);

            // Add delegate based on selection
            String selectedDelegate = delegateSpinner.getSelectedItem().toString();

            switch (selectedDelegate) {
                case "GPU":
                    yolov5TFLiteDetector.addGPUDelegate();
                    break;
                case "NNAPI":
                    yolov5TFLiteDetector.addNNApiDelegate();
                    break;
                default:
                    // CPU delegate is the default
            }
            this.yolov5TFLiteDetector.initialModel(this);
            Log.i("model", "Success loading model: " + this.yolov5TFLiteDetector.getModelFile());
            Toast.makeText(this, modelName + " model loaded successfully with " + selectedDelegate + " delegate.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("image", "Load model error: " + e.getMessage(), e);
            Toast.makeText(this, "Error loading " + modelName + " model", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_detect);

        // Set up immersive mode
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        // Full screen
        cameraPreviewMatch = findViewById(R.id.camera_preview_match);
        cameraPreviewMatch.setScaleType(PreviewView.ScaleType.FILL_START);

        // box/label screen
        boxLabelCanvas = findViewById(R.id.box_label_canvas);

        // drop down button
        modelSpinner = findViewById(R.id.model);
        delegateSpinner = findViewById(R.id.delegate);

        // Some views updated in real time
        inferenceTimeTextView = findViewById(R.id.inference_time);
        frameSizeTextView = findViewById(R.id.frame_size);
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        detectThresholdIncrementButton = findViewById(R.id.detectThresholdIncrementButton);
        detectThresholdDecrementButton = findViewById(R.id.detectThresholdDecrementButton);
        detectThresholdTextView = findViewById(R.id.detectThresholdTextView);

        objectCountsTextView = findViewById(R.id.objectCountsTextView);

        closeButton = findViewById(R.id.closebutton);

        dbHelper = new DatabaseHelper(this); // Initialize the database helper
        sessionId = startNewSession(); // Start a new session and get the session ID

        // Handle the back button press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Toast.makeText(DetectActivity.this, "Press X at Top-Right corner to close", Toast.LENGTH_SHORT).show();
            }
        });

        detectThresholdIncrementButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                yolov5TFLiteDetector.DETECT_THRESHOLD = Math.min(yolov5TFLiteDetector.DETECT_THRESHOLD + 0.1f, 1f);
                detectThresholdTextView.setText(String.format("Detect Threshold: %.2f", yolov5TFLiteDetector.DETECT_THRESHOLD));
            }
        });

        detectThresholdDecrementButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                yolov5TFLiteDetector.DETECT_THRESHOLD = Math.max(yolov5TFLiteDetector.DETECT_THRESHOLD - 0.1f, 0f);
                detectThresholdTextView.setText(String.format("Detect Threshold: %.2f", yolov5TFLiteDetector.DETECT_THRESHOLD));
            }
        });

        // Request camera permissions if not granted
        if (!cameraProcess.allPermissionsGranted(this)) {
            cameraProcess.requestPermissions(this);
        }

        // Set default model selection to yolov5s-fp16
        String defaultModel = "yolov5s-fp16";
        int defaultModelPosition = 0; // Position of yolov5s-fp16 in the spinner array
        modelSpinner.setSelection(defaultModelPosition);

        // Initialize model
        initModel(defaultModel);

        // Set up model spinner listener
        modelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String model = (String) adapterView.getItemAtPosition(i);
                Toast.makeText(DetectActivity.this, "Loading model: " + model, Toast.LENGTH_LONG).show();
                initModel(model);
                updateCameraView();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // Do nothing
            }
        });

        // Set up delegate spinner listener
        delegateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String delegate = (String) adapterView.getItemAtPosition(i);
                Toast.makeText(DetectActivity.this, "Delegate switched to: " + delegate, Toast.LENGTH_LONG).show();
                // Reinitialize the model with the new delegate
                initModel((String) modelSpinner.getSelectedItem());
                updateCameraView();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // Do nothing
            }
        });

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //closeButton.setEnabled(false);
                // Save object counts to the database
                saveObjectCountsToDatabase();

                Toast.makeText(DetectActivity.this, "Detection Session Closed. Session Data Saved.", Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(DetectActivity.this, DashboardActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
    }

    //Listen to the view change button
    private void updateCameraView() {
        int rotation = getScreenOrientation();
        // Enter full screen mode
        fullScreenAnalyse = new FullScreenAnalyse(
                DetectActivity.this,
                cameraPreviewMatch,
                boxLabelCanvas,
                rotation,
                inferenceTimeTextView,
                frameSizeTextView,
                yolov5TFLiteDetector,
                objectCountsTextView);
        cameraProcess.startCamera(DetectActivity.this, fullScreenAnalyse, cameraPreviewMatch);
    }

    // Start a new session
    private long startNewSession() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Get the next session number for the user
        Cursor cursor = db.rawQuery("SELECT IFNULL(MAX(session_number), 0) FROM sessions WHERE user_id = ?", new String[]{String.valueOf(getUserID())});
        int sessionNumber = 1;
        if (cursor.moveToFirst()) {
            sessionNumber = cursor.getInt(0) + 1;
        }
        cursor.close();

        ContentValues values = new ContentValues();
        values.put("user_id", getUserID());
        values.put("session_number", sessionNumber);
        values.put("status", "active"); // Set initial status to active

        long newSessionId = db.insert("sessions", null, values);
        db.close();
        return newSessionId;
    }

    private void saveObjectCountsToDatabase() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        if (fullScreenAnalyse == null) {
            Log.e(TAG, "FullScreenAnalyse instance is null. Cannot save spike data.");
            return;
        }
        // Get the spike per pot data from fullScreenAnalyse
        HashMap<String, Integer> spikePerPot = fullScreenAnalyse.getSpikePerPot();
        HashMap<String, Integer> validCounts = new HashMap<>();

        for (Map.Entry<String, Integer> entry : spikePerPot.entrySet()) {
            String potID = entry.getKey();
            int spikeCount = entry.getValue();

            // Filter out invalid counts like Pot0: -1
            if (spikeCount >= 0 && !(potID.equals("Pot0"))) {
                validCounts.put(potID, spikeCount);

                // Save the valid counts to the database
                ContentValues values = new ContentValues();
                values.put("session_id", sessionId);
                values.put("pot_id", potID);
                values.put("wheat_spikes", spikeCount);

                db.insert("potData", null, values);
            }
        }

        if (validCounts.isEmpty()) {
            // No detection occurred, save NULL values
            ContentValues nullValues = new ContentValues();
            nullValues.put("session_id", sessionId);
            nullValues.put("pot_id", (String) null);
            nullValues.put("wheat_spikes", (Integer) null);

            db.insert("potData", null, nullValues);
            Log.d(TAG, "No detections. Saved NULL values for pot_id and wheat_spikes.");
        } else {
            Log.d(TAG, "Valid object counts saved: " + validCounts.toString());
        }

        // Get the end time and timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        // Set the time zone to UTC
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        // Get the current time
        String currentTime = sdf.format(new Date());

        // Mark the session as complete and save end time
        ContentValues sessionValues = new ContentValues();
        sessionValues.put("status", "complete");
        sessionValues.put("end_time", currentTime);
        db.update("sessions", sessionValues, "session_id=?", new String[]{String.valueOf(sessionId)});

        db.close();
    }

    // Method to get the current user ID
    private int getUserID() {
        // Return the ID of the currently logged-in user
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        return prefs.getInt("user_id", -1); // Default value is -1 if user_id is not found
    }
}
