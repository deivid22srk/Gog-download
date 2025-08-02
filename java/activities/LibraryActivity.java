package com.example.gogdownloader.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gogdownloader.R;
import com.example.gogdownloader.adapters.GamesAdapter;
import com.example.gogdownloader.api.GOGAuthManager;
import com.example.gogdownloader.api.GOGLibraryManager;
import com.example.gogdownloader.database.DatabaseHelper;
import com.example.gogdownloader.models.Game;
import com.example.gogdownloader.services.DownloadService;
import com.example.gogdownloader.utils.PermissionHelper;
import com.example.gogdownloader.utils.PreferencesManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONObject;

import java.util.List;

public class LibraryActivity extends AppCompatActivity implements GamesAdapter.OnGameActionListener {
    
    private static final int SETTINGS_REQUEST_CODE = 100;
    
    private RecyclerView gamesRecyclerView;
    private GamesAdapter gamesAdapter;
    private LinearLayout loadingLayout;
    private LinearLayout emptyLayout;
    private TextView userNameText;
    private TextView gameCountText;
    private Button refreshButton;
    private Button retryButton;
    private SearchView searchView;
    private FloatingActionButton settingsFab;
    
    private GOGLibraryManager libraryManager;
    private PreferencesManager preferencesManager;
    private DatabaseHelper databaseHelper;
    private PermissionHelper permissionHelper;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);
        
        initializeViews();
        initializeManagers();
        setupToolbar();
        setupRecyclerView();
        setupClickListeners();
        checkPermissions();
        loadLibrary();
    }
    
    private void initializeViews() {
        gamesRecyclerView = findViewById(R.id.gamesRecyclerView);
        loadingLayout = findViewById(R.id.loadingLayout);
        emptyLayout = findViewById(R.id.emptyLayout);
        userNameText = findViewById(R.id.userNameText);
        gameCountText = findViewById(R.id.gameCountText);
        refreshButton = findViewById(R.id.refreshButton);
        retryButton = findViewById(R.id.retryButton);
        searchView = findViewById(R.id.searchView);
        settingsFab = findViewById(R.id.settingsFab);
    }
    
    private void initializeManagers() {
        preferencesManager = new PreferencesManager(this);
        databaseHelper = new DatabaseHelper(this);
        libraryManager = new GOGLibraryManager(this);
        permissionHelper = new PermissionHelper(this);
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }
    
    private void setupRecyclerView() {
        gamesAdapter = new GamesAdapter(this);
        gamesAdapter.setOnGameActionListener(this);
        
        gamesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        gamesRecyclerView.setAdapter(gamesAdapter);
    }
    
    private void setupClickListeners() {
        refreshButton.setOnClickListener(v -> refreshLibrary());
        retryButton.setOnClickListener(v -> loadLibrary());
        settingsFab.setOnClickListener(v -> openSettings());
        
        // Configurar SearchView
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                gamesAdapter.filter(query);
                return true;
            }
            
            @Override
            public boolean onQueryTextChange(String newText) {
                gamesAdapter.filter(newText);
                return true;
            }
        });
    }
    
    private void checkPermissions() {
        permissionHelper.requestStoragePermissions(granted -> {
            if (!granted) {
                showError(getString(R.string.permission_storage));
            }
        });
        
        permissionHelper.requestNotificationPermissions(granted -> {
            if (!granted) {
                showError(getString(R.string.permission_notification));
            }
        });
    }
    
    private void loadLibrary() {
        showLoading(true);
        
        // Primeiro, tentar carregar do cache local
        List<Game> cachedGames = databaseHelper.getAllGames();
        if (!cachedGames.isEmpty()) {
            displayGames(cachedGames);
            showLoading(false);
            
            // Atualizar informações do usuário
            updateUserInfo();
            
            // Atualizar em background
            refreshLibraryInBackground();
        } else {
            // Se não há cache, carregar diretamente da API
            loadLibraryFromAPI();
        }
    }
    
    private void refreshLibrary() {
        refreshButton.setEnabled(false);
        loadLibraryFromAPI();
    }
    
    private void refreshLibraryInBackground() {
        libraryManager.loadUserLibrary(new GOGLibraryManager.LibraryCallback() {
            @Override
            public void onSuccess(List<Game> games) {
                runOnUiThread(() -> {
                    // Atualizar cache
                    databaseHelper.insertOrUpdateGames(games);
                    // Atualizar UI apenas se houve mudanças
                    displayGames(games);
                });
            }
            
            @Override
            public void onError(String error) {
                // Falha silenciosa em background
            }
        });
    }
    
    private void loadLibraryFromAPI() {
        showLoading(true);
        
        libraryManager.loadUserLibrary(new GOGLibraryManager.LibraryCallback() {
            @Override
            public void onSuccess(List<Game> games) {
                runOnUiThread(() -> {
                    showLoading(false);
                    refreshButton.setEnabled(true);
                    
                    // Salvar no cache
                    databaseHelper.insertOrUpdateGames(games);
                    
                    // Exibir jogos
                    displayGames(games);
                    
                    // Atualizar informações do usuário
                    updateUserInfo();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    showLoading(false);
                    refreshButton.setEnabled(true);
                    showError(error);
                    
                    // Se há cache, mostrar dados em cache
                    List<Game> cachedGames = databaseHelper.getAllGames();
                    if (!cachedGames.isEmpty()) {
                        displayGames(cachedGames);
                        updateUserInfo();
                    } else {
                        showEmpty();
                    }
                });
            }
        });
    }
    
    private void displayGames(List<Game> games) {
        if (games.isEmpty()) {
            showEmpty();
        } else {
            gamesAdapter.setGames(games);
            showContent();
            updateGameCount(games.size());
        }
    }
    
    private void updateUserInfo() {
        String displayName = preferencesManager.getDisplayName();
        
        // Se o nome exibido for vazio ou padrão, tentar recarregar as informações do usuário
        if (displayName == null || displayName.isEmpty() || displayName.equals("Usuário GOG")) {
            Log.d("LibraryActivity", "Display name is empty or default, trying to reload user info");
            reloadUserInfo();
        } else {
            userNameText.setText(displayName);
        }
        
        // Log para debug
        Log.d("LibraryActivity", "Display name: " + displayName);
        Log.d("LibraryActivity", "User email: " + preferencesManager.getUserEmail());
        Log.d("LibraryActivity", "User name: " + preferencesManager.getUserName());
        Log.d("LibraryActivity", "User ID: " + preferencesManager.getUserId());
    }
    
    private void reloadUserInfo() {
        String authToken = preferencesManager.getAuthToken();
        if (authToken != null && !authToken.isEmpty()) {
            GOGAuthManager authManager = new GOGAuthManager(this);
            
            // Usar o novo método getUserData para obter informações completas do usuário
            authManager.getUserData(authToken, new GOGAuthManager.UserInfoCallback() {
                @Override
                public void onSuccess(JSONObject userData) {
                    runOnUiThread(() -> {
                        try {
                            // Extrair informações do userData.json
                            String email = userData.optString("email", "");
                            String username = userData.optString("username", "");
                            String userId = userData.optString("userId", userData.optString("user_id", ""));
                            String avatar = userData.optString("avatar", "");
                            String firstName = userData.optString("first_name", "");
                            String lastName = userData.optString("last_name", "");
                            
                            // Criar nome de exibição
                            String displayName = "";
                            if (!firstName.isEmpty() || !lastName.isEmpty()) {
                                displayName = (firstName + " " + lastName).trim();
                            }
                            if (displayName.isEmpty() && !username.isEmpty()) {
                                displayName = username;
                            }
                            if (displayName.isEmpty() && !email.isEmpty()) {
                                displayName = email.split("@")[0]; // Usar parte antes do @ do email
                            }
                            if (displayName.isEmpty()) {
                                displayName = "Usuário GOG";
                            }
                            
                            // Salvar informações atualizadas
                            preferencesManager.saveAuthData(authToken, preferencesManager.getRefreshToken(), 
                                                           email, displayName, userId, avatar);
                            
                            // Atualizar UI
                            userNameText.setText(displayName);
                            
                            Log.d("LibraryActivity", "User data reloaded successfully: " + displayName);
                            Log.d("LibraryActivity", "Email: " + email + ", Username: " + username + ", Avatar: " + avatar);
                            
                        } catch (Exception e) {
                            Log.e("LibraryActivity", "Error processing user data", e);
                            userNameText.setText("Usuário GOG");
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Log.e("LibraryActivity", "Failed to reload user data: " + error);
                        userNameText.setText("Usuário GOG");
                    });
                }
            });
        } else {
            userNameText.setText("Usuário GOG");
        }
    }
    
    private void updateGameCount(int count) {
        String gameCountStr = getResources().getQuantityString(
                R.plurals.game_count, count, count);
        gameCountText.setText(gameCountStr);
    }
    
    private void showLoading(boolean show) {
        if (show) {
            loadingLayout.setVisibility(View.VISIBLE);
            gamesRecyclerView.setVisibility(View.GONE);
            emptyLayout.setVisibility(View.GONE);
        } else {
            loadingLayout.setVisibility(View.GONE);
        }
    }
    
    private void showContent() {
        loadingLayout.setVisibility(View.GONE);
        gamesRecyclerView.setVisibility(View.VISIBLE);
        emptyLayout.setVisibility(View.GONE);
    }
    
    private void showEmpty() {
        loadingLayout.setVisibility(View.GONE);
        gamesRecyclerView.setVisibility(View.GONE);
        emptyLayout.setVisibility(View.VISIBLE);
    }
    
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
    
    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivityForResult(intent, SETTINGS_REQUEST_CODE);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == SETTINGS_REQUEST_CODE && resultCode == RESULT_OK) {
            // Configurações foram alteradas, pode precisar recarregar
            // Por exemplo, se a pasta de download mudou
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_library, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_settings) {
            openSettings();
            return true;
        } else if (id == R.id.action_logout) {
            showLogoutDialog();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Tem certeza que deseja sair?")
                .setPositiveButton("Sim", (dialog, which) -> logout())
                .setNegativeButton("Cancelar", null)
                .show();
    }
    
    private void logout() {
        // Limpar dados de autenticação
        preferencesManager.clearAuthData();
        
        // Voltar para tela de login
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    // Implementações dos callbacks do adapter
    @Override
    public void onDownloadGame(Game game) {
        // Verificar permissões antes de iniciar download
        if (!permissionHelper.hasStoragePermissions()) {
            showError(getString(R.string.permission_storage));
            return;
        }
        
        // Iniciar serviço de download
        Intent downloadIntent = DownloadService.createDownloadIntent(this, game);
        startForegroundService(downloadIntent);
        
        // Atualizar status do jogo
        game.setStatus(Game.DownloadStatus.DOWNLOADING);
        databaseHelper.updateGame(game);
        gamesAdapter.updateGame(game);
        
        Toast.makeText(this, "Download iniciado: " + game.getTitle(), Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onCancelDownload(Game game) {
        // Cancelar download
        Intent cancelIntent = DownloadService.createCancelIntent(this, game.getId());
        startService(cancelIntent);
        
        // Atualizar status do jogo
        game.setStatus(Game.DownloadStatus.NOT_DOWNLOADED);
        game.setDownloadProgress(0);
        databaseHelper.updateGame(game);
        gamesAdapter.updateGame(game);
        
        Toast.makeText(this, "Download cancelado: " + game.getTitle(), Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onOpenGame(Game game) {
        // Mostrar informações do jogo baixado ou abrir pasta
        if (game.getLocalPath() != null && !game.getLocalPath().isEmpty()) {
            showGameDetails(game);
        }
    }
    
    @Override
    public void onGameClick(Game game) {
        showGameDetails(game);
    }
    
    private void showGameDetails(Game game) {
        // Criar dialog com detalhes do jogo
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(game.getTitle());
        
        StringBuilder details = new StringBuilder();
        
        if (game.getDeveloper() != null) {
            details.append("Desenvolvedor: ").append(game.getDeveloper()).append("\n");
        }
        
        if (game.getPublisher() != null) {
            details.append("Publisher: ").append(game.getPublisher()).append("\n");
        }
        
        if (!game.getGenres().isEmpty()) {
            details.append("Gêneros: ").append(game.getGenresString()).append("\n");
        }
        
        if (game.getTotalSize() > 0) {
            details.append("Tamanho: ").append(game.getFormattedSize()).append("\n");
        }
        
        details.append("Status: ");
        switch (game.getStatus()) {
            case NOT_DOWNLOADED:
                details.append("Não baixado");
                break;
            case DOWNLOADING:
                details.append("Baixando... ").append(game.getDownloadProgressPercent()).append("%");
                break;
            case DOWNLOADED:
                details.append("Baixado");
                break;
            case FAILED:
                details.append("Falha no download");
                break;
        }
        
        if (game.getLocalPath() != null && !game.getLocalPath().isEmpty()) {
            details.append("\nLocal: ").append(game.getLocalPath());
        }
        
        builder.setMessage(details.toString());
        builder.setPositiveButton("OK", null);
        builder.show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (databaseHelper != null) {
            databaseHelper.close();
        }
    }
}