package com.kigaliwebartisans.traffix;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    // Define a request code for the QR scanner.
    public static final int QR_SCAN_REQUEST_CODE = 49374;

    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private NavController navController;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request permissions
        requestRequiredPermissions();

        // NFC setup
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        // Navigation Component setup
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavigationUI.setupWithNavController(bottomNav, navController);

        Log.d("APP_INIT", "MainActivity started successfully");
    }

    private void requestRequiredPermissions() {
        String[] permissions = new String[]{
                Manifest.permission.INTERNET,
                Manifest.permission.NFC,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        boolean allGranted = true;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        } else {
            Log.d("PERMISSIONS", "All required permissions already granted");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Log.d("PERMISSIONS", "All permissions granted by user");
            } else {
                Log.e("PERMISSIONS", "Some permissions were denied");
                Toast.makeText(this, "Permissions are required for app to function properly", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null) {
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
            Log.d("NFC", "Foreground dispatch enabled");
        } else {
            Log.e("NFC", "No NFC adapter available");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
            Log.d("NFC", "Foreground dispatch disabled");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        try {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag != null) {
                String tagId = bytesToHexString(tag.getId());
                Log.d("NFC", "Tag detected: " + tagId);

                NavHostFragment navHostFragment =
                        (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                if (navHostFragment != null && navHostFragment.getChildFragmentManager().getFragments().size() > 0) {
                    for (androidx.fragment.app.Fragment f : navHostFragment.getChildFragmentManager().getFragments()) {
                        if (f != null && f.isVisible()) {
                            if (f instanceof RegisterDriverFragment) {
                                ((RegisterDriverFragment) f).setNfcTag(tagId);
                                Toast.makeText(this, "Tag captured for registration: " + tagId, Toast.LENGTH_SHORT).show();
                                Log.d("NFC", "Tag passed to RegisterDriverFragment");
                            } else if (f instanceof TraffickingCheckpointFragment) {
                                ((TraffickingCheckpointFragment) f).searchByNfcTag(tagId);
                                Toast.makeText(this, "Tag captured for checkpoint: " + tagId, Toast.LENGTH_SHORT).show();
                                Log.d("NFC", "Tag passed to TraffickingCheckpointFragment");
                            } else {
                                Toast.makeText(this, "NFC tag detected: " + tagId, Toast.LENGTH_SHORT).show();
                                Log.d("NFC", "Tag detected on unknown fragment");
                            }
                            break;
                        }
                    }
                }
            } else {
                Log.e("NFC", "No NFC tag found in intent");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("NFC", "Exception while handling NFC intent: " + e.toString());
            new AlertDialog.Builder(this)
                    .setTitle("NFC Error")
                    .setMessage("Error: " + e.getClass().getName() + "\nMessage: " + e.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    // This is the new method you'll use to handle the QR scan result.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Check if the result is from our QR scanner and it was successful.
        if (requestCode == QR_SCAN_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            String qrContent = data.getStringExtra("SCAN_RESULT");
            if (qrContent != null) {
                Log.d("QR_SCAN", "QR Code scanned: " + qrContent);
                Toast.makeText(this, "QR Code scanned successfully!", Toast.LENGTH_SHORT).show();

                // Find the currently visible fragment and pass the result.
                NavHostFragment navHostFragment =
                        (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                if (navHostFragment != null && navHostFragment.getChildFragmentManager().getFragments().size() > 0) {
                    for (androidx.fragment.app.Fragment f : navHostFragment.getChildFragmentManager().getFragments()) {
                        if (f != null && f.isVisible()) {
                            if (f instanceof TraffickingCheckpointFragment) {
                                // This is the core logic: call the public method on the fragment.
                                ((TraffickingCheckpointFragment) f).processQrCode(qrContent);
                                Log.d("QR_SCAN", "QR code passed to TraffickingCheckpointFragment");
                            }
                            break;
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Scan QR annulé ou échoué.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String bytesToHexString(byte[] src) {
        if (src == null || src.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : src) sb.append(String.format("%02X", b));
        return sb.toString();
    }
}
