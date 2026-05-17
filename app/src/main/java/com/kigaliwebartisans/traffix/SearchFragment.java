package com.kigaliwebartisans.traffix;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SearchFragment extends Fragment {

    private static final String TAG = "SearchFragment";
    private ProgressBar progressBar;
    private TextView infoText;
    private TextView licenseNumberInput;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search_card, container, false);

        progressBar = view.findViewById(R.id.progress_bar);
        infoText = view.findViewById(R.id.info_text);
        licenseNumberInput = view.findViewById(R.id.input_license_number);
        Button searchButton = view.findViewById(R.id.button_search);

        if (searchButton != null) {
            searchButton.setOnClickListener(v -> {
                String licenseNumber = licenseNumberInput.getText().toString().trim();
                if (licenseNumber.isEmpty()) {
                    Toast.makeText(getContext(), "Veuillez entrer un numéro", Toast.LENGTH_SHORT).show();
                } else {
                    fetchCardData(licenseNumber);
                }
            });
        }

        return view;
    }

    /**
     * Public method called from MainActivity when an NFC tag is detected.
     */
    public void searchByNfcTag(String tagId) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (licenseNumberInput != null) {
                licenseNumberInput.setText(tagId);
                fetchCardData(tagId);
            }
        });
    }

    private void fetchCardData(String query) {
        if (progressBar == null || infoText == null) return;
        progressBar.setVisibility(View.VISIBLE);
        infoText.setText("Recherche de : " + query);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(ApiConstants.URL + "/print-card");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);

                JSONObject postData = new JSONObject();
                postData.put("query", query);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData.toString().getBytes(StandardCharsets.UTF_8));
                }

                int status = conn.getResponseCode();
                InputStream inputStream = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();

                StringBuilder response = new StringBuilder();
                int ch;
                while ((ch = inputStream.read()) != -1) {
                    response.append((char) ch);
                }

                JSONObject json = new JSONObject(response.toString());
                JSONArray driversArray = json.optJSONArray("drivers");
                if (driversArray == null || driversArray.length() == 0) {
                    throw new Exception("Aucun conducteur trouvé.");
                }

                JSONObject driverData = driversArray.getJSONObject(0);
                JSONObject driver = unwrap(driverData.optJSONObject("driver"));
                JSONObject license = unwrap(driverData.optJSONObject("license"));

                if (driver == null || license == null) {
                    throw new Exception("Format de données invalide.");
                }

                Bundle bundle = new Bundle();
                bundle.putString("name", driver.optString("name", "N/A"));
                bundle.putString("surName", driver.optString("surName", driver.optString("surname", "N/A")));
                bundle.putString("address", driver.optString("address", "N/A"));
                bundle.putString("nationalId", driver.optString("nationalId", "N/A"));
                bundle.putString("profileImagePath", driver.optString("profileImage", ""));
                bundle.putString("nationality", driver.optString("nationality", "N/A"));
                bundle.putString("dob", driver.optString("dateOfBirth", "N/A"));
                bundle.putString("licenseNumber", license.optString("licenseNumber", "N/A"));
                bundle.putString("issue", license.optString("issueDate", "N/A"));
                bundle.putString("expiry", license.optString("expiryDate", "N/A"));
                bundle.putString("dateLieuDelivrance", license.optString("dateLieuDelivrance", "N/A"));
                
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    infoText.setText("Carte trouvée.");
                    PrintCardFragment printCardFragment = new PrintCardFragment();
                    printCardFragment.setArguments(bundle);
                    requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.nav_host_fragment, printCardFragment)
                        .addToBackStack(null)
                        .commit();
                });

            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (infoText != null) infoText.setText("Erreur : " + e.getMessage());
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private JSONObject unwrap(JSONObject obj) {
        if (obj == null) return null;
        String[] keys = {"App\\Models\\Driver", "App\\Models\\License", "App\\Models\\Card"};
        for (String key : keys) {
            if (obj.has(key)) return obj.optJSONObject(key);
        }
        return obj;
    }
}
