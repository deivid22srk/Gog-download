package com.example.gogdownloader.activities;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.color.DynamicColors;
import com.example.gogdownloader.utils.DynamicColorManager;

/**
 * Base activity that sets up Material You Dynamic Color for all activities.
 * Optimized and simplified for Material Design Components 1.10.0
 * All activities in the app should extend this class to get Dynamic Color support.
 */
public abstract class BaseActivity extends AppCompatActivity {
    
    private static final String TAG = "BaseActivity";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply Dynamic Color before calling super.onCreate()
        applyDynamicColor();
        super.onCreate(savedInstanceState);
        
        // Log dynamic color status
        logDynamicColorStatus();
        
        // Setup edge-to-edge experience
        setupEdgeToEdge();
    }
    
    /**
     * Applies Material You Dynamic Color to this activity.
     * Optimized for Material 1.10.0 - uses programmatic approach with robust error handling.
     */
    private void applyDynamicColor() {
        try {
            if (isDynamicColorAvailable()) {
                Log.d(TAG, "Applying Dynamic Color to " + getClass().getSimpleName() + " (Material 1.10)");
                DynamicColors.applyToActivityIfAvailable(this);
                DynamicColorManager.applyToActivityManually(this);
            } else {
                Log.d(TAG, "Dynamic Color not available for " + getClass().getSimpleName() + 
                    " (Android " + Build.VERSION.SDK_INT + "), using Material You fallback");
            }
        } catch (Exception e) {
            Log.w(TAG, "Error applying Dynamic Color to " + getClass().getSimpleName() + 
                ", using standard Material You colors", e);
        }
    }
    
    /**
     * Logs current dynamic color status for debugging.
     */
    private void logDynamicColorStatus() {
        try {
            Log.d(TAG, "=== " + getClass().getSimpleName() + " Theme Status (Material 1.10) ===");
            Log.d(TAG, DynamicColorManager.getThemeInfo(this));
            Log.d(TAG, "=== End Theme Info ===");
        } catch (Exception e) {
            Log.w(TAG, "Error logging theme status", e);
        }
    }
    
    /**
     * Sets up edge-to-edge experience for immersive display.
     * Simplified version compatible with Material 1.10.0
     */
    private void setupEdgeToEdge() {
        try {
            // Make status bar and navigation bar transparent
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ approach - simplified
                getWindow().setDecorFitsSystemWindows(false);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6+ approach - more compatible
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                );
            }
            
            Log.d(TAG, "Edge-to-edge setup completed for " + getClass().getSimpleName());
            
        } catch (Exception e) {
            Log.w(TAG, "Error setting up edge-to-edge experience, using standard layout", e);
        }
    }
    
    /**
     * Simplified helper method to handle window insets.
     * Compatible with Material 1.10.0 - uses AndroidX types only.
     */
    protected void handleWindowInsets(View rootView) {
        try {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        } catch (Exception e) {
            Log.w(TAG, "Error handling window insets", e);
        }
    }
    
    /**
     * Checks if Dynamic Color is available.
     * Compatible with Material 1.10.0
     */
    private boolean isDynamicColorAvailable() {
        return DynamicColorManager.isDynamicColorAvailable();
    }
    
    /**
     * Gets current dynamic color information for debugging.
     */
    protected String getDynamicColorInfo() {
        return DynamicColorManager.getThemeInfo(this);
    }
}