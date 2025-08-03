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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        preferencesManager = new PreferencesManager(this);
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
        private final Pattern progressPattern = Pattern.compile("(\\d{1,3})\\.\\d{2}\\%");


        public InstallationTask(Uri installerFolderUri) {
            this.installerFolderUri = installerFolderUri;
        }

        @Override
        public void run() {
            try {
                // 1. Get path to innoextract binary (assuming it's bundled)
                String innoextractPath = getInnoextractPath();

                // 2. Find the setup_*.exe file
                DocumentFile installerFolder = DocumentFile.fromTreeUri(getApplicationContext(), installerFolderUri);
                if (installerFolder == null) {
                    throw new IOException("Installer folder not found.");
                }

                DocumentFile setupFile = null;
                for (DocumentFile file : installerFolder.listFiles()) {
                    String fileName = file.getName();
                    if (fileName != null && fileName.toLowerCase().startsWith("setup_") && fileName.toLowerCase().endsWith(".exe")) {
                        setupFile = file;
                        break;
                    }
                }

                if (setupFile == null) {
                    throw new IOException("Setup executable not found in the selected folder.");
                }

                // 3. Get the output directory
                String installUriString = preferencesManager.getInstallUri();
                if (installUriString == null) {
                    throw new IOException("Installation directory not set.");
                }
                // Note: We can't directly write to a DocumentFile URI path with native code.
                // A workaround is to use a temporary directory in the app's private space
                // and then move the files, but for this implementation, we'll assume a direct path
                // can be resolved, which might require root or more complex file handling.
                // For now, we'll use a placeholder path.
                File outputDir = new File(getCacheDir(), "install_temp");
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }

                // 4. Construct the command
                String[] command = {
                        innoextractPath,
                        "--output-dir",
                        outputDir.getAbsolutePath(),
                        setupFile.getUri().toString() // This might need a real file path
                };

                // 5. Execute the command
                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();

                // 6. Read output and parse progress
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Log.d(TAG, "innoextract: " + line);
                        Matcher matcher = progressPattern.matcher(line);
                        if (matcher.find()) {
                            String progressStr = matcher.group(1);
                            if (progressStr != null) {
                                int progress = Integer.parseInt(progressStr);
                                updateProgress(progress, line);
                            }
                        } else {
                            updateProgress(-1, line); // Indeterminate progress
                        }
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    updateProgress(100, "Instalação concluída!");
                } else {
                    throw new IOException("innoextract exited with error code: " + exitCode);
                }

            } catch (Exception e) {
                Log.e(TAG, "Installation failed", e);
                sendError(e.getMessage());
            } finally {
                stopForeground(true);
            }
        }

        private String getInnoextractPath() throws IOException {
            // In a real app, you'd copy the binary from assets to internal storage
            // and return the path to it.
            // For now, we'll assume it's in the system path for simplicity.
            return "innoextract";
        }

        private void updateProgress(int progress, String message) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(InstallationService.this, CHANNEL_ID)
                    .setContentTitle("Instalando Jogo")
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
