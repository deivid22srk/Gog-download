package com.example.gogdownloader.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.gogdownloader.R;
import com.example.gogdownloader.activities.LibraryActivity;
import com.example.gogdownloader.api.GOGLibraryManager;
import com.example.gogdownloader.database.DatabaseHelper;
import com.example.gogdownloader.models.DownloadLink;
import com.example.gogdownloader.models.Game;
import com.example.gogdownloader.utils.PreferencesManager;
import com.example.gogdownloader.utils.SAFDownloadManager;
import com.example.gogdownloader.utils.SpeedMeter;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import android.content.ContentValues;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadService extends Service {
    
    private static final String TAG = "DownloadService";
    
    // Actions
    public static final String ACTION_DOWNLOAD_PROGRESS = "com.example.gogdownloader.DOWNLOAD_PROGRESS";
    private static final String ACTION_DOWNLOAD = "com.example.gogdownloader.DOWNLOAD";
    private static final String ACTION_DOWNLOAD_MULTIPLE = "com.example.gogdownloader.DOWNLOAD_MULTIPLE";
    private static final String ACTION_RESUME_DOWNLOADS = "com.example.gogdownloader.RESUME_DOWNLOADS";
    private static final String ACTION_CANCEL = "com.example.gogdownloader.CANCEL";
    private static final String ACTION_STOP_SERVICE = "com.example.gogdownloader.STOP_SERVICE";
    
    // Extras
    public static final String EXTRA_GAME_ID = "extra_game_id";
    public static final String EXTRA_BYTES_DOWNLOADED = "extra_bytes_downloaded";
    public static final String EXTRA_TOTAL_BYTES = "extra_total_bytes";
    public static final String EXTRA_CURRENT_FILE_INDEX = "extra_current_file_index";
    public static final String EXTRA_TOTAL_FILES = "extra_total_files";
    public static final String EXTRA_DOWNLOAD_SPEED = "extra_download_speed";
    public static final String EXTRA_ETA = "extra_eta";
    private static final String EXTRA_GAME = "extra_game";
    private static final String EXTRA_DOWNLOAD_LINK = "extra_download_link";
    private static final String EXTRA_DOWNLOAD_LINKS = "extra_download_links";
    
    // Notification
    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 1000;
    
    private NotificationManager notificationManager;
    private ExecutorService executorService;
    private Map<Long, DownloadTask> activeDownloads;
    private Map<Long, BatchDownloadTask> activeBatchDownloads;
    
    private GOGLibraryManager libraryManager;
    private DatabaseHelper databaseHelper;
    private PreferencesManager preferencesManager;
    private SAFDownloadManager safDownloadManager;
    private OkHttpClient httpClient;
    
    public static Intent createDownloadIntent(Context context, Game game, DownloadLink downloadLink) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_DOWNLOAD);
        intent.putExtra(EXTRA_GAME, game);
        intent.putExtra(EXTRA_DOWNLOAD_LINK, downloadLink);
        return intent;
    }
    
    public static Intent createMultipleDownloadIntent(Context context, Game game, List<DownloadLink> downloadLinks) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_DOWNLOAD_MULTIPLE);
        intent.putExtra(EXTRA_GAME, game);
        intent.putExtra(EXTRA_DOWNLOAD_LINKS, new ArrayList<>(downloadLinks));
        return intent;
    }
    
    public static Intent createCancelIntent(Context context, long gameId) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_CANCEL);
        intent.putExtra(EXTRA_GAME_ID, gameId);
        return intent;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        executorService = Executors.newFixedThreadPool(3); // Máximo 3 downloads simultâneos
        activeDownloads = new HashMap<>();
        activeBatchDownloads = new HashMap<>();
        
        libraryManager = new GOGLibraryManager(this);
        databaseHelper = new DatabaseHelper(this);
        preferencesManager = new PreferencesManager(this);
        safDownloadManager = new SAFDownloadManager(this);
        
        // Configurar cliente HTTP otimizado para downloads rápidos
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)  // Timeout de conexão mais rápido
                .readTimeout(60, TimeUnit.SECONDS)     // Timeout de leitura otimizado
                .writeTimeout(30, TimeUnit.SECONDS)    // Timeout de escrita otimizado
                .connectionPool(new okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES)) // Pool de conexões
                .retryOnConnectionFailure(true)       // Retry automático em falhas
                .followRedirects(true)                 // Seguir redirects automaticamente
                .followSslRedirects(true)
                .build();
        
        createNotificationChannel();
        
        // Retomar downloads pendentes
        resumePendingDownloads();
        
        Log.d(TAG, "DownloadService created");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        
        String action = intent.getAction();
        
        if (ACTION_DOWNLOAD.equals(action)) {
            Game game = (Game) intent.getSerializableExtra(EXTRA_GAME);
            DownloadLink downloadLink = (DownloadLink) intent.getSerializableExtra(EXTRA_DOWNLOAD_LINK);
            if (game != null && downloadLink != null) {
                startDownload(game, downloadLink);
            }
        } else if (ACTION_DOWNLOAD_MULTIPLE.equals(action)) {
            Game game = (Game) intent.getSerializableExtra(EXTRA_GAME);
            @SuppressWarnings("unchecked")
            List<DownloadLink> downloadLinks = (List<DownloadLink>) intent.getSerializableExtra(EXTRA_DOWNLOAD_LINKS);
            if (game != null && downloadLinks != null && !downloadLinks.isEmpty()) {
                startBatchDownload(game, downloadLinks);
            }
        } else if (ACTION_RESUME_DOWNLOADS.equals(action)) {
            Log.d(TAG, "Received RESUME_DOWNLOADS action");
            // Não fazer nada aqui, o resumePendingDownloads() já foi chamado no onCreate
        } else if (ACTION_CANCEL.equals(action)) {
            long gameId = intent.getLongExtra(EXTRA_GAME_ID, -1);
            if (gameId != -1) {
                cancelDownload(gameId);
            }
        } else if (ACTION_STOP_SERVICE.equals(action)) {
            stopService();
        }
        
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Serviço não precisa de binding
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // Cancelar todos os downloads ativos
        for (DownloadTask task : activeDownloads.values()) {
            task.cancel();
        }
        
        for (BatchDownloadTask task : activeBatchDownloads.values()) {
            task.cancel();
        }
        
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
        
        if (databaseHelper != null) {
            databaseHelper.close();
        }
        
        Log.d(TAG, "DownloadService destroyed");
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.download_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.download_channel_description));
            channel.setShowBadge(true);
            
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private void startDownload(Game game, DownloadLink downloadLink) {
        Log.d(TAG, "Starting download for game: " + game.getTitle());
        
        // Check if already downloading
        if (activeDownloads.containsKey(game.getId())) {
            Log.w(TAG, "Game is already being downloaded: " + game.getTitle());
            return;
        }
        
        // Update status in db
        game.setStatus(Game.DownloadStatus.DOWNLOADING);
        databaseHelper.updateGame(game);
        
        // Create initial notification
        showDownloadNotification(game, 0, "Starting download...");
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID + (int) game.getId(),
                createDownloadNotification(game, 0, "Starting download..."));
        
        // Start the file download directly
        startFileDownload(game, downloadLink);
    }
    
    private void startBatchDownload(Game game, List<DownloadLink> downloadLinks) {
        Log.d(TAG, "Starting batch download for game: " + game.getTitle() + " with " + downloadLinks.size() + " files");
        
        // Check if already downloading
        if (activeBatchDownloads.containsKey(game.getId())) {
            Log.w(TAG, "Game is already being downloaded: " + game.getTitle());
            return;
        }
        
        // Update status in db
        game.setStatus(Game.DownloadStatus.DOWNLOADING);
        databaseHelper.updateGame(game);
        
        // Create initial notification
        showBatchDownloadNotification(game, 0, downloadLinks.size(), "Iniciando downloads...");
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID + (int) game.getId(),
                createBatchDownloadNotification(game, 0, downloadLinks.size(), "Iniciando downloads..."));
        
        // Create and start batch download task
        BatchDownloadTask batchTask = new BatchDownloadTask(game, downloadLinks);
        activeBatchDownloads.put(game.getId(), batchTask);
        executorService.execute(batchTask);
    }
    
    private void resumePendingDownloads() {
        Log.d(TAG, "Checking for pending downloads to resume...");
        
        executorService.execute(() -> {
            try {
                // Buscar downloads ativos no banco de dados
                List<ContentValues> activeDownloads = databaseHelper.getActiveDownloads();
                
                if (activeDownloads.isEmpty()) {
                    Log.d(TAG, "No pending downloads found");
                    return;
                }
                
                Log.d(TAG, "Found " + activeDownloads.size() + " pending downloads");
                
                // Agrupar downloads por jogo
                Map<Long, List<ContentValues>> downloadsByGame = new HashMap<>();
                for (ContentValues download : activeDownloads) {
                    long gameId = download.getAsLong("game_id");
                    downloadsByGame.computeIfAbsent(gameId, k -> new ArrayList<>()).add(download);
                }
                
                for (Map.Entry<Long, List<ContentValues>> entry : downloadsByGame.entrySet()) {
                    long gameId = entry.getKey();
                    List<ContentValues> gameDownloads = entry.getValue();
                    
                    Game game = databaseHelper.getGame(gameId);
                    if (game == null) {
                        Log.w(TAG, "Game not found for ID: " + gameId + ", cleaning up downloads");
                        // Limpar downloads órfãos
                        for (ContentValues download : gameDownloads) {
                            long downloadId = download.getAsLong("id");
                            databaseHelper.updateDownloadStatus(downloadId, "CANCELLED", "Game not found");
                        }
                        continue;
                    }
                    
                    if (gameDownloads.size() == 1) {
                        // Download único
                        ContentValues download = gameDownloads.get(0);
                        resumeSingleDownload(game, download);
                    } else {
                        // Batch download
                        resumeBatchDownload(game, gameDownloads);
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error resuming pending downloads", e);
            }
        });
    }
    
    private void resumeSingleDownload(Game game, ContentValues downloadData) {
        try {
            String linkId = downloadData.getAsString("link_id");
            String fileName = downloadData.getAsString("file_name");
            String downloadUrl = downloadData.getAsString("download_url");
            
            DownloadLink downloadLink = new DownloadLink();
            downloadLink.setId(linkId);
            downloadLink.setName(fileName);
            downloadLink.setDownloadUrl(downloadUrl);
            
            Log.d(TAG, "Resuming single download: " + game.getTitle() + " - " + fileName);
            
            // A lógica de resume agora é tratada diretamente dentro do DownloadTask
            startDownload(game, downloadLink);
            
        } catch (Exception e) {
            Log.e(TAG, "Error resuming single download for game: " + game.getTitle(), e);
        }
    }
    
    private void resumeBatchDownload(Game game, List<ContentValues> downloads) {
        try {
            List<DownloadLink> downloadLinks = new ArrayList<>();
            
            for (ContentValues downloadData : downloads) {
                String linkId = downloadData.getAsString("link_id");
                String fileName = downloadData.getAsString("file_name");
                String downloadUrl = downloadData.getAsString("download_url");
                
                DownloadLink downloadLink = new DownloadLink();
                downloadLink.setId(linkId);
                downloadLink.setName(fileName);
                downloadLink.setDownloadUrl(downloadUrl);
                
                downloadLinks.add(downloadLink);
            }
            
            Log.d(TAG, "Resuming batch download: " + game.getTitle() + " - " + downloadLinks.size() + " files");
            
            // A lógica de resume agora é tratada diretamente dentro do BatchDownloadTask
            startBatchDownload(game, downloadLinks);
            
        } catch (Exception e) {
            Log.e(TAG, "Error resuming batch download for game: " + game.getTitle(), e);
        }
    }
    
    private void startFileDownload(Game game, DownloadLink downloadLink) {
        // Obter URL de download real
        libraryManager.getDownloadLink(game.getId(), downloadLink, "installer",
                new GOGLibraryManager.DownloadLinkCallback() {
            @Override
            public void onSuccess(String downloadUrl) {
                downloadLink.setDownloadUrl(downloadUrl);
                
                // Criar tarefa de download
                DownloadTask task = new DownloadTask(game, downloadLink);
                activeDownloads.put(game.getId(), task);
                
                // Executar download
                executorService.execute(task);
            }
            
            @Override
            public void onError(String error) {
                onDownloadError(game, "Erro ao obter URL de download: " + error);
            }
        });
    }
    
    private void cancelDownload(long gameId) {
        Log.d(TAG, "Cancelling download for game ID: " + gameId);
        
        DownloadTask task = activeDownloads.get(gameId);
        BatchDownloadTask batchTask = activeBatchDownloads.get(gameId);
        
        if (task != null) {
            task.cancel();
            activeDownloads.remove(gameId);
        }
        
        if (batchTask != null) {
            batchTask.cancel();
            activeBatchDownloads.remove(gameId);
        }
        
        if (task != null || batchTask != null) {
            // Atualizar status no banco
            Game game = databaseHelper.getGame(gameId);
            if (game != null) {
                game.setStatus(Game.DownloadStatus.NOT_DOWNLOADED);
                game.setDownloadProgress(0);
                databaseHelper.updateGame(game);
            }
            
            // Remover notificação
            notificationManager.cancel(NOTIFICATION_ID + (int) gameId);
            
            // Parar foreground se não há mais downloads
            if (activeDownloads.isEmpty() && activeBatchDownloads.isEmpty()) {
                stopForeground(true);
            }
        }
    }
    
    private void stopService() {
        Log.d(TAG, "Stopping download service");
        
        // Cancelar todos os downloads
        Set<Long> allGameIds = new HashSet<>();
        allGameIds.addAll(activeDownloads.keySet());
        allGameIds.addAll(activeBatchDownloads.keySet());
        
        for (long gameId : allGameIds) {
            cancelDownload(gameId);
        }
        
        stopSelf();
    }
    
    private void onDownloadProgress(Game game, long bytesDownloaded, long totalBytes) {
        onDownloadProgress(game, bytesDownloaded, totalBytes, 0, 0, 0, 0);
    }
    
    private void onDownloadProgress(Game game, long bytesDownloaded, long totalBytes, 
                                   int currentFileIndex, int totalFiles, double speed, long eta) {
        int progress = totalBytes > 0 ? (int) ((bytesDownloaded * 100) / totalBytes) : 0;
        
        // Atualizar banco de dados
        game.setDownloadProgress(bytesDownloaded);
        game.setTotalSize(totalBytes);
        databaseHelper.updateGame(game);
        
        // Atualizar notificação
        String progressText;
        if (totalFiles > 1) {
            // Batch download
            String speedText = speed > 0 ? String.format(" - %.1f MB/s", speed / (1024 * 1024)) : "";
            String etaText = eta > 0 ? String.format(" - ETA: %s", formatETA(eta)) : "";
            progressText = String.format("Arquivo %d/%d - %d%% - %s / %s%s%s", 
                    currentFileIndex + 1, totalFiles, progress,
                    Game.formatFileSize(bytesDownloaded),
                    Game.formatFileSize(totalBytes),
                    speedText, etaText);
            showBatchDownloadNotification(game, currentFileIndex, totalFiles, progressText);
        } else {
            // Single download
            String speedText = speed > 0 ? String.format(" - %.1f MB/s", speed / (1024 * 1024)) : "";
            String etaText = eta > 0 ? String.format(" - ETA: %s", formatETA(eta)) : "";
            progressText = String.format("%d%% - %s / %s%s%s", 
                    progress,
                    Game.formatFileSize(bytesDownloaded),
                    Game.formatFileSize(totalBytes),
                    speedText, etaText);
            showDownloadNotification(game, progress, progressText);
        }

        Intent intent = new Intent(ACTION_DOWNLOAD_PROGRESS);
        intent.putExtra(EXTRA_GAME_ID, game.getId());
        intent.putExtra(EXTRA_BYTES_DOWNLOADED, bytesDownloaded);
        intent.putExtra(EXTRA_TOTAL_BYTES, totalBytes);
        intent.putExtra(EXTRA_CURRENT_FILE_INDEX, currentFileIndex);
        intent.putExtra(EXTRA_TOTAL_FILES, totalFiles);
        intent.putExtra(EXTRA_DOWNLOAD_SPEED, speed);
        intent.putExtra(EXTRA_ETA, eta);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    
    private String formatETA(long etaSeconds) {
        if (etaSeconds < 60) {
            return etaSeconds + "s";
        } else if (etaSeconds < 3600) {
            return (etaSeconds / 60) + "m " + (etaSeconds % 60) + "s";
        } else {
            long hours = etaSeconds / 3600;
            long minutes = (etaSeconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        }
    }
    
    private void onDownloadComplete(Game game, String filePath) {
        Log.d(TAG, "Download completed for game: " + game.getTitle());
        
        // Atualizar status no banco
        game.setStatus(Game.DownloadStatus.DOWNLOADED);
        game.setLocalPath(filePath);
        databaseHelper.updateGame(game);
        
        // Remover da lista de downloads ativos
        activeDownloads.remove(game.getId());
        
        // Mostrar notificação de conclusão
        showCompletionNotification(game);
        
        // Parar foreground se não há mais downloads
        if (activeDownloads.isEmpty() && activeBatchDownloads.isEmpty()) {
            stopForeground(true);
        }
    }
    
    private void onDownloadError(Game game, String error) {
        Log.e(TAG, "Download failed for game: " + game.getTitle() + " - " + error);
        
        // Atualizar status no banco
        game.setStatus(Game.DownloadStatus.FAILED);
        databaseHelper.updateGame(game);
        
        // Remover da lista de downloads ativos
        activeDownloads.remove(game.getId());
        activeBatchDownloads.remove(game.getId());
        
        // Mostrar notificação de erro
        showErrorNotification(game, error);
        
        // Parar foreground se não há mais downloads
        if (activeDownloads.isEmpty() && activeBatchDownloads.isEmpty()) {
            stopForeground(true);
        }
    }
    
    private void showDownloadNotification(Game game, int progress, String progressText) {
        Notification notification = createDownloadNotification(game, progress, progressText);
        notificationManager.notify(NOTIFICATION_ID + (int) game.getId(), notification);
    }
    
    private Notification createDownloadNotification(Game game, int progress, String progressText) {
        Intent intent = new Intent(this, LibraryActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        Intent cancelIntent = createCancelIntent(this, game.getId());
        PendingIntent cancelPendingIntent = PendingIntent.getService(this, (int) game.getId(), 
                cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.downloading_game, game.getTitle()))
                .setContentText(progressText)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, progress, progress == 0)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_delete, 
                        getString(R.string.cancel), cancelPendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .build();
    }
    
    private void showCompletionNotification(Game game) {
        Intent intent = new Intent(this, LibraryActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.download_complete, game.getTitle()))
                .setContentText("Download concluído com sucesso")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
        
        notificationManager.notify(NOTIFICATION_ID + (int) game.getId(), notification);
    }
    
    private void showErrorNotification(Game game, String error) {
        Intent intent = new Intent(this, LibraryActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.download_failed, game.getTitle()))
                .setContentText(error)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
        
        notificationManager.notify(NOTIFICATION_ID + (int) game.getId(), notification);
    }
    
    private void showBatchDownloadNotification(Game game, int currentFileIndex, int totalFiles, String progressText) {
        Notification notification = createBatchDownloadNotification(game, currentFileIndex, totalFiles, progressText);
        notificationManager.notify(NOTIFICATION_ID + (int) game.getId(), notification);
    }
    
    private Notification createBatchDownloadNotification(Game game, int currentFileIndex, int totalFiles, String progressText) {
        Intent intent = new Intent(this, LibraryActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        Intent cancelIntent = createCancelIntent(this, game.getId());
        PendingIntent cancelPendingIntent = PendingIntent.getService(this, (int) game.getId(), 
                cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        int overallProgress = totalFiles > 0 ? (int) (((currentFileIndex * 100.0) / totalFiles)) : 0;
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.downloading_game, game.getTitle()))
                .setContentText(progressText)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, overallProgress, false)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_delete, 
                        getString(R.string.cancel), cancelPendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .build();
    }
    
    // Classe interna para gerenciar o download de um arquivo
    private class DownloadTask implements Runnable {
        private static final int NUM_CHUNKS = 4;
        private static final long MIN_SIZE_FOR_CHUNKED_DOWNLOAD = 10 * 1024 * 1024; // 10 MB

        private Game game;
        private DownloadLink downloadLink;
        private volatile boolean cancelled = false;
        private ExecutorService chunkExecutor;
        private SpeedMeter speedMeter = new SpeedMeter();

        public DownloadTask(Game game, DownloadLink downloadLink) {
            this.game = game;
            this.downloadLink = downloadLink;
        }

        public void cancel() {
            cancelled = true;
            if (chunkExecutor != null && !chunkExecutor.isShutdown()) {
                chunkExecutor.shutdownNow();
            }
        }

        @Override
        public void run() {
            try {
                if (safDownloadManager.hasDownloadLocationConfigured()) {
                    downloadFileUsingSAF();
                } else {
                    // O download legado não suportará aceleração por enquanto para simplificar.
                    downloadFileLegacy();
                }
            } catch (Exception e) {
                if (!cancelled) {
                    Log.e(TAG, "Download error for " + game.getTitle(), e);
                    onDownloadError(game, e.getMessage());
                }
            } finally {
                if (chunkExecutor != null && !chunkExecutor.isShutdown()) {
                    chunkExecutor.shutdown();
                }
            }
        }

        private void downloadFileUsingSAF() throws IOException {
            String downloadUrl = downloadLink.getDownloadUrl();
            if (downloadUrl == null || downloadUrl.isEmpty()) {
                throw new IOException("URL de download inválida");
            }

            // 1. Obter tamanho do arquivo com uma requisição HEAD
            Request headRequest = new Request.Builder().url(downloadUrl).head().build();
            long totalSize = -1;
            try (Response response = httpClient.newCall(headRequest).execute()) {
                if (response.isSuccessful()) {
                    totalSize = Long.parseLong(response.header("Content-Length", "-1"));
                    String acceptRanges = response.header("Accept-Ranges");
                    if (!"bytes".equalsIgnoreCase(acceptRanges)) {
                        Log.d(TAG, "Server does not accept range requests. Falling back to single thread.");
                        totalSize = -1; // Forçar fallback
                    }
                }
            } catch (IOException | NumberFormatException e) {
                Log.w(TAG, "HEAD request failed or content length invalid. Falling back to single thread.", e);
            }

            if (totalSize < MIN_SIZE_FOR_CHUNKED_DOWNLOAD) {
                Log.d(TAG, "File size is small (" + totalSize + " bytes). Using single-threaded download.");
                DocumentFile finalFile = safDownloadManager.createDownloadFile(game, downloadLink);
                if (finalFile == null) throw new IOException("Could not create download file.");
                singleThreadedDownload(finalFile, downloadUrl);
                return;
            }

            // 2. Iniciar download acelerado
            Log.d(TAG, "Starting accelerated download for " + game.getTitle());
            runChunkedDownload(totalSize, downloadUrl);
        }

        private void runChunkedDownload(long totalSize, String downloadUrl) throws IOException {
            chunkExecutor = Executors.newFixedThreadPool(NUM_CHUNKS);
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            List<DocumentFile> partFiles = new ArrayList<>();
            final Map<Integer, Long> chunkProgress = new java.util.concurrent.ConcurrentHashMap<>();

            long chunkSize = totalSize / NUM_CHUNKS;

            for (int i = 0; i < NUM_CHUNKS; i++) {
                long startByte = i * chunkSize;
                long endByte = (i == NUM_CHUNKS - 1) ? totalSize - 1 : startByte + chunkSize - 1;

                DocumentFile partFile = safDownloadManager.createOrFindDownloadPartFile(game, downloadLink, i);
                partFiles.add(partFile);
                chunkProgress.put(i, safDownloadManager.getFileSize(partFile));

                Runnable chunkDownloader = new ChunkDownloader(downloadUrl, startByte, endByte, i, partFile, chunkProgress);
                futures.add(chunkExecutor.submit(chunkDownloader));
            }

            // Progress aggregator
            long totalDownloaded = 0;
            while (totalDownloaded < totalSize && !cancelled) {
                totalDownloaded = 0;
                for (Long progress : chunkProgress.values()) {
                    totalDownloaded += progress;
                }
                double speed = speedMeter.updateSpeed(totalDownloaded);
                long eta = speedMeter.calculateETA(totalDownloaded, totalSize);
                onDownloadProgress(game, totalDownloaded, totalSize, 0, 1, speed, eta);
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    if (cancelled) break;
                }
            }

            try {
                for (java.util.concurrent.Future<?> future : futures) {
                    future.get(); // Wait for each chunk to complete and throw exception on failure
                }

                if (cancelled) {
                    Log.d(TAG, "Chunked download cancelled during chunk completion wait.");
                    return;
                }

                // 3. Concatenar arquivos
                Log.d(TAG, "All chunks downloaded. Concatenating files...");
                DocumentFile finalFile = safDownloadManager.createDownloadFile(game, downloadLink);
                safDownloadManager.concatenateFiles(partFiles, finalFile);

                onDownloadComplete(game, finalFile.getUri().toString());

            } catch (Exception e) {
                throw new IOException("A chunk download failed.", e);
            } finally {
                chunkExecutor.shutdownNow();
            }
        }
        
        private void singleThreadedDownload(DocumentFile outputFile, String downloadUrl) throws IOException {
            long bytesDownloaded = safDownloadManager.getFileSize(outputFile);
            Request.Builder requestBuilder = new Request.Builder().url(downloadUrl).get();
            if (bytesDownloaded > 0) {
                requestBuilder.addHeader("Range", "bytes=" + bytesDownloaded + "-");
            }

            try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                if (!response.isSuccessful() && response.code() != 206) throw new IOException("HTTP Error: " + response.code());

                boolean isResume = response.code() == 206;
                if (!isResume) bytesDownloaded = 0;

                long totalBytes = isResume ? bytesDownloaded + response.body().contentLength() : response.body().contentLength();

                try (InputStream in = response.body().byteStream(); OutputStream out = safDownloadManager.getOutputStream(outputFile, isResume)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1 && !cancelled) {
                        out.write(buffer, 0, len);
                        bytesDownloaded += len;
                        double speed = speedMeter.updateSpeed(bytesDownloaded);
                        long eta = speedMeter.calculateETA(bytesDownloaded, totalBytes);
                        onDownloadProgress(game, bytesDownloaded, totalBytes, 0, 1, speed, eta);
                    }
                }
                if (!cancelled && bytesDownloaded >= totalBytes && totalBytes > 0) {
                    onDownloadComplete(game, outputFile.getUri().toString());
                }
            }
        }
        
        private void downloadFileLegacy() throws IOException {
            Log.d(TAG, "Using legacy method for download: " + game.getTitle());
            String downloadPath = preferencesManager.getDownloadPath();
            File gameDir = new File(downloadPath, game.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_"));
            if (!gameDir.exists()) gameDir.mkdirs();
            File outputFile = new File(gameDir, downloadLink.getFileName());
            // Legacy download does not support acceleration for now.
            realDownloadLegacy(outputFile);
        }
        
        private void realDownloadLegacy(File outputFile) throws IOException {
            // This is the old single-threaded logic, kept for fallback.
             String downloadUrl = downloadLink.getDownloadUrl();
            long bytesDownloaded = 0;
            if (outputFile.exists()) {
                bytesDownloaded = outputFile.length();
            }

            Request.Builder requestBuilder = new Request.Builder().url(downloadUrl).get();
            if (bytesDownloaded > 0) {
                requestBuilder.addHeader("Range", "bytes=" + bytesDownloaded + "-");
            }
            
            try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                if (!response.isSuccessful() && response.code() != 206) throw new IOException("HTTP Error: " + response.code());
                
                boolean isResume = response.code() == 206;
                if (!isResume) bytesDownloaded = 0;
                
                long totalBytes = isResume ? bytesDownloaded + response.body().contentLength() : response.body().contentLength();
                
                try (InputStream in = response.body().byteStream(); FileOutputStream out = new FileOutputStream(outputFile, isResume)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1 && !cancelled) {
                        out.write(buffer, 0, len);
                        bytesDownloaded += len;
                         double speed = speedMeter.updateSpeed(bytesDownloaded);
                        long eta = speedMeter.calculateETA(bytesDownloaded, totalBytes);
                        onDownloadProgress(game, bytesDownloaded, totalBytes, 0, 1, speed, eta);
                    }
                }
                 if (!cancelled && bytesDownloaded >= totalBytes && totalBytes > 0) {
                    onDownloadComplete(game, outputFile.getAbsolutePath());
                }
            }
        }

        private class ChunkDownloader implements Runnable {
            private final String downloadUrl;
            private final long startByte;
            private final long endByte;
            private final int chunkIndex;
            private final DocumentFile partFile;
            private final Map<Integer, Long> chunkProgress;

            ChunkDownloader(String url, long start, long end, int index, DocumentFile file, Map<Integer, Long> progress) {
                this.downloadUrl = url;
                this.startByte = start;
                this.endByte = end;
                this.chunkIndex = index;
                this.partFile = file;
                this.chunkProgress = progress;
            }

            @Override
            public void run() {
                long bytesDownloaded = safDownloadManager.getFileSize(partFile);
                
                Request.Builder requestBuilder = new Request.Builder().url(downloadUrl)
                    .header("Range", "bytes=" + (startByte + bytesDownloaded) + "-" + endByte);

                try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                    if (response.code() != 206) throw new IOException("Server does not support partial requests for chunks. Code: " + response.code());

                    try (InputStream in = response.body().byteStream(); OutputStream out = safDownloadManager.getOutputStream(partFile, true)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            if (cancelled) {
                                Log.d(TAG, "Chunk " + chunkIndex + " cancelled.");
                                return;
                            }
                            out.write(buffer, 0, len);
                            bytesDownloaded += len;
                            chunkProgress.put(chunkIndex, bytesDownloaded);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Chunk " + chunkIndex + " failed to download.", e);
                    throw new RuntimeException(e);
                }
            }
        }
    }
    
    // Classe interna para gerenciar download de múltiplos arquivos
    private class BatchDownloadTask implements Runnable {
        private Game game;
        private List<DownloadLink> downloadLinks;
        private volatile boolean cancelled = false;

        public BatchDownloadTask(Game game, List<DownloadLink> downloadLinks) {
            this.game = game;
            this.downloadLinks = new ArrayList<>(downloadLinks);
        }

        public void cancel() {
            this.cancelled = true;
            // The cancellation will be picked up by the individual DownloadTask
        }
        
        @Override
        public void run() {
            for (int i = 0; i < downloadLinks.size() && !cancelled; i++) {
                DownloadLink link = downloadLinks.get(i);
                Log.d(TAG, "Starting download for file " + (i+1) + "/" + downloadLinks.size() + ": " + link.getName());
                
                // Create a temporary single-file download task for this file.
                // This reuses the new accelerated DownloadTask logic.
                DownloadTask fileTask = new DownloadTask(game, link);

                // We need to manage the active downloads map for this sub-task
                activeDownloads.put(game.getId(), fileTask);
                
                // Run the task synchronously within the batch task's thread.
                // The service's main executor is already running this BatchDownloadTask.
                fileTask.run();

                activeDownloads.remove(game.getId());

                if (cancelled) {
                    Log.d(TAG, "Batch download cancelled.");
                    break;
                }
            }

            if (!cancelled) {
                Log.d(TAG, "Batch download finished for " + game.getTitle());
                // The onDownloadComplete is called by the inner DownloadTask,
                // so we just need to update the final game status if needed.
                game.setStatus(Game.DownloadStatus.DOWNLOADED);
                databaseHelper.updateGame(game);
                showCompletionNotification(game); // Show a final notification for the batch
            }
            activeBatchDownloads.remove(game.getId());
        }
    }