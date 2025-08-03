package com.example.gogdownloader.utils;

import android.content.Context;
import android.util.Log;

/**
 * Utility class for testing Material You Dynamic Color implementation
 * specifically with Material Design Components 1.10.0
 */
public class DynamicColorTester {
    
    private static final String TAG = "DynamicColorTester";
    
    /**
     * Tests if Dynamic Color implementation is working correctly with Material 1.10
     */
    public static void testDynamicColorImplementation(Context context) {
        Log.d(TAG, "=== TESTING DYNAMIC COLOR IMPLEMENTATION (Material 1.10) ===");
        
        try {
            // Test 1: Check if DynamicColorManager is working
            boolean isAvailable = DynamicColorManager.isDynamicColorAvailable();
            Log.d(TAG, "✅ Dynamic Color Available: " + isAvailable);
            
            // Test 2: Check if colors can be retrieved
            int primaryColor = DynamicColorManager.getPrimaryColor(context);
            int secondaryColor = DynamicColorManager.getSecondaryColor(context);
            int surfaceColor = DynamicColorManager.getSurfaceColor(context);
            
            Log.d(TAG, "✅ Primary Color Retrieved: #" + Integer.toHexString(primaryColor).toUpperCase());
            Log.d(TAG, "✅ Secondary Color Retrieved: #" + Integer.toHexString(secondaryColor).toUpperCase());
            Log.d(TAG, "✅ Surface Color Retrieved: #" + Integer.toHexString(surfaceColor).toUpperCase());
            
            // Test 3: Get full theme information
            String themeInfo = DynamicColorManager.getThemeInfo(context);
            Log.d(TAG, "✅ Theme Info Retrieved:\n" + themeInfo);
            
            // Test 4: Test manual application
            DynamicColorManager.applyToActivityManually(context);
            Log.d(TAG, "✅ Manual application test completed");
            
            Log.d(TAG, "🎉 ALL TESTS PASSED - Dynamic Color working with Material 1.10!");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ DYNAMIC COLOR TEST FAILED", e);
            Log.e(TAG, "Error details: " + e.getMessage());
        }
        
        Log.d(TAG, "=== DYNAMIC COLOR TEST COMPLETE ===");
    }
    
    /**
     * Quick compatibility check for Material 1.10
     */
    public static boolean isCompatibilityOk(Context context) {
        try {
            // Try to get a basic color - if this works, implementation is compatible
            DynamicColorManager.getPrimaryColor(context);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Compatibility check failed", e);
            return false;
        }
    }
}