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

public class SettingsActivity extends AppCompatActivity {
    
    private TextView currentPathText;
    private TextView userEmailText;
    private TextView appVersionText;
    private Button chooseFolderButton;
    private Button logoutButton;
    private Button clearCacheButton;
    private Button saveButton;
    
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
        currentPathText = findViewById(R.id.currentPathText);
        userEmailText = findViewById(R.id.userEmailText);
        appVersionText = findViewById(R.id.appVersionText);
        chooseFolderButton = findViewById(R.id.chooseFolderButton);
        logoutButton = findViewById(R.id.logoutButton);
        clearCacheButton = findViewById(R.id.clearCacheButton);
        saveButton = findViewById(R.id.saveButton);
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
        chooseFolderButton.setOnClickListener(v -> openFolderPicker());
        logoutButton.setOnClickListener(v -> showLogoutConfirmation());
        clearCacheButton.setOnClickListener(v -> showClearCacheConfirmation());
        saveButton.setOnClickListener(v -> saveSettings());
    }
    
    private void loadCurrentSettings() {
        android.util.Log.d("SettingsActivity", "=== LOADING CURRENT SETTINGS ===");
        
        // Carregar pasta de download atual com SAF
        SAFDownloadManager safManager = new SAFDownloadManager(this);
        String displayPath = safManager.getDisplayPath();
        
        android.util.Log.d("SettingsActivity", "Download path: '" + displayPath + "'");
        currentPathText.setText(getString(R.string.folder_selected) + " " + displayPath);
        
        // Para o seletor de pasta, manter referência para qualquer path configurado
        String legacyPath = preferencesManager.getDownloadPathLegacy();
        String uriPath = preferencesManager.getDownloadUri();
        selectedPath = (uriPath != null && !uriPath.isEmpty()) ? uriPath : legacyPath;
        
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - usar Storage Access Framework
            openStorageAccessFramework();
        } else {
            // Android 10 e anteriores - usar seletor de diretório simples
            openLegacyFolderPicker();
        }
    }
    
    private void openStorageAccessFramework() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | 
                       Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                       Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        
        // Definir diretório inicial se possível
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            Uri initialUri = Uri.fromFile(downloadsDir);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        }
        
        try {
            folderPickerLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao abrir seletor de pasta", Toast.LENGTH_LONG).show();
        }
    }
    
    private void openLegacyFolderPicker() {
        // Para Android mais antigo, mostrar dialog com opções pré-definidas
        String[] options = {
            "Downloads/GOG (Padrão)",
            "Armazenamento interno/GOG",
            "Cartão SD/GOG (se disponível)",
            "Escolher manualmente"
        };
        
        new AlertDialog.Builder(this)
                .setTitle("Escolher pasta de download")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Downloads/GOG
                            selectedPath = preferencesManager.getDefaultDownloadPath();
                            updatePathDisplay();
                            break;
                        case 1: // Armazenamento interno
                            File internalDir = new File(getExternalFilesDir(null), "GOG");
                            selectedPath = internalDir.getAbsolutePath();
                            updatePathDisplay();
                            break;
                        case 2: // Cartão SD
                            checkExternalStorage();
                            break;
                        case 3: // Manual
                            showManualPathInput();
                            break;
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
    
    private void handleSelectedFolder(Uri uri) {
        try {
            // Obter permissão persistente
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            
            // Converter URI para caminho se possível
            String path = getPathFromUri(uri);
            if (path != null) {
                selectedPath = path;
                updatePathDisplay();
            } else {
                Toast.makeText(this, "Não foi possível acessar a pasta selecionada", Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao configurar pasta: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private String getPathFromUri(Uri uri) {
        // Tentativa simples de converter URI em caminho
        // Para implementação completa, seria necessário usar DocumentFile
        String uriString = uri.toString();
        
        if (uriString.contains("primary")) {
            String relativePath = uriString.substring(uriString.lastIndexOf("primary:") + 8);
            return Environment.getExternalStorageDirectory() + "/" + relativePath;
        }
        
        // Fallback: usar URI como está e criar diretório GOG dentro
        return Environment.getExternalStorageDirectory() + "/GOG";
    }
    
    private void checkExternalStorage() {
        // Verificar se há cartão SD disponível
        File[] externalDirs = getExternalFilesDirs(null);
        if (externalDirs.length > 1 && externalDirs[1] != null) {
            File sdDir = new File(externalDirs[1], "GOG");
            selectedPath = sdDir.getAbsolutePath();
            updatePathDisplay();
        } else {
            Toast.makeText(this, "Cartão SD não encontrado", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showManualPathInput() {
        // Dialog para inserir caminho manualmente
        android.widget.EditText editText = new android.widget.EditText(this);
        editText.setText(selectedPath);
        editText.setHint("Ex: /storage/emulated/0/Download/GOG");
        
        new AlertDialog.Builder(this)
                .setTitle("Inserir caminho da pasta")
                .setView(editText)
                .setPositiveButton("OK", (dialog, which) -> {
                    String path = editText.getText().toString().trim();
                    if (!path.isEmpty()) {
                        selectedPath = path;
                        updatePathDisplay();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
    
    private void updatePathDisplay() {
        currentPathText.setText(getString(R.string.folder_selected) + " " + selectedPath);
    }
    
    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
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
        new AlertDialog.Builder(this)
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
    
    private void saveSettings() {
        try {
            // Validar pasta selecionada
            if (selectedPath == null || selectedPath.isEmpty()) {
                Toast.makeText(this, "Selecione uma pasta de download", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Verificar se a pasta é válida
            if (!preferencesManager.isValidDownloadPath(selectedPath)) {
                // Tentar criar a pasta
                if (!preferencesManager.createDownloadDirectory(selectedPath)) {
                    Toast.makeText(this, "Não foi possível criar/acessar a pasta selecionada", Toast.LENGTH_LONG).show();
                    return;
                }
            }
            
            // Salvar configurações
            preferencesManager.setDownloadPath(selectedPath);
            
            Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show();
            
            // Retornar com resultado OK
            setResult(RESULT_OK);
            finish();
            
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao salvar configurações: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    public void onBackPressed() {
        // Verificar se há mudanças não salvas
        String currentSavedPath = preferencesManager.getDownloadPath();
        if (!currentSavedPath.equals(selectedPath)) {
            new AlertDialog.Builder(this)
                    .setTitle("Mudanças não salvas")
                    .setMessage("Você tem mudanças não salvas. Deseja sair sem salvar?")
                    .setPositiveButton("Sair", (dialog, which) -> super.onBackPressed())
                    .setNegativeButton("Cancelar", null)
                    .show();
        } else {
            super.onBackPressed();
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