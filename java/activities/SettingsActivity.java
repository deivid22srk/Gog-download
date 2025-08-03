package com.example.gogdownloader.activities;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.gogdownloader.R;
import com.example.gogdownloader.database.DatabaseHelper;
import com.example.gogdownloader.utils.ImageLoader;
import com.example.gogdownloader.utils.PreferencesManager;
import com.example.gogdownloader.utils.SAFDownloadManager;

import java.io.File;

public class SettingsActivity extends BaseActivity {
    
    private TextView userEmailText;
    private TextView appVersionText;
    private TextView safPathText;
    private Button changeSafFolderButton;
    private Button logoutButton;
    private Button clearCacheButton;
    
    private PreferencesManager preferencesManager;
    private DatabaseHelper databaseHelper;
    
    private ActivityResultLauncher<Intent> folderPickerLauncher;
    private String selectedPath;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        android.util.Log.d("SettingsActivity", "=== SETTINGS ACTIVITY CREATED ===");
        
        initializeViews();
        initializeManagers();
        setupToolbar();
        setupActivityLaunchers();
        setupClickListeners();
        loadCurrentSettings();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        android.util.Log.d("SettingsActivity", "=== SETTINGS ACTIVITY RESUMED - RELOADING DATA ===");
        // Recarregar dados quando a activity voltar ao foco
        loadCurrentSettings();
    }
    
    private void initializeViews() {
        userEmailText = findViewById(R.id.userEmailText);
        appVersionText = findViewById(R.id.appVersionText);
        safPathText = findViewById(R.id.safPathText);
        changeSafFolderButton = findViewById(R.id.changeSafFolderButton);
        logoutButton = findViewById(R.id.logoutButton);
        clearCacheButton = findViewById(R.id.clearCacheButton);
    }
    
    private void initializeManagers() {
        preferencesManager = new PreferencesManager(this);
        databaseHelper = new DatabaseHelper(this);
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }
    
    private void setupActivityLaunchers() {
        // Launcher para seleção de pasta
        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            handleSelectedFolder(uri);
                        }
                    }
                });
    }
    
    private void setupClickListeners() {
        changeSafFolderButton.setOnClickListener(v -> openFolderPicker());
        logoutButton.setOnClickListener(v -> showLogoutConfirmation());
        clearCacheButton.setOnClickListener(v -> showClearCacheConfirmation());
    }
    
    private void loadCurrentSettings() {
        android.util.Log.d("SettingsActivity", "=== LOADING CURRENT SETTINGS ===");
        
        // Carregar pasta de download atual com SAF
        SAFDownloadManager safManager = new SAFDownloadManager(this);
        String displayPath = safManager.getDisplayPath();
        
        android.util.Log.d("SettingsActivity", "Download path: '" + displayPath + "'");
        safPathText.setText(displayPath);
        
        // Para o seletor de pasta, manter referência para qualquer path configurado
        String uriPath = preferencesManager.getDownloadUri();
        selectedPath = uriPath;
        
        // Carregar email do usuário com debug
        String userEmail = preferencesManager.getUserEmail();
        String userName = preferencesManager.getUserName();
        String userId = preferencesManager.getUserId();
        
        android.util.Log.d("SettingsActivity", "=== USER DATA FROM PREFERENCES ===");
        android.util.Log.d("SettingsActivity", "User email: '" + userEmail + "'");
        android.util.Log.d("SettingsActivity", "User name: '" + userName + "'");
        android.util.Log.d("SettingsActivity", "User ID: '" + userId + "'");
        
        if (userEmail != null && !userEmail.isEmpty()) {
            android.util.Log.d("SettingsActivity", "Setting email to TextView: '" + userEmail + "'");
            userEmailText.setText(userEmail);
        } else {
            android.util.Log.w("SettingsActivity", "No email found, showing 'Não logado'");
            userEmailText.setText("Não logado");
        }
        
        // Carregar versão do app
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            android.util.Log.d("SettingsActivity", "App version: '" + versionName + "'");
            appVersionText.setText(versionName);
        } catch (Exception e) {
            android.util.Log.e("SettingsActivity", "Failed to get app version", e);
            appVersionText.setText("Desconhecida");
        }
        
        android.util.Log.d("SettingsActivity", "=== SETTINGS LOADING COMPLETE ===");
    }
    
    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            Uri initialUri = Uri.fromFile(downloadsDir);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        }

        try {
            folderPickerLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Error opening folder picker", Toast.LENGTH_LONG).show();
        }
    }

    private void handleSelectedFolder(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            selectedPath = uri.toString();
            preferencesManager.setDownloadUri(selectedPath);
            updatePathDisplay();

        } catch (Exception e) {
            Toast.makeText(this, "Error setting folder: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updatePathDisplay() {
        SAFDownloadManager safManager = new SAFDownloadManager(this);
        safPathText.setText(safManager.getDisplayPath());
    }
    
    private void showLogoutConfirmation() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Logout")
                .setMessage("Tem certeza que deseja sair? Todos os dados de login serão removidos.")
                .setPositiveButton("Sim", (dialog, which) -> performLogout())
                .setNegativeButton("Cancelar", null)
                .show();
    }
    
    private void performLogout() {
        // Limpar dados de autenticação
        preferencesManager.clearAuthData();
        
        // Voltar para tela de login
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private void showClearCacheConfirmation() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Limpar Cache")
                .setMessage("Isso irá remover todos os dados salvos de jogos e imagens. Os arquivos baixados não serão afetados.")
                .setPositiveButton("Limpar", (dialog, which) -> clearCache())
                .setNegativeButton("Cancelar", null)
                .show();
    }
    
    private void clearCache() {
        try {
            // Limpar cache de imagens
            ImageLoader.getInstance().clearCache();
            
            // Limpar banco de dados
            databaseHelper.clearAllGames();
            
            Toast.makeText(this, "Cache limpo com sucesso", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao limpar cache: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (databaseHelper != null) {
            databaseHelper.close();
        }
    }
}