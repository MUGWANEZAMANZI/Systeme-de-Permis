package com.kigaliwebartisans.traffix;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public class PrintCardFragment extends Fragment {
    private static final int REQUEST_WRITE_STORAGE = 101;

    private ProgressBar progressBar;
    private TextView infoText;
    private View searchContainer;
    private View cardsContainer;
    private View frontCard;
    private View backCard;

    // Card UI Elements - Declared here for wider access
    private TextView cardName, cardSurname, cardDob, cardNationality, cardNationalId, cardAddress, cardIssue, cardExpiry, lieuDateDeLivraison, carNumber;
    private ImageView cardProfile, qrCodeBack;
    private TextView licenseNumberBack;
    private LinearLayout categoryRow;
    private LinearLayout vehicleCategoriesTable;
    private Button saveButton;
    private TextView licenseNumberInput;
    private Button searchButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_print_card, container, false);

        // ✅ 1. Initialize all views from the layout
        progressBar = view.findViewById(R.id.progress_bar);
        infoText = view.findViewById(R.id.info_text);
        searchContainer = view.findViewById(R.id.search_container);
        cardsContainer = view.findViewById(R.id.cards_container);
        frontCard = view.findViewById(R.id.front_card);
        saveButton = view.findViewById(R.id.save_button);
        backCard = view.findViewById(R.id.back_card);
        licenseNumberInput = view.findViewById(R.id.input_license_number);
        searchButton = view.findViewById(R.id.button_search);

        // Bind card-specific views
        cardName = view.findViewById(R.id.card_name);
        cardSurname = view.findViewById(R.id.card_surname);
        cardDob = view.findViewById(R.id.card_dob);
        cardNationality = view.findViewById(R.id.card_nationality);
        cardNationalId = view.findViewById(R.id.card_nationalId);
        cardAddress = view.findViewById(R.id.card_address);
        cardIssue = view.findViewById(R.id.card_issue);
        cardExpiry = view.findViewById(R.id.card_expiry);
        cardProfile = view.findViewById(R.id.card_profile);
        qrCodeBack = view.findViewById(R.id.qr_code);
        licenseNumberBack = view.findViewById(R.id.license_number_back);
        categoryRow = view.findViewById(R.id.category_holders_row);
        vehicleCategoriesTable = view.findViewById(R.id.vehicle_categories_table);

        lieuDateDeLivraison = view.findViewById(R.id.lieu_date_de_livraison);
        carNumber = view.findViewById(R.id.card_number);

        // ✅ 2. Set up initial UI state
        searchContainer.setVisibility(View.VISIBLE);
        cardsContainer.setVisibility(View.GONE);
        infoText.setText("Enter a license number to search.");

        // ✅ 3. Set up listeners
        searchButton.setOnClickListener(v -> {
            String licenseNumber = licenseNumberInput.getText().toString().trim();
            if (licenseNumber.isEmpty()) {
                Toast.makeText(getContext(), "Please enter a license number", Toast.LENGTH_SHORT).show();
            } else {
                fetchCardData(licenseNumber);
            }
        });

        saveButton.setOnClickListener(v -> {
            if (frontCard == null || backCard == null) {
                Toast.makeText(getContext(), "Carte non trouvée", Toast.LENGTH_SHORT).show();
                return;
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
                    Toast.makeText(getContext(), "Autorisation requise pour enregistrer l'image", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // Get driver's first name for file naming
            String firstName = "driver";
            if (cardSurname != null && cardSurname.getText() != null) {
                String text = cardSurname.getText().toString();
                // Expecting format: "Prénom: John"
                int idx = text.indexOf(":");
                if (idx != -1 && text.length() > idx + 1) {
                    firstName = text.substring(idx + 1).trim();
                }
            }
            long timestamp = System.currentTimeMillis();
            saveCardAsPng(frontCard, firstName + "_front_" + timestamp + ".png");
            saveCardAsPng(backCard, firstName + "_back_" + timestamp + ".png");
        });

        return view;
    }

    private void fetchCardData(String query) {
        progressBar.setVisibility(View.VISIBLE);
        infoText.setText("Searching for: " + query);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://traffic.up.railway.app/api/print-card");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);

                JSONObject postData = new JSONObject();
                postData.put("query", query);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData.toString().getBytes("utf-8"));
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
                    throw new Exception("No driver data found for this query.");
                }

                JSONObject driverData = driversArray.getJSONObject(0);
                JSONObject driver = driverData.getJSONObject("driver");
                JSONObject license = driverData.getJSONObject("license");
                JSONObject card = driverData.getJSONObject("card");


                // Get allowed categories
                Set<String> allowedSet = new HashSet<>();
                String allowedCategoriesStr = null;
                if (!license.isNull("allowedCategories")) {
                    allowedCategoriesStr = license.optString("allowedCategories", null);
                } else if (!driverData.isNull("allowedCategories")) {
                    allowedCategoriesStr = driverData.optString("allowedCategories", null);
                }
                if (allowedCategoriesStr != null && !allowedCategoriesStr.trim().isEmpty()) {
                    String[] split = allowedCategoriesStr.split(",");
                    for (String s : split) {
                        allowedSet.add(s.trim().toUpperCase());
                    }
                }

                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    infoText.setText("Card found. Rendering...");
                    searchContainer.setVisibility(View.GONE);
                    cardsContainer.setVisibility(View.VISIBLE);

                    // --- Populate card UI ---
                    cardName.setText("Nom: " + driver.optString("name", "N/A"));
                    cardSurname.setText("Prénom: " + driver.optString("surName", "N/A"));
                    cardDob.setText("Date & Lieu de Naissance: " + driver.optString("dateOfBirth", "N/A"));
                    cardNationality.setText("Nationalité: " + driver.optString("nationality", "N/A"));
                    cardNationalId.setText("N° National: " + driver.optString("nationalId", "N/A"));
                    cardAddress.setText("Adresse: " + driver.optString("address", "N/A"));
                    cardIssue.setText("Délivré le: " + license.optString("issueDate", "N/A"));
                    cardExpiry.setText("Expire le: " + license.optString("expiryDate", "N/A"));

                    // Populate Lieu & Date de Livraison and Card Number
                    lieuDateDeLivraison.setText("Lieu & Date de Livraison: " + driverData.optString("dateLieuDelivrance", "N/A"));
                    carNumber.setText(" " + card.optString("cardNumber", "N/A"));

                    // Load profile image
                    String profileImagePath = driver.optString("profileImage", "");
                    String fullProfileImageUrl = "https://traffic.up.railway.app/storage/" + profileImagePath;
                    Glide.with(requireContext())
                            .load(fullProfileImageUrl)
                            .error(new ColorDrawable(Color.LTGRAY))
                            .into(cardProfile);

                    // Set license number and QR code
                    String licenseNumber = license.optString("licenseNumber", "N/A");
                    licenseNumberBack.setText("Permis No: " + licenseNumber);
                    Bitmap qrBitmap = generateQrCode(licenseNumber);
                    if (qrBitmap != null) {
                        qrCodeBack.setImageBitmap(qrBitmap);
                    }

                    // Set allowed categories in the UI
                    String[] categories = {"A", "B", "C", "D", "E"};
                    if (categoryRow != null) {
                        for (int i = 0; i < categories.length; i++) {
                            int textViewIdx = i * 2;
                            if (categoryRow.getChildCount() > textViewIdx) {
                                View v = categoryRow.getChildAt(textViewIdx);
                                if (v instanceof TextView) {
                                    TextView catText = (TextView) v;
                                    String cat = categories[i];
                                    if (allowedSet.contains(cat)) {
                                        catText.setAlpha(1f);
                                        catText.setTypeface(null, android.graphics.Typeface.BOLD);
                                        catText.setTextColor(android.graphics.Color.parseColor("#1976D2"));
                                    } else {
                                        catText.setAlpha(0.4f);
                                        catText.setTypeface(null, android.graphics.Typeface.NORMAL);
                                        catText.setTextColor(android.graphics.Color.parseColor("#888888"));
                                    }
                                }
                            }
                        }
                    }

                    if (vehicleCategoriesTable != null) {
                        for (int i = 0, rowIdx = 2; i < categories.length; i++, rowIdx += 2) {
                            View rowView = vehicleCategoriesTable.getChildAt(rowIdx);
                            if (!(rowView instanceof LinearLayout)) continue;
                            LinearLayout row = (LinearLayout) rowView;
                            if (row.getChildCount() < 5) continue;
                            View allowedView = row.getChildAt(4);
                            if (!(allowedView instanceof TextView)) continue;
                            TextView allowedText = (TextView) allowedView;
                            String cat = categories[i];
                            allowedText.setText(allowedSet.contains(cat) ? "Oui" : "Non");
                        }
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    infoText.setText("Error: " + e.getMessage());
                    Toast.makeText(getContext(), "Failed to fetch card data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void saveCardAsPng(View cardView, String fileName) {
        if (cardView.getWidth() == 0 || cardView.getHeight() == 0) {
            Toast.makeText(getContext(), "Erreur : la vue n'est pas prête à être enregistrée", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap bitmap = Bitmap.createBitmap(cardView.getWidth(), cardView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        cardView.draw(canvas);

        boolean saved = false;
        String savedPath = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Permis");
                Uri uri = requireContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (java.io.OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                        saved = true;
                        savedPath = "Galerie/Pictures/Permis/" + fileName;
                    }
                }
            } else {
                java.io.File dir = new java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Permis");
                if (!dir.exists()) dir.mkdirs();
                java.io.File file = new java.io.File(dir, fileName);
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    saved = true;
                    savedPath = file.getAbsolutePath();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (saved) {
            Toast.makeText(getContext(), "Carte enregistrée : " + savedPath, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getContext(), "Erreur lors de l'enregistrement", Toast.LENGTH_LONG).show();
        }
    }

    private Bitmap generateQrCode(String content) {
        // ... (The generateQrCode method remains the same)
        try {
            QRCodeWriter writer = new QRCodeWriter();
            com.google.zxing.common.BitMatrix bitMatrix =
                    writer.encode(content, BarcodeFormat.QR_CODE, 200, 200);
            Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.RGB_565);
            for (int x = 0; x < 200; x++) {
                for (int y = 0; y < 200; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }
}
