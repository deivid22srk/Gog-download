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
    
    // URLs da API do GOG - usando api.gog.com com Bearer tokens OAuth
    private static final String USER_GAMES_URL = "https://api.gog.com/user/data/games";
    private static final String LIBRARY_FILTERED_URL = "https://api.gog.com/account/getFilteredProducts?page=%d&sortBy=title&sortOrder=asc";
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
        // Para api.gog.com, usar Authorization Bearer header
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
                                loadDetailedLibrary(authToken, 1, new ArrayList<>(), callback);
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
    private void loadDetailedLibrary(String authToken, int page, List<Game> accumulatedGames, LibraryCallback callback) {
        Log.d(TAG, "Loading detailed library from GOG API - Page " + page);

        String url = String.format(LIBRARY_FILTERED_URL, page);
        // Para api.gog.com, usar Authorization Bearer header
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
                            JSONObject json = new JSONObject(responseBody);
                            List<Game> games = parseLibraryResponse(responseBody);
                            accumulatedGames.addAll(games);

                            int totalPages = json.optInt("totalPages", 1);
                            if (page < totalPages) {
                                loadDetailedLibrary(authToken, page + 1, accumulatedGames, callback);
                            } else {
                                Log.d(TAG, "Library loaded successfully: " + accumulatedGames.size() + " games");
                                callback.onSuccess(accumulatedGames);
                            }

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
        
        Log.d(TAG, "Parsing library response: " + responseBody.substring(0, Math.min(500, responseBody.length())));
        
        JSONObject json = new JSONObject(responseBody);
        
        // Para getFilteredProducts, a resposta tem formato: {"products": [...], "page": 1, "totalResults": X}
        JSONArray products = json.optJSONArray("products");
        
        if (products != null) {
            Log.d(TAG, "Found products array with " + products.length() + " items");
            
            for (int i = 0; i < products.length(); i++) {
                try {
                    JSONObject productJson = products.getJSONObject(i);
                    
                    // Log dos dados do produto para debug
                    Log.d(TAG, "Processing product " + i + ": " + productJson.toString());
                    
                    Game game = Game.fromJson(productJson);
                    games.add(game);
                    
                    Log.d(TAG, "Successfully parsed game: " + game.getTitle() + " (ID: " + game.getId() + ")");
                    
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing game at index " + i + ": " + e.getMessage(), e);
                    // Continuar com os outros jogos
                }
            }
        } else {
            // Para /user/data/games, a resposta tem formato: {"owned": [id1, id2, id3, ...]}
            JSONArray owned = json.optJSONArray("owned");
            if (owned != null) {
                Log.d(TAG, "Found owned array with " + owned.length() + " items (IDs only)");
                
                for (int i = 0; i < owned.length(); i++) {
                    try {
                        long gameId = owned.getLong(i);
                        
                        // Criar jogo simples com ID - o título será carregado depois
                        Game game = new Game(gameId, "Carregando...");
                        games.add(game);
                        
                        Log.d(TAG, "Created game placeholder for ID: " + gameId);
                        
                    } catch (JSONException e) {
                        Log.w(TAG, "Error parsing owned game ID at index " + i, e);
                    }
                }
            } else {
                Log.w(TAG, "No products or owned array found in response");
                Log.d(TAG, "Full response: " + responseBody);
                
                // Verificar se a resposta é um erro
                if (json.has("error")) {
                    String error = json.optString("error", "Erro desconhecido");
                    Log.e(TAG, "API returned error: " + error);
                    throw new JSONException("API Error: " + error);
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
        
        // Baseado na documentação oficial do GOG, downloads é um array de arrays
        JSONArray downloads = gameJson.optJSONArray("downloads");
        if (downloads == null) {
            Log.w(TAG, "No downloads array found in game details");
            return downloadLinks;
        }
        
        Log.d(TAG, "Found downloads array with " + downloads.length() + " items");
        
        // Percorrer cada item no array downloads
        for (int i = 0; i < downloads.length(); i++) {
            try {
                JSONArray languageArray = downloads.getJSONArray(i);
                if (languageArray.length() >= 2) {
                    String language = languageArray.getString(0);
                    JSONObject platforms = languageArray.getJSONObject(1);
                    
                    Log.d(TAG, "Processing downloads for language: " + language);
                    
                    // Parsear downloads do Windows
                    JSONArray windowsDownloads = platforms.optJSONArray("windows");
                    if (windowsDownloads != null) {
                        for (int j = 0; j < windowsDownloads.length(); j++) {
                            try {
                                JSONObject downloadItem = windowsDownloads.getJSONObject(j);
                                DownloadLink link = DownloadLink.fromJson(downloadItem);
                                link.setType(DownloadLink.FileType.INSTALLER);
                                downloadLinks.add(link);
                                Log.d(TAG, "Added installer: " + downloadItem.optString("name"));
                            } catch (JSONException e) {
                                Log.w(TAG, "Error parsing Windows download at index " + j, e);
                            }
                        }
                    }
                    
                    // Parsear downloads do Mac se existir
                    JSONArray macDownloads = platforms.optJSONArray("mac");
                    if (macDownloads != null) {
                        for (int j = 0; j < macDownloads.length(); j++) {
                            try {
                                JSONObject downloadItem = macDownloads.getJSONObject(j);
                                DownloadLink link = DownloadLink.fromJson(downloadItem);
                                link.setType(DownloadLink.FileType.INSTALLER);
                                downloadLinks.add(link);
                                Log.d(TAG, "Added Mac installer: " + downloadItem.optString("name"));
                            } catch (JSONException e) {
                                Log.w(TAG, "Error parsing Mac download at index " + j, e);
                            }
                        }
                    }
                    
                    // Parsear downloads do Linux se existir
                    JSONArray linuxDownloads = platforms.optJSONArray("linux");
                    if (linuxDownloads != null) {
                        for (int j = 0; j < linuxDownloads.length(); j++) {
                            try {
                                JSONObject downloadItem = linuxDownloads.getJSONObject(j);
                                DownloadLink link = DownloadLink.fromJson(downloadItem);
                                link.setType(DownloadLink.FileType.INSTALLER);
                                downloadLinks.add(link);
                                Log.d(TAG, "Added Linux installer: " + downloadItem.optString("name"));
                            } catch (JSONException e) {
                                Log.w(TAG, "Error parsing Linux download at index " + j, e);
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                Log.w(TAG, "Error parsing download language group at index " + i, e);
            }
        }
        
        // Parsear extras (bonus content)
        JSONArray extras = gameJson.optJSONArray("extras");
        if (extras != null) {
            Log.d(TAG, "Found extras array with " + extras.length() + " items");
            for (int i = 0; i < extras.length(); i++) {
                try {
                    JSONObject extra = extras.getJSONObject(i);
                    DownloadLink link = DownloadLink.fromJson(extra);
                    link.setType(DownloadLink.FileType.EXTRA);
                    downloadLinks.add(link);
                    Log.d(TAG, "Added extra: " + extra.optString("name"));
                } catch (JSONException e) {
                    Log.w(TAG, "Error parsing extra at index " + i, e);
                }
            }
        }
        
        Log.d(TAG, "Total download links parsed: " + downloadLinks.size());
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