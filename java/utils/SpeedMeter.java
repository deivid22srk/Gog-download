package com.example.gogdownloader.utils;

/**
 * Classe para medir velocidade de download em tempo real
 */
public class SpeedMeter {
    private static final long SPEED_CALCULATION_INTERVAL = 500; // 500ms
    private long lastSpeedUpdate = 0;
    private long lastBytesForSpeed = 0;
    private double currentSpeed = 0.0;
    
    public double updateSpeed(long totalBytesDownloaded) {
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastSpeedUpdate >= SPEED_CALCULATION_INTERVAL) {
            if (lastSpeedUpdate > 0) {
                long timeDiff = currentTime - lastSpeedUpdate;
                long bytesDiff = totalBytesDownloaded - lastBytesForSpeed;
                currentSpeed = (bytesDiff * 1000.0) / timeDiff; // bytes por segundo
            }
            
            lastSpeedUpdate = currentTime;
            lastBytesForSpeed = totalBytesDownloaded;
        }
        
        return currentSpeed;
    }
    
    public long calculateETA(long bytesDownloaded, long totalBytes) {
        if (currentSpeed <= 0 || totalBytes <= bytesDownloaded) {
            return 0;
        }
        return (long) ((totalBytes - bytesDownloaded) / currentSpeed);
    }
    
    public double getCurrentSpeed() {
        return currentSpeed;
    }
    
    /**
     * Formata a velocidade de download em formato legível
     */
    public static String formatSpeed(double bytesPerSecond) {
        if (bytesPerSecond < 1024) {
            return String.format("%.1f B/s", bytesPerSecond);
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format("%.1f KB/s", bytesPerSecond / 1024);
        } else {
            return String.format("%.1f MB/s", bytesPerSecond / (1024 * 1024));
        }
    }
    
    /**
     * Formata o tempo restante estimado em formato legível
     */
    public static String formatETA(long seconds) {
        if (seconds <= 0) {
            return "Calculando...";
        }
        
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%02d:%02d", minutes, secs);
        }
    }
    
    /**
     * Reset do medidor para reiniciar a medição
     */
    public void reset() {
        lastSpeedUpdate = 0;
        lastBytesForSpeed = 0;
        currentSpeed = 0.0;
    }
}