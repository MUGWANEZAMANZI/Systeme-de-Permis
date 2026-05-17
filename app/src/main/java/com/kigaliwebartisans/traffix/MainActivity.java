package com.kigaliwebartisans.traffix;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    public static final int QR_SCAN_REQUEST_CODE = 49374;

    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private NavController navController;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkAndRequestPermissions();

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0;
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavigationUI.setupWithNavController(bottomNav, navController);

        handleIntent(getIntent());
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag != null) {
            String tagId = bytesToHexString(tag.getId());
            Log.d(TAG, "NFC Detected: " + tagId);
            Toast.makeText(this, "Carte NFC détectée: " + tagId, Toast.LENGTH_SHORT).show();
            dispatchTagToVisibleFragment(tagId);
        }
    }

    private void checkAndRequestPermissions() {
        List<String> listPermissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.CAMERA);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    private void checkNfcStatus() {
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC non supporté", Toast.LENGTH_LONG).show();
        } else if (!nfcAdapter.isEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("NFC Désactivé")
                    .setMessage("Veuillez activer le NFC pour scanner les cartes.")
                    .setPositiveButton("Paramètres", (dialog, which) -> startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)))
                    .setNegativeButton("Annuler", null)
                    .show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkNfcStatus();
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void dispatchTagToVisibleFragment(String tagId) {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            List<Fragment> fragments = navHostFragment.getChildFragmentManager().getFragments();
            for (Fragment f : fragments) {
                if (f != null && f.isVisible()) {
                    if (f instanceof RegisterDriverFragment) {
                        ((RegisterDriverFragment) f).setNfcTag(tagId);
                    } else if (f instanceof TraffickingCheckpointFragment) {
                        ((TraffickingCheckpointFragment) f).searchByNfcTag(tagId);
                    } else if (f instanceof SearchFrament) {
                        ((SearchFrament) f).searchByNfcTag(tagId);
                    } else if (f instanceof PrintCardFragment) {
                        ((PrintCardFragment) f).searchByNfcTag(tagId);
                    }
                }
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