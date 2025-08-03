package com.example.gogdownloader.models;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.Serializable;
import java.net.URL;

public class DownloadLink implements Serializable {
    
    public enum FileType {
        INSTALLER,
        PATCH,
        EXTRA,
        DLC,
        LANGUAGE_PACK
    }
    
    public enum Platform {
        WINDOWS,
        MAC,
        LINUX
    }
    
    private String id;
    private String name;
    private String url;
    private String downloadUrl;
    private long size;
    private String checksum;
    private FileType type;
    private Platform platform;
    private String language;
    private String version;
    private boolean isAvailable;
    
    public DownloadLink() {
        this.isAvailable = true;
    }
    
    public DownloadLink(String id, String name, String url) {
        this();
        this.id = id;
        this.name = name;
        this.url = url;
    }
    
    // Construtor para criar DownloadLink a partir de JSON da API
    public static DownloadLink fromJson(JSONObject json) throws JSONException {
        DownloadLink link = new DownloadLink();
        
        link.id = json.optString("id", "");
        link.name = json.optString("name", "");
        link.url = json.optString("downlink", ""); // Corrigido: API retorna "downlink" não "manualUrl"
        link.size = json.optLong("size", 0);
        link.checksum = json.optString("checksum", "");
        link.version = json.optString("version", "");
        
        // Determinar tipo de arquivo
        String typeName = json.optString("type", "installer").toLowerCase();
        switch (typeName) {
            case "patch":
                link.type = FileType.PATCH;
                break;
            case "extra":
                link.type = FileType.EXTRA;
                break;
            case "dlc":
                link.type = FileType.DLC;
                break;
            case "language_pack":
                link.type = FileType.LANGUAGE_PACK;
                break;
            default:
                link.type = FileType.INSTALLER;
                break;
        }
        
        // Determinar plataforma
        String osName = json.optString("os", "windows").toLowerCase();
        switch (osName) {
            case "mac":
                link.platform = Platform.MAC;
                break;
            case "linux":
                link.platform = Platform.LINUX;
                break;
            default:
                link.platform = Platform.WINDOWS;
                break;
        }
        
        // Idioma
        link.language = json.optString("language", "en");
        
        return link;
    }
    
    // Converter para JSON para salvar no banco
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        
        json.put("id", id);
        json.put("name", name);
        json.put("url", url);
        json.put("downloadUrl", downloadUrl);
        json.put("size", size);
        json.put("checksum", checksum);
        json.put("type", type.name());
        json.put("platform", platform.name());
        json.put("language", language);
        json.put("version", version);
        json.put("isAvailable", isAvailable);
        
        return json;
    }
    
    // Getters e Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    
    public FileType getType() { return type; }
    public void setType(FileType type) { this.type = type; }
    
    public Platform getPlatform() { return platform; }
    public void setPlatform(Platform platform) { this.platform = platform; }
    
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    
    public boolean isAvailable() { return isAvailable; }
    public void setAvailable(boolean available) { isAvailable = available; }
    
    // Métodos utilitários
    public String getFormattedSize() {
        return Game.formatFileSize(size);
    }
    
    public String getTypeDisplayName() {
        switch (type) {
            case INSTALLER:
                return "Instalador";
            case PATCH:
                return "Patch";
            case EXTRA:
                return "Extra";
            case DLC:
                return "DLC";
            case LANGUAGE_PACK:
                return "Pacote de Idioma";
            default:
                return "Arquivo";
        }
    }
    
    public String getPlatformDisplayName() {
        switch (platform) {
            case WINDOWS:
                return "Windows";
            case MAC:
                return "macOS";
            case LINUX:
                return "Linux";
            default:
                return "Desconhecido";
        }
    }
    
    public String getFileName() {
        if (downloadUrl != null && !downloadUrl.isEmpty()) {
            try {
                return new File(new URL(downloadUrl).getPath()).getName();
            } catch (Exception e) {
                // Ignore
            }
        }

        if (name != null && !name.isEmpty()) {
            return name;
        }
        
        // Gerar nome baseado no tipo e plataforma
        return String.format("%s_%s_%s.exe", 
                getTypeDisplayName().toLowerCase().replace(" ", "_"),
                getPlatformDisplayName().toLowerCase(),
                version != null ? version : "latest");
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        DownloadLink that = (DownloadLink) obj;
        return id != null ? id.equals(that.id) : that.id == null;
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
    
    @Override
    public String toString() {
        return "DownloadLink{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", platform=" + platform +
                ", size=" + getFormattedSize() +
                '}';
    }
}