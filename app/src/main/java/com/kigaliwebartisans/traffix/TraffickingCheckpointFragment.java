package com.kigaliwebartisans.traffix;

import static com.kigaliwebartisans.traffix.MainActivity.QR_SCAN_REQUEST_CODE;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;

import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;


public class TraffickingCheckpointFragment extends Fragment {
    private EditText inputEdit;
    private Button searchButton, nfcButton, qrButton;
    private FloatingActionButton addPenaltyButton;
    private ProgressBar progressBar;
    private LinearLayout resultBox;
    private TextView penaltiesTitle;
    private ProgressBar penaltiesLoading;
    private LinearLayout penaltiesList;
    private LinearLayout addPenaltyDialog;

    // NFC reading logic
    public void processNfcTag(android.nfc.Tag tag) {
        if (tag == null) {
            Toast.makeText(getContext(), "Tag NFC non détecté.", Toast.LENGTH_SHORT).show();
            return;
        }
        // Try NDEF first
        android.nfc.tech.Ndef ndef = android.nfc.tech.Ndef.get(tag);
        if (ndef != null) {
            try {
                ndef.connect();
                android.nfc.NdefMessage ndefMessage = ndef.getNdefMessage();
                if (ndefMessage != null && ndefMessage.getRecords().length > 0) {
                    String text = getTextFromNdefRecord(ndefMessage.getRecords()[0]);
                    if (text != null && !text.isEmpty()) {
                        searchByNfcTag(text);
                        ndef.close();
                        return;
                    }
                }
                ndef.close();
            } catch (Exception e) {
                // fallback below
            }
        }
        // Try NdefFormattable (for blank tags)
        android.nfc.tech.NdefFormatable ndefFormattable = android.nfc.tech.NdefFormatable.get(tag);
        if (ndefFormattable != null) {
            try {
                ndefFormattable.connect();
                // Just get tag ID as fallback (formatting requires writing, not reading)
                String tagId = bytesToHexString(tag.getId());
                searchByNfcTag(tagId);
                ndefFormattable.close();
                return;
            } catch (Exception e) {
                // fallback below
            }
        }
        // Fallback: use tag ID
        String tagId = bytesToHexString(tag.getId());
        searchByNfcTag(tagId);
    }

    // Helper to extract text from NDEF record (well-known type)
    private String getTextFromNdefRecord(android.nfc.NdefRecord record) {
        try {
            byte[] payload = record.getPayload();
            String textEncoding = ((payload[0] & 0x80) == 0) ? "UTF-8" : "UTF-16";
            int languageCodeLength = payload[0] & 0x3F;
            return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
        } catch (Exception e) {
            return null;
        }
    }

    // Helper to convert byte array to hex string
    private String bytesToHexString(byte[] src) {
        if (src == null || src.length == 0) return "";
        char[] hexChars = new char[src.length * 2];
        for (int j = 0; j < src.length; j++) {
            int v = src[j] & 0xFF;
            hexChars[j * 2] = "0123456789ABCDEF".charAt(v >>> 4);
            hexChars[j * 2 + 1] = "0123456789ABCDEF".charAt(v & 0x0F);
        }
        return new String(hexChars);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view;
        try {
            view = inflater.inflate(R.layout.fragment_trafficking_checkpoint, container, false);
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
            addPenaltyDialog = view.findViewById(R.id.add_penalty_dialog);

            // Hide the dialog by default
            if (addPenaltyDialog != null) {
                addPenaltyDialog.setVisibility(View.GONE);
            }

            // Show dialog when 'Nouveau' is clicked
            if (addPenaltyButton != null && addPenaltyDialog != null) {
                addPenaltyButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        addPenaltyDialog.setVisibility(View.VISIBLE);
                    }
                });
            }

            // Optionally, add a close button inside the dialog (if not present, you can add it in XML)
            Button closePenaltyDialogButton = view.findViewById(R.id.button_close_penalty_dialog);
            if (closePenaltyDialogButton != null && addPenaltyDialog != null) {
                closePenaltyDialogButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        addPenaltyDialog.setVisibility(View.GONE);
                    }
                });
            }

            if (resultBox != null) {
                resultBox.setOnClickListener(v -> {
                    try {
                        showDriverOptionsDialog();
                    } catch (Exception e) {
                        showErrorToast("Erreur lors de l'affichage des options.");
                    }
                });
            }

            if (searchButton != null) searchButton.setOnClickListener(v -> safeHandleSearch());
            if (nfcButton != null) nfcButton.setOnClickListener(v -> safeHandleNfcScan());
            if (qrButton != null) qrButton.setOnClickListener(v -> safeHandleQrScan());
        } catch (Exception e) {
            showErrorToast("Erreur d'initialisation de la vue.");
            return null;
        }
        return view;
    }

    private void showErrorToast(String message) {
        Context ctx = getContext();
        if (ctx != null) {
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void safeHandleSearch() {
        try {
            handleSearch();
        } catch (Exception e) {
            showErrorToast("Erreur lors de la recherche.");
        }
    }

    private void safeHandleNfcScan() {
        try {
            handleNfcScan();
        } catch (Exception e) {
            showErrorToast("Erreur lors du scan NFC.");
        }
    }

    private void safeHandleQrScan() {
        try {
            handleQrScan();
        } catch (Exception e) {
            showErrorToast("Erreur lors du scan QR.");
        }
    }

    // Show dialog with options for punishments, reporting fraud, etc.
    private void showDriverOptionsDialog() {
        String[] options = {"Ajouter une pénalité", "Signaler la carte comme fraude", "Autres actions"};
        new AlertDialog.Builder(getContext())
                .setTitle("Actions sur le conducteur")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showPenaltyDropdown();
                            break;
                        case 1:
                            Toast.makeText(getContext(), "Carte signalée comme fraude", Toast.LENGTH_SHORT).show();
                            break;
                        case 2:
                            Toast.makeText(getContext(), "Autres actions", Toast.LENGTH_SHORT).show();
                            break;
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }



    // Show a styled dropdown for selecting a penalty and entering a fine
    private void showPenaltyDropdown() {
        String[] penalties = {
                "Excès de vitesse (Speeding)",
                "Mauvais stationnement (Bad parking)",
                "Non présentation d'assurance (No insurance)",
                "Non-port de la ceinture de sécurité (No seatbelt)",
                "Téléphone au volant (Phone while driving)",
                "Non-respect du feu rouge (Red light violation)",
                "Conduite en état d'ivresse (DUI)",
                "Non-présentation du permis (No license)",
                "Plaque illisible ou manquante (No/Unreadable plate)",
                "Vitesse excessive en zone scolaire (Speeding in school zone)",
                "Refus d'obtempérer (Failure to comply)",
                "Conduite sans assurance (Driving without insurance)",
                "Non-respect de la priorité (Failure to yield)",
                "Défaut d'éclairage (Lights not working)",
                "Conduite dangereuse (Dangerous driving)",
                "Transport illégal de passagers (Illegal passenger transport)",
                "Non-respect de la distance de sécurité (Tailgating)",
                "Excès de bruit (Noise violation)",
                "Pollution (Pollution)",
                "Autre (Other)"
        };
        final int[] selected = {0};
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 0);

        final EditText fineEdit = new EditText(getContext());
        fineEdit.setHint("Montant de l'amende (FCFA)");
        fineEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(fineEdit);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Sélectionner une infraction");
        builder.setSingleChoiceItems(penalties, 0, (dialog, which) -> selected[0] = which);
        builder.setView(layout);
        builder.setPositiveButton("Valider", (dialog, which) -> {
            String penalty = penalties[selected[0]];
            String fine = fineEdit.getText().toString().trim();
            if (fine.isEmpty()) {
                Toast.makeText(getContext(), "Veuillez entrer le montant de l'amende.", Toast.LENGTH_SHORT).show();
                return;
            }
            // Get the plate number from the last search result if possible
            String plateNumber = "";
            if (resultBox != null) {
                TextView plateView = resultBox.findViewById(R.id.result_plate);
                if (plateView != null) {
                    String plateText = plateView.getText().toString();
                    if (plateText.contains(":")) {
                        plateNumber = plateText.substring(plateText.indexOf(":") + 1).trim();
                    } else {
                        plateNumber = plateText.trim();
                    }
                }
            }
            if (plateNumber.isEmpty()) {
                Toast.makeText(getContext(), "Aucune plaque trouvée dans les résultats.", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(getContext(), "Envoi de la pénalité...", Toast.LENGTH_SHORT).show();
            sendPenaltyToBackend(plateNumber, penalty, fine);
        });
        builder.setNegativeButton("Annuler", null);
        builder.show();
    }

    private void sendPenaltyToBackend(String plateNumber, String penalty, String fine) {
        // Show a loading dialog
        Activity activity = getActivity();
        if (activity == null) {
            Toast.makeText(getContext(), "Erreur : activité non disponible.", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog loadingDialog = new AlertDialog.Builder(activity)
                .setMessage("Ajout de la pénalité...")
                .setCancelable(false)
                .create();
        loadingDialog.show();

        new Thread(() -> {
            boolean success = false;
            String errorMsg = null;
            try {
                java.net.URL url = new java.net.URL("https://traffic.up.railway.app/api/add-penalty");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                org.json.JSONObject body = new org.json.JSONObject();
                body.put("plateNumber", plateNumber);
                body.put("penalty", penalty);
                body.put("fine", Integer.parseInt(fine));
                java.io.OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();
                int responseCode = conn.getResponseCode();
                if (responseCode == 200 || responseCode == 201) {
                    success = true;
                } else {
                    java.io.InputStream err = conn.getErrorStream();
                    if (err != null) {
                        java.util.Scanner s = new java.util.Scanner(err).useDelimiter("\\A");
                        errorMsg = s.hasNext() ? s.next() : "Erreur inconnue";
                        err.close();
                    } else {
                        errorMsg = "Erreur inconnue (code: " + responseCode + ")";
                    }
                }
            } catch (Exception e) {
                errorMsg = e.getMessage();
            }
            boolean finalSuccess = success;
            String finalErrorMsg = errorMsg;
            activity.runOnUiThread(() -> {
                loadingDialog.dismiss();
                if (finalSuccess) {
                    Toast.makeText(getContext(), "Pénalité ajoutée avec succès.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getContext(), "Erreur lors de l'ajout de la pénalité :\n" + (finalErrorMsg != null ? finalErrorMsg : "Erreur inconnue"), Toast.LENGTH_LONG).show();
                }
                AlertDialog.Builder resultDialog = new AlertDialog.Builder(activity);
                if (finalSuccess) {
                    resultDialog.setTitle("Succès")
                            .setMessage("Pénalité ajoutée avec succès.")
                            .setPositiveButton("OK", null)
                            .show();
                } else {
                    resultDialog.setTitle("Erreur")
                            .setMessage("Échec de l'ajout de la pénalité :\n" + (finalErrorMsg != null ? finalErrorMsg : "Erreur inconnue"))
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }).start();
    }

    private void handleSearch() {
        if (inputEdit == null) {
            showErrorToast("Champ de recherche introuvable.");
            return;
        }
        String input = inputEdit.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            showErrorToast("Veuillez entrer un numéro de permis ou une plaque.");
            return;
        }
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (resultBox != null) resultBox.setVisibility(View.GONE);
        if (penaltiesList != null) penaltiesList.removeAllViews();
        if (penaltiesTitle != null) penaltiesTitle.setVisibility(View.GONE);
        if (penaltiesLoading != null) penaltiesLoading.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                String apiUrl = "https://traffic.up.railway.app/api/penalties-search?query=" + java.net.URLEncoder.encode(input, "UTF-8");
                java.net.URL url = new java.net.URL(apiUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    java.io.InputStream is = conn.getInputStream();
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    is.close();
                    JSONObject json = null;
                    try {
                        json = new JSONObject(sb.toString());
                    } catch (Exception je) {
                        postErrorToUi("Erreur de format de réponse serveur.");
                        return;
                    }

                    // Parse driver and penalty data from the new JSON structure
                    JSONObject driver = json.optJSONObject("driver");
                    JSONArray penaltiesArray = json.optJSONArray("penalties");

                    if (driver == null) {
                        postErrorToUi("Aucun conducteur trouvé pour cette recherche.");
                        return;
                    }

                    String name = driver.optString("name", "N/A");
                    String surName = driver.optString("surName", "N/A");
                    String nationalId = driver.optString("nationalId", "N/A");

                    // Update UI on main thread
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            try {
                                if (progressBar != null) progressBar.setVisibility(View.GONE);
                                if (resultBox != null) resultBox.setVisibility(View.VISIBLE);
                                if (penaltiesTitle != null) penaltiesTitle.setVisibility(View.VISIBLE);
                                if (penaltiesLoading != null) penaltiesLoading.setVisibility(View.GONE);
                                if (resultBox != null) {
                                    ((TextView) resultBox.findViewById(R.id.result_name)).setText("Nom: " + name + " " + surName);
                                    ((TextView) resultBox.findViewById(R.id.result_nationalid)).setText("ID National: " + nationalId);
                                    // You may need to fetch and populate other fields from the new JSON structure if they exist in your UI
                                }

                                // Populate penalties list
                                penaltiesList.removeAllViews(); // Clear previous penalties
                                if (penaltiesArray != null && penaltiesArray.length() > 0) {
                                    for (int i = 0; i < penaltiesArray.length(); i++) {
                                        JSONObject penaltyItem = penaltiesArray.getJSONObject(i);
                                        JSONObject penaltyDetails = penaltyItem.optJSONObject("penalty");
                                        if (penaltyDetails != null) {
                                            String penaltyType = penaltyDetails.optString("penaltyType", "N/A");
                                            double amount = penaltyDetails.optDouble("amount", 0.0);
                                            String penaltyText = "Infraction: " + penaltyType + " - " + amount + " FC";
                                            TextView penaltyView = new TextView(getContext());
                                            penaltyView.setText(penaltyText);
                                            penaltyView.setTextColor(android.graphics.Color.RED);
                                            penaltyView.setTextSize(16);
                                            penaltiesList.addView(penaltyView);
                                        }
                                    }
                                } else {
                                    // Display a message for no penalties
                                    TextView noPenaltiesView = new TextView(getContext());
                                    noPenaltiesView.setText("Aucune pénalité trouvée.");
                                    noPenaltiesView.setTextSize(16);
                                    penaltiesList.addView(noPenaltiesView);
                                }
                            } catch (Exception e) {
                                showErrorToast("Erreur lors de l'affichage des résultats.");
                            }
                        });
                    }
                } else {
                    postErrorToUi("Aucun conducteur trouvé.");
                }
            } catch (Exception e) {
                postErrorToUi("Erreur réseau ou serveur.");
            }
        }).start();
    }

    private void postErrorToUi(String message) {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> showErrorToast(message));
        }
    }


    private void handleNfcScan() {
        Toast.makeText(getContext(), "Approchez une carte NFC pour scanner.", Toast.LENGTH_SHORT).show();
        // The actual tag reading is triggered from MainActivity/onNewIntent, which should call processNfcTag(tag)
    }

    // Called from MainActivity when an NFC tag is detected
    public void searchByNfcTag(String tag) {
        // Use the backend endpoint to fetch driver by card number
        progressBar.setVisibility(View.VISIBLE);
        resultBox.setVisibility(View.GONE);
        penaltiesList.removeAllViews();
        penaltiesTitle.setVisibility(View.GONE);
        penaltiesLoading.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                String apiUrl = "https://traffic.up.railway.app/api/driver-by-card/" + java.net.URLEncoder.encode(tag, "UTF-8");
                java.net.URL url = new java.net.URL(apiUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    java.io.InputStream is = conn.getInputStream();
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    is.close();
                    org.json.JSONObject json = new org.json.JSONObject(sb.toString());
                    // Parse driver data
                    String name = json.optString("name", "");
                    String surName = json.optString("surName", "");
                    String licenseId = json.optString("licenseId", "");
                    String plate = json.optString("plate", "");
                    String bloodGroup = json.optString("bloodGroup", "");
                    String issue = json.optString("issue", "");
                    String expiry = json.optString("expiry", "");
                    String nationalId = json.optString("nationalId", "");
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        resultBox.setVisibility(View.VISIBLE);
                        penaltiesTitle.setVisibility(View.VISIBLE);
                        penaltiesLoading.setVisibility(View.GONE);
                        ((TextView) resultBox.findViewById(R.id.result_name)).setText("Nom: " + name + " " + surName);
                        ((TextView) resultBox.findViewById(R.id.result_license)).setText("Permis: " + licenseId);
                        ((TextView) resultBox.findViewById(R.id.result_plate)).setText("Plaque: " + plate);
                        ((TextView) resultBox.findViewById(R.id.result_bloodgroup)).setText("Groupe sanguin: " + bloodGroup);
                        ((TextView) resultBox.findViewById(R.id.result_issue)).setText("Date d'émission: " + issue);
                        ((TextView) resultBox.findViewById(R.id.result_expiry)).setText("Date d'expiration: " + expiry);
                        ((TextView) resultBox.findViewById(R.id.result_nationalid)).setText("ID National: " + nationalId);
                    });
                } else {
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "Aucun conducteur trouvé pour cette carte.", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                getActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Erreur réseau ou serveur.", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * Public method to process a QR code string from an external source.
     * Call this from your app's built-in QR code reader's success callback.
     * @param qrContent The string content read from the QR code.
     */
    public void processQrCode(String qrContent) {
        if (qrContent != null && !qrContent.isEmpty()) {
            inputEdit.setText(qrContent);
            handleSearch();
        } else {
            showErrorToast("Scan QR annulé ou échoué.");
        }
    }

    private void handleQrScan() {
        Toast.makeText(getContext(), "Veuillez utiliser le lecteur QR code de votre application pour scanner.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == QR_SCAN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                String qrContent = data.getStringExtra("SCAN_RESULT");
                if (qrContent != null) {
                    inputEdit.setText(qrContent);
                    handleSearch();
                }
            } else {
                Toast.makeText(getContext(), "Scan QR annulé ou échoué.", Toast.LENGTH_SHORT).show();
            }
        }

    }
}
