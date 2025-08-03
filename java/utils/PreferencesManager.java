package com.example.gogdownloader.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;

public class PreferencesManager {
    
    private static final String PREF_NAME = "GOGDownloaderPrefs";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_AVATAR = "user_avatar";
    private static final String KEY_DOWNLOAD_PATH = "download_path";
    private static final String KEY_FIRST_RUN = "first_run";
    private static final String KEY_LOGIN_TIME = "login_time";
    
    private SharedPreferences preferences;
    private SharedPreferences.Editor editor;
    
    public PreferencesManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = preferences.edit();
    }
    
    // Métodos de autenticação
    public void saveAuthData(String authToken, String refreshToken, String email) {
        saveAuthData(authToken, refreshToken, email, null, null, null);
    }
    
    public void saveAuthData(String authToken, String refreshToken, String email, String userName, String userId, String avatarUrl) {
        editor.putString(KEY_AUTH_TOKEN, authToken);
        editor.putString(KEY_REFRESH_TOKEN, refreshToken);
        editor.putString(KEY_USER_EMAIL, email);
        editor.putString(KEY_USER_NAME, userName);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USER_AVATAR, avatarUrl);
        editor.putLong(KEY_LOGIN_TIME, System.currentTimeMillis());
        editor.apply();
    }
    
    public String getAuthToken() {
        return preferences.getString(KEY_AUTH_TOKEN, null);
    }
    
    public String getRefreshToken() {
        return preferences.getString(KEY_REFRESH_TOKEN, null);
    }
    
    public String getUserEmail() {
        return preferences.getString(KEY_USER_EMAIL, null);
    }
    
    public String getUserName() {
        return preferences.getString(KEY_USER_NAME, null);
    }
    
    public String getUserId() {
        return preferences.getString(KEY_USER_ID, null);
    }
    
    public String getUserAvatar() {
        return preferences.getString(KEY_USER_AVATAR, null);
    }
    
    public long getLoginTime() {
        return preferences.getLong(KEY_LOGIN_TIME, 0);
    }
    
    public String getDisplayName() {
        String userName = getUserName();
        if (userName != null && !userName.trim().isEmpty()) {
            return userName;
        }
        
        String email = getUserEmail();
        if (email != null && !email.trim().isEmpty()) {
            // Extrair nome do email se não há username
            int atIndex = email.indexOf('@');
            if (atIndex > 0) {
                return email.substring(0, atIndex);
            }
            return email;
        }
        
        return "Usuário GOG";
    }
    
    public void clearAuthData() {
        editor.remove(KEY_AUTH_TOKEN);
        editor.remove(KEY_REFRESH_TOKEN);
        editor.remove(KEY_USER_EMAIL);
        editor.remove(KEY_USER_NAME);
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_USER_AVATAR);
        editor.remove(KEY_LOGIN_TIME);
        editor.apply();
    }
    
    public boolean isLoggedIn() {
        String token = getAuthToken();
        return token != null && !token.isEmpty();
    }
    
    // Verificar se o login ainda é válido (não expirou)
    public boolean isLoginValid() {
        if (!isLoggedIn()) {
            return false;
        }
        
        long loginTime = getLoginTime();
        if (loginTime == 0) {
            return true; // Se não temos tempo de login, assumir válido
        }
        
        // Token GOG geralmente expira em 1 hora
        long currentTime = System.currentTimeMillis();
        long oneHour = 60 * 60 * 1000; // 1 hora em milliseconds
        
        return (currentTime - loginTime) < oneHour;
    }
    
    // Métodos de download com SAF
    private static final String KEY_DOWNLOAD_URI = "download_uri";
    
    public void setDownloadPath(String path) {
        editor.putString(KEY_DOWNLOAD_PATH, path);
        editor.apply();
    }
    
    public void setDownloadUri(String uriString) {
        editor.putString(KEY_DOWNLOAD_URI, uriString);
        editor.apply();
    }
    
    public String getDownloadUri() {
        return preferences.getString(KEY_DOWNLOAD_URI, null);
    }

    public void setInstallUri(String uriString) {
        editor.putString("install_uri", uriString);
        editor.apply();
    }

    public String getInstallUri() {
        return preferences.getString("install_uri", null);
    }

    public void clearDownloadUri() {
        editor.remove(KEY_DOWNLOAD_URI).apply();
    }
    
    public boolean hasDownloadLocationConfigured() {
        String uri = getDownloadUri();
        String path = preferences.getString(KEY_DOWNLOAD_PATH, null);
        return (uri != null && !uri.isEmpty()) || (path != null && !path.isEmpty());
    }
    
    public String getDownloadPath() {
        // Priorizar URI se disponível, senão usar path legado
        String savedPath = preferences.getString(KEY_DOWNLOAD_PATH, null);
        if (savedPath != null && !savedPath.isEmpty()) {
            return savedPath;
        }
        
        // Retorna caminho padrão se não houver um salvo
        return getDefaultDownloadPath();
    }
    
    public String getDownloadPathLegacy() {
        return preferences.getString(KEY_DOWNLOAD_PATH, null);
    }
    
    public String getDefaultDownloadPath() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File gogDir = new File(downloadsDir, "GOG");
        
        if (!gogDir.exists()) {
            gogDir.mkdirs();
        }
        
        return gogDir.getAbsolutePath();
    }
    
    // Primeira execução
    public boolean isFirstRun() {
        return preferences.getBoolean(KEY_FIRST_RUN, true);
    }
    
    public void setFirstRunCompleted() {
        editor.putBoolean(KEY_FIRST_RUN, false);
        editor.apply();
    }
    
    // Limpar todas as preferências
    public void clearAll() {
        editor.clear();
        editor.apply();
    }
    
    // Verificar se um caminho é válido
    public boolean isValidDownloadPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        
        File dir = new File(path);
        return dir.exists() && dir.isDirectory() && dir.canWrite();
    }
    
    // Criar diretório se não existir
    public boolean createDownloadDirectory(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        
        File dir = new File(path);
        if (!dir.exists()) {
            return dir.mkdirs();
        }
        
        return dir.isDirectory() && dir.canWrite();
    }
}