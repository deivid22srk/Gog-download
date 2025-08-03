package com.example.gogdownloader.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gogdownloader.R;
import com.example.gogdownloader.api.GOGAuthManager;
import com.example.gogdownloader.utils.PreferencesManager;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

public class LoginActivity extends BaseActivity {
    
    private static final String TAG = "LoginActivity";
    private static final int OAUTH_REQUEST_CODE = 1001;
    
    private Button loginButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private TextView descriptionText;
    
    private GOGAuthManager authManager;
    private PreferencesManager preferencesManager;
    
    // ActivityResultLauncher para o WebView OAuth
    private ActivityResultLauncher<Intent> oauthLauncher;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        initializeViews();
        initializeManagers();
        setupClickListeners();
        checkExistingLogin();
    }
    
    private void initializeViews() {
        loginButton = findViewById(R.id.loginButton);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        descriptionText = findViewById(R.id.descriptionText);
        
        // Atualizar texto do botão e descrição
        loginButton.setText("Entrar com GOG.com");
        if (descriptionText != null) {
            descriptionText.setText("Você será redirecionado para o site oficial do GOG.com para fazer login com segurança.");
        }
    }
    
    private void initializeManagers() {
        authManager = new GOGAuthManager(this);
        preferencesManager = new PreferencesManager(this);
        
        // Configurar ActivityResultLauncher para OAuth
        oauthLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    handleOAuthResult(result);
                }
            }
        );
    }
    
    private void setupClickListeners() {
        loginButton.setOnClickListener(v -> startOAuthLogin());
    }
    
    private void checkExistingLogin() {
        if (preferencesManager.isLoggedIn()) {
            if (preferencesManager.isLoginValid()) {
                // Login válido, ir direto para biblioteca
                Log.d(TAG, "Valid login found, navigating to library");
                showLoading(true, "Entrando automaticamente...");
                navigateToLibrary();
            } else {
                // Token pode ter expirado, tentar validar
                String savedToken = preferencesManager.getAuthToken();
                Log.d(TAG, "Login found but may be expired, validating token");
                validateExistingToken(savedToken);
            }
        } else {
            Log.d(TAG, "No existing login found");
        }
    }
    
    private void validateExistingToken(String token) {
        showLoading(true, getString(R.string.logging_in));
        
        authManager.validateToken(token, new GOGAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String authToken, String refreshToken) {
                runOnUiThread(() -> {
                    showLoading(false, null);
                    navigateToLibrary();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    showLoading(false, null);
                    // Token inválido, limpar dados salvos
                    preferencesManager.clearAuthData();
                });
            }
        });
    }
    
    /**
     * Inicia o processo de autenticação OAuth via WebView
     */
    private void startOAuthLogin() {
        Log.d(TAG, "Starting OAuth login process");
        
        showLoading(true, "Preparando login...");
        
        try {
            Intent oauthIntent = new Intent(this, OAuthWebViewActivity.class);
            oauthLauncher.launch(oauthIntent);
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting OAuth", e);
            showLoading(false, null);
            showError("Erro ao iniciar processo de login");
        }
    }
    
    /**
     * Processa o resultado do WebView OAuth
     * @param result Resultado da activity OAuth
     */
    private void handleOAuthResult(ActivityResult result) {
        showLoading(false, null);
        
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent data = result.getData();
            if (data != null) {
                String authCode = data.getStringExtra(OAuthWebViewActivity.EXTRA_AUTH_CODE);
                if (authCode != null && !authCode.isEmpty()) {
                    Log.d(TAG, "OAuth code received, exchanging for token");
                    exchangeCodeForToken(authCode);
                } else {
                    Log.e(TAG, "No auth code in result");
                    showError("Erro: código de autorização não recebido");
                }
            } else {
                Log.e(TAG, "No data in OAuth result");
                showError("Erro: dados de autenticação não recebidos");
            }
        } else {
            // Login cancelado ou com erro
            Intent data = result.getData();
            if (data != null) {
                String error = data.getStringExtra(OAuthWebViewActivity.EXTRA_ERROR);
                if (error != null && !error.isEmpty()) {
                    Log.e(TAG, "OAuth error: " + error);
                    showError("Erro de autenticação: " + error);
                } else {
                    Log.d(TAG, "OAuth cancelled by user");
                    showError("Login cancelado");
                }
            } else {
                Log.d(TAG, "OAuth cancelled");
                showError("Login cancelado");
            }
        }
    }
    
    /**
     * Troca o código de autorização por tokens de acesso
     * @param authCode Código de autorização recebido
     */
    private void exchangeCodeForToken(String authCode) {
        runOnUiThread(() -> showLoading(true, "Obtendo tokens de acesso..."));
        
        authManager.exchangeCodeForToken(authCode, new GOGAuthManager.TokenExchangeCallback() {
            @Override
            public void onSuccess(String accessToken, String refreshToken, long expiresIn) {
                Log.d(TAG, "Token exchange successful");
                
                // Obter informações do usuário
                getUserInfoAndSave(accessToken, refreshToken);
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Token exchange error: " + error);
                runOnUiThread(() -> {
                    showLoading(false, null);
                    showError("Erro ao obter tokens: " + error);
                });
            }
        });
    }
    
    /**
     * Obtém informações do usuário e salva dados de autenticação
     * @param accessToken Token de acesso
     * @param refreshToken Token de renovação
     */
    private void getUserInfoAndSave(String accessToken, String refreshToken) {
        runOnUiThread(() -> showLoading(true, "Obtendo informações do usuário..."));
        
        authManager.getUserInfo(accessToken, new GOGAuthManager.UserInfoCallback() {
            @Override
            public void onSuccess(JSONObject userInfo) {
                Log.d(TAG, "User info retrieved successfully");
                
                runOnUiThread(() -> {
                    try {
                        // Extrair informações do usuário baseado na documentação da API
                        String email = userInfo.optString("email", "usuario@gog.com");
                        String username = userInfo.optString("username", "");
                        String userId = userInfo.optString("userId", userInfo.optString("user_id", userInfo.optString("id", "")));
                        String avatar = userInfo.optString("avatar", userInfo.optString("avatar_url", ""));
                        
                        // Se não há username, tentar usar outras propriedades
                        if (username.trim().isEmpty()) {
                            username = userInfo.optString("name", "");
                        }
                        if (username.trim().isEmpty()) {
                            username = userInfo.optString("display_name", "");
                        }
                        
                        // Se ainda não temos username, usar parte do email
                        if (username.trim().isEmpty() && !email.isEmpty()) {
                            int atIndex = email.indexOf('@');
                            if (atIndex > 0) {
                                username = email.substring(0, atIndex);
                            }
                        }
                        
                        Log.d(TAG, "User info - email: " + email + ", username: " + username + ", userId: " + userId);
                        
                        // Salvar tokens e informações do usuário
                        preferencesManager.saveAuthData(accessToken, refreshToken, email, username, userId, avatar);
                        
                        showLoading(false, null);
                        
                        // Mostrar sucesso
                        String displayName = preferencesManager.getDisplayName();
                        Toast.makeText(LoginActivity.this, 
                            "Login realizado com sucesso! Bem-vindo, " + displayName, 
                            Toast.LENGTH_SHORT).show();
                        
                        // Navegar para biblioteca
                        navigateToLibrary();
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing user info", e);
                        showLoading(false, null);
                        showError("Erro ao processar informações do usuário");
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "User info error: " + error);
                
                runOnUiThread(() -> {
                    // Mesmo com erro nas informações do usuário, 
                    // podemos prosseguir com os tokens
                    preferencesManager.saveAuthData(accessToken, refreshToken, "usuario@gog.com", "Usuário GOG", "", "");
                    
                    showLoading(false, null);
                    
                    String displayName = preferencesManager.getDisplayName();
                    Toast.makeText(LoginActivity.this, 
                        "Login realizado com sucesso! Bem-vindo, " + displayName, 
                        Toast.LENGTH_SHORT).show();
                    
                    navigateToLibrary();
                });
            }
        });
    }
    
    private void showLoading(boolean isLoading, String message) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            statusText.setVisibility(View.VISIBLE);
            statusText.setText(message);
            loginButton.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            statusText.setVisibility(View.GONE);
            loginButton.setEnabled(true);
        }
    }
    
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
    
    private void navigateToLibrary() {
        Intent intent = new Intent(this, FolderSelectionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Limpar status de loading quando voltar para a activity
        showLoading(false, null);
    }
    
    @Override
    public void onBackPressed() {
        // Impedir que o usuário volte sem fazer login
        moveTaskToBack(true);
    }
}