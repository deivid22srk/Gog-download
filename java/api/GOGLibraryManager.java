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
    
    // URLs da API do GOG - usando endpoints que funcionam com Bearer token
    private static final String USER_GAMES_URL = "https://api.gog.com/user/data/games";
    private static final String LIBRARY_FILTERED_URL = "https://api.gog.com/user/data/games";
    private static final String GAME_DETAILS_URL = "https://api.gog.com/products/%d?expand=downloads";
    private static final String DOWNLOAD_LINK_URL = "https://api.gog.com/products/%d/downlink/download/%s";
    private static final String DOWNLINK_INFO_URL = "https://api.gog.com/products/%d/downlink/%s";
    
    // Fallback URLs para embed.gog.com se api.gog.com falhar
    private static final String EMBED_USER_GAMES_URL = "https://embed.gog.com/user/data/games";
    private static final String EMBED_LIBRARY_FILTERED_URL = "https://embed.gog.com/account/getFilteredProducts?mediaType=1&page=%d";
    
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
     * Tenta api.gog.com primeiro, fallback para embed.gog.com
     * @param callback Callback para o resultado
     */
    public void loadUserLibrary(LibraryCallback callback) {
        String authToken = preferencesManager.getAuthToken();
        if (authToken == null || authToken.isEmpty()) {
            callback.onError("Token de autenticação não encontrado");
            return;
        }
        
        Log.d(TAG, "Loading user library from GOG API");
        
        // Usar embed.gog.com diretamente (como o minigalaxy faz)
        tryEmbedGogLibrary(authToken, callback);
    }
    
    /**
     * Tenta carregar biblioteca do api.gog.com
     * NOTA: Mantido como fallback, mas embed.gog.com é preferido
     * pois api.gog.com pode retornar 403 para aplicações de terceiros
     */
    private void tryApiGogLibrary(String authToken, LibraryCallback callback) {
        Log.d(TAG, "Trying api.gog.com for user library");
        
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
                Log.e(TAG, "api.gog.com failed, trying embed.gog.com", e);
                tryEmbedGogLibrary(authToken, callback);
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response autoCloseResponse = response) {
                    String responseBody = autoCloseResponse.body() != null ? autoCloseResponse.body().string() : "";
                    
                    Log.d(TAG, "api.gog.com response code: " + response.code());
                    Log.d(TAG, "api.gog.com response: " + responseBody);
                    
                    if (response.isSuccessful()) {
                        try {
                            List<Game> games = parseApiGogResponse(responseBody);
                            Log.d(TAG, "Successfully loaded " + games.size() + " games from api.gog.com");
                            
                            // Retornar jogos imediatamente para mostrar a lista
                            callback.onSuccess(games);
                            
                            // Carregar tamanhos dos jogos de forma assíncrona
                            loadGameSizesAsync(games);
                            
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing api.gog.com response, trying embed.gog.com", e);
                            tryEmbedGogLibrary(authToken, callback);
                        }
                    } else {
                        Log.e(TAG, "api.gog.com failed with code: " + response.code() + ", trying embed.gog.com");
                        tryEmbedGogLibrary(authToken, callback);
                    }
                }
            }
        });
    }
    
    /**
     * Fallback para embed.gog.com
     */
    private void tryEmbedGogLibrary(String authToken, LibraryCallback callback) {
        Log.d(TAG, "Trying embed.gog.com for user library");
        
        Request request = new Request.Builder()
                .url(EMBED_USER_GAMES_URL)
                .get()
                .addHeader("Authorization", "Bearer " + authToken)
                .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "All library endpoints failed", e);
                callback.onError("Erro de conexão: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response autoCloseResponse = response) {
                    String responseBody = autoCloseResponse.body() != null ? autoCloseResponse.body().string() : "";
                    
                    Log.d(TAG, "embed.gog.com response code: " + response.code());
                    Log.d(TAG, "embed.gog.com response: " + responseBody);
                    
                    if (response.isSuccessful()) {
                        try {
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
                            Log.e(TAG, "Error parsing embed.gog.com response", e);
                            callback.onError("Erro ao processar lista de jogos");
                        }
                    } else {
                        Log.e(TAG, "embed.gog.com failed with code: " + response.code());
                        
                        if (response.code() == 401 || response.code() == 403) {
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
     * Parseia resposta do api.gog.com que tem formato diferente
     */
    private List<Game> parseApiGogResponse(String responseBody) throws JSONException {
        List<Game> games = new ArrayList<>();
        
        Log.d(TAG, "Parsing api.gog.com response");
        
        JSONObject json = new JSONObject(responseBody);
        
        // api.gog.com pode retornar formato diferente
        if (json.has("owned")) {
            JSONArray ownedGames = json.getJSONArray("owned");
            
            for (int i = 0; i < ownedGames.length(); i++) {
                try {
                    Object gameObj = ownedGames.get(i);
                    
                    if (gameObj instanceof JSONObject) {
                        // Se for objeto completo
                        JSONObject gameJson = (JSONObject) gameObj;
                        Game game = Game.fromJson(gameJson);
                        games.add(game);
                    } else {
                        // Se for apenas ID
                        long gameId = ownedGames.getLong(i);
                        Game game = new Game(gameId, "Game ID: " + gameId);
                        games.add(game);
                    }
                    
                } catch (Exception e) {
                    Log.w(TAG, "Error parsing game at index " + i, e);
                }
            }
        }
        
        Log.d(TAG, "Parsed " + games.size() + " games from api.gog.com");
        return games;
    }
    
    /**
     * Carrega detalhes da biblioteca usando o endpoint filtrado (apenas para embed.gog.com)
     * @param authToken Token de autenticação
     * @param callback Callback para resultado
     */
    private void loadDetailedLibrary(String authToken, int page, List<Game> accumulatedGames, LibraryCallback callback) {
        Log.d(TAG, "Loading detailed library from embed.gog.com - Page " + page);

        String url = String.format(EMBED_LIBRARY_FILTERED_URL, page);
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
                
                // Se houve erro mas já temos alguns jogos acumulados, retornar eles
                if (!accumulatedGames.isEmpty()) {
                    Log.w(TAG, "Returning " + accumulatedGames.size() + " games despite error");
                    callback.onSuccess(accumulatedGames);
                } else {
                    callback.onError("Erro de conexão: " + e.getMessage());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response autoCloseResponse = response) {
                    String responseBody = autoCloseResponse.body() != null ? autoCloseResponse.body().string() : "";

                    Log.d(TAG, "Detailed library response code: " + response.code());
                    Log.d(TAG, "Detailed library response preview: " + 
                           responseBody.substring(0, Math.min(300, responseBody.length())));

                    if (response.isSuccessful()) {
                        try {
                            JSONObject json = new JSONObject(responseBody);
                            List<Game> games = parseLibraryResponse(responseBody);
                            accumulatedGames.addAll(games);

                            int totalPages = json.optInt("totalPages", 1);
                            if (page < totalPages && page < 10) { // Limite de 10 páginas para evitar loops infinitos
                                loadDetailedLibrary(authToken, page + 1, accumulatedGames, callback);
                            } else {
                                Log.d(TAG, "Library loaded successfully: " + accumulatedGames.size() + " games");
                                
                                // Retornar jogos imediatamente para mostrar a lista
                                callback.onSuccess(accumulatedGames);
                                
                                // Carregar tamanhos dos jogos de forma assíncrona
                                loadGameSizesAsync(accumulatedGames);
                            }

                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing detailed library response", e);
                            
                            // Se houve erro mas já temos alguns jogos acumulados, retornar eles
                            if (!accumulatedGames.isEmpty()) {
                                Log.w(TAG, "Returning " + accumulatedGames.size() + " games despite parsing error");
                                
                                // Retornar jogos imediatamente
                                callback.onSuccess(accumulatedGames);
                                
                                // Carregar tamanhos dos jogos de forma assíncrona
                                loadGameSizesAsync(accumulatedGames);
                            } else {
                                callback.onError("Erro ao processar biblioteca de jogos");
                            }
                        }
                    } else {
                        Log.e(TAG, "Detailed library loading failed with code: " + response.code());

                        // Se houve erro mas já temos alguns jogos acumulados, retornar eles
                        if (!accumulatedGames.isEmpty()) {
                            Log.w(TAG, "Returning " + accumulatedGames.size() + " games despite HTTP error");
                            callback.onSuccess(accumulatedGames);
                        } else if (response.code() == 401 || response.code() == 403) {
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
        
        // Estrutura atual da API GOG (2025)
        JSONObject downloads = gameJson.optJSONObject("downloads");
        if (downloads == null) {
            Log.w(TAG, "No downloads object found in game details");
            return downloadLinks;
        }
        
        Log.d(TAG, "Found downloads object, parsing installers...");
        
        // Parsear installers
        JSONArray installers = downloads.optJSONArray("installers");
        if (installers != null) {
            Log.d(TAG, "Found installers array with " + installers.length() + " items");
            for (int i = 0; i < installers.length(); i++) {
                try {
                    JSONObject installer = installers.getJSONObject(i);
                    
                    // Parsear arquivos do installer
                    JSONArray files = installer.optJSONArray("files");
                    if (files != null) {
                        for (int j = 0; j < files.length(); j++) {
                            try {
                                JSONObject file = files.getJSONObject(j);
                                DownloadLink link = DownloadLink.fromJson(file);
                                
                                // Definir dados do installer
                                link.setName(installer.optString("name", "Unknown"));
                                link.setType(DownloadLink.FileType.INSTALLER);
                                
                                // Definir plataforma baseado no campo "os"
                                String os = installer.optString("os", "windows").toLowerCase();
                                switch (os) {
                                    case "mac":
                                        link.setPlatform(DownloadLink.Platform.MAC);
                                        break;
                                    case "linux":
                                        link.setPlatform(DownloadLink.Platform.LINUX);
                                        break;
                                    default:
                                        link.setPlatform(DownloadLink.Platform.WINDOWS);
                                        break;
                                }
                                
                                // Definir idioma
                                link.setLanguage(installer.optString("language", "en"));
                                
                                downloadLinks.add(link);
                                Log.d(TAG, "Added installer file: " + file.optString("id") + 
                                          " (" + link.getFormattedSize() + ") - " + link.getUrl());
                            } catch (JSONException e) {
                                Log.w(TAG, "Error parsing installer file at index " + j, e);
                            }
                        }
                    }
                } catch (JSONException e) {
                    Log.w(TAG, "Error parsing installer at index " + i, e);
                }
            }
        }
        
        // Parsear patches
        JSONArray patches = downloads.optJSONArray("patches");
        if (patches != null) {
            Log.d(TAG, "Found patches array with " + patches.length() + " items");
            for (int i = 0; i < patches.length(); i++) {
                try {
                    JSONObject patch = patches.getJSONObject(i);
                    JSONArray files = patch.optJSONArray("files");
                    if (files != null) {
                        for (int j = 0; j < files.length(); j++) {
                            try {
                                JSONObject file = files.getJSONObject(j);
                                DownloadLink link = DownloadLink.fromJson(file);
                                link.setName(patch.optString("name", "Patch"));
                                link.setType(DownloadLink.FileType.PATCH);
                                downloadLinks.add(link);
                                Log.d(TAG, "Added patch: " + file.optString("id"));
                            } catch (JSONException e) {
                                Log.w(TAG, "Error parsing patch file at index " + j, e);
                            }
                        }
                    }
                } catch (JSONException e) {
                    Log.w(TAG, "Error parsing patch at index " + i, e);
                }
            }
        }
        
        // Parsear bonus content (se existir)
        JSONArray bonusContent = downloads.optJSONArray("bonus_content");
        if (bonusContent != null) {
            Log.d(TAG, "Found bonus content array with " + bonusContent.length() + " items");
            for (int i = 0; i < bonusContent.length(); i++) {
                try {
                    JSONObject extra = bonusContent.getJSONObject(i);
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
    public void getDownloadLink(long gameId, DownloadLink downloadLink, String type, DownloadLinkCallback callback) {
        String authToken = preferencesManager.getAuthToken();
        if (authToken == null || authToken.isEmpty()) {
            callback.onError("Token de autenticação não encontrado");
            return;
        }
        
        Log.d(TAG, "Getting download link for game " + gameId + ", link " + downloadLink.getId());
        
        String url = downloadLink.getUrl();
        
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
                        Log.d(TAG, "Download link response body: " + responseBody);
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            String downloadUrl = jsonResponse.optString("downlink", "");

                            if (downloadUrl.isEmpty()) {
                                downloadUrl = jsonResponse.optString("url", "");
                            }
                            
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
    
    /**
     * Carrega tamanhos dos jogos de forma assíncrona
     * Atualiza os jogos progressivamente conforme os tamanhos são obtidos
     */
    private void loadGameSizesAsync(List<Game> games) {
        if (games == null || games.isEmpty()) {
            Log.d(TAG, "No games to load sizes for");
            return;
        }
        
        Log.d(TAG, "Starting async size loading for " + games.size() + " games");
        
        // Carregar tamanhos em background thread para não bloquear UI
        new Thread(() -> {
            for (Game game : games) {
                if (game.getTotalSize() > 0) {
                    // Já tem tamanho, pular
                    continue;
                }
                
                try {
                    // Carregar detalhes do jogo para obter tamanho
                    loadGameDetails(game.getId(), new GameDetailsCallback() {
                        @Override
                        public void onSuccess(Game detailedGame, List<DownloadLink> downloadLinks) {
                            if (detailedGame.getTotalSize() > 0) {
                                // Atualizar tamanho do jogo original
                                game.setTotalSize(detailedGame.getTotalSize());
                                Log.d(TAG, "Updated size for game '" + game.getTitle() + "': " + game.getFormattedSize());
                                
                                // TODO: Notificar UI para atualizar (implementar observer pattern se necessário)
                            }
                        }
                        
                        @Override
                        public void onError(String error) {
                            Log.w(TAG, "Failed to load size for game '" + game.getTitle() + "': " + error);
                        }
                    });
                    
                    // Delay pequeno entre requisições para não sobrecarregar API
                    Thread.sleep(500);
                    
                } catch (InterruptedException e) {
                    Log.w(TAG, "Size loading interrupted", e);
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Error loading size for game '" + game.getTitle() + "'", e);
                }
            }
            
            Log.d(TAG, "Finished async size loading");
        }).start();
    }
}