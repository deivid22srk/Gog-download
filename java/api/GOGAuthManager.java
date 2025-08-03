package com.example.gogdownloader.api;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.CookieJar;
import okhttp3.Cookie;
import okhttp3.HttpUrl;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;

public class GOGAuthManager {
    
    private static final String TAG = "GOGAuthManager";
    
    // URLs da API do GOG - endpoints reais
    private static final String TOKEN_URL = "https://auth.gog.com/token";
    private static final String USER_DATA_URL = "https://embed.gog.com/userData.json";
    private static final String USER_INFO_URL = "https://api.gog.com/user";
    private static final String REFRESH_TOKEN_URL = "https://auth.gog.com/token";
    private static final String USER_ACCOUNT_URL = "https://menu.gog.com/v1/account/basic";
    
    // Client ID e configurações OAuth do GOG
    private static final String CLIENT_ID = "46899977096215655";
    private static final String CLIENT_SECRET = "9d85c43b1482497dbbce61f6e4aa173a433796eeae2ca8c5f6129f2dc4de46d9";
    private static final String REDIRECT_URI = "https://embed.gog.com/on_login_success?origin=client";
    
    private Context context;
    private OkHttpClient httpClient;
    private CookieManager cookieManager;
    
    public GOGAuthManager(Context context) {
        this.context = context;
        
        // Configurar gerenciamento de cookies
        cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        
        // CookieJar simples para compatibilidade
        CookieJar cookieJar = new CookieJar() {
            @Override
            public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                // Implementação simples para salvar cookies
                for (Cookie cookie : cookies) {
                    java.net.HttpCookie httpCookie = new java.net.HttpCookie(cookie.name(), cookie.value());
                    httpCookie.setDomain(cookie.domain());
                    httpCookie.setPath(cookie.path());
                    try {
                        cookieManager.getCookieStore().add(url.uri(), httpCookie);
                    } catch (Exception e) {
                        Log.w(TAG, "Error saving cookie: " + e.getMessage());
                    }
                }
            }
            
            @Override
            public List<Cookie> loadForRequest(HttpUrl url) {
                List<Cookie> cookies = new ArrayList<>();
                try {
                    List<java.net.HttpCookie> httpCookies = cookieManager.getCookieStore().get(url.uri());
                    for (java.net.HttpCookie httpCookie : httpCookies) {
                        Cookie.Builder builder = new Cookie.Builder()
                                .name(httpCookie.getName())
                                .value(httpCookie.getValue())
                                .domain(httpCookie.getDomain());
                        
                        if (httpCookie.getPath() != null) {
                            builder.path(httpCookie.getPath());
                        }
                        
                        cookies.add(builder.build());
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error loading cookies: " + e.getMessage());
                }
                return cookies;
            }
        };
        
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .cookieJar(cookieJar)
                .build();
    }
    
    public interface AuthCallback {
        void onSuccess(String authToken, String refreshToken);
        void onError(String error);
    }
    
    public interface UserInfoCallback {
        void onSuccess(JSONObject userInfo);
        void onError(String error);
    }
    
    public interface TokenExchangeCallback {
        void onSuccess(String accessToken, String refreshToken, long expiresIn);
        void onError(String error);
    }
    
    /**
     * Troca o código de autorização OAuth por tokens de acesso
     * @param authorizationCode Código recebido do redirect OAuth
     * @param callback Callback para resultado
     */
    public void exchangeCodeForToken(String authorizationCode, TokenExchangeCallback callback) {
        Log.d(TAG, "Exchanging authorization code for token");
        
        if (authorizationCode == null || authorizationCode.trim().isEmpty()) {
            callback.onError("Código de autorização é obrigatório");
            return;
        }
        
        // Preparar requisição POST para trocar código por token
        RequestBody formBody = new FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("grant_type", "authorization_code")
                .add("code", authorizationCode)
                .add("redirect_uri", REDIRECT_URI)
                .build();
        
        Request request = new Request.Builder()
                .url(TOKEN_URL)
                .post(formBody)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("User-Agent", "GOGDownloaderApp/1.0")
                .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Token exchange network error", e);
                callback.onError("Erro de conexão: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    String responseString = responseBody != null ? responseBody.string() : "";
                    
                    Log.d(TAG, "Token exchange response code: " + response.code());
                    Log.d(TAG, "Token exchange response: " + responseString);
                    
                    if (response.isSuccessful() && responseBody != null) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseString);
                            
                            String accessToken = jsonResponse.getString("access_token");
                            String refreshToken = jsonResponse.optString("refresh_token", "");
                            long expiresIn = jsonResponse.optLong("expires_in", 3600);
                            String scope = jsonResponse.optString("scope", "");
                            
                            Log.d(TAG, "Token exchange successful");
                            Log.d(TAG, "Token scope received: '" + scope + "'");
                            if (scope.isEmpty()) {
                                Log.w(TAG, "WARNING: Token has empty scope - may not have sufficient permissions");
                            }
                            
                            callback.onSuccess(accessToken, refreshToken, expiresIn);
                            
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing token response", e);
                            callback.onError("Erro ao processar resposta do servidor");
                        }
                    } else {
                        Log.e(TAG, "Token exchange failed with code: " + response.code() + " body: " + responseString);
                        
                        try {
                            JSONObject errorJson = new JSONObject(responseString);
                            String error = errorJson.optString("error", "Erro desconhecido");
                            String errorDescription = errorJson.optString("error_description", "");
                            
                            String fullError = error;
                            if (!errorDescription.isEmpty()) {
                                fullError += ": " + errorDescription;
                            }
                            
                            callback.onError(fullError);
                        } catch (JSONException e) {
                            callback.onError("Erro de autenticação (" + response.code() + ")");
                        }
                    }
                }
            }
        });
    }
    
    /**
     * Valida um token de acesso fazendo uma chamada para obter informações do usuário
     * @param token Token de acesso
     * @param callback Callback para resultado
     */
    public void validateToken(String token, AuthCallback callback) {
        Log.d(TAG, "Validating token");
        
        if (token == null || token.trim().isEmpty()) {
            callback.onError("Token é obrigatório");
            return;
        }
        
        // Tentar obter informações do usuário para validar o token
        getUserInfo(token, new UserInfoCallback() {
            @Override
            public void onSuccess(JSONObject userInfo) {
                Log.d(TAG, "Token validation successful");
                callback.onSuccess(token, "");
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Token validation failed: " + error);
                callback.onError("Token inválido ou expirado");
            }
        });
    }
    
    /**
     * Obtém informações básicas do usuário para validação usando o token de acesso
     * @param authToken Token de acesso
     * @param callback Callback para resultado
     */
    public void getUserInfo(String authToken, UserInfoCallback callback) {
        Log.d(TAG, "Getting user info for validation");
        
        if (authToken == null || authToken.trim().isEmpty()) {
            callback.onError("Token de acesso é obrigatório");
            return;
        }
        
        // Usar API endpoint que funciona com Bearer token
        Request request = new Request.Builder()
                .url(USER_INFO_URL)
                .get()
                .addHeader("Authorization", "Bearer " + authToken)
                .addHeader("User-Agent", "GOGDownloaderApp/1.0")
                .addHeader("Accept", "application/json")
                .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "User info network error", e);
                callback.onError("Erro de conexão: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    String responseString = responseBody != null ? responseBody.string() : "";
                    
                    Log.d(TAG, "User info response code: " + response.code());
                    Log.d(TAG, "User info response: " + responseString);
                    
                    if (response.isSuccessful() && responseBody != null) {
                        try {
                            JSONObject responseJson = new JSONObject(responseString);
                            
                            Log.d(TAG, "Token validation successful - API responded correctly");
                            
                            // Extrair informações básicas do usuário
                            JSONObject userInfo = new JSONObject();
                            userInfo.put("isLoggedIn", true);
                            userInfo.put("tokenValid", true);
                            
                            // Extrair dados se disponíveis
                            if (responseJson.has("username")) {
                                userInfo.put("username", responseJson.optString("username"));
                            }
                            if (responseJson.has("email")) {
                                userInfo.put("email", responseJson.optString("email"));
                            }
                            if (responseJson.has("id")) {
                                userInfo.put("userId", responseJson.optString("id"));
                            }
                            
                            callback.onSuccess(userInfo);
                            
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing token validation response", e);
                            callback.onError("Erro ao processar resposta de validação");
                        }
                    } else {
                        Log.e(TAG, "User info failed with code: " + response.code() + " body: " + responseString);
                        
                        if (response.code() == 401) {
                            callback.onError("Token expirado ou inválido");
                        } else {
                            callback.onError("Erro ao obter informações do usuário (" + response.code() + ")");
                        }
                    }
                }
            }
        });
    }
    
    /**
     * Obtém informações completas do usuário (nome, email, avatar)
     * Tenta múltiplos endpoints para obter dados completos
     * @param authToken Token de acesso
     * @param callback Callback para resultado
     */
    public void getUserData(String authToken, UserInfoCallback callback) {
        Log.d(TAG, "=== GETTING USER DATA ===");
        
        if (authToken == null || authToken.trim().isEmpty()) {
            Log.e(TAG, "AUTH TOKEN IS EMPTY!");
            callback.onError("Token de acesso é obrigatório");
            return;
        }
        
        // Primeiro, tentar o endpoint de conta básica
        Log.d(TAG, "Trying account basic endpoint: " + USER_ACCOUNT_URL);
        
        Request request = new Request.Builder()
                .url(USER_ACCOUNT_URL)
                .get()
                .addHeader("Authorization", "Bearer " + authToken)
                .addHeader("User-Agent", "GOGDownloaderApp/1.0")
                .addHeader("Accept", "application/json")
                .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Account basic endpoint failed, trying userData.json", e);
                // Fallback para userData.json
                tryUserDataEndpoint(authToken, callback);
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    String responseString = responseBody != null ? responseBody.string() : "";
                    
                    Log.d(TAG, "Account basic response code: " + response.code());
                    Log.d(TAG, "Account basic response: " + responseString);
                    
                    if (response.isSuccessful() && responseBody != null) {
                        try {
                            JSONObject userData = new JSONObject(responseString);
                            Log.d(TAG, "=== ACCOUNT BASIC DATA PARSED SUCCESSFULLY ===");
                            
                            // Processar dados da conta básica
                            JSONObject processedData = processAccountBasicData(userData);
                            callback.onSuccess(processedData);
                            
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing account basic data, trying userData.json", e);
                            tryUserDataEndpoint(authToken, callback);
                        }
                    } else {
                        Log.e(TAG, "Account basic failed, trying userData.json");
                        tryUserDataEndpoint(authToken, callback);
                    }
                }
            }
        });
    }
    
    /**
     * Tenta obter dados do endpoint userData.json como fallback
     */
    private void tryUserDataEndpoint(String authToken, UserInfoCallback callback) {
        Log.d(TAG, "Trying userData.json endpoint: " + USER_DATA_URL);
        
        Request request = new Request.Builder()
                .url(USER_DATA_URL)
                .get()
                .addHeader("Authorization", "Bearer " + authToken)
                .addHeader("User-Agent", "GOGDownloaderApp/1.0")
                .addHeader("Accept", "application/json")
                .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "=== ALL USER DATA ENDPOINTS FAILED ===", e);
                callback.onError("Erro de conexão: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    String responseString = responseBody != null ? responseBody.string() : "";
                    
                    Log.d(TAG, "userData.json response code: " + response.code());
                    Log.d(TAG, "userData.json response: " + responseString);
                    
                    if (response.isSuccessful() && responseBody != null) {
                        try {
                            JSONObject userData = new JSONObject(responseString);
                            Log.d(TAG, "=== USER DATA JSON PARSED SUCCESSFULLY ===");
                            
                            // Se userData.json não tem dados úteis, criar dados básicos
                            JSONObject processedData = processUserDataJson(userData, authToken);
                            callback.onSuccess(processedData);
                            
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing userData.json", e);
                            // Criar dados básicos como último recurso
                            JSONObject basicData = createBasicUserData();
                            callback.onSuccess(basicData);
                        }
                    } else {
                        Log.e(TAG, "userData.json failed, using basic data");
                        JSONObject basicData = createBasicUserData();
                        callback.onSuccess(basicData);
                    }
                }
            }
        });
    }
    
    /**
     * Processa dados do endpoint account/basic
     */
    private JSONObject processAccountBasicData(JSONObject accountData) throws JSONException {
        JSONObject userData = new JSONObject();
        
        // Extrair dados da conta básica
        userData.put("email", accountData.optString("email", ""));
        userData.put("username", accountData.optString("username", ""));
        userData.put("userId", accountData.optString("userId", ""));
        userData.put("first_name", accountData.optString("firstName", ""));
        userData.put("last_name", accountData.optString("lastName", ""));

        String avatarUrl = "";
        if (accountData.has("avatars")) {
            JSONObject avatars = accountData.getJSONObject("avatars");
            // Prioritize the high-resolution image
            avatarUrl = avatars.optString("menu_user_av_big2", "");
            // Fallback to other sizes if the preferred one isn't available
            if (avatarUrl.isEmpty()) {
                avatarUrl = avatars.optString("menu_user_av_big", "");
            }
            if (avatarUrl.isEmpty()) {
                avatarUrl = avatars.optString("menu_user_av_small2", "");
            }
            if (avatarUrl.isEmpty()) {
                avatarUrl = avatars.optString("menu_user_av_small", "");
            }
        }

        if (avatarUrl.isEmpty()) {
            // Fallback to the old method if the new one fails
            avatarUrl = accountData.optString("avatar", "");
            if (avatarUrl.startsWith("//")) {
                avatarUrl = "https:" + avatarUrl;
            }
        }

        userData.put("avatar", avatarUrl);
        
        Log.d(TAG, "Processed account basic data: " + userData.toString());
        return userData;
    }
    
    /**
     * Processa dados do endpoint userData.json
     */
    private JSONObject processUserDataJson(JSONObject originalData, String authToken) throws JSONException {
        JSONObject userData = new JSONObject();
        
        // userData.json geralmente não tem dados pessoais, então criar dados básicos
        userData.put("email", "");
        userData.put("username", "");
        userData.put("userId", "");
        userData.put("first_name", "");
        userData.put("last_name", "");
        userData.put("avatar", "");
        
        Log.d(TAG, "Created basic data from userData.json");
        return userData;
    }
    
    /**
     * Cria dados básicos do usuário quando nenhum endpoint funciona
     */
    private JSONObject createBasicUserData() {
        JSONObject userData = new JSONObject();
        
        try {
            userData.put("email", "");
            userData.put("username", "");
            userData.put("userId", "");
            userData.put("first_name", "");
            userData.put("last_name", "");
            userData.put("avatar", "");
        } catch (JSONException e) {
            Log.e(TAG, "Error creating basic user data", e);
        }
        
        Log.d(TAG, "Created fallback basic user data");
        return userData;
    }
    
    /**
     * Renova o token de acesso usando o refresh token
     * @param refreshToken Refresh token
     * @param callback Callback para resultado
     */
    public void refreshToken(String refreshToken, AuthCallback callback) {
        Log.d(TAG, "Refreshing token");
        
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            callback.onError("Refresh token é obrigatório");
            return;
        }
        
        RequestBody formBody = new FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build();
        
        Request request = new Request.Builder()
                .url(REFRESH_TOKEN_URL)
                .post(formBody)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("User-Agent", "GOGDownloaderApp/1.0")
                .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Token refresh network error", e);
                callback.onError("Erro de conexão: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    String responseString = responseBody != null ? responseBody.string() : "";
                    
                    Log.d(TAG, "Token refresh response code: " + response.code());
                    Log.d(TAG, "Token refresh response: " + responseString);
                    
                    if (response.isSuccessful() && responseBody != null) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseString);
                            
                            String accessToken = jsonResponse.getString("access_token");
                            String newRefreshToken = jsonResponse.optString("refresh_token", refreshToken);
                            
                            Log.d(TAG, "Token refresh successful");
                            callback.onSuccess(accessToken, newRefreshToken);
                            
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing refresh token response", e);
                            callback.onError("Erro ao processar resposta do servidor");
                        }
                    } else {
                        Log.e(TAG, "Token refresh failed with code: " + response.code() + " body: " + responseString);
                        
                        if (response.code() == 401) {
                            callback.onError("Refresh token expirado. Faça login novamente.");
                        } else {
                            callback.onError("Erro ao renovar token (" + response.code() + ")");
                        }
                    }
                }
            }
        });
    }
    
    /**
     * Obtém a URL de autorização para iniciar o flow OAuth
     * @return URL para WebView
     */
    public static String getAuthorizationUrl() {
        try {
            String redirectUri = URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8.toString());
            
            return "https://auth.gog.com/auth" +
                    "?client_id=" + CLIENT_ID +
                    "&redirect_uri=" + redirectUri +
                    "&response_type=code" +
                    "&layout=client2";
                    
        } catch (Exception e) {
            Log.e(TAG, "Error creating authorization URL", e);
            return "https://auth.gog.com/auth?client_id=" + CLIENT_ID + "&response_type=code";
        }
    }
}