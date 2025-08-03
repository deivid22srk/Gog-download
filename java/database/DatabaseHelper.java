package com.example.gogdownloader.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.example.gogdownloader.models.Game;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import android.content.ContentValues;
import android.database.Cursor;

public class DatabaseHelper extends SQLiteOpenHelper {
    
    private static final String TAG = "DatabaseHelper";
    
    // Database info
    private static final String DATABASE_NAME = "gog_downloader.db";
    private static final int DATABASE_VERSION = 2; // Versão atualizada para suporte a múltiplos downloads
    
    // Table names
    private static final String TABLE_GAMES = "games";
    private static final String TABLE_DOWNLOADS = "downloads";
    
    // Games table columns
    private static final String COLUMN_GAME_ID = "id";
    private static final String COLUMN_GAME_TITLE = "title";
    private static final String COLUMN_GAME_SLUG = "slug";
    private static final String COLUMN_GAME_COVER_IMAGE = "cover_image";
    private static final String COLUMN_GAME_BACKGROUND_IMAGE = "background_image";
    private static final String COLUMN_GAME_DESCRIPTION = "description";
    private static final String COLUMN_GAME_STATUS = "status";
    private static final String COLUMN_GAME_DOWNLOAD_PROGRESS = "download_progress";
    private static final String COLUMN_GAME_TOTAL_SIZE = "total_size";
    private static final String COLUMN_GAME_LOCAL_PATH = "local_path";
    private static final String COLUMN_GAME_RELEASE_DATE = "release_date";
    private static final String COLUMN_GAME_DEVELOPER = "developer";
    private static final String COLUMN_GAME_PUBLISHER = "publisher";
    private static final String COLUMN_GAME_GENRES = "genres";
    private static final String COLUMN_GAME_JSON_DATA = "json_data";
    private static final String COLUMN_GAME_LAST_UPDATED = "last_updated";
    
    // Downloads table columns
    private static final String COLUMN_DOWNLOAD_ID = "id";
    private static final String COLUMN_DOWNLOAD_GAME_ID = "game_id";
    private static final String COLUMN_DOWNLOAD_LINK_ID = "download_link_id";
    private static final String COLUMN_DOWNLOAD_FILE_NAME = "file_name";
    private static final String COLUMN_DOWNLOAD_URL = "download_url";
    private static final String COLUMN_DOWNLOAD_FILE_PATH = "file_path";
    private static final String COLUMN_DOWNLOAD_STATUS = "status";
    private static final String COLUMN_DOWNLOAD_PROGRESS = "progress";
    private static final String COLUMN_DOWNLOAD_TOTAL_BYTES = "total_bytes";
    private static final String COLUMN_DOWNLOAD_DOWNLOADED_BYTES = "downloaded_bytes";
    private static final String COLUMN_DOWNLOAD_START_TIME = "start_time";
    private static final String COLUMN_DOWNLOAD_END_TIME = "end_time";
    private static final String COLUMN_DOWNLOAD_SPEED = "download_speed";
    private static final String COLUMN_DOWNLOAD_ETA = "eta";
    private static final String COLUMN_DOWNLOAD_RETRY_COUNT = "retry_count";
    private static final String COLUMN_DOWNLOAD_ERROR_MESSAGE = "error_message";
    
    // Batch downloads table columns
    private static final String TABLE_DOWNLOAD_BATCHES = "download_batches";
    private static final String COLUMN_BATCH_ID = "id";
    private static final String COLUMN_BATCH_GAME_ID = "game_id";
    private static final String COLUMN_BATCH_TOTAL_FILES = "total_files";
    private static final String COLUMN_BATCH_COMPLETED_FILES = "completed_files";
    private static final String COLUMN_BATCH_STATUS = "status";
    private static final String COLUMN_BATCH_START_TIME = "start_time";
    private static final String COLUMN_BATCH_END_TIME = "end_time";
    
    // Create table statements
    private static final String CREATE_GAMES_TABLE = 
        "CREATE TABLE " + TABLE_GAMES + " (" +
            COLUMN_GAME_ID + " INTEGER PRIMARY KEY, " +
            COLUMN_GAME_TITLE + " TEXT NOT NULL, " +
            COLUMN_GAME_SLUG + " TEXT, " +
            COLUMN_GAME_COVER_IMAGE + " TEXT, " +
            COLUMN_GAME_BACKGROUND_IMAGE + " TEXT, " +
            COLUMN_GAME_DESCRIPTION + " TEXT, " +
            COLUMN_GAME_STATUS + " TEXT DEFAULT 'NOT_DOWNLOADED', " +
            COLUMN_GAME_DOWNLOAD_PROGRESS + " INTEGER DEFAULT 0, " +
            COLUMN_GAME_TOTAL_SIZE + " INTEGER DEFAULT 0, " +
            COLUMN_GAME_LOCAL_PATH + " TEXT, " +
            COLUMN_GAME_RELEASE_DATE + " TEXT, " +
            COLUMN_GAME_DEVELOPER + " TEXT, " +
            COLUMN_GAME_PUBLISHER + " TEXT, " +
            COLUMN_GAME_GENRES + " TEXT, " +
            COLUMN_GAME_JSON_DATA + " TEXT, " +
            COLUMN_GAME_LAST_UPDATED + " INTEGER DEFAULT 0" +
        ")";
    
    private static final String CREATE_DOWNLOADS_TABLE = 
        "CREATE TABLE " + TABLE_DOWNLOADS + " (" +
            COLUMN_DOWNLOAD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_DOWNLOAD_GAME_ID + " INTEGER NOT NULL, " +
            COLUMN_DOWNLOAD_LINK_ID + " TEXT NOT NULL, " +
            COLUMN_DOWNLOAD_FILE_NAME + " TEXT NOT NULL, " +
            COLUMN_DOWNLOAD_URL + " TEXT NOT NULL, " +
            COLUMN_DOWNLOAD_FILE_PATH + " TEXT, " +
            COLUMN_DOWNLOAD_STATUS + " TEXT DEFAULT 'PENDING', " +
            COLUMN_DOWNLOAD_PROGRESS + " INTEGER DEFAULT 0, " +
            COLUMN_DOWNLOAD_TOTAL_BYTES + " INTEGER DEFAULT 0, " +
            COLUMN_DOWNLOAD_DOWNLOADED_BYTES + " INTEGER DEFAULT 0, " +
            COLUMN_DOWNLOAD_START_TIME + " INTEGER DEFAULT 0, " +
            COLUMN_DOWNLOAD_END_TIME + " INTEGER DEFAULT 0, " +
            COLUMN_DOWNLOAD_SPEED + " REAL DEFAULT 0, " +
            COLUMN_DOWNLOAD_ETA + " INTEGER DEFAULT 0, " +
            COLUMN_DOWNLOAD_RETRY_COUNT + " INTEGER DEFAULT 0, " +
            COLUMN_DOWNLOAD_ERROR_MESSAGE + " TEXT, " +
            "FOREIGN KEY(" + COLUMN_DOWNLOAD_GAME_ID + ") REFERENCES " + 
                TABLE_GAMES + "(" + COLUMN_GAME_ID + ")" +
            ")";
        
    private static final String CREATE_DOWNLOAD_BATCHES_TABLE = 
        "CREATE TABLE " + TABLE_DOWNLOAD_BATCHES + " (" +
            COLUMN_BATCH_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_BATCH_GAME_ID + " INTEGER NOT NULL, " +
            COLUMN_BATCH_TOTAL_FILES + " INTEGER DEFAULT 0, " +
            COLUMN_BATCH_COMPLETED_FILES + " INTEGER DEFAULT 0, " +
            COLUMN_BATCH_STATUS + " TEXT DEFAULT 'PENDING', " +
            COLUMN_BATCH_START_TIME + " INTEGER DEFAULT 0, " +
            COLUMN_BATCH_END_TIME + " INTEGER DEFAULT 0, " +
            "FOREIGN KEY(" + COLUMN_BATCH_GAME_ID + ") REFERENCES " + 
                TABLE_GAMES + "(" + COLUMN_GAME_ID + ")" +
        ")";
    
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_GAMES_TABLE);
        db.execSQL(CREATE_DOWNLOADS_TABLE);
        db.execSQL(CREATE_DOWNLOAD_BATCHES_TABLE);
        
        // Criar índices para melhor performance
        db.execSQL("CREATE INDEX idx_games_status ON " + TABLE_GAMES + "(" + COLUMN_GAME_STATUS + ")");
        db.execSQL("CREATE INDEX idx_downloads_game_id ON " + TABLE_DOWNLOADS + "(" + COLUMN_DOWNLOAD_GAME_ID + ")");
        db.execSQL("CREATE INDEX idx_downloads_status ON " + TABLE_DOWNLOADS + "(" + COLUMN_DOWNLOAD_STATUS + ")");
        db.execSQL("CREATE INDEX idx_downloads_link_id ON " + TABLE_DOWNLOADS + "(" + COLUMN_DOWNLOAD_LINK_ID + ")");
        db.execSQL("CREATE INDEX idx_batches_game_id ON " + TABLE_DOWNLOAD_BATCHES + "(" + COLUMN_BATCH_GAME_ID + ")");
        db.execSQL("CREATE INDEX idx_batches_status ON " + TABLE_DOWNLOAD_BATCHES + "(" + COLUMN_BATCH_STATUS + ")");
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
        
        if (oldVersion < 2) {
            // Migração da versão 1 para 2: adicionar suporte a múltiplos downloads
            
            // Fazer backup dos downloads existentes
            List<ContentValues> existingDownloads = new ArrayList<>();
            Cursor cursor = db.query(TABLE_DOWNLOADS, null, null, null, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    ContentValues values = new ContentValues();
                    values.put(COLUMN_DOWNLOAD_GAME_ID, cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_GAME_ID)));
                    values.put(COLUMN_DOWNLOAD_URL, cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_URL)));
                    values.put(COLUMN_DOWNLOAD_STATUS, cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_STATUS)));
                    existingDownloads.add(values);
                }
                cursor.close();
            }
            
            // Recriar tabela de downloads com nova estrutura
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_DOWNLOADS);
            db.execSQL(CREATE_DOWNLOADS_TABLE);
            
            // Criar nova tabela de batches
            db.execSQL(CREATE_DOWNLOAD_BATCHES_TABLE);
            
            // Recriar índices
            db.execSQL("CREATE INDEX idx_downloads_game_id ON " + TABLE_DOWNLOADS + "(" + COLUMN_DOWNLOAD_GAME_ID + ")");
            db.execSQL("CREATE INDEX idx_downloads_status ON " + TABLE_DOWNLOADS + "(" + COLUMN_DOWNLOAD_STATUS + ")");
            db.execSQL("CREATE INDEX idx_downloads_link_id ON " + TABLE_DOWNLOADS + "(" + COLUMN_DOWNLOAD_LINK_ID + ")");
            db.execSQL("CREATE INDEX idx_batches_game_id ON " + TABLE_DOWNLOAD_BATCHES + "(" + COLUMN_BATCH_GAME_ID + ")");
            db.execSQL("CREATE INDEX idx_batches_status ON " + TABLE_DOWNLOAD_BATCHES + "(" + COLUMN_BATCH_STATUS + ")");
            
            Log.d(TAG, "Database upgraded successfully to version 2");
        }
    }
    
    // Métodos para gerenciar jogos
    
    public long insertGame(Game game) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = gameToContentValues(game);
        
        long id = db.insertWithOnConflict(TABLE_GAMES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        
        if (id == -1) {
            Log.e(TAG, "Error inserting game: " + game.getTitle());
        } else {
            Log.d(TAG, "Game inserted successfully: " + game.getTitle());
        }
        
        return id;
    }
    
    public void insertOrUpdateGames(List<Game> games) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        db.beginTransaction();
        try {
            for (Game game : games) {
                ContentValues values = gameToContentValues(game);
                values.put(COLUMN_GAME_LAST_UPDATED, System.currentTimeMillis());
                
                db.insertWithOnConflict(TABLE_GAMES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            
            db.setTransactionSuccessful();
            Log.d(TAG, "Inserted/updated " + games.size() + " games");
            
        } catch (Exception e) {
            Log.e(TAG, "Error inserting/updating games", e);
        } finally {
            db.endTransaction();
        }
    }
    
    public boolean updateGame(Game game) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = gameToContentValues(game);
        values.put(COLUMN_GAME_LAST_UPDATED, System.currentTimeMillis());
        
        int rowsAffected = db.update(TABLE_GAMES, values, 
                COLUMN_GAME_ID + " = ?", new String[]{String.valueOf(game.getId())});
        
        if (rowsAffected > 0) {
            Log.d(TAG, "Game updated successfully: " + game.getTitle());
            return true;
        } else {
            Log.w(TAG, "No game found to update with ID: " + game.getId());
            return false;
        }
    }
    
    public Game getGame(long gameId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Game game = null;
        
        Cursor cursor = db.query(TABLE_GAMES, null, 
                COLUMN_GAME_ID + " = ?", new String[]{String.valueOf(gameId)},
                null, null, null);
        
        if (cursor != null && cursor.moveToFirst()) {
            game = cursorToGame(cursor);
            cursor.close();
        }
        
        return game;
    }
    
    public List<Game> getAllGames() {
        List<Game> games = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        Cursor cursor = db.query(TABLE_GAMES, null, null, null, null, null, 
                COLUMN_GAME_TITLE + " ASC");
        
        if (cursor != null) {
            while (cursor.moveToNext()) {
                Game game = cursorToGame(cursor);
                if (game != null) {
                    games.add(game);
                }
            }
            cursor.close();
        }
        
        Log.d(TAG, "Retrieved " + games.size() + " games from database");
        return games;
    }
    
    public List<Game> getGamesByStatus(Game.DownloadStatus status) {
        List<Game> games = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        Cursor cursor = db.query(TABLE_GAMES, null, 
                COLUMN_GAME_STATUS + " = ?", new String[]{status.name()},
                null, null, COLUMN_GAME_TITLE + " ASC");
        
        if (cursor != null) {
            while (cursor.moveToNext()) {
                Game game = cursorToGame(cursor);
                if (game != null) {
                    games.add(game);
                }
            }
            cursor.close();
        }
        
        return games;
    }
    
    // Métodos para gerenciar downloads individuais
    
    public long insertDownload(long gameId, String downloadLinkId, String fileName, String downloadUrl) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(COLUMN_DOWNLOAD_GAME_ID, gameId);
        values.put(COLUMN_DOWNLOAD_LINK_ID, downloadLinkId);
        values.put(COLUMN_DOWNLOAD_FILE_NAME, fileName);
        values.put(COLUMN_DOWNLOAD_URL, downloadUrl);
        values.put(COLUMN_DOWNLOAD_STATUS, "PENDING");
        values.put(COLUMN_DOWNLOAD_START_TIME, System.currentTimeMillis());
        
        long id = db.insert(TABLE_DOWNLOADS, null, values);
        
        if (id == -1) {
            Log.e(TAG, "Error inserting download for game ID: " + gameId);
        } else {
            Log.d(TAG, "Download inserted successfully: " + fileName);
        }
        
        return id;
    }
    
    public boolean updateDownloadProgress(long downloadId, long downloadedBytes, long totalBytes, double speed, long eta) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(COLUMN_DOWNLOAD_DOWNLOADED_BYTES, downloadedBytes);
        values.put(COLUMN_DOWNLOAD_TOTAL_BYTES, totalBytes);
        values.put(COLUMN_DOWNLOAD_SPEED, speed);
        values.put(COLUMN_DOWNLOAD_ETA, eta);
        
        int progress = totalBytes > 0 ? (int) ((downloadedBytes * 100) / totalBytes) : 0;
        values.put(COLUMN_DOWNLOAD_PROGRESS, progress);
        
        int rowsAffected = db.update(TABLE_DOWNLOADS, values, 
                COLUMN_DOWNLOAD_ID + " = ?", new String[]{String.valueOf(downloadId)});
        
        return rowsAffected > 0;
    }
    
    public boolean updateDownloadStatus(long downloadId, String status, String errorMessage) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(COLUMN_DOWNLOAD_STATUS, status);
        if (errorMessage != null) {
            values.put(COLUMN_DOWNLOAD_ERROR_MESSAGE, errorMessage);
        }
        
        if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
            values.put(COLUMN_DOWNLOAD_END_TIME, System.currentTimeMillis());
        }
        
        int rowsAffected = db.update(TABLE_DOWNLOADS, values, 
                COLUMN_DOWNLOAD_ID + " = ?", new String[]{String.valueOf(downloadId)});
        
        return rowsAffected > 0;
    }
    
    public List<ContentValues> getActiveDownloads() {
        List<ContentValues> downloads = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        Cursor cursor = db.query(TABLE_DOWNLOADS, null, 
                COLUMN_DOWNLOAD_STATUS + " IN ('PENDING', 'DOWNLOADING')", null,
                null, null, COLUMN_DOWNLOAD_START_TIME + " ASC");
        
        if (cursor != null) {
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                values.put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_ID)));
                values.put("game_id", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_GAME_ID)));
                values.put("link_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_LINK_ID)));
                values.put("file_name", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_FILE_NAME)));
                values.put("download_url", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_URL)));
                values.put("status", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_STATUS)));
                values.put("downloaded_bytes", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_DOWNLOADED_BYTES)));
                values.put("total_bytes", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_TOTAL_BYTES)));
                downloads.add(values);
            }
            cursor.close();
        }
        
        return downloads;
    }

    public ContentValues getDownload(long downloadId) {
        SQLiteDatabase db = this.getReadableDatabase();
        ContentValues values = null;

        Cursor cursor = db.query(TABLE_DOWNLOADS, null,
                COLUMN_DOWNLOAD_ID + " = ?", new String[]{String.valueOf(downloadId)},
                null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            values = new ContentValues();
            values.put(COLUMN_DOWNLOAD_ID, cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_ID)));
            values.put(COLUMN_DOWNLOAD_GAME_ID, cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_GAME_ID)));
            values.put(COLUMN_DOWNLOAD_LINK_ID, cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_LINK_ID)));
            values.put(COLUMN_DOWNLOAD_FILE_NAME, cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_FILE_NAME)));
            values.put(COLUMN_DOWNLOAD_URL, cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_URL)));
            values.put(COLUMN_DOWNLOAD_FILE_PATH, cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_FILE_PATH)));
            values.put(COLUMN_DOWNLOAD_STATUS, cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_STATUS)));
            values.put(COLUMN_DOWNLOAD_PROGRESS, cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_PROGRESS)));
            values.put(COLUMN_DOWNLOAD_TOTAL_BYTES, cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_TOTAL_BYTES)));
            values.put(COLUMN_DOWNLOAD_DOWNLOADED_BYTES, cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_DOWNLOADED_BYTES)));
            cursor.close();
        }

        return values;
    }
    
    public List<ContentValues> getDownloadsForGame(long gameId) {
        List<ContentValues> downloads = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        Cursor cursor = db.query(TABLE_DOWNLOADS, null, 
                COLUMN_DOWNLOAD_GAME_ID + " = ?", new String[]{String.valueOf(gameId)},
                null, null, COLUMN_DOWNLOAD_START_TIME + " ASC");
        
        if (cursor != null) {
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                values.put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_ID)));
                values.put("link_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_LINK_ID)));
                values.put("file_name", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_FILE_NAME)));
                values.put("status", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_STATUS)));
                values.put("progress", cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_PROGRESS)));
                values.put("downloaded_bytes", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_DOWNLOADED_BYTES)));
                values.put("total_bytes", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_TOTAL_BYTES)));
                values.put("speed", cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_SPEED)));
                values.put("eta", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_ETA)));
                downloads.add(values);
            }
            cursor.close();
        }
        
        return downloads;
    }
    
    // Métodos para gerenciar batches de download
    
    public long createDownloadBatch(long gameId, int totalFiles) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(COLUMN_BATCH_GAME_ID, gameId);
        values.put(COLUMN_BATCH_TOTAL_FILES, totalFiles);
        values.put(COLUMN_BATCH_COMPLETED_FILES, 0);
        values.put(COLUMN_BATCH_STATUS, "PENDING");
        values.put(COLUMN_BATCH_START_TIME, System.currentTimeMillis());
        
        long id = db.insert(TABLE_DOWNLOAD_BATCHES, null, values);
        
        if (id == -1) {
            Log.e(TAG, "Error creating download batch for game ID: " + gameId);
        } else {
            Log.d(TAG, "Download batch created successfully: " + id);
        }
        
        return id;
    }
    
    public boolean updateBatchProgress(long batchId, int completedFiles, String status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(COLUMN_BATCH_COMPLETED_FILES, completedFiles);
        values.put(COLUMN_BATCH_STATUS, status);
        
        if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
            values.put(COLUMN_BATCH_END_TIME, System.currentTimeMillis());
        }
        
        int rowsAffected = db.update(TABLE_DOWNLOAD_BATCHES, values, 
                COLUMN_BATCH_ID + " = ?", new String[]{String.valueOf(batchId)});
        
        return rowsAffected > 0;
    }
    
    public ContentValues getDownloadBatch(long gameId) {
        SQLiteDatabase db = this.getReadableDatabase();
        ContentValues batch = null;
        
        Cursor cursor = db.query(TABLE_DOWNLOAD_BATCHES, null, 
                COLUMN_BATCH_GAME_ID + " = ? AND " + COLUMN_BATCH_STATUS + " NOT IN ('COMPLETED', 'CANCELLED')", 
                new String[]{String.valueOf(gameId)}, null, null, 
                COLUMN_BATCH_START_TIME + " DESC", "1");
        
        if (cursor != null && cursor.moveToFirst()) {
            batch = new ContentValues();
            batch.put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_BATCH_ID)));
            batch.put("total_files", cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_BATCH_TOTAL_FILES)));
            batch.put("completed_files", cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_BATCH_COMPLETED_FILES)));
            batch.put("status", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_BATCH_STATUS)));
            cursor.close();
        }
        
        return batch;
    }
    
    public boolean deleteGame(long gameId) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        // Primeiro, deletar downloads relacionados
        db.delete(TABLE_DOWNLOADS, COLUMN_DOWNLOAD_GAME_ID + " = ?", 
                new String[]{String.valueOf(gameId)});
        
        // Depois, deletar o jogo
        int rowsAffected = db.delete(TABLE_GAMES, COLUMN_GAME_ID + " = ?", 
                new String[]{String.valueOf(gameId)});
        
        return rowsAffected > 0;
    }
    
    public void clearAllGames() {
        SQLiteDatabase db = this.getWritableDatabase();
        
        db.beginTransaction();
        try {
            db.delete(TABLE_DOWNLOAD_BATCHES, null, null);
            db.delete(TABLE_DOWNLOADS, null, null);
            db.delete(TABLE_GAMES, null, null);
            db.setTransactionSuccessful();
            Log.d(TAG, "All games and downloads cleared from database");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing database", e);
        } finally {
            db.endTransaction();
        }
    }
    
    // Métodos auxiliares
    
    private ContentValues gameToContentValues(Game game) {
        ContentValues values = new ContentValues();
        
        values.put(COLUMN_GAME_ID, game.getId());
        values.put(COLUMN_GAME_TITLE, game.getTitle());
        values.put(COLUMN_GAME_SLUG, game.getSlug());
        values.put(COLUMN_GAME_COVER_IMAGE, game.getCoverImage());
        values.put(COLUMN_GAME_BACKGROUND_IMAGE, game.getBackgroundImage());
        values.put(COLUMN_GAME_DESCRIPTION, game.getDescription());
        values.put(COLUMN_GAME_STATUS, game.getStatus().name());
        values.put(COLUMN_GAME_DOWNLOAD_PROGRESS, game.getDownloadProgress());
        values.put(COLUMN_GAME_TOTAL_SIZE, game.getTotalSize());
        values.put(COLUMN_GAME_LOCAL_PATH, game.getLocalPath());
        values.put(COLUMN_GAME_RELEASE_DATE, game.getReleaseDate());
        values.put(COLUMN_GAME_DEVELOPER, game.getDeveloper());
        values.put(COLUMN_GAME_PUBLISHER, game.getPublisher());
        values.put(COLUMN_GAME_GENRES, game.getGenresString());
        
        // Salvar dados JSON completos para recuperação futura
        try {
            values.put(COLUMN_GAME_JSON_DATA, game.toJson().toString());
        } catch (JSONException e) {
            Log.w(TAG, "Error converting game to JSON", e);
        }
        
        return values;
    }
    
    private Game cursorToGame(Cursor cursor) {
        try {
            Game game = new Game();
            
            game.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_GAME_ID)));
            game.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GAME_TITLE)));
            game.setSlug(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GAME_SLUG)));
            game.setCoverImage(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GAME_COVER_IMAGE)));
            game.setBackgroundImage(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GAME_BACKGROUND_IMAGE)));
            game.setDescription(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GAME_DESCRIPTION)));
            
            String statusStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GAME_STATUS));
            try {
                game.setStatus(Game.DownloadStatus.valueOf(statusStr));
            } catch (IllegalArgumentException e) {
                game.setStatus(Game.DownloadStatus.NOT_DOWNLOADED);
            }
            
            game.setDownloadProgress(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_GAME_DOWNLOAD_PROGRESS)));
            game.setTotalSize(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_GAME_TOTAL_SIZE)));
            game.setLocalPath(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GAME_LOCAL_PATH)));
            game.setReleaseDate(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GAME_RELEASE_DATE)));
            game.setDeveloper(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GAME_DEVELOPER)));
            game.setPublisher(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GAME_PUBLISHER)));
            
            // Parsear gêneros
            String genresStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GAME_GENRES));
            if (genresStr != null && !genresStr.isEmpty()) {
                List<String> genres = new ArrayList<>();
                for (String genre : genresStr.split(", ")) {
                    genres.add(genre.trim());
                }
                game.setGenres(genres);
            }
            
            return game;
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting cursor to game", e);
            return null;
        }
    }
}