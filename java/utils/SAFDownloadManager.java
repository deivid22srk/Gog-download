package com.example.gogdownloader.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.example.gogdownloader.models.DownloadLink;
import com.example.gogdownloader.models.Game;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Gerenciador de downloads usando Storage Access Framework (SAF)
 * Permite salvar arquivos em qualquer local que o usuário selecionar
 */
public class SAFDownloadManager {
    
    private static final String TAG = "SAFDownloadManager";
    
    private Context context;
    private PreferencesManager preferencesManager;
    
    public SAFDownloadManager(Context context) {
        this.context = context;
        this.preferencesManager = new PreferencesManager(context);
    }
    
    /**
     * Verifica se há uma localização de download configurada
     */
    public boolean hasDownloadLocationConfigured() {
        return preferencesManager.hasDownloadLocationConfigured();
    }
    
    /**
     * Obtém o diretório de download configurado como DocumentFile
     */
    public DocumentFile getDownloadDirectory() {
        String uriString = preferencesManager.getDownloadUri();
        if (uriString != null && !uriString.isEmpty()) {
            try {
                Uri uri = Uri.parse(uriString);
                DocumentFile documentFile = DocumentFile.fromTreeUri(context, uri);
                if (documentFile != null && documentFile.exists() && documentFile.canWrite()) {
                    Log.d(TAG, "Using SAF directory: " + documentFile.getName());
                    return documentFile;
                } else {
                    Log.w(TAG, "SAF directory is no longer valid, clearing preference.");
                    preferencesManager.clearDownloadUri();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error accessing SAF directory, clearing preference.", e);
                preferencesManager.clearDownloadUri();
            }
        }

        Log.w(TAG, "No valid SAF directory configured");
        return null;
    }
    
    /**
     * Cria diretório para um jogo específico
     */
    public DocumentFile createGameDirectory(Game game) {
        DocumentFile downloadDir = getDownloadDirectory();
        if (downloadDir == null) {
            Log.e(TAG, "No download directory available");
            return null;
        }
        
        // Limpar nome do jogo para usar como nome de diretório
        String gameDirName = sanitizeFileName(game.getTitle());
        
        // Verificar se o diretório já existe
        DocumentFile gameDir = downloadDir.findFile(gameDirName);
        if (gameDir != null && gameDir.isDirectory()) {
            Log.d(TAG, "Using existing game directory: " + gameDirName);
            return gameDir;
        }
        
        // Criar novo diretório
        gameDir = downloadDir.createDirectory(gameDirName);
        if (gameDir != null) {
            Log.d(TAG, "Created game directory: " + gameDirName);
            return gameDir;
        }
        
        Log.e(TAG, "Failed to create game directory: " + gameDirName);
        return null;
    }
    
    /**
     * Cria arquivo para download
     */
    public DocumentFile createDownloadFile(Game game, DownloadLink downloadLink) {
        DocumentFile gameDir = createGameDirectory(game);
        if (gameDir == null) {
            return null;
        }
        
        String fileName = sanitizeFileName(downloadLink.getFileName());
        if (fileName == null || fileName.isEmpty()) {
            fileName = "installer.exe"; // Fallback
        }
        
        // Verificar se arquivo já existe
        DocumentFile existingFile = gameDir.findFile(fileName);
        if (existingFile != null && existingFile.isFile()) {
            Log.d(TAG, "File already exists, returning for resume: " + fileName);
            return existingFile;
        }
        
        // Determinar MIME type
        String mimeType = getMimeTypeFromFileName(fileName);
        
        // Criar novo arquivo
        DocumentFile file = gameDir.createFile(mimeType, fileName);
        if (file != null) {
            Log.d(TAG, "Created new download file: " + fileName);
            return file;
        }
        
        Log.e(TAG, "Failed to create download file: " + fileName);
        return null;
    }
    
    /**
     * Encontra um arquivo de download para um jogo
     */
    public DocumentFile findDownloadFile(Game game, DownloadLink downloadLink) {
        DocumentFile gameDir = createGameDirectory(game);
        if (gameDir == null) {
            return null;
        }

        String fileName = sanitizeFileName(downloadLink.getFileName());
        if (fileName == null || fileName.isEmpty()) {
            fileName = "installer.exe"; // Fallback
        }

        DocumentFile file = gameDir.findFile(fileName);
        if (file != null && file.isFile()) {
            return file;
        }

        return null;
    }

    /**
     * Obtém OutputStream para escrita no arquivo (por padrão, sobrescreve)
     */
    public OutputStream getOutputStream(DocumentFile file) throws IOException {
        return getOutputStream(file, false);
    }

    /**
     * Obtém OutputStream para escrita no arquivo
     * @param file O arquivo a ser escrito
     * @param append Se true, abre o arquivo em modo de append. Se false, sobrescreve.
     */
    public OutputStream getOutputStream(DocumentFile file, boolean append) throws IOException {
        if (file == null || !file.canWrite()) {
            throw new IOException("Cannot write to file");
        }
        
        String mode = append ? "wa" : "w";
        try {
            return context.getContentResolver().openOutputStream(file.getUri(), mode);
        } catch (IOException e) {
            Log.e(TAG, "Failed to open output stream for " + file.getUri() + " with mode " + mode, e);
            throw e;
        }
    }
    
    /**
     * Obtém InputStream para leitura do arquivo
     */
    public InputStream getInputStream(DocumentFile file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IOException("File does not exist");
        }
        
        return context.getContentResolver().openInputStream(file.getUri());
    }
    
    /**
     * Obtém o tamanho de um arquivo
     */
    public long getFileSize(DocumentFile file) {
        if (file == null || !file.exists()) {
            return 0;
        }
        return file.length();
    }
    
    /**
     * Verifica se um arquivo existe
     */
    public boolean fileExists(Game game, DownloadLink downloadLink) {
        DocumentFile gameDir = createGameDirectory(game);
        if (gameDir == null) {
            return false;
        }
        
        String fileName = sanitizeFileName(downloadLink.getFileName());
        DocumentFile file = gameDir.findFile(fileName);
        return file != null && file.exists();
    }
    
    /**
     * Limpa nome de arquivo removendo caracteres inválidos
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        
        // Remover caracteres não permitidos em nomes de arquivo
        return fileName.replaceAll("[^a-zA-Z0-9._\\-\\s]", "_")
                       .replaceAll("\\s+", "_")
                       .replaceAll("_{2,}", "_");
    }
    
    /**
     * Determina MIME type baseado na extensão do arquivo
     */
    private String getMimeTypeFromFileName(String fileName) {
        if (fileName == null) {
            return "application/octet-stream";
        }
        
        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.endsWith(".exe")) {
            return "application/vnd.microsoft.portable-executable";
        } else if (lowerFileName.endsWith(".msi")) {
            return "application/x-msi";
        } else if (lowerFileName.endsWith(".zip")) {
            return "application/zip";
        } else if (lowerFileName.endsWith(".7z")) {
            return "application/x-7z-compressed";
        } else if (lowerFileName.endsWith(".rar")) {
            return "application/x-rar-compressed";
        } else if (lowerFileName.endsWith(".bin")) {
            return "application/octet-stream";
        }
        
        return "application/octet-stream";
    }
    
    /**
     * Obtém informações sobre o espaço disponível
     * Nota: DocumentFile não oferece uma maneira direta de verificar espaço livre
     */
    public boolean hasAvailableSpace(long requiredBytes) {
        // Para SAF, assumimos que há espaço suficiente
        // O sistema operacional lidará com erro de espaço insuficiente
        return true;
    }
    
    /**
     * Deleta um arquivo de download
     */
    public boolean deleteDownloadFile(Game game, DownloadLink downloadLink) {
        DocumentFile gameDir = createGameDirectory(game);
        if (gameDir == null) {
            return false;
        }
        
        String fileName = sanitizeFileName(downloadLink.getFileName());
        DocumentFile file = gameDir.findFile(fileName);
        if (file != null && file.exists()) {
            return file.delete();
        }
        
        return false;
    }
    
    /**
     * Lista todos os arquivos de um jogo
     */
    public DocumentFile[] getGameFiles(Game game) {
        DocumentFile gameDir = createGameDirectory(game);
        if (gameDir == null) {
            return new DocumentFile[0];
        }
        
        return gameDir.listFiles();
    }
    
    /**
     * Obtém o caminho de exibição para o usuário
     */
    public String getDisplayPath() {
        DocumentFile downloadDir = getDownloadDirectory();
        if (downloadDir != null) {
            return downloadDir.getName() != null ? downloadDir.getName() : "Pasta selecionada";
        }
        
        // Fallback para path legado
        String legacyPath = preferencesManager.getDownloadPathLegacy();
        if (legacyPath != null && !legacyPath.isEmpty()) {
            return legacyPath;
        }
        
        return "Nenhuma pasta selecionada";
    }

    /**
     * Cria ou encontra um arquivo de parte para um download em pedaços.
     */
    public DocumentFile createOrFindDownloadPartFile(Game game, DownloadLink downloadLink, int partIndex) {
        DocumentFile gameDir = createGameDirectory(game);
        if (gameDir == null) {
            return null;
        }

        String baseFileName = sanitizeFileName(downloadLink.getFileName());
        String partFileName = baseFileName + ".part" + partIndex;

        DocumentFile partFile = gameDir.findFile(partFileName);
        if (partFile != null && partFile.isFile()) {
            return partFile;
        }

        return gameDir.createFile("application/octet-stream", partFileName);
    }

    /**
     * Concatena uma lista de arquivos de parte em um único arquivo final.
     * DELETA os arquivos de parte após a concatenação bem-sucedida.
     */
    public void concatenateFiles(List<DocumentFile> partFiles, DocumentFile finalFile) throws IOException {
        Log.d(TAG, "Concatenating " + partFiles.size() + " parts into " + finalFile.getName());

        try (OutputStream finalOut = getOutputStream(finalFile, false)) { // Always overwrite final file
            byte[] buffer = new byte[65536];
            for (DocumentFile partFile : partFiles) {
                if (partFile == null || !partFile.exists()) {
                    throw new IOException("Part file is missing: " + (partFile != null ? partFile.getName() : "null"));
                }
                try (InputStream partIn = getInputStream(partFile)) {
                    int bytesRead;
                    while ((bytesRead = partIn.read(buffer)) != -1) {
                        finalOut.write(buffer, 0, bytesRead);
                    }
                }
            }
            finalOut.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to concatenate files.", e);
            throw e;
        }

        Log.d(TAG, "Concatenation successful. Deleting part files.");
        for (DocumentFile partFile : partFiles) {
            if (partFile != null && partFile.exists()) {
                if (!partFile.delete()) {
                    Log.w(TAG, "Failed to delete part file: " + partFile.getName());
                }
            }
        }
    }

    /**
     * Deleta todos os arquivos de parte para um download.
     */
    public void deleteDownloadPartFiles(Game game, DownloadLink downloadLink, int numParts) {
        DocumentFile gameDir = createGameDirectory(game);
        if (gameDir == null) {
            return;
        }

        String baseFileName = sanitizeFileName(downloadLink.getFileName());
        for (int i = 0; i < numParts; i++) {
            String partFileName = baseFileName + ".part" + i;
            DocumentFile partFile = gameDir.findFile(partFileName);
            if (partFile != null && partFile.exists()) {
                if (!partFile.delete()) {
                    Log.w(TAG, "Failed to delete part file: " + partFile.getName());
                }
            }
        }
    }
}