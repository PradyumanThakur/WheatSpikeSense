package com.example.detectify.DashboardFeaturesActivity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.detectify.DatabaseActivity.DatabaseHelper;
import com.example.detectify.R;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.slider.RangeSlider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class VisualizationActivity extends AppCompatActivity {
    private static final String TAG = "VisualizationActivity";

    private RangeSlider rangeSlider;
    private BarChart barChart;
    private int minSession = 1; // Default value
    private int maxSession = 1;
    private TextView sessionTextView;// Default value

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_visualization);

        rangeSlider = findViewById(R.id.range_slider);
        sessionTextView = findViewById(R.id.session_text);

        // Set the colors programmatically
        rangeSlider.setThumbTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.slider_thumb_color)));
        rangeSlider.setTrackActiveTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.slider_track_active_color)));
        rangeSlider.setTrackInactiveTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.slider_track_inactive_color)));


        barChart = findViewById(R.id.bar_chart);

        // Get logged-in user ID from SharedPreferences
        int loggedInUserId = getLoggedInUserId();

        if (loggedInUserId == -1) {
            // Handle case where user ID is not found (e.g., user not logged in)
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
            finish(); // Or take other appropriate action
            return;
        }

        // Fetch data and determine range
        SQLiteOpenHelper dbHelper = new DatabaseHelper(this);
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT MIN(" + DatabaseHelper.COLUMN_SESSION_NUMBER + ") AS min_session, MAX(" + DatabaseHelper.COLUMN_SESSION_NUMBER + ") AS max_session FROM " + DatabaseHelper.TABLE_SESSIONS + " WHERE " + DatabaseHelper.COLUMN_USER_ID + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(loggedInUserId)});

        if (cursor.moveToFirst()) {
            minSession = cursor.getInt(cursor.getColumnIndexOrThrow("min_session"));
            maxSession = cursor.getInt(cursor.getColumnIndexOrThrow("max_session"));
        }

        cursor.close();
        db.close();

        // Check if there are any sessions
        if (minSession == 0 && maxSession == 0) {
            handleEmptyData("No sessions found.");
            return; // Stop further execution
        }

        // Check if minSession and maxSession are equal
        if (minSession == maxSession) {
            // Handle the case where there's only one session
            // Option 1: Disable the RangeSlider
            rangeSlider.setEnabled(false);
            // Option 2: Adjust the values (e.g., add 1 to maxSession)
            // maxSession++;
        }

        if (minSession < maxSession) {
            // Set up range slider
            rangeSlider.setValueFrom(minSession);
            rangeSlider.setValueTo(maxSession);

            List<Float> initialValues = new ArrayList<>();
            initialValues.add((float) minSession); // Convert to float
            initialValues.add((float) maxSession); // Convert to float
            rangeSlider.setValues(initialValues);

        }

        // Set the initial text for the sessionTextView
        sessionTextView.setText("Session " + minSession + " - " + maxSession);

        // Range slider listener
        rangeSlider.addOnChangeListener((slider, value, fromUser) -> {
            int minSelectedSession = (int) (float) slider.getValues().get(0);
            int maxSelectedSession = (int) (float) slider.getValues().get(1);

            // Update the session range text
            sessionTextView.setText("Session " + minSelectedSession + " - " + maxSelectedSession);
            Log.d(TAG, "Session Range: " + minSelectedSession + " - " + maxSelectedSession);

            // Filter data
            Map<Integer, Map<String, Float>> filteredData = fetchDataFromDatabase(loggedInUserId, minSelectedSession, maxSelectedSession);

            if (filteredData.isEmpty()) {
                handleEmptyData("No data found for the selected sessions.");
                return; // Stop further execution
            }

            // Update the chart
            updateChart(filteredData);
        });

        // Initial chart setup (using data for all sessions)
        Map<Integer, Map<String, Float>> initialData = fetchDataFromDatabase(loggedInUserId, minSession, maxSession);
        updateChart(initialData);

        setChartBackground(); // Set background based on theme
    }

    // Method to fetch data from the database based on session range
    private Map<Integer, Map<String, Float>> fetchDataFromDatabase(int userId, int minSession, int maxSession) {
        Map<Integer, Map<String, Float>> sessionPotSpikes = new HashMap<>();

        DatabaseHelper dbHelper = new DatabaseHelper(this);
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT s." + DatabaseHelper.COLUMN_SESSION_NUMBER + ", p." + DatabaseHelper.COLUMN_POT_ID + ", SUM(p." + DatabaseHelper.COLUMN_WHEAT_SPIKES + ") AS total_spikes " +
                "FROM " + DatabaseHelper.TABLE_POT_DATA + " p " +
                "INNER JOIN " + DatabaseHelper.TABLE_SESSIONS + " s ON p." + DatabaseHelper.COLUMN_SESSION_ID + " = s." + DatabaseHelper.COLUMN_SESSION_ID + " " +
                "WHERE s." + DatabaseHelper.COLUMN_USER_ID + " = ? AND s." + DatabaseHelper.COLUMN_SESSION_NUMBER + " BETWEEN ? AND ? " +
                "GROUP BY s." + DatabaseHelper.COLUMN_SESSION_NUMBER + ", p." + DatabaseHelper.COLUMN_POT_ID;

        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(userId), String.valueOf(minSession), String.valueOf(maxSession)});

        while (cursor.moveToNext()) {
            int sessionNumber = cursor.getInt(cursor.getColumnIndexOrThrow("session_number"));
            String potId = cursor.getString(cursor.getColumnIndexOrThrow("pot_id"));
            float totalSpikes = cursor.getFloat(cursor.getColumnIndexOrThrow("total_spikes"));

            if (!sessionPotSpikes.containsKey(sessionNumber)) {
                sessionPotSpikes.put(sessionNumber, new HashMap<>());
            }
            sessionPotSpikes.get(sessionNumber).put(potId, totalSpikes);
        }

        cursor.close();
        db.close();
        Log.d(TAG, "SessionPotSpikes: " + sessionPotSpikes);

        return sessionPotSpikes;
    }

    // Method to handle empty data
    private void handleEmptyData(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        barChart.setVisibility(View.GONE); // Or disable the chart
        // ... (show empty state view or take other actions if needed)
        //Log.d(TAG, message);
        //setChartBackground();
        return;
    }

    // Method to generate a list of colors based on the number of pots
    private List<Integer> generateColors(int numColors) {
        List<Integer> colors = new ArrayList<>();
        int colorStart = ContextCompat.getColor(this, R.color.color_start); // Start color
        int colorEnd = ContextCompat.getColor(this, R.color.color_end); // End color

        for (int i = 0; i < numColors; i++) {
            float ratio = (float) i / (numColors - 1);
            int color = blendColors(colorStart, colorEnd, ratio);
            colors.add(color);
        }
        return colors;
    }

    // Method to blend two colors
    private int blendColors(int color1, int color2, float ratio) {
        int red = (int) (Color.red(color1) * (1 - ratio) + Color.red(color2) * ratio);
        int green = (int) (Color.green(color1) * (1 - ratio) + Color.green(color2) * ratio);
        int blue = (int) (Color.blue(color1) * (1 - ratio) + Color.blue(color2) * ratio);
        return Color.rgb(red, green, blue);
    }

    // Method to update the chart with data
    private void updateChart(Map<Integer, Map<String, Float>> data) {
        if (data.isEmpty()) {
            handleEmptyData("No data available for the chart.");
            return;
        }

        // Collect all unique pot IDs across all sessions
        Set<String> uniquePotIds = new TreeSet<>(); // TreeSet will sort them alphabetically by default
        for (Map<String, Float> potSpikes : data.values()) {
            for (String potId : potSpikes.keySet()) {
                if (potId != null) {
                    uniquePotIds.add(potId);
                }
            }
        }

        // Convert the set to a list (if needed, otherwise use the set directly)
        List<String> sortedPotIds = new ArrayList<>(uniquePotIds);
        List<BarEntry> yValues = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        Log.d(TAG, "Sorted Pot IDs: " + sortedPotIds);

        // Generate colors based on the number of unique pot IDs
        List<Integer> colors = generateColors(sortedPotIds.size());

        for (Map.Entry<Integer, Map<String, Float>> sessionEntry : data.entrySet()) {
            int sessionNumber = sessionEntry.getKey();
            Map<String, Float> potSpikes = sessionEntry.getValue();
            float[] spikesArray = new float[sortedPotIds.size()];
            Log.d(TAG, "Pot Spikes for Session " + sessionNumber + ": " + potSpikes);
            Log.d(TAG, "spikesArray: " + spikesArray);
            int i = 0;
            for (String potId : sortedPotIds) {
                if (potId != null) {
                    spikesArray[i++] = potSpikes.getOrDefault(potId, 0f);
                }
            }

            yValues.add(new BarEntry(sessionNumber, spikesArray));
            labels.add("Session " + sessionNumber);
            Log.d(TAG, "Session " + sessionNumber + ": " + spikesArray);
        }
        Log.d(TAG, "Labels: " + labels);
        Log.d(TAG, "yValues: " + yValues);

        BarDataSet dataSet = new BarDataSet(yValues, "Wheat Spikes per Pot");
        dataSet.setDrawIcons(false);
        dataSet.setColors(colors);
        dataSet.setValueTextColor(ContextCompat.getColor(this, R.color.white)); // White text color for readability
        dataSet.setValueTextSize(10f); // Adjust text size if needed

        BarData barData = new BarData(dataSet);
        barChart.setData(barData);
        barChart.setDrawValueAboveBar(false);
        barChart.setDrawGridBackground(false);
        barChart.setDrawBarShadow(false);
        barChart.setHighlightFullBarEnabled(false);

        // change the position of the y-labels
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f); // this replaces setStartAtZero(true)
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float yValues) {
                return String.valueOf((int) yValues);
            }
        });
        leftAxis.setTextSize(12f);
        barChart.getAxisRight().setEnabled(false);

        XAxis xLabels = barChart.getXAxis();
        xLabels.setPosition(XAxis.XAxisPosition.TOP);
        xLabels.setGranularity(1f);
        xLabels.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float sessionNumber) {
                return "Session " + (int) sessionNumber;
            }
        });
        xLabels.setTextSize(12f);

        barChart.getDescription().setEnabled(false);
        barChart.getLegend().setEnabled(false);

        barChart.invalidate();
    }

    private int getLoggedInUserId() {
        SharedPreferences sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        return sharedPreferences.getInt("user_id", -1);
    }

    private void setChartBackground() {
        int backgroundColor = getResources().getColor(R.color.background_color); // Default light mode color
        if (isDarkMode()) {
            backgroundColor = getResources().getColor(R.color.background_color); // Dark mode color
        }
        barChart.setBackgroundColor(backgroundColor);
    }

    private boolean isDarkMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
}