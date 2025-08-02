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

import com.example.gogdownloader.R;
import com.example.gogdownloader.activities.LibraryActivity;
import com.example.gogdownloader.api.GOGLibraryManager;
import com.example.gogdownloader.database.DatabaseHelper;
import com.example.gogdownloader.models.DownloadLink;
import com.example.gogdownloader.models.Game;
import com.example.gogdownloader.utils.PreferencesManager;
import com.example.gogdownloader.utils.SAFDownloadManager;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadService extends Service {
    
    private static final String TAG = "DownloadService";
    
    // Actions
    private static final String ACTION_DOWNLOAD = "com.example.gogdownloader.DOWNLOAD";
    private static final String ACTION_CANCEL = "com.example.gogdownloader.CANCEL";
    private static final String ACTION_STOP_SERVICE = "com.example.gogdownloader.STOP_SERVICE";
    
    // Extras
    private static final String EXTRA_GAME = "extra_game";
    private static final String EXTRA_GAME_ID = "extra_game_id";
    
    // Notification
    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 1000;
    
    private NotificationManager notificationManager;
    private ExecutorService executorService;
    private Map<Long, DownloadTask> activeDownloads;
    
    private GOGLibraryManager libraryManager;
    private DatabaseHelper databaseHelper;
    private PreferencesManager preferencesManager;
    private SAFDownloadManager safDownloadManager;
    private OkHttpClient httpClient;
    
    public static Intent createDownloadIntent(Context context, Game game) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_DOWNLOAD);
        intent.putExtra(EXTRA_GAME, game);
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
        
        libraryManager = new GOGLibraryManager(this);
        databaseHelper = new DatabaseHelper(this);
        preferencesManager = new PreferencesManager(this);
        safDownloadManager = new SAFDownloadManager(this);
        
        // Configurar cliente HTTP com timeouts maiores para download
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)  // 5 minutos para leitura
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        
        createNotificationChannel();
        
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
            if (game != null) {
                startDownload(game);
            }
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
    
    private void startDownload(Game game) {
        Log.d(TAG, "Starting download for game: " + game.getTitle());
        
        // Verificar se já está sendo baixado
        if (activeDownloads.containsKey(game.getId())) {
            Log.w(TAG, "Game is already being downloaded: " + game.getTitle());
            return;
        }
        
        // Atualizar status no banco
        game.setStatus(Game.DownloadStatus.DOWNLOADING);
        databaseHelper.updateGame(game);
        
        // Criar notificação inicial
        showDownloadNotification(game, 0, "Iniciando download...");
        
        // Iniciar como foreground service
        startForeground(NOTIFICATION_ID + (int) game.getId(), 
                createDownloadNotification(game, 0, "Iniciando download..."));
        
        // Carregar detalhes do jogo e links de download
        libraryManager.loadGameDetails(game.getId(), new GOGLibraryManager.GameDetailsCallback() {
            @Override
            public void onSuccess(Game detailedGame, List<DownloadLink> downloadLinks) {
                if (downloadLinks.isEmpty()) {
                    onDownloadError(game, "Nenhum link de download encontrado");
                    return;
                }
                
                // Usar o primeiro link de instalador disponível
                DownloadLink mainInstaller = null;
                for (DownloadLink link : downloadLinks) {
                    if (link.getType() == DownloadLink.FileType.INSTALLER && 
                        link.getPlatform() == DownloadLink.Platform.WINDOWS) {
                        mainInstaller = link;
                        break;
                    }
                }
                
                if (mainInstaller == null && !downloadLinks.isEmpty()) {
                    mainInstaller = downloadLinks.get(0); // Usar qualquer link se não encontrar instalador Windows
                }
                
                if (mainInstaller != null) {
                    startFileDownload(detailedGame, mainInstaller);
                } else {
                    onDownloadError(game, "Nenhum instalador compatível encontrado");
                }
            }
            
            @Override
            public void onError(String error) {
                onDownloadError(game, error);
            }
        });
    }
    
    private void startFileDownload(Game game, DownloadLink downloadLink) {
        // Obter URL de download real
        libraryManager.getDownloadLink(game.getId(), downloadLink.getId(), "installer", 
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
        if (task != null) {
            task.cancel();
            activeDownloads.remove(gameId);
            
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
            if (activeDownloads.isEmpty()) {
                stopForeground(true);
            }
        }
    }
    
    private void stopService() {
        Log.d(TAG, "Stopping download service");
        
        // Cancelar todos os downloads
        for (long gameId : activeDownloads.keySet()) {
            cancelDownload(gameId);
        }
        
        stopSelf();
    }
    
    private void onDownloadProgress(Game game, long bytesDownloaded, long totalBytes) {
        int progress = totalBytes > 0 ? (int) ((bytesDownloaded * 100) / totalBytes) : 0;
        
        // Atualizar banco de dados
        game.setDownloadProgress(bytesDownloaded);
        game.setTotalSize(totalBytes);
        databaseHelper.updateGame(game);
        
        // Atualizar notificação
        String progressText = String.format("%d%% - %s / %s", 
                progress,
                Game.formatFileSize(bytesDownloaded),
                Game.formatFileSize(totalBytes));
        
        showDownloadNotification(game, progress, progressText);
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
        if (activeDownloads.isEmpty()) {
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
        
        // Mostrar notificação de erro
        showErrorNotification(game, error);
        
        // Parar foreground se não há mais downloads
        if (activeDownloads.isEmpty()) {
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
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, 
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
    
    // Classe interna para gerenciar o download de um arquivo
    private class DownloadTask implements Runnable {
        private Game game;
        private DownloadLink downloadLink;
        private volatile boolean cancelled = false;
        
        public DownloadTask(Game game, DownloadLink downloadLink) {
            this.game = game;
            this.downloadLink = downloadLink;
        }
        
        public void cancel() {
            cancelled = true;
        }
        
        @Override
        public void run() {
            try {
                downloadFile();
            } catch (Exception e) {
                if (!cancelled) {
                    Log.e(TAG, "Download error", e);
                    onDownloadError(game, e.getMessage());
                }
            }
        }
        
        private void downloadFile() throws IOException {
            String downloadUrl = downloadLink.getDownloadUrl();
            if (downloadUrl == null || downloadUrl.isEmpty()) {
                throw new IOException("URL de download inválida");
            }
            
            Log.d(TAG, "Starting download using SAF for: " + game.getTitle());
            
            // Tentar usar SAF primeiro
            if (safDownloadManager.hasDownloadLocationConfigured()) {
                downloadFileUsingSAF();
            } else {
                // Fallback para método legado
                downloadFileLegacy();
            }
        }
        
        private void downloadFileUsingSAF() throws IOException {
            Log.d(TAG, "Using SAF for download: " + game.getTitle());
            
            // Criar arquivo usando SAF
            DocumentFile downloadFile = safDownloadManager.createDownloadFile(game, downloadLink);
            if (downloadFile == null) {
                throw new IOException("Não foi possível criar arquivo de download");
            }
            
            Log.d(TAG, "Created download file: " + downloadFile.getName());
            
            // Download real usando SAF
            realDownloadSAF(downloadFile);
        }
        
        private void downloadFileLegacy() throws IOException {
            Log.d(TAG, "Using legacy method for download: " + game.getTitle());
            
            // Criar arquivo de destino (método original)
            String downloadPath = preferencesManager.getDownloadPath();
            File gameDir = new File(downloadPath, game.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_"));
            if (!gameDir.exists()) {
                gameDir.mkdirs();
            }
            
            String fileName = downloadLink.getFileName();
            File outputFile = new File(gameDir, fileName);
            
            // Download real usando arquivo local
            realDownloadLegacy(outputFile);
        }
        
        private void realDownloadSAF(DocumentFile outputFile) throws IOException {
            String downloadUrl = downloadLink.getDownloadUrl();
            Log.d(TAG, "Starting real SAF download from: " + downloadUrl);
            
            Request request = new Request.Builder()
                    .url(downloadUrl)
                    .get()
                    .addHeader("User-Agent", "Mozilla/5.0 (Android 10; Mobile; rv:91.0) Gecko/91.0 Firefox/91.0")
                    .addHeader("Accept", "*/*")
                    .addHeader("Accept-Language", "en-US,en;q=0.5")
                    .addHeader("Accept-Encoding", "gzip, deflate")
                    .addHeader("DNT", "1")
                    .addHeader("Connection", "keep-alive")
                    .addHeader("Referer", "https://www.gog.com/")
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("HTTP Error: " + response.code() + " - " + response.message());
                }
                
                long totalBytes = response.body().contentLength();
                if (totalBytes <= 0) {
                    totalBytes = downloadLink.getSize();
                }
                
                Log.d(TAG, "Content-Length: " + totalBytes + " bytes");
                
                try (InputStream inputStream = response.body().byteStream();
                     OutputStream outputStream = safDownloadManager.getOutputStream(outputFile)) {
                    
                    long bytesDownloaded = 0;
                    byte[] buffer = new byte[16384]; // 16KB buffer
                    int bytesRead;
                    
                    long lastProgressUpdate = System.currentTimeMillis();
                    
                    while ((bytesRead = inputStream.read(buffer)) != -1 && !cancelled) {
                        outputStream.write(buffer, 0, bytesRead);
                        bytesDownloaded += bytesRead;
                        
                        // Atualizar progresso a cada 500ms para não sobrecarregar
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastProgressUpdate > 500) {
                            onDownloadProgress(game, bytesDownloaded, totalBytes);
                            lastProgressUpdate = currentTime;
                        }
                        
                        // Verificar se foi cancelado
                        if (cancelled) {
                            outputFile.delete();
                            return;
                        }
                    }
                    
                    // Flush final
                    outputStream.flush();
                    
                    if (cancelled) {
                        outputFile.delete();
                        return;
                    }
                    
                    // Progresso final
                    onDownloadProgress(game, bytesDownloaded, bytesDownloaded);
                    
                    // Download completo
                    String filePath = outputFile.getUri().toString();
                    Log.d(TAG, "SAF download completed: " + filePath + " (" + bytesDownloaded + " bytes)");
                    onDownloadComplete(game, filePath);
                    
                } catch (IOException e) {
                    // Deletar arquivo em caso de erro
                    if (outputFile.exists()) {
                        outputFile.delete();
                    }
                    throw e;
                }
            }
        }
        
        private void realDownloadLegacy(File outputFile) throws IOException {
            String downloadUrl = downloadLink.getDownloadUrl();
            Log.d(TAG, "Starting real legacy download from: " + downloadUrl);
            
            Request request = new Request.Builder()
                    .url(downloadUrl)
                    .get()
                    .addHeader("User-Agent", "Mozilla/5.0 (Android 10; Mobile; rv:91.0) Gecko/91.0 Firefox/91.0")
                    .addHeader("Accept", "*/*")
                    .addHeader("Accept-Language", "en-US,en;q=0.5")
                    .addHeader("Accept-Encoding", "gzip, deflate")
                    .addHeader("DNT", "1")
                    .addHeader("Connection", "keep-alive")
                    .addHeader("Referer", "https://www.gog.com/")
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("HTTP Error: " + response.code() + " - " + response.message());
                }
                
                long totalBytes = response.body().contentLength();
                if (totalBytes <= 0) {
                    totalBytes = downloadLink.getSize();
                }
                
                Log.d(TAG, "Content-Length: " + totalBytes + " bytes");
                
                try (InputStream inputStream = response.body().byteStream();
                     FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                    
                    long bytesDownloaded = 0;
                    byte[] buffer = new byte[16384]; // 16KB buffer
                    int bytesRead;
                    
                    long lastProgressUpdate = System.currentTimeMillis();
                    
                    while ((bytesRead = inputStream.read(buffer)) != -1 && !cancelled) {
                        outputStream.write(buffer, 0, bytesRead);
                        bytesDownloaded += bytesRead;
                        
                        // Atualizar progresso a cada 500ms para não sobrecarregar
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastProgressUpdate > 500) {
                            onDownloadProgress(game, bytesDownloaded, totalBytes);
                            lastProgressUpdate = currentTime;
                        }
                        
                        // Verificar se foi cancelado
                        if (cancelled) {
                            outputFile.delete();
                            return;
                        }
                    }
                    
                    // Flush final
                    outputStream.flush();
                    
                    if (cancelled) {
                        outputFile.delete();
                        return;
                    }
                    
                    // Progresso final
                    onDownloadProgress(game, bytesDownloaded, bytesDownloaded);
                    
                    // Download completo
                    Log.d(TAG, "Legacy download completed: " + outputFile.getAbsolutePath() + " (" + bytesDownloaded + " bytes)");
                    onDownloadComplete(game, outputFile.getAbsolutePath());
                    
                } catch (IOException e) {
                    // Deletar arquivo em caso de erro
                    if (outputFile.exists()) {
                        outputFile.delete();
                    }
                    throw e;
                }
            }
        }
    }
}