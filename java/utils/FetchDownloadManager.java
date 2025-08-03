package com.example.gogdownloader.utils;

import android.content.Context;
import androidx.annotation.NonNull;

import com.tonyocfrancis.fetch2.AbstractFetchListener;
import com.tonyocfrancis.fetch2.Download;
import com.tonyocfrancis.fetch2.Error;
import com.tonyocfrancis.fetch2.Fetch;
import com.tonyocfrancis.fetch2.FetchConfiguration;
import com.tonyocfrancis.fetch2.FetchListener;
import com.tonyocfrancis.fetch2.NetworkType;
import com.tonyocfrancis.fetch2.Priority;
import com.tonyocfrancis.fetch2.Request;
import com.tonyocfrancis.fetch2core.Downloader;
import com.tonyocfrancis.fetch2okhttp.OkHttpDownloader;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;

public class FetchDownloadManager {

    private static FetchDownloadManager instance;
    private final Fetch fetch;
    private final List<FetchListener> listeners = new ArrayList<>();

    private FetchDownloadManager(Context context) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
        FetchConfiguration fetchConfiguration = new FetchConfiguration.Builder(context)
                .setDownloadConcurrentLimit(3)
                .setHttpDownloader(new OkHttpDownloader(okHttpClient, Downloader.FileDownloaderType.PARALLEL))
                .build();
        fetch = Fetch.Impl.getInstance(fetchConfiguration);

        fetch.addListener(new AbstractFetchListener() {
            @Override
            public void onProgress(@NonNull Download download, long etaInMilliSeconds, long downloadedBytesPerSecond) {
                for (FetchListener listener : listeners) {
                    listener.onProgress(download, etaInMilliSeconds, downloadedBytesPerSecond);
                }
            }

            @Override
            public void onCompleted(@NonNull Download download) {
                for (FetchListener listener : listeners) {
                    listener.onCompleted(download);
                }
            }

            @Override
            public void onError(@NonNull Download download, @NonNull Error error, Throwable throwable) {
                for (FetchListener listener : listeners) {
                    listener.onError(download, error, throwable);
                }
            }

            // Add other listener methods as needed (onPaused, onResumed, etc.)
        });
    }

    public static synchronized FetchDownloadManager getInstance(Context context) {
        if (instance == null) {
            instance = new FetchDownloadManager(context.getApplicationContext());
        }
        return instance;
    }

    public Request download(String url, String filePath) {
        final Request request = new Request(url, filePath);
        request.setPriority(Priority.HIGH);
        request.setNetworkType(NetworkType.ALL);

        fetch.enqueue(request, updatedRequest -> {
            // Request was successfully enqueued for download.
        }, error -> {
            // An error occurred enqueuing the request.
        });
        return request;
    }

    public void addListener(FetchListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(FetchListener listener) {
        listeners.remove(listener);
    }

    public void close() {
        fetch.close();
    }
}
