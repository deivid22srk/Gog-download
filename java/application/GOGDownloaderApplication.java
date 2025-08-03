package com.example.gogdownloader.application;

import android.app.Application;
import android.content.Intent;
import android.util.Log;
import com.example.gogdownloader.utils.DynamicColorManager;
import com.example.gogdownloader.services.DownloadService;
import com.example.gogdownloader.database.DatabaseHelper;

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
        
        // Inicializar sistema de downloads
        initializeDownloadSystem();
        
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
    
    /**
     * Inicializa o sistema de downloads para retomar downloads pendentes
     */
    private void initializeDownloadSystem() {
        Log.d(TAG, "Initializing download system...");
        
        try {
            // Verificar se há downloads pendentes
            DatabaseHelper databaseHelper = new DatabaseHelper(this);
            
            // Executar em thread separada para não bloquear a UI
            new Thread(() -> {
                try {
                    java.util.List<android.content.ContentValues> activeDownloads = databaseHelper.getActiveDownloads();
                    
                    if (!activeDownloads.isEmpty()) {
                        Log.d(TAG, "Found " + activeDownloads.size() + " pending downloads, starting DownloadService");
                        
                        // Iniciar o DownloadService para retomar downloads
                        Intent serviceIntent = new Intent(this, DownloadService.class);
                        serviceIntent.setAction("com.example.gogdownloader.RESUME_DOWNLOADS");
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent);
                        } else {
                            startService(serviceIntent);
                        }
                    } else {
                        Log.d(TAG, "No pending downloads found");
                    }
                    
                    databaseHelper.close();
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error checking for pending downloads", e);
                }
            }).start();
            
            Log.d(TAG, "Download system initialization complete");
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing download system", e);
        }
    }
}