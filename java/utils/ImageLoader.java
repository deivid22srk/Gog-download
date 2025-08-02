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
    
    public static void loadImage(Context context, String imageUrl, ImageView imageView) {
        getInstance().load(context, imageUrl, imageView);
    }
    
    public void load(Context context, String imageUrl, ImageView imageView) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            return;
        }
        
        // Verificar cache primeiro
        Bitmap cachedBitmap = memoryCache.get(imageUrl);
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap);
            return;
        }
        
        // Definir placeholder enquanto carrega
        imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        
        // Carregar imagem em background
        executorService.execute(() -> {
            try {
                Bitmap bitmap = downloadBitmap(imageUrl);
                if (bitmap != null) {
                    // Adicionar ao cache
                    memoryCache.put(imageUrl, bitmap);
                    
                    // Atualizar UI na thread principal
                    mainHandler.post(() -> {
                        imageView.setImageBitmap(bitmap);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading image: " + imageUrl, e);
                // Manter placeholder em caso de erro
            }
        });
    }
    
    private Bitmap downloadBitmap(String imageUrl) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        
        try {
            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("GET");
            connection.setDoInput(true);
            
            // Adicionar User-Agent para evitar bloqueios
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Android; Mobile; rv:40.0) Gecko/40.0 Firefox/40.0");
            
            connection.connect();
            
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                inputStream = connection.getInputStream();
                
                // Decodificar bitmap com sampling para economizar memória
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = calculateInSampleSize(options, 200, 200);
                options.inJustDecodeBounds = false;
                
                return BitmapFactory.decodeStream(inputStream, null, options);
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Error downloading image: " + imageUrl, e);
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