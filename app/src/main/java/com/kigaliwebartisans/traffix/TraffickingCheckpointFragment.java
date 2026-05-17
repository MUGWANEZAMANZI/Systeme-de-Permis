package com.kigaliwebartisans.traffix;

import static com.kigaliwebartisans.traffix.MainActivity.QR_SCAN_REQUEST_CODE;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class TraffickingCheckpointFragment extends Fragment {
    private static final String TAG = "CheckpointFragment";
    private EditText inputEdit;
    private Button searchButton, nfcButton, qrButton;
    private FloatingActionButton addPenaltyButton;
    private ProgressBar progressBar;
    private LinearLayout resultBox;
    private TextView penaltiesTitle;
    private ProgressBar penaltiesLoading;
    private LinearLayout penaltiesList;
    private ImageView resultImage;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_trafficking_checkpoint, container, false);
        
        inputEdit = view.findViewById(R.id.input_edit);
        searchButton = view.findViewById(R.id.button_search);
        nfcButton = view.findViewById(R.id.button_nfc);
        qrButton = view.findViewById(R.id.button_qr);
        progressBar = view.findViewById(R.id.progress_bar);
        resultBox = view.findViewById(R.id.result_box);
        penaltiesTitle = view.findViewById(R.id.penalties_title);
        penaltiesLoading = view.findViewById(R.id.penalties_loading);
        penaltiesList = view.findViewById(R.id.penalties_list);
        addPenaltyButton = view.findViewById(R.id.button_add_penalty);
        resultImage = view.findViewById(R.id.result_image);

        if (addPenaltyButton != null) {
            addPenaltyButton.setOnClickListener(v -> showPenaltyDropdown());
        }

        if (resultBox != null) {
            resultBox.setOnClickListener(v -> showDriverOptionsDialog());
        }

        if (searchButton != null) searchButton.setOnClickListener(v -> handleSearch());
        if (nfcButton != null) nfcButton.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Approchez une carte NFC...", Toast.LENGTH_SHORT).show();
        });
        if (qrButton != null) qrButton.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Veuillez utiliser le lecteur QR code de votre application", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    public void searchByNfcTag(String tagId) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (inputEdit != null) inputEdit.setText(tagId);
            performNetworkSearch(ApiConstants.URL + "/driver-by-card/" + tagId);
        });
    }

    private void handleSearch() {
        String input = inputEdit.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            Toast.makeText(getContext(), "Entrez un numéro de permis ou plaque", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String encodedInput = URLEncoder.encode(input, "UTF-8");
            performNetworkSearch(ApiConstants.URL + "/penalties-search?query=" + encodedInput);
        } catch (Exception e) {
            Log.e(TAG, "Encoding error", e);
        }
    }

    private void performNetworkSearch(String urlString) {
        progressBar.setVisibility(View.VISIBLE);
        resultBox.setVisibility(View.GONE);
        penaltiesList.removeAllViews();
        penaltiesTitle.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                
                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode == 200) ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                String rawResponse = sb.toString();
                Log.d(TAG, "Raw Response: " + rawResponse);

                getActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (responseCode == 200) {
                        displayDriverData(rawResponse);
                    } else {
                        Toast.makeText(getContext(), "Aucun conducteur trouvé", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                getActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Erreur réseau: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void displayDriverData(String jsonString) {
        try {
            JSONObject json = new JSONObject(jsonString);
            
            // Handle Laravel's nested model structure if present
            JSONObject driver = json.optJSONObject("driver");
            if (driver != null && driver.has("App\\Models\\Driver")) {
                driver = driver.getJSONObject("App\\Models\\Driver");
            } else if (driver == null) {
                driver = json;
            }

            JSONObject license = json.optJSONObject("license");
            if (license != null && license.has("App\\Models\\License")) {
                license = license.getJSONObject("App\\Models\\License");
            } else if (license == null) {
                license = json;
            }

            JSONArray penaltiesArray = json.optJSONArray("penalties");

            resultBox.setVisibility(View.VISIBLE);
            
            String firstName = driver.optString("name", "N/A");
            String surName = driver.optString("surName", driver.optString("surname", ""));
            ((TextView) resultBox.findViewById(R.id.result_name)).setText("Nom: " + firstName + " " + surName);
            
            String licenseNum = license.optString("licenseNumber", driver.optString("licenseId", "N/A"));
            ((TextView) resultBox.findViewById(R.id.result_license)).setText("Permis: " + licenseNum);
            
            String plateNum = license.optString("plateNumber", driver.optString("plate", "N/A"));
            ((TextView) resultBox.findViewById(R.id.result_plate)).setText("Plaque: " + plateNum);
            
            ((TextView) resultBox.findViewById(R.id.result_nationalid)).setText("ID National: " + driver.optString("nationalId", "N/A"));

            // Load driver image
            String imagePath = driver.optString("profileImage", "");
            if (resultImage != null && !imagePath.isEmpty()) {
                Glide.with(this)
                        .load(ApiConstants.STORAGE_URL + imagePath)
                        .placeholder(android.R.drawable.ic_menu_report_image)
                        .error(android.R.drawable.ic_menu_report_image)
                        .into(resultImage);
            } else if (resultImage != null) {
                resultImage.setImageResource(android.R.drawable.ic_menu_report_image);
            }

            // Optional fields
            setTextIfAvailable(resultBox, R.id.result_bloodgroup, "Groupe sanguin: " + driver.optString("bloodGroup", "N/A"));
            setTextIfAvailable(resultBox, R.id.result_issue, "Délivré le: " + license.optString("issueDate", "N/A"));
            setTextIfAvailable(resultBox, R.id.result_expiry, "Expire le: " + license.optString("expiryDate", "N/A"));

            penaltiesTitle.setVisibility(View.VISIBLE);
            if (penaltiesArray != null && penaltiesArray.length() > 0) {
                for (int i = 0; i < penaltiesArray.length(); i++) {
                    JSONObject item = penaltiesArray.getJSONObject(i);
                    JSONObject pObj = item.optJSONObject("penalty");
                    if (pObj == null) pObj = item; // Fallback

                    TextView tv = new TextView(getContext());
                    String type = pObj.optString("penaltyType", pObj.optString("penalty", "Inconnu"));
                    String amount = pObj.optString("amount", pObj.optString("fine", "0"));
                    tv.setText("• " + type + " (" + amount + " FC)");
                    tv.setTextColor(0xFFFF0000);
                    penaltiesList.addView(tv);
                }
            } else {
                TextView tv = new TextView(getContext());
                tv.setText("Aucune pénalité active.");
                penaltiesList.addView(tv);
            }
        } catch (Exception e) {
            Log.e(TAG, "JSON parse error", e);
            Toast.makeText(getContext(), "Erreur d'affichage des données", Toast.LENGTH_SHORT).show();
        }
    }

    private void setTextIfAvailable(View root, int id, String text) {
        View v = root.findViewById(id);
        if (v instanceof TextView) ((TextView) v).setText(text);
    }

    private void showDriverOptionsDialog() {
        String[] options = {"Ajouter une pénalité", "Signaler comme fraude"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Options du Conducteur")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showPenaltyDropdown();
                    else Toast.makeText(getContext(), "Signalé comme fraude", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showPenaltyDropdown() {
        String[] penalties = {"Excès de vitesse", "Mauvais stationnement", "Pas de ceinture", "Téléphone au volant", "Feu rouge", "Alcool", "Autre"};
        final int[] selected = {0};
        
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 0);
        
        final EditText fineEdit = new EditText(getContext());
        fineEdit.setHint("Montant (FC)");
        fineEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(fineEdit);

        new AlertDialog.Builder(requireContext())
                .setTitle("Sélectionner l'infraction")
                .setSingleChoiceItems(penalties, 0, (dialog, which) -> selected[0] = which)
                .setView(layout)
                .setPositiveButton("Valider", (dialog, which) -> {
                    String fine = fineEdit.getText().toString().trim();
                    if (!fine.isEmpty()) {
                        TextView plateView = resultBox.findViewById(R.id.result_plate);
                        String plateStr = plateView.getText().toString();
                        String plate = plateStr.substring(plateStr.indexOf(":") + 1).trim();
                        sendPenalty(plate, penalties[selected[0]], fine);
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void sendPenalty(String plate, String type, String amount) {
        new Thread(() -> {
            try {
                URL url = new URL(ApiConstants.URL + "/add-penalty");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("plateNumber", plate);
                body.put("penalty", type);
                body.put("fine", amount);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                os.close();

                int code = conn.getResponseCode();
                getActivity().runOnUiThread(() -> {
                    if (code == code) {
                        Toast.makeText(getContext(), "Pénalité ajoutée avec succès", Toast.LENGTH_SHORT).show();
                        handleSearch();
                    } else {
                        Toast.makeText(getContext(), "Échec de l'ajout", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Penalty error", e);
            }
        }).start();
    }

    public void processQrCode(String content) {
        if (inputEdit != null) {
            inputEdit.setText(content);
            handleSearch();
        }
    }
}