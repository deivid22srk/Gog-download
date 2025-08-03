package com.example.gogdownloader.application;

import android.app.Application;
import android.util.Log;
import com.example.gogdownloader.utils.DynamicColorManager;

/**
 * Application class for GOG Downloader.
 * Handles global initialization including Material You Dynamic Color support.
 * Compatible with Material Design Components 1.10.0+
 */
public class GOGDownloaderApplication extends Application {
    
    private static final String TAG = "GOGDownloaderApp";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.d(TAG, "=== GOG Downloader Application Starting (Material 1.10 Compatible) ===");
        
        // Apply Material You Dynamic Color to all activities
        initializeDynamicColor();
        
        Log.d(TAG, "=== Application Initialization Complete ===");
    }
    
    /**
     * Initializes Material You Dynamic Color support across the entire application.
     * Compatible with Material Design Components 1.10.0
     */
    private void initializeDynamicColor() {
        Log.d(TAG, "Initializing Material You Dynamic Color (Material 1.10 compatible)...");
        
        try {
            // Apply Dynamic Color to all activities
            DynamicColorManager.applyToApplication(this);
            
            // Log theme information
            String themeInfo = DynamicColorManager.getThemeInfo(this);
            Log.d(TAG, "Theme Information:\n" + themeInfo);
            
            Log.d(TAG, "Dynamic Color initialization successful");
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Dynamic Color, falling back to standard Material You colors", e);
            // App will still work with standard Material You colors from themes
        }
    }
}