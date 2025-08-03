package com.example.gogdownloader.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gogdownloader.R;
import com.example.gogdownloader.utils.PreferencesManager;

public class FolderSelectionActivity extends BaseActivity {

    private Button selectDownloadFolderButton;
    private Button selectInstallFolderButton;
    private Button continueButton;
    private TextView downloadFolderPath;
    private TextView installFolderPath;

    private PreferencesManager preferencesManager;

    private Uri downloadFolderUri;
    private Uri installFolderUri;

    private final ActivityResultLauncher<Intent> downloadFolderPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        downloadFolderUri = uri;
                        preferencesManager.setDownloadUri(uri.toString());
                        downloadFolderPath.setText(uri.getPath());
                        checkContinueButtonState();
                    }
                }
            });

    private final ActivityResultLauncher<Intent> installFolderPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        installFolderUri = uri;
                        preferencesManager.setInstallUri(uri.toString());
                        installFolderPath.setText(uri.getPath());
                        checkContinueButtonState();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_selection);

        preferencesManager = new PreferencesManager(this);

        selectDownloadFolderButton = findViewById(R.id.selectDownloadFolderButton);
        selectInstallFolderButton = findViewById(R.id.selectInstallFolderButton);
        continueButton = findViewById(R.id.continueButton);
        downloadFolderPath = findViewById(R.id.downloadFolderPath);
        installFolderPath = findViewById(R.id.installFolderPath);

        selectDownloadFolderButton.setOnClickListener(v -> openFolderPicker(downloadFolderPickerLauncher));
        selectInstallFolderButton.setOnClickListener(v -> openFolderPicker(installFolderPickerLauncher));

        continueButton.setOnClickListener(v -> {
            Intent intent = new Intent(FolderSelectionActivity.this, LibraryActivity.class);
            startActivity(intent);
            finish();
        });

        loadExistingPreferences();
    }

    private void openFolderPicker(ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        launcher.launch(intent);
    }

    private void loadExistingPreferences() {
        String downloadUriString = preferencesManager.getDownloadUri();
        if (downloadUriString != null) {
            downloadFolderUri = Uri.parse(downloadUriString);
            downloadFolderPath.setText(downloadFolderUri.getPath());
        }

        String installUriString = preferencesManager.getInstallUri();
        if (installUriString != null) {
            installFolderUri = Uri.parse(installUriString);
            installFolderPath.setText(installFolderUri.getPath());
        }

        checkContinueButtonState();
    }

    private void checkContinueButtonState() {
        continueButton.setEnabled(downloadFolderUri != null && installFolderUri != null);
    }
}
