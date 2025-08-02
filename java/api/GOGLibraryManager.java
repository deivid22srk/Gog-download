package com.example.gogdownloader.api;

import android.content.Context;
import android.util.Log;

import com.example.gogdownloader.models.DownloadLink;
import com.example.gogdownloader.models.Game;
import com.example.gogdownloader.utils.PreferencesManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GOGLibraryManager {
    
    private static final String TAG = "GOGLibraryManager";
    
    // URLs da API do GOG - endpoints corretos conforme documentação
    private static final String USER_GAMES_URL = "https://embed.gog.com/user/data/games";
    private static final String LIBRARY_FILTERED_URL = "https://embed.gog.com/account/getFilteredProducts";
    private static final String GAME_DETAILS_URL = "https://api.gog.com/products/%d?expand=downloads";
    private static final String DOWNLOAD_LINK_URL = "https://api.gog.com/products/%d/downlink/download/%s";
    private static final String DOWNLINK_INFO_URL = "https://api.gog.com/products/%d/downlink/%s";
    
    private Context context;
    private PreferencesManager preferencesManager;
    private OkHttpClient httpClient;
    
    public GOGLibraryManager(Context context) {
        this.context = context;
        this.preferencesManager = new PreferencesManager(context);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    public interface LibraryCallback {
        void onSuccess(List<Game> games);
        void onError(String error);
    }
    
    public interface GameDetailsCallback {
        void onSuccess(Game game, List<DownloadLink> downloadLinks);
        void onError(String error);
    }
    
    public interface DownloadLinkCallback {
        void onSuccess(String downloadUrl);
        void onError(String error);
    }
    
    /**
     * Carrega a biblioteca do usuário a partir da API real do GOG
     * Primeiro obtém lista de IDs dos jogos, depois obtém detalhes filtrados
     * @param callback Callback para o resultado
     */
    public void loadUserLibrary(LibraryCallback callback) {
        String authToken = preferencesManager.getAuthToken();
        if (authToken == null || authToken.isEmpty()) {
            callback.onError("Token de autenticação não encontrado");
            return;
        }
        
        Log.d(TAG, "Loading user library from GOG API - Step 1: Getting user games");
        
        // Primeiro, obter lista de jogos do usuário
        Request request = new Request.Builder()
                .url(USER_GAMES_URL)
                .get()
                .addHeader("Authorization", "Bearer " + authToken)
                .addHeader("User-Agent", "GOGDownloaderApp/1.0")
                .addHeader("Accept", "application/json")
                .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "User games loading network error", e);
                callback.onError("Erro de conexão: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response autoCloseResponse = response) {
                    String responseBody = autoCloseResponse.body() != null ? autoCloseResponse.body().string() : "";
                    
                    Log.d(TAG, "User games response code: " + response.code());
                    Log.d(TAG, "User games response: " + responseBody);
                    
                    if (response.isSuccessful()) {
                        try {
                            // Verificar se temos jogos
                            JSONObject json = new JSONObject(responseBody);
                            JSONArray ownedGames = json.optJSONArray("owned");
                            
                            if (ownedGames != null && ownedGames.length() > 0) {
                                Log.d(TAG, "Found " + ownedGames.length() + " owned games, getting detailed info");
                                loadDetailedLibrary(authToken, callback);
                            } else {
                                Log.d(TAG, "No owned games found");
                                callback.onSuccess(new ArrayList<>());
                            }
                            
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing user games response", e);
                            callback.onError("Erro ao processar lista de jogos");
                        }
                    } else {
                        Log.e(TAG, "User games loading failed with code: " + response.code());
                        
                        if (response.code() == 401) {
                            callback.onError("Token expirado. Faça login novamente.");
                        } else {
                            callback.onError("Erro ao carregar jogos (" + response.code() + ")");
                        }
                    }
                }
            }
        });
    }
    
    /**
     * Carrega detalhes da biblioteca usando o endpoint filtrado
     * @param authToken Token de autenticação
     * @param callback Callback para resultado
     */
    private void loadDetailedLibrary(String authToken, LibraryCallback callback) {
        Log.d(TAG, "Loading detailed library from GOG API - Step 2: Getting filtered products");
        
        Request request = new Request.Builder()
                .url(LIBRARY_FILTERED_URL)
                .get()
                .addHeader("Authorization", "Bearer " + authToken)
                .addHeader("User-Agent", "GOGDownloaderApp/1.0")
                .addHeader("Accept", "application/json")
                .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Detailed library loading network error", e);
                callback.onError("Erro de conexão: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response autoCloseResponse = response) {
                    String responseBody = autoCloseResponse.body() != null ? autoCloseResponse.body().string() : "";
                    
                    Log.d(TAG, "Detailed library response code: " + response.code());
                    Log.d(TAG, "Detailed library response: " + responseBody);
                    
                    if (response.isSuccessful()) {
                        try {
                            List<Game> games = parseLibraryResponse(responseBody);
                            Log.d(TAG, "Library loaded successfully: " + games.size() + " games");
                            callback.onSuccess(games);
                            
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing detailed library response", e);
                            callback.onError("Erro ao processar biblioteca de jogos");
                        }
                    } else {
                        Log.e(TAG, "Detailed library loading failed with code: " + response.code());
                        
                        if (response.code() == 401) {
                            callback.onError("Token expirado. Faça login novamente.");
                        } else {
                            callback.onError("Erro ao carregar biblioteca (" + response.code() + ")");
                        }
                    }
                }
            }
        });
    }
    

    
    private List<Game> parseLibraryResponse(String responseBody) throws JSONException {
        List<Game> games = new ArrayList<>();
        
        JSONObject json = new JSONObject(responseBody);
        
        // Tentar diferentes formatos de resposta
        JSONArray products = json.optJSONArray("products");
        if (products == null) {
            products = json.optJSONArray("owned");
        }
        
        if (products != null) {
            Log.d(TAG, "Found " + products.length() + " products in library response");
            
            for (int i = 0; i < products.length(); i++) {
                try {
                    JSONObject productJson;
                    
                    // Se for apenas um ID (número), criar objeto simples
                    if (products.get(i) instanceof Number) {
                        long gameId = products.getLong(i);
                        productJson = new JSONObject();
                        productJson.put("id", gameId);
                        productJson.put("title", "Jogo ID: " + gameId);
                        productJson.put("slug", "game-" + gameId);
                    } else {
                        productJson = products.getJSONObject(i);
                    }
                    
                    Game game = Game.fromJson(productJson);
                    games.add(game);
                    
                    Log.d(TAG, "Parsed game: " + game.getTitle() + " (ID: " + game.getId() + ")");
                    
                } catch (JSONException e) {
                    Log.w(TAG, "Error parsing game at index " + i, e);
                    // Continuar com os outros jogos
                }
            }
        } else {
            Log.w(TAG, "No products or owned array found in response");
            
            // Se não encontrar products, tentar buscar diretamente
            JSONArray owned = json.optJSONArray("owned");
            if (owned != null) {
                Log.d(TAG, "Found owned array with " + owned.length() + " items");
                
                for (int i = 0; i < owned.length(); i++) {
                    try {
                        long gameId = owned.getLong(i);
                        
                        // Criar jogo simples com ID
                        Game game = new Game(gameId, "Jogo ID: " + gameId);
                        games.add(game);
                        
                        Log.d(TAG, "Created simple game: " + game.getTitle() + " (ID: " + game.getId() + ")");
                        
                    } catch (JSONException e) {
                        Log.w(TAG, "Error parsing owned game at index " + i, e);
                    }
                }
            }
        }
        
        Log.d(TAG, "Total games parsed: " + games.size());
        return games;
    }
    
    /**
     * Carrega detalhes de um jogo específico incluindo links de download
     * @param gameId ID do jogo
     * @param callback Callback para o resultado
     */
    public void loadGameDetails(long gameId, GameDetailsCallback callback) {
        String authToken = preferencesManager.getAuthToken();
        if (authToken == null || authToken.isEmpty()) {
            callback.onError("Token de autenticação não encontrado");
            return;
        }
        
        Log.d(TAG, "Loading game details for game ID: " + gameId);
        
        String url = String.format(GAME_DETAILS_URL, gameId);
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer " + authToken)
                .addHeader("User-Agent", "GOGDownloaderApp/1.0")
                .addHeader("Accept", "application/json")
                .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Game details network error", e);
                callback.onError("Erro de conexão: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response autoCloseResponse = response) {
                    String responseBody = autoCloseResponse.body() != null ? autoCloseResponse.body().string() : "";
                    
                    Log.d(TAG, "Game details response code: " + response.code());
                    Log.d(TAG, "Game details response: " + responseBody);
                    
                    if (response.isSuccessful()) {
                        try {
                            JSONObject gameJson = new JSONObject(responseBody);
                            Game game = Game.fromJson(gameJson);
                            List<DownloadLink> downloadLinks = parseDownloadLinks(gameJson);
                            game.setDownloadLinks(downloadLinks);
                            
                            Log.d(TAG, "Game details loaded: " + game.getTitle() + 
                                   " with " + downloadLinks.size() + " download links");
                            callback.onSuccess(game, downloadLinks);
                            
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing game details", e);
                            callback.onError("Erro ao processar detalhes do jogo");
                        }
                    } else {
                        Log.e(TAG, "Game details failed with code: " + response.code());
                        
                        if (response.code() == 401) {
                            callback.onError("Token expirado. Faça login novamente.");
                        } else if (response.code() == 404) {
                            callback.onError("Jogo não encontrado");
                        } else {
                            callback.onError("Erro ao carregar detalhes do jogo (" + response.code() + ")");
                        }
                    }
                }
            }
        });
    }
    

    
    private List<DownloadLink> parseDownloadLinks(JSONObject gameJson) throws JSONException {
        List<DownloadLink> downloadLinks = new ArrayList<>();
        
        JSONObject downloads = gameJson.optJSONObject("downloads");
        if (downloads == null) {
            return downloadLinks;
        }
        
        // Parsear instaladores
        JSONArray installers = downloads.optJSONArray("installers");
        if (installers != null) {
            for (int i = 0; i < installers.length(); i++) {
                try {
                    JSONObject installer = installers.getJSONObject(i);
                    DownloadLink link = DownloadLink.fromJson(installer);
                    link.setType(DownloadLink.FileType.INSTALLER);
                    downloadLinks.add(link);
                } catch (JSONException e) {
                    Log.w(TAG, "Error parsing installer at index " + i, e);
                }
            }
        }
        
        // Parsear patches
        JSONArray patches = downloads.optJSONArray("patches");
        if (patches != null) {
            for (int i = 0; i < patches.length(); i++) {
                try {
                    JSONObject patch = patches.getJSONObject(i);
                    DownloadLink link = DownloadLink.fromJson(patch);
                    link.setType(DownloadLink.FileType.PATCH);
                    downloadLinks.add(link);
                } catch (JSONException e) {
                    Log.w(TAG, "Error parsing patch at index " + i, e);
                }
            }
        }
        
        // Parsear extras
        JSONArray extras = downloads.optJSONArray("bonus_content");
        if (extras != null) {
            for (int i = 0; i < extras.length(); i++) {
                try {
                    JSONObject extra = extras.getJSONObject(i);
                    DownloadLink link = DownloadLink.fromJson(extra);
                    link.setType(DownloadLink.FileType.EXTRA);
                    downloadLinks.add(link);
                } catch (JSONException e) {
                    Log.w(TAG, "Error parsing extra at index " + i, e);
                }
            }
        }
        
        return downloadLinks;
    }
    
    /**
     * Obtém o link direto de download para um arquivo específico
     * @param gameId ID do jogo
     * @param downlinkId ID do link de download
     * @param type Tipo do arquivo (opcional)
     * @param callback Callback para o resultado
     */
    public void getDownloadLink(long gameId, String downlinkId, String type, DownloadLinkCallback callback) {
        String authToken = preferencesManager.getAuthToken();
        if (authToken == null || authToken.isEmpty()) {
            callback.onError("Token de autenticação não encontrado");
            return;
        }
        
        Log.d(TAG, "Getting download link for game " + gameId + ", link " + downlinkId);
        
        String url = String.format(DOWNLOAD_LINK_URL, gameId, downlinkId);
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer " + authToken)
                .addHeader("User-Agent", "GOGDownloaderApp/1.0")
                .addHeader("Accept", "application/json")
                .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Download link network error", e);
                callback.onError("Erro de conexão: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response autoCloseResponse = response) {
                    String responseBody = autoCloseResponse.body() != null ? autoCloseResponse.body().string() : "";
                    
                    Log.d(TAG, "Download link response code: " + response.code());
                    Log.d(TAG, "Download link response: " + responseBody);
                    
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            String downloadUrl = jsonResponse.optString("downlink", "");
                            
                            if (!downloadUrl.isEmpty()) {
                                Log.d(TAG, "Download link obtained successfully");
                                callback.onSuccess(downloadUrl);
                            } else {
                                Log.e(TAG, "No download URL in response");
                                callback.onError("Link de download não encontrado na resposta");
                            }
                            
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing download link response", e);
                            callback.onError("Erro ao processar link de download");
                        }
                    } else {
                        Log.e(TAG, "Download link failed with code: " + response.code());
                        
                        if (response.code() == 401) {
                            callback.onError("Token expirado. Faça login novamente.");
                        } else if (response.code() == 404) {
                            callback.onError("Link de download não encontrado");
                        } else {
                            callback.onError("Erro ao obter link de download (" + response.code() + ")");
                        }
                    }
                }
            }
        });
    }
}