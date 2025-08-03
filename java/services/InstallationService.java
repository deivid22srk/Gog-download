package com.example.gogdownloader.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.gogdownloader.R;
import com.example.gogdownloader.activities.LibraryActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InstallationService extends Service {

    private static final String TAG = "InstallationService";

    // Actions
    public static final String ACTION_INSTALL = "com.example.gogdownloader.INSTALL";
    public static final String ACTION_INSTALL_PROGRESS = "com.example.gogdownloader.INSTALL_PROGRESS";

    // Extras
    public static final String EXTRA_INSTALLER_FOLDER_URI = "extra_installer_folder_uri";
    public static final String EXTRA_INSTALL_PROGRESS = "extra_install_progress";
    public static final String EXTRA_INSTALL_MESSAGE = "extra_install_message";
    public static final String EXTRA_INSTALL_ERROR = "extra_install_error";

    // Notification
    private static final String CHANNEL_ID = "install_channel";
    private static final int NOTIFICATION_ID = 2000;

    private NotificationManager notificationManager;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_INSTALL.equals(intent.getAction())) {
            Uri installerFolderUri = intent.getParcelableExtra(EXTRA_INSTALLER_FOLDER_URI);
            if (installerFolderUri != null) {
                executorService.execute(new InstallationTask(installerFolderUri));
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Instalação de Jogos",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Mostra o progresso da instalação dos jogos.");
            notificationManager.createNotificationChannel(channel);
        }
    }

    private class InstallationTask implements Runnable {

        private final Uri installerFolderUri;

        public InstallationTask(Uri installerFolderUri) {
            this.installerFolderUri = installerFolderUri;
        }

        @Override
        public void run() {
            // This is a placeholder for the actual innoextract logic
            // In a real implementation, you would:
            // 1. Get the path to the bundled innoextract binary
            // 2. Find the setup_*.exe file in the installerFolderUri
            // 3. Get the output directory from PreferencesManager
            // 4. Construct the command line arguments
            // 5. Use ProcessBuilder to execute the command
            // 6. Read the process's stdout and stderr to get progress and errors
            // 7. Send broadcasts with progress updates

            try {
                // Simulate a long-running installation
                for (int i = 0; i <= 100; i += 10) {
                    updateProgress(i, "Instalando...");
                    Thread.sleep(1000);
                }
                updateProgress(100, "Instalação concluída.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sendError("Instalação interrompida.");
            }
        }

        private void updateProgress(int progress, String message) {
            // Update notification
            Notification notification = new NotificationCompat.Builder(InstallationService.this, CHANNEL_ID)
                    .setContentTitle("Instalando Jogo")
                    .setContentText(message)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setProgress(100, progress, false)
                    .setOngoing(true)
                    .build();
            startForeground(NOTIFICATION_ID, notification);

            // Send broadcast
            Intent intent = new Intent(ACTION_INSTALL_PROGRESS);
            intent.putExtra(EXTRA_INSTALL_PROGRESS, progress);
            intent.putExtra(EXTRA_INSTALL_MESSAGE, message);
            LocalBroadcastManager.getInstance(InstallationService.this).sendBroadcast(intent);
        }

        private void sendError(String errorMessage) {
            Intent intent = new Intent(ACTION_INSTALL_PROGRESS);
            intent.putExtra(EXTRA_INSTALL_ERROR, errorMessage);
            LocalBroadcastManager.getInstance(InstallationService.this).sendBroadcast(intent);
            stopForeground(true);
        }
    }
}
