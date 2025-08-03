package com.example.gogdownloader.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gogdownloader.R;
import com.example.gogdownloader.models.Game;
import com.example.gogdownloader.utils.ImageLoader;

import java.util.ArrayList;
import java.util.List;

public class GamesAdapter extends RecyclerView.Adapter<GamesAdapter.GameViewHolder> {
    
    private Context context;
    private List<Game> games;
    private List<Game> filteredGames;
    private OnGameActionListener listener;
    
    public interface OnGameActionListener {
        void onDownloadGame(Game game);
        void onCancelDownload(Game game);
        void onOpenGame(Game game);
        void onGameClick(Game game);
    }
    
    public GamesAdapter(Context context) {
        this.context = context;
        this.games = new ArrayList<>();
        this.filteredGames = new ArrayList<>();
    }
    
    public void setOnGameActionListener(OnGameActionListener listener) {
        this.listener = listener;
    }
    
    public void setGames(List<Game> games) {
        this.games = new ArrayList<>(games);
        this.filteredGames = new ArrayList<>(games);
        notifyDataSetChanged();
    }
    
    public void updateGame(Game updatedGame) {
        for (int i = 0; i < games.size(); i++) {
            if (games.get(i).getId() == updatedGame.getId()) {
                games.set(i, updatedGame);
                break;
            }
        }
        
        for (int i = 0; i < filteredGames.size(); i++) {
            if (filteredGames.get(i).getId() == updatedGame.getId()) {
                filteredGames.set(i, updatedGame);
                notifyItemChanged(i);
                break;
            }
        }
    }
    
    public void filter(String query) {
        filteredGames.clear();
        
        if (query == null || query.isEmpty()) {
            filteredGames.addAll(games);
        } else {
            String lowercaseQuery = query.toLowerCase();
            for (Game game : games) {
                if (game.getTitle().toLowerCase().contains(lowercaseQuery) ||
                    (game.getDeveloper() != null && game.getDeveloper().toLowerCase().contains(lowercaseQuery)) ||
                    game.getGenresString().toLowerCase().contains(lowercaseQuery)) {
                    filteredGames.add(game);
                }
            }
        }
        
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_game, parent, false);
        return new GameViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        Game game = filteredGames.get(position);
        holder.bind(game);
    }
    
    @Override
    public int getItemCount() {
        return filteredGames.size();
    }
    
    public class GameViewHolder extends RecyclerView.ViewHolder {
        
        private ImageView gameCoverImage;
        private TextView gameTitleText;
        private TextView gameDeveloperText;
        private TextView gameSizeText;
        private TextView gameGenresText;
        private LinearLayout downloadProgressLayout;
        private ProgressBar downloadProgressBar;
        private TextView downloadProgressText;
        private Button actionButton;
        private ImageView statusIcon;
        
        public GameViewHolder(@NonNull View itemView) {
            super(itemView);
            
            gameCoverImage = itemView.findViewById(R.id.gameCoverImage);
            gameTitleText = itemView.findViewById(R.id.gameTitleText);
            gameDeveloperText = itemView.findViewById(R.id.gameDeveloperText);
            gameSizeText = itemView.findViewById(R.id.gameSizeText);
            gameGenresText = itemView.findViewById(R.id.gameGenresText);
            downloadProgressLayout = itemView.findViewById(R.id.downloadProgressLayout);
            downloadProgressBar = itemView.findViewById(R.id.downloadProgressBar);
            downloadProgressText = itemView.findViewById(R.id.downloadProgressText);
            actionButton = itemView.findViewById(R.id.actionButton);
            statusIcon = itemView.findViewById(R.id.statusIcon);
            
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onGameClick(filteredGames.get(position));
                }
            });
        }
        
        public void bind(Game game) {
            // Título do jogo
            gameTitleText.setText(game.getTitle());
            
            // Desenvolvedor
            if (game.getDeveloper() != null && !game.getDeveloper().isEmpty()) {
                gameDeveloperText.setText(game.getDeveloper());
                gameDeveloperText.setVisibility(View.VISIBLE);
            } else {
                gameDeveloperText.setVisibility(View.GONE);
            }
            
            // Tamanho
            if (game.getTotalSize() > 0) {
                gameSizeText.setText(game.getFormattedSize());
                gameSizeText.setVisibility(View.VISIBLE);
            } else {
                gameSizeText.setText("Tamanho desconhecido");
                gameSizeText.setVisibility(View.VISIBLE);
            }
            
            // Gêneros
            if (!game.getGenres().isEmpty()) {
                gameGenresText.setText(game.getGenresString());
                gameGenresText.setVisibility(View.VISIBLE);
            } else {
                gameGenresText.setVisibility(View.GONE);
            }
            
            // Imagem de capa
            if (game.getCoverImage() != null && !game.getCoverImage().isEmpty()) {
                ImageLoader.loadImage(context, game.getCoverImage(), game.getBackgroundImage(), gameCoverImage);
            } else {
                gameCoverImage.setImageResource(R.drawable.ic_image);
            }
            
            // Status e ações baseados no estado do download
            updateDownloadStatus(game);
            
            // Configurar clique do botão de ação
            actionButton.setOnClickListener(v -> handleActionButtonClick(game));
        }
        
        private void updateDownloadStatus(Game game) {
            switch (game.getStatus()) {
                case NOT_DOWNLOADED:
                    showNotDownloadedState();
                    break;
                    
                case DOWNLOADING:
                    showDownloadingState(game);
                    break;
                    
                case DOWNLOADED:
                    showDownloadedState();
                    break;
                    
                case FAILED:
                    showFailedState();
                    break;
            }
        }
        
        private void showNotDownloadedState() {
            downloadProgressLayout.setVisibility(View.GONE);
            statusIcon.setVisibility(View.GONE);
            
            actionButton.setText(context.getString(R.string.download));
            actionButton.setEnabled(true);
        }
        
        private void showDownloadingState(Game game) {
            downloadProgressLayout.setVisibility(View.VISIBLE);
            statusIcon.setVisibility(View.GONE);
            
            int progress = game.getDownloadProgressPercent();
            downloadProgressBar.setProgress(progress);
            downloadProgressText.setText(
                context.getString(R.string.download_progress, progress, game.getFormattedSize())
            );
            
            actionButton.setText(context.getString(R.string.cancel));
            actionButton.setEnabled(true);
        }
        
        private void showDownloadedState() {
            downloadProgressLayout.setVisibility(View.GONE);
            statusIcon.setVisibility(View.VISIBLE);
            statusIcon.setImageResource(android.R.drawable.ic_dialog_info);
            
            actionButton.setText(context.getString(R.string.downloaded));
            actionButton.setEnabled(false);
        }
        
        private void showFailedState() {
            downloadProgressLayout.setVisibility(View.GONE);
            statusIcon.setVisibility(View.VISIBLE);
            statusIcon.setImageResource(android.R.drawable.ic_dialog_alert);
            
            actionButton.setText(context.getString(R.string.retry));
            actionButton.setEnabled(true);
        }
        
        private void handleActionButtonClick(Game game) {
            if (listener == null) return;
            
            switch (game.getStatus()) {
                case NOT_DOWNLOADED:
                case FAILED:
                    listener.onDownloadGame(game);
                    break;
                    
                case DOWNLOADING:
                    listener.onCancelDownload(game);
                    break;
                    
                case DOWNLOADED:
                    listener.onOpenGame(game);
                    break;
            }
        }
    }
}