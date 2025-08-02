package com.example.gogdownloader.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.provider.DocumentsContract;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.example.gogdownloader.utils.ImageLoader;
import com.example.gogdownloader.utils.SAFDownloadManager;
import com.example.gogdownloader.utils.PreferencesManager;
import com.example.gogdownloader.utils.PermissionHelper;
import androidx.appcompat.widget.SearchView;
import android.widget.Toast;
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
    private SAFDownloadManager safDownloadManager;
    
    // Launcher para seleção de pasta de download
    private ActivityResultLauncher<Intent> folderPickerLauncher;
    private Game pendingDownloadGame; // Jogo aguardando seleção de pasta
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);
        
        setupFolderPickerLauncher();
        initializeViews();
        initializeManagers();
        setupToolbar();
        setupRecyclerView();
        setupClickListeners();
        checkPermissions();
        loadLibrary();
    }
    
    private void setupFolderPickerLauncher() {
        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        android.net.Uri uri = result.getData().getData();
                        if (uri != null) {
                            handleSelectedFolder(uri);
                        }
                    } else {
                        // Usuário cancelou seleção
                        pendingDownloadGame = null;
                        Toast.makeText(this, "Seleção de pasta cancelada", Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    private void handleSelectedFolder(android.net.Uri uri) {
        try {
            // Dar permissão persistente para a URI
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            
            // Salvar URI nas preferências
            preferencesManager.setDownloadUri(uri.toString());
            
            Log.d("LibraryActivity", "Pasta selecionada e salva: " + uri.toString());
            Toast.makeText(this, "Pasta de download configurada!", Toast.LENGTH_SHORT).show();
            
            // Se havia um download pendente, iniciar agora
            if (pendingDownloadGame != null) {
                Game gameToDownload = pendingDownloadGame;
                pendingDownloadGame = null;
                startGameDownload(gameToDownload);
            }
            
        } catch (Exception e) {
            Log.e("LibraryActivity", "Erro ao processar pasta selecionada", e);
            Toast.makeText(this, "Erro ao configurar pasta de download", Toast.LENGTH_SHORT).show();
            pendingDownloadGame = null;
        }
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
        safDownloadManager = new SAFDownloadManager(this);
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
        // Solicitar permissões de armazenamento apenas para compatibilidade com configurações legadas
        // Com SAF não são obrigatórias
        permissionHelper.requestStoragePermissions(granted -> {
            if (!granted) {
                Log.i("LibraryActivity", "Permissões de armazenamento negadas - usará apenas SAF");
                // Não mostrar erro, pois SAF funciona sem essas permissões
            }
        });
        
        // Permissões de notificação são importantes para o progresso de download
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
        
        Log.d("LibraryActivity", "=== UPDATE USER INFO ===");
        Log.d("LibraryActivity", "Current display name: '" + displayName + "'");
        Log.d("LibraryActivity", "User email: '" + preferencesManager.getUserEmail() + "'");
        Log.d("LibraryActivity", "User name: '" + preferencesManager.getUserName() + "'");
        Log.d("LibraryActivity", "User ID: '" + preferencesManager.getUserId() + "'");
        
        // Sempre tentar carregar dados do usuário se não temos informações
        boolean shouldReloadUserInfo = displayName == null || 
                                      displayName.isEmpty() || 
                                      displayName.equals("Usuário GOG") ||
                                      preferencesManager.getUserEmail() == null ||
                                      preferencesManager.getUserEmail().isEmpty();
        
        if (shouldReloadUserInfo) {
            Log.d("LibraryActivity", "Need to reload user info, current data is insufficient");
            reloadUserInfo();
        } else {
            Log.d("LibraryActivity", "Using existing user data");
            setUserDisplayInfo(displayName);
        }
    }
    
    /**
     * Define as informações de exibição do usuário na UI
     */
    private void setUserDisplayInfo(String displayName) {
        if (userNameText != null) {
            userNameText.setText(displayName);
            Log.d("LibraryActivity", "Set userNameText to: '" + displayName + "'");
            
            // Carregar avatar se disponível
            String avatarUrl = preferencesManager.getUserAvatar();
            loadUserAvatar(avatarUrl);
        } else {
            Log.e("LibraryActivity", "userNameText is null!");
        }
    }
    
    /**
     * Carrega o avatar do usuário
     */
    private void loadUserAvatar(String avatarUrl) {
        ImageView userAvatar = findViewById(R.id.userAvatar);
        if (userAvatar != null && avatarUrl != null && !avatarUrl.isEmpty()) {
            Log.d("LibraryActivity", "Loading user avatar: " + avatarUrl);
            ImageLoader.loadImage(this, avatarUrl, userAvatar);
        } else {
            Log.d("LibraryActivity", "No avatar to load or userAvatar view not found");
            if (userAvatar != null) {
                userAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
            }
        }
    }
    
    private void reloadUserInfo() {
        String authToken = preferencesManager.getAuthToken();
        Log.d("LibraryActivity", "=== RELOADING USER INFO ===");
        Log.d("LibraryActivity", "Auth token available: " + (authToken != null && !authToken.isEmpty()));
        
        if (authToken != null && !authToken.isEmpty()) {
            GOGAuthManager authManager = new GOGAuthManager(this);
            
            Log.d("LibraryActivity", "Calling getUserData...");
            
            // Usar o novo método getUserData para obter informações completas do usuário
            authManager.getUserData(authToken, new GOGAuthManager.UserInfoCallback() {
                @Override
                public void onSuccess(JSONObject userData) {
                    Log.d("LibraryActivity", "=== USER DATA SUCCESS ===");
                    runOnUiThread(() -> {
                        try {
                            processUserData(userData, authToken);
                            
                        } catch (Exception e) {
                            Log.e("LibraryActivity", "=== ERROR PROCESSING USER DATA ===", e);
                            setFallbackUserInfo();
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    Log.e("LibraryActivity", "=== USER DATA ERROR: " + error + " ===");
                    runOnUiThread(() -> {
                        setFallbackUserInfo();
                    });
                }
            });
        } else {
            Log.w("LibraryActivity", "No auth token available!");
            setFallbackUserInfo();
        }
    }
    
    /**
     * Processa os dados do usuário recebidos da API
     */
    private void processUserData(JSONObject userData, String authToken) {
        // Extrair informações do userData
        String email = userData.optString("email", "");
        String username = userData.optString("username", "");
        String userId = userData.optString("userId", userData.optString("user_id", ""));
        String avatar = userData.optString("avatar", "");
        String firstName = userData.optString("first_name", "");
        String lastName = userData.optString("last_name", "");
        
        Log.d("LibraryActivity", "=== EXTRACTED DATA ===");
        Log.d("LibraryActivity", "Email: '" + email + "'");
        Log.d("LibraryActivity", "Username: '" + username + "'");
        Log.d("LibraryActivity", "First name: '" + firstName + "'");
        Log.d("LibraryActivity", "Last name: '" + lastName + "'");
        Log.d("LibraryActivity", "Avatar: '" + avatar + "'");
        
        // Criar nome de exibição
        String displayName = createDisplayName(firstName, lastName, username, email);
        
        Log.d("LibraryActivity", "=== FINAL DISPLAY NAME: '" + displayName + "' ===");
        
        // Salvar informações atualizadas
        Log.d("LibraryActivity", "Saving auth data...");
        preferencesManager.saveAuthData(authToken, preferencesManager.getRefreshToken(), 
                                       email, displayName, userId, avatar);
        
        // Atualizar UI
        setUserDisplayInfo(displayName);
        
        Log.d("LibraryActivity", "=== USER INFO UPDATE COMPLETE ===");
    }
    
    /**
     * Cria o nome de exibição baseado nos dados disponíveis
     */
    private String createDisplayName(String firstName, String lastName, String username, String email) {
        String displayName = "";
        
        // Tentar usar nome completo primeiro
        if (!firstName.isEmpty() || !lastName.isEmpty()) {
            displayName = (firstName + " " + lastName).trim();
            Log.d("LibraryActivity", "Using full name: '" + displayName + "'");
        }
        
        // Se não tem nome, usar username
        if (displayName.isEmpty() && !username.isEmpty()) {
            displayName = username;
            Log.d("LibraryActivity", "Using username: '" + displayName + "'");
        }
        
        // Se não tem username, usar parte do email
        if (displayName.isEmpty() && !email.isEmpty()) {
            displayName = email.split("@")[0];
            Log.d("LibraryActivity", "Using email prefix: '" + displayName + "'");
        }
        
        // Fallback final
        if (displayName.isEmpty()) {
            displayName = "Usuário GOG";
            Log.d("LibraryActivity", "Using fallback: '" + displayName + "'");
        }
        
        return displayName;
    }
    
    /**
     * Define informações de fallback quando não consegue carregar dados do usuário
     */
    private void setFallbackUserInfo() {
        if (userNameText != null) {
            userNameText.setText("Usuário GOG");
            Log.d("LibraryActivity", "Set fallback user info");
        }
        
        // Definir avatar padrão
        ImageView userAvatar = findViewById(R.id.userAvatar);
        if (userAvatar != null) {
            userAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
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
        Log.d("LibraryActivity", "Download solicitado para: " + game.getTitle());
        
        // Com SAF não precisamos de permissões de armazenamento tradicionais
        // O sistema fornece acesso através de URIs selecionadas pelo usuário
        
        // Verificar se há pasta de download configurada
        if (!safDownloadManager.hasDownloadLocationConfigured()) {
            Log.d("LibraryActivity", "Nenhuma pasta configurada, solicitando seleção");
            
            // Salvar jogo pendente e solicitar seleção de pasta
            pendingDownloadGame = game;
            showFolderSelectionDialog();
            return;
        }
        
        // Pasta já configurada, iniciar download
        startGameDownload(game);
    }
    
    private void showFolderSelectionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Pasta de Download")
                .setMessage("Você precisa selecionar uma pasta onde os jogos serão salvos. Deseja escolher uma pasta agora?")
                .setPositiveButton("Escolher Pasta", (dialog, which) -> {
                    openFolderPicker();
                })
                .setNegativeButton("Cancelar", (dialog, which) -> {
                    pendingDownloadGame = null;
                })
                .setCancelable(false)
                .show();
    }
    
    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | 
                       Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                       Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        
        // Definir diretório inicial se possível
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.io.File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            android.net.Uri initialUri = android.net.Uri.fromFile(downloadsDir);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        }
        
        try {
            folderPickerLauncher.launch(intent);
        } catch (Exception e) {
            Log.e("LibraryActivity", "Erro ao abrir seletor de pasta", e);
            Toast.makeText(this, "Erro ao abrir seletor de pasta", Toast.LENGTH_SHORT).show();
            pendingDownloadGame = null;
        }
    }
    
    private void startGameDownload(Game game) {
        Log.d("LibraryActivity", "Iniciando download para: " + game.getTitle());
        
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