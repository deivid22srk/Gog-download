package com.example.gogdownloader.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionHelper {
    
    private Context context;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private PermissionCallback currentCallback;
    
    public interface PermissionCallback {
        void onResult(boolean granted);
    }
    
    public PermissionHelper(AppCompatActivity activity) {
        this.context = activity;
        
        // Configurar launcher para múltiplas permissões
        permissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }
                    
                    if (currentCallback != null) {
                        currentCallback.onResult(allGranted);
                        currentCallback = null;
                    }
                });
    }
    
    public PermissionHelper(Context context) {
        this.context = context;
    }
    
    public void requestStoragePermissions(PermissionCallback callback) {
        List<String> permissions = getRequiredStoragePermissions();
        
        if (permissions.isEmpty()) {
            // Todas as permissões já foram concedidas
            callback.onResult(true);
            return;
        }
        
        this.currentCallback = callback;
        permissionLauncher.launch(permissions.toArray(new String[0]));
    }
    
    public void requestNotificationPermissions(PermissionCallback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                
                this.currentCallback = callback;
                permissionLauncher.launch(new String[]{Manifest.permission.POST_NOTIFICATIONS});
                return;
            }
        }
        
        // Permissão já concedida ou não necessária
        callback.onResult(true);
    }
    
    public boolean hasStoragePermissions() {
        return getRequiredStoragePermissions().isEmpty();
    }
    
    public boolean hasNotificationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Não necessário em versões anteriores
    }
    
    private List<String> getRequiredStoragePermissions() {
        List<String> permissions = new ArrayList<>();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: usar MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                // Esta permissão requer um processo especial, não pode ser solicitada diretamente
                // O usuário precisa ir para as configurações
                permissions.add(Manifest.permission.MANAGE_EXTERNAL_STORAGE);
            }
        } else {
            // Android 10 e anteriores: usar READ/WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        
        return permissions;
    }
    
    public static void requestStoragePermissionsLegacy(Activity activity, int requestCode) {
        List<String> permissions = new ArrayList<>();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // Para Android 11+, precisaríamos usar Intent para configurações
                // ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                permissions.add(Manifest.permission.MANAGE_EXTERNAL_STORAGE);
            }
        } else {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(activity, 
                    permissions.toArray(new String[0]), requestCode);
        }
    }
    
    public boolean shouldShowStoragePermissionRationale() {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return false; // MANAGE_EXTERNAL_STORAGE não suporta rationale
            } else {
                return ActivityCompat.shouldShowRequestPermissionRationale(activity, 
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                       ActivityCompat.shouldShowRequestPermissionRationale(activity, 
                        Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        return false;
    }
    
    public boolean shouldShowNotificationPermissionRationale() {
        if (context instanceof Activity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Activity activity = (Activity) context;
            return ActivityCompat.shouldShowRequestPermissionRationale(activity, 
                    Manifest.permission.POST_NOTIFICATIONS);
        }
        return false;
    }
    
    // Método para verificar se todas as permissões essenciais estão concedidas
    public boolean hasAllEssentialPermissions() {
        return hasStoragePermissions() && hasNotificationPermissions();
    }
    
    // Método para solicitar todas as permissões essenciais de uma vez
    public void requestAllEssentialPermissions(PermissionCallback callback) {
        List<String> allPermissions = new ArrayList<>();
        
        // Adicionar permissões de armazenamento
        allPermissions.addAll(getRequiredStoragePermissions());
        
        // Adicionar permissão de notificação se necessário
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                allPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        
        if (allPermissions.isEmpty()) {
            callback.onResult(true);
            return;
        }
        
        this.currentCallback = callback;
        permissionLauncher.launch(allPermissions.toArray(new String[0]));
    }
}