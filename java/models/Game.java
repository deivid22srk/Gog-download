package com.example.gogdownloader.models;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Game implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    public enum DownloadStatus {
        NOT_DOWNLOADED,
        DOWNLOADING,
        DOWNLOADED,
        FAILED
    }
    
    private long id;
    private String title;
    private String slug;
    private String coverImage;
    private String backgroundImage;
    private String description;
    private List<DownloadLink> downloadLinks;
    private DownloadStatus status;
    private long downloadProgress;
    private long totalSize;
    private String localPath;
    private String releaseDate;
    private List<String> genres;
    private String developer;
    private String publisher;
    
    public Game() {
        this.downloadLinks = new ArrayList<>();
        this.status = DownloadStatus.NOT_DOWNLOADED;
        this.downloadProgress = 0;
        this.totalSize = 0;
        this.genres = new ArrayList<>();
    }
    
    public Game(long id, String title) {
        this();
        this.id = id;
        this.title = title;
    }
    
    // Construtor para criar Game a partir de JSON da API
    public static Game fromJson(JSONObject json) throws JSONException {
        Game game = new Game();
        
        // ID é obrigatório
        game.id = json.getLong("id");
        
        // Título com fallback
        game.title = json.optString("title", "Jogo ID: " + game.id);
        game.slug = json.optString("slug", "game-" + game.id);
        
        // Imagens
        JSONObject images = json.optJSONObject("images");
        if (images != null) {
            game.coverImage = images.optString("sidebarIcon", "");
            if (game.coverImage.isEmpty()) {
                game.coverImage = images.optString("icon", "");
            }
            game.backgroundImage = images.optString("background", "");
        }
        
        // Descrição
        game.description = json.optString("description", "");
        if (game.description.isEmpty()) {
            game.description = json.optString("summary", "");
        }
        
        // Data de lançamento
        JSONObject releaseDate = json.optJSONObject("releaseDate");
        if (releaseDate != null) {
            game.releaseDate = releaseDate.optString("date", "");
        } else {
            game.releaseDate = json.optString("releaseDate", "");
        }
        
        // Gêneros
        JSONArray genresArray = json.optJSONArray("genres");
        if (genresArray != null) {
            for (int i = 0; i < genresArray.length(); i++) {
                try {
                    JSONObject genre = genresArray.getJSONObject(i);
                    String genreName = genre.optString("name", "");
                    if (!genreName.isEmpty()) {
                        game.genres.add(genreName);
                    }
                } catch (JSONException e) {
                    // Se não for objeto, pode ser string
                    try {
                        String genreName = genresArray.getString(i);
                        if (!genreName.isEmpty()) {
                            game.genres.add(genreName);
                        }
                    } catch (JSONException ignored) {
                        // Ignorar gêneros mal formatados
                    }
                }
            }
        }
        
        // Desenvolvedor e Publisher
        JSONArray developers = json.optJSONArray("developers");
        if (developers != null && developers.length() > 0) {
            try {
                JSONObject dev = developers.getJSONObject(0);
                game.developer = dev.optString("name", "");
            } catch (JSONException e) {
                // Se não for objeto, pode ser string
                try {
                    game.developer = developers.getString(0);
                } catch (JSONException ignored) {
                    // Ignorar
                }
            }
        } else {
            game.developer = json.optString("developer", "");
        }
        
        JSONArray publishers = json.optJSONArray("publishers");
        if (publishers != null && publishers.length() > 0) {
            try {
                JSONObject pub = publishers.getJSONObject(0);
                game.publisher = pub.optString("name", "");
            } catch (JSONException e) {
                // Se não for objeto, pode ser string
                try {
                    game.publisher = publishers.getString(0);
                } catch (JSONException ignored) {
                    // Ignorar
                }
            }
        } else {
            game.publisher = json.optString("publisher", "");
        }
        
        return game;
    }
    
    // Converter para JSON para salvar no banco
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        
        json.put("id", id);
        json.put("title", title);
        json.put("slug", slug);
        json.put("coverImage", coverImage);
        json.put("backgroundImage", backgroundImage);
        json.put("description", description);
        json.put("status", status.name());
        json.put("downloadProgress", downloadProgress);
        json.put("totalSize", totalSize);
        json.put("localPath", localPath);
        json.put("releaseDate", releaseDate);
        json.put("developer", developer);
        json.put("publisher", publisher);
        
        // Gêneros
        JSONArray genresArray = new JSONArray();
        for (String genre : genres) {
            genresArray.put(genre);
        }
        json.put("genres", genresArray);
        
        return json;
    }
    
    // Getters e Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    
    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }
    
    public String getBackgroundImage() { return backgroundImage; }
    public void setBackgroundImage(String backgroundImage) { this.backgroundImage = backgroundImage; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public List<DownloadLink> getDownloadLinks() { return downloadLinks; }
    public void setDownloadLinks(List<DownloadLink> downloadLinks) { this.downloadLinks = downloadLinks; }
    
    public DownloadStatus getStatus() { return status; }
    public void setStatus(DownloadStatus status) { this.status = status; }
    
    public long getDownloadProgress() { return downloadProgress; }
    public void setDownloadProgress(long downloadProgress) { this.downloadProgress = downloadProgress; }
    
    public long getTotalSize() { return totalSize; }
    public void setTotalSize(long totalSize) { this.totalSize = totalSize; }
    
    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }
    
    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }
    
    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }
    
    public String getDeveloper() { return developer; }
    public void setDeveloper(String developer) { this.developer = developer; }
    
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    
    // Métodos utilitários
    public String getGenresString() {
        return String.join(", ", genres);
    }
    
    public int getDownloadProgressPercent() {
        if (totalSize > 0) {
            return (int) ((downloadProgress * 100) / totalSize);
        }
        return 0;
    }
    
    public String getFormattedSize() {
        return formatFileSize(totalSize);
    }
    
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Game game = (Game) obj;
        return id == game.id;
    }
    
    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
    
    @Override
    public String toString() {
        return "Game{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", status=" + status +
                '}';
    }
}