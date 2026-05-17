package com.kigaliwebartisans.traffix;

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
    private Button searchButton, nfcButton;
    private FloatingActionButton addPenaltyButton;
    private ProgressBar progressBar;
    private LinearLayout resultBox;
    private TextView penaltiesTitle;
    private LinearLayout penaltiesList;
    private ImageView resultImage;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_trafficking_checkpoint, container, false);
        
        inputEdit = view.findViewById(R.id.input_edit);
        searchButton = view.findViewById(R.id.button_search);
        nfcButton = view.findViewById(R.id.button_nfc);
        progressBar = view.findViewById(R.id.progress_bar);
        resultBox = view.findViewById(R.id.result_box);
        penaltiesTitle = view.findViewById(R.id.penalties_title);
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

        return view;
    }

    public void searchByNfcTag(String tagId) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
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
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                
                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                String rawResponse = sb.toString();
                Log.d(TAG, "Search Response: " + rawResponse);

                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (responseCode == 200) {
                        displayDriverData(rawResponse);
                    } else {
                        Toast.makeText(getContext(), "Aucun conducteur trouvé", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Erreur réseau: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void displayDriverData(String jsonString) {
        try {
            JSONObject json = new JSONObject(jsonString);
            JSONObject driverData = json;

            // Handle structure with 'drivers' array (from driver-by-card)
            if (json.has("drivers")) {
                JSONArray driversArray = json.getJSONArray("drivers");
                if (driversArray.length() > 0) driverData = driversArray.getJSONObject(0);
                else throw new Exception("Liste de conducteurs vide.");
            }

            // Unwrap Laravel model wrappers if present
            JSONObject driver = unwrap(driverData.optJSONObject("driver"));
            JSONObject license = unwrap(driverData.optJSONObject("license"));
            JSONArray penaltiesArray = driverData.optJSONArray("penalties");

            if (driver == null || license == null) {
                throw new Exception("Données du serveur incomplètes.");
            }

            resultBox.setVisibility(View.VISIBLE);
            
            String fullName = driver.optString("name", "N/A") + " " + driver.optString("surName", driver.optString("surname", ""));
            ((TextView) resultBox.findViewById(R.id.result_name)).setText("Nom: " + fullName.trim());
            ((TextView) resultBox.findViewById(R.id.result_license)).setText("Permis: " + license.optString("licenseNumber", "N/A"));
            ((TextView) resultBox.findViewById(R.id.result_plate)).setText("Plaque: " + license.optString("plateNumber", "N/A"));
            ((TextView) resultBox.findViewById(R.id.result_nationalid)).setText("ID National: " + driver.optString("nationalId", "N/A"));

            // Fix: Load driver image correctly
            String imagePath = driver.optString("profileImage", "");
            if (resultImage != null) {
                if (!imagePath.isEmpty()) {
                    Glide.with(requireContext())
                            .load(ApiConstants.STORAGE_URL + imagePath)
                            .placeholder(android.R.drawable.ic_menu_report_image)
                            .error(android.R.drawable.ic_menu_report_image)
                            .into(resultImage);
                } else {
                    resultImage.setImageResource(android.R.drawable.ic_menu_report_image);
                }
            }

            penaltiesTitle.setVisibility(View.VISIBLE);
            if (penaltiesArray != null && penaltiesArray.length() > 0) {
                for (int i = 0; i < penaltiesArray.length(); i++) {
                    JSONObject item = penaltiesArray.getJSONObject(i);
                    JSONObject pObj = unwrap(item.optJSONObject("penalty"));
                    if (pObj == null) pObj = item;

                    TextView tv = new TextView(getContext());
                    String type = pObj.optString("penaltyType", "Inconnu");
                    String amount = pObj.optString("amount", "0");
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
            Log.e(TAG, "Parse error: ", e);
            Toast.makeText(getContext(), "Erreur d'affichage : " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private JSONObject unwrap(JSONObject obj) {
        if (obj == null) return null;
        String[] keys = {"App\\Models\\Driver", "App\\Models\\License", "App\\Models\\Card", "App\\Models\\Penalty"};
        for (String key : keys) {
            if (obj.has(key)) return obj.optJSONObject(key);
        }
        return obj;
    }

    private void showDriverOptionsDialog() {
        String[] options = {"Ajouter une pénalité", "Signaler comme fraude"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Options du Conducteur")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showPenaltyDropdown();
                }).show();
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
                .setNegativeButton("Annuler", null).show();
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
                requireActivity().runOnUiThread(() -> {
                    if (code == 201 || code == 200) {
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
}