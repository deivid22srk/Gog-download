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
import androidx.documentfile.provider.DocumentFile;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.gogdownloader.R;
import com.example.gogdownloader.activities.LibraryActivity;
import com.example.gogdownloader.utils.PreferencesManager;
import com.example.gogdownloader.utils.SAFDownloadManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InstallationService extends Service {

    private static final String TAG = "InstallationService";

    public static final String ACTION_INSTALL = "com.example.gogdownloader.INSTALL";
    public static final String ACTION_INSTALL_PROGRESS = "com.example.gogdownloader.INSTALL_PROGRESS";

    public static final String EXTRA_INSTALLER_FOLDER_URI = "extra_installer_folder_uri";
    public static final String EXTRA_INSTALL_PROGRESS = "extra_install_progress";
    public static final String EXTRA_INSTALL_MESSAGE = "extra_install_message";
    public static final String EXTRA_INSTALL_ERROR = "extra_install_error";

    private static final String CHANNEL_ID = "install_channel";
    private static final int NOTIFICATION_ID = 2000;

    private NotificationManager notificationManager;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private PreferencesManager preferencesManager;
    private SAFDownloadManager safDownloadManager;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        preferencesManager = new PreferencesManager(this);
        safDownloadManager = new SAFDownloadManager(this);
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
            try {
                DocumentFile installerFolder = DocumentFile.fromTreeUri(getApplicationContext(), installerFolderUri);
                if (installerFolder == null || !installerFolder.isDirectory()) {
                    throw new IOException("Installer folder not found or is not a directory.");
                }

                List<DocumentFile> binFiles = new ArrayList<>();
                DocumentFile setupFile = null;
                for (DocumentFile file : installerFolder.listFiles()) {
                    String fileName = file.getName();
                    if (fileName != null) {
                        if (fileName.toLowerCase().startsWith("setup_") && fileName.toLowerCase().endsWith(".exe")) {
                            setupFile = file;
                        } else if (fileName.toLowerCase().endsWith(".bin")) {
                            binFiles.add(file);
                        }
                    }
                }

                if (setupFile == null) {
                    throw new IOException("Setup executable not found.");
                }

                binFiles.add(0, setupFile); // Process setup file first

                DocumentFile installDir = safDownloadManager.getInstallDirectory();
                if (installDir == null) {
                    throw new IOException("Installation directory not configured.");
                }

                long totalSize = 0;
                for(DocumentFile file : binFiles) {
                    totalSize += file.length();
                }

                long processedBytes = 0;
                updateProgress(0, "Starting installation...");

                for (int i = 0; i < binFiles.size(); i++) {
                    DocumentFile file = binFiles.get(i);
                    String msg = String.format("Extracting: %s (%d/%d)", file.getName(), i + 1, binFiles.size());
                    updateProgress((int) ((processedBytes * 100) / totalSize), msg);

                    // Simulate extraction by creating a dummy file
                    DocumentFile dummyFile = installDir.createFile("text/plain", "extracted_" + file.getName());
                    if (dummyFile != null) {
                        // Simulate writing content
                        try (OutputStream os = safDownloadManager.getOutputStream(dummyFile, false)) {
                            os.write(("Extracted from " + file.getName()).getBytes());
                        }
                    }

                    processedBytes += file.length();
                    Thread.sleep(500); // Simulate work
                }

                updateProgress(100, "Installation complete!");
                stopForeground(true);

            } catch (Exception e) {
                Log.e(TAG, "Installation failed", e);
                sendError(e.getMessage());
                stopForeground(true);
            }
        }

        private void updateProgress(int progress, String message) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(InstallationService.this, CHANNEL_ID)
                    .setContentTitle("Installing Game")
                    .setContentText(message)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOngoing(true);

            if (progress >= 0) {
                builder.setProgress(100, progress, false);
            } else {
                builder.setProgress(100, 0, true); // Indeterminate
            }

            startForeground(NOTIFICATION_ID, builder.build());

            Intent intent = new Intent(ACTION_INSTALL_PROGRESS);
            intent.putExtra(EXTRA_INSTALL_PROGRESS, progress);
            intent.putExtra(EXTRA_INSTALL_MESSAGE, message);
            LocalBroadcastManager.getInstance(InstallationService.this).sendBroadcast(intent);
        }

        private void sendError(String errorMessage) {
            Intent intent = new Intent(ACTION_INSTALL_PROGRESS);
            intent.putExtra(EXTRA_INSTALL_ERROR, errorMessage);
            LocalBroadcastManager.getInstance(InstallationService.this).sendBroadcast(intent);
        }
    }
}
