package com.example.gogdownloader.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import com.example.gogdownloader.R;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageLoader {
    
    private static final String TAG = "ImageLoader";
    private static final int CACHE_SIZE = 20 * 1024 * 1024; // 20MB
    
    private static ImageLoader instance;
    private LruCache<String, Bitmap> memoryCache;
    private ExecutorService executorService;
    private Handler mainHandler;
    
    private ImageLoader() {
        // Configurar cache de memória
        memoryCache = new LruCache<String, Bitmap>(CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };
        
        executorService = Executors.newFixedThreadPool(4);
        mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public static synchronized ImageLoader getInstance() {
        if (instance == null) {
            instance = new ImageLoader();
        }
        return instance;
    }
    
    public static void loadImage(Context context, String coverImageUrl, String backgroundImageUrl, ImageView imageView) {
        getInstance().load(context, coverImageUrl, backgroundImageUrl, imageView);
    }

    public static void loadImage(Context context, String imageUrl, ImageView imageView) {
        getInstance().load(context, imageUrl, null, imageView);
    }
    
    public void load(Context context, String coverImageUrl, String backgroundImageUrl, ImageView imageView) {
        Log.d(TAG, "=== LOADING IMAGE ===");
        Log.d(TAG, "Cover URL: '" + coverImageUrl + "'");
        Log.d(TAG, "Background URL: '" + backgroundImageUrl + "'");

        if (coverImageUrl == null || coverImageUrl.isEmpty()) {
            if (backgroundImageUrl != null && !backgroundImageUrl.isEmpty()) {
                load(context, backgroundImageUrl, null, imageView);
            } else {
                Log.w(TAG, "Image URLs are empty, using placeholder");
                imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            }
            return;
        }
        
        // Verificar cache primeiro
        Bitmap cachedBitmap = memoryCache.get(coverImageUrl);
        if (cachedBitmap != null) {
            Log.d(TAG, "Image found in cache: " + coverImageUrl);
            imageView.setImageBitmap(cachedBitmap);
            return;
        }
        
        // Definir placeholder enquanto carrega
        Log.d(TAG, "Setting placeholder for: " + coverImageUrl);
        imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        
        // Carregar imagem em background
        Log.d(TAG, "Starting background download for: " + coverImageUrl);
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Downloading bitmap: " + coverImageUrl);
                Bitmap bitmap = downloadBitmap(coverImageUrl);
                if (bitmap != null) {
                    Log.d(TAG, "Bitmap downloaded successfully: " + coverImageUrl);
                    // Adicionar ao cache
                    memoryCache.put(coverImageUrl, bitmap);
                    
                    // Atualizar UI na thread principal
                    mainHandler.post(() -> {
                        Log.d(TAG, "Setting bitmap to ImageView: " + coverImageUrl);
                        imageView.setImageBitmap(bitmap);
                    });
                } else {
                    Log.e(TAG, "Failed to download bitmap: " + coverImageUrl);
                    if (backgroundImageUrl != null && !backgroundImageUrl.isEmpty()) {
                        load(context, backgroundImageUrl, null, imageView);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading image: " + coverImageUrl, e);
                if (backgroundImageUrl != null && !backgroundImageUrl.isEmpty()) {
                    load(context, backgroundImageUrl, null, imageView);
                }
            }
        });
    }
    
    private Bitmap downloadBitmap(String imageUrl) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        
        try {
            Log.d(TAG, "=== STARTING BITMAP DOWNLOAD ===");
            Log.d(TAG, "Target URL: " + imageUrl);
            
            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000); // Aumentar timeout
            connection.setReadTimeout(15000);
            connection.setRequestMethod("GET");
            connection.setDoInput(true);
            connection.setInstanceFollowRedirects(true);
            
            // Headers importantes para GOG
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Android 10; Mobile; rv:91.0) Gecko/91.0 Firefox/91.0");
            connection.setRequestProperty("Accept", 
                "image/webp,image/apng,image/*,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
            connection.setRequestProperty("DNT", "1");
            connection.setRequestProperty("Connection", "keep-alive");
            connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
            connection.setRequestProperty("Referer", "https://www.gog.com/");
            
            Log.d(TAG, "Connecting to: " + url.getHost());
            connection.connect();
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "HTTP Response Code: " + responseCode);
            Log.d(TAG, "Response Message: " + connection.getResponseMessage());
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "Connection successful, reading image data...");
                inputStream = connection.getInputStream();
                
                // Decodificar bitmap diretamente sem sampling primeiro
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                
                if (bitmap != null) {
                    Log.d(TAG, "Bitmap decoded successfully. Size: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                    // Se a imagem for muito grande, redimensionar
                    if (bitmap.getWidth() > 300 || bitmap.getHeight() > 300) {
                        Log.d(TAG, "Resizing large bitmap...");
                        bitmap = Bitmap.createScaledBitmap(bitmap, 300, 300, true);
                    }
                    return bitmap;
                } else {
                    Log.e(TAG, "Failed to decode bitmap from stream");
                }
            } else if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                      responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                      responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                String redirectUrl = connection.getHeaderField("Location");
                Log.d(TAG, "Redirect detected to: " + redirectUrl);
                if (redirectUrl != null) {
                    return downloadBitmap(redirectUrl);
                }
            } else {
                Log.e(TAG, "HTTP Error " + responseCode + ": " + connection.getResponseMessage());
                // Ler error stream para mais detalhes
                try {
                    InputStream errorStream = connection.getErrorStream();
                    if (errorStream != null) {
                        byte[] errorBytes = new byte[1024];
                        int bytesRead = errorStream.read(errorBytes);
                        String errorMsg = new String(errorBytes, 0, bytesRead);
                        Log.e(TAG, "Error response body: " + errorMsg);
                        errorStream.close();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to read error stream", e);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Exception during image download: " + imageUrl, e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing input stream", e);
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
        
        Log.e(TAG, "Download failed for: " + imageUrl);
        return null;
    }
    
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        
        return inSampleSize;
    }
    
    public void clearCache() {
        memoryCache.evictAll();
    }
    
    public void preloadImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty() || memoryCache.get(imageUrl) != null) {
            return;
        }
        
        executorService.execute(() -> {
            try {
                Bitmap bitmap = downloadBitmap(imageUrl);
                if (bitmap != null) {
                    memoryCache.put(imageUrl, bitmap);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error preloading image: " + imageUrl, e);
            }
        });
    }
    
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}