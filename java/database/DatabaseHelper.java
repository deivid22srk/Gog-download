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

public class DatabaseHelper extends SQLiteOpenHelper {
    
    private static final String TAG = "DatabaseHelper";
    
    // Database info
    private static final String DATABASE_NAME = "gog_downloader.db";
    private static final int DATABASE_VERSION = 1;
    
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
    private static final String COLUMN_DOWNLOAD_URL = "download_url";
    private static final String COLUMN_DOWNLOAD_FILE_PATH = "file_path";
    private static final String COLUMN_DOWNLOAD_STATUS = "status";
    private static final String COLUMN_DOWNLOAD_PROGRESS = "progress";
    private static final String COLUMN_DOWNLOAD_TOTAL_BYTES = "total_bytes";
    private static final String COLUMN_DOWNLOAD_DOWNLOADED_BYTES = "downloaded_bytes";
    private static final String COLUMN_DOWNLOAD_START_TIME = "start_time";
    private static final String COLUMN_DOWNLOAD_END_TIME = "end_time";
    
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
            COLUMN_DOWNLOAD_URL + " TEXT NOT NULL, " +
            COLUMN_DOWNLOAD_FILE_PATH + " TEXT, " +
            COLUMN_DOWNLOAD_STATUS + " TEXT DEFAULT 'PENDING', " +
            COLUMN_DOWNLOAD_PROGRESS + " INTEGER DEFAULT 0, " +
            COLUMN_DOWNLOAD_TOTAL_BYTES + " INTEGER DEFAULT 0, " +
            COLUMN_DOWNLOAD_DOWNLOADED_BYTES + " INTEGER DEFAULT 0, " +
            COLUMN_DOWNLOAD_START_TIME + " INTEGER DEFAULT 0, " +
            COLUMN_DOWNLOAD_END_TIME + " INTEGER DEFAULT 0, " +
            "FOREIGN KEY(" + COLUMN_DOWNLOAD_GAME_ID + ") REFERENCES " + 
                TABLE_GAMES + "(" + COLUMN_GAME_ID + ")" +
        ")";
    
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_GAMES_TABLE);
        db.execSQL(CREATE_DOWNLOADS_TABLE);
        
        // Criar índices para melhor performance
        db.execSQL("CREATE INDEX idx_games_status ON " + TABLE_GAMES + "(" + COLUMN_GAME_STATUS + ")");
        db.execSQL("CREATE INDEX idx_downloads_game_id ON " + TABLE_DOWNLOADS + "(" + COLUMN_DOWNLOAD_GAME_ID + ")");
        db.execSQL("CREATE INDEX idx_downloads_status ON " + TABLE_DOWNLOADS + "(" + COLUMN_DOWNLOAD_STATUS + ")");
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Para versões futuras, implementar migração adequada
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DOWNLOADS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_GAMES);
        onCreate(db);
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