package com.example.gogdownloader.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.gogdownloader.R;

public class OAuthWebViewActivity extends BaseActivity {
    
    private static final String TAG = "OAuthWebViewActivity";
    
    // Constantes OAuth do GOG
    private static final String CLIENT_ID = "46899977096215655";
    private static final String REDIRECT_URI = "https://embed.gog.com/on_login_success?origin=client";
    
    // URLs do GOG OAuth
    private static final String GOG_AUTH_URL = "https://auth.gog.com/auth" +
            "?client_id=" + CLIENT_ID +
            "&redirect_uri=" + REDIRECT_URI +
            "&response_type=code" +
            "&layout=client2";
    
    // Keys para resultado
    public static final String EXTRA_AUTH_CODE = "auth_code";
    public static final String EXTRA_ERROR = "error";
    
    // UI Elements
    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout loadingOverlay;
    private LinearLayout errorOverlay;
    private TextView errorTitle;
    private TextView errorMessage;
    private Button btnRetry;
    private ImageView btnBack;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oauth_webview);
        
        initViews();
        setupWebView();
        setupClickListeners();
        
        loadAuthPage();
    }
    
    private void initViews() {
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        errorOverlay = findViewById(R.id.errorOverlay);
        errorTitle = findViewById(R.id.errorTitle);
        errorMessage = findViewById(R.id.errorMessage);
        btnRetry = findViewById(R.id.btnRetry);
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        // Configurações do WebView
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        
        // User Agent personalizado para identificar o app
        String userAgent = webView.getSettings().getUserAgentString();
        webView.getSettings().setUserAgentString(userAgent + " GOGDownloaderApp/1.0");
        
        // Limpar cookies para garantir login limpo
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        
        // WebViewClient para interceptar navegação
        webView.setWebViewClient(new WebViewClient() {
            
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Log.d(TAG, "Page started loading: " + url);
                
                showLoading();
                
                // Verificar se é o redirect de sucesso
                if (url.startsWith(REDIRECT_URI)) {
                    Log.d(TAG, "Redirect detected: " + url);
                    handleAuthRedirect(url);
                }
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page finished loading: " + url);
                
                hideLoading();
                hideError();
            }
            
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                
                // Só mostrar erro para a página principal, não para recursos
                if (request.getUrl().toString().equals(view.getUrl())) {
                    Log.e(TAG, "WebView error: " + error.getDescription());
                    showError("Erro de Conexão", 
                            "Não foi possível carregar a página de login. Verifique sua conexão.");
                }
            }
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                Log.d(TAG, "URL loading: " + url);
                
                // Permitir URLs do GOG
                if (url.startsWith("https://auth.gog.com") || 
                    url.startsWith("https://login.gog.com") ||
                    url.startsWith("https://embed.gog.com")) {
                    return false; // Permitir carregamento no WebView
                }
                
                // Interceptar redirect de sucesso
                if (url.startsWith(REDIRECT_URI)) {
                    handleAuthRedirect(url);
                    return true; // Interceptar
                }
                
                return false;
            }
        });
        
        // WebChromeClient para progress bar
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                }
            }
        });
    }
    
    private void setupClickListeners() {
        btnRetry.setOnClickListener(v -> {
            hideError();
            loadAuthPage();
        });
    }
    
    private void loadAuthPage() {
        Log.d(TAG, "Loading GOG auth page: " + GOG_AUTH_URL);
        showLoading();
        webView.loadUrl(GOG_AUTH_URL);
    }
    
    private void handleAuthRedirect(String url) {
        Log.d(TAG, "Handling auth redirect: " + url);
        
        try {
            Uri uri = Uri.parse(url);
            String code = uri.getQueryParameter("code");
            String error = uri.getQueryParameter("error");
            
            if (code != null && !code.isEmpty()) {
                Log.d(TAG, "Authorization code received: " + code);
                
                // Retornar código de autorização para a activity anterior
                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_AUTH_CODE, code);
                setResult(Activity.RESULT_OK, resultIntent);
                finish();
                
            } else if (error != null) {
                Log.e(TAG, "OAuth error: " + error);
                
                String errorDescription = uri.getQueryParameter("error_description");
                String fullError = error;
                if (errorDescription != null) {
                    fullError += ": " + errorDescription;
                }
                
                // Retornar erro
                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_ERROR, fullError);
                setResult(Activity.RESULT_CANCELED, resultIntent);
                finish();
                
            } else {
                Log.e(TAG, "No code or error in redirect URL");
                showError("Erro de Autenticação", 
                        "Resposta inválida do servidor de autenticação.");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing redirect URL", e);
            showError("Erro de Autenticação", 
                    "Erro ao processar resposta de autenticação.");
        }
    }
    
    private void showLoading() {
        loadingOverlay.setVisibility(View.VISIBLE);
        errorOverlay.setVisibility(View.GONE);
    }
    
    private void hideLoading() {
        loadingOverlay.setVisibility(View.GONE);
    }
    
    private void showError(String title, String message) {
        hideLoading();
        
        errorTitle.setText(title);
        errorMessage.setText(message);
        errorOverlay.setVisibility(View.VISIBLE);
    }
    
    private void hideError() {
        errorOverlay.setVisibility(View.GONE);
    }
    
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            setResult(Activity.RESULT_CANCELED);
            super.onBackPressed();
        }
    }
    
    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}