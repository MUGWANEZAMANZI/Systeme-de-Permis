package com.kigaliwebartisans.traffix;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.IOException;
import java.util.Calendar;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;

import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class RegisterDriverFragment extends Fragment {
    // Handle NFC tag (NDEF or NdefFormattable)
    public void processNfcTag(android.nfc.Tag tag) {
        if (tag == null) return;
        // Try to read NDEF text (optional, for future use)
        android.nfc.tech.Ndef ndef = android.nfc.tech.Ndef.get(tag);
        if (ndef != null) {
            try {
                ndef.connect();
                android.nfc.NdefMessage ndefMessage = ndef.getNdefMessage();
                if (ndefMessage != null && ndefMessage.getRecords().length > 0) {
                    String text = getTextFromNdefRecord(ndefMessage.getRecords()[0]);
                    if (text != null && !text.isEmpty()) {
                        setNfcTag(text);
                        ndef.close();
                        return;
                    }
                }
                ndef.close();
            } catch (Exception e) {
                // fallback below
            }
        }
        // Fallback: use tag ID
        String tagId = bytesToHexString(tag.getId());
        setNfcTag(tagId);
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
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int TAKE_PHOTO_REQUEST = 2;
    private EditText nameEdit, surNameEdit, addressEdit, plateEdit, bloodGroupEdit, licenseIdEdit, issueEdit, expiryEdit, nationalityEdit, nationalIdEdit, phoneEdit, emailEdit, nfcTagEdit;
    private EditText licensesAllowedEdit, dateLieuEdit;
    private android.widget.CheckBox catABox, catBBox, catCBox, catDBox, catEBox;
    private ImageView imageView;
    private Uri imageUri;
    private ProgressBar progressBar;
    private TextView nfcContentTextView;
    private Button registerButton, pickImageButton, takePhotoButton;
    private Calendar issueCalendar = Calendar.getInstance();
    private Calendar expiryCalendar = Calendar.getInstance();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_register_driver, container, false);
    nameEdit = view.findViewById(R.id.edit_name);
    surNameEdit = view.findViewById(R.id.edit_surname);
    addressEdit = view.findViewById(R.id.edit_address);
    plateEdit = view.findViewById(R.id.edit_plate);
    bloodGroupEdit = view.findViewById(R.id.edit_bloodgroup);
    licenseIdEdit = view.findViewById(R.id.edit_licenseid);
    issueEdit = view.findViewById(R.id.edit_issue);
    expiryEdit = view.findViewById(R.id.edit_expiry);
    nationalityEdit = view.findViewById(R.id.edit_nationality);
    nationalIdEdit = view.findViewById(R.id.edit_nationalid);
    phoneEdit = view.findViewById(R.id.edit_phone);
    emailEdit = view.findViewById(R.id.edit_email);
//    licensesAllowedEdit = view.findViewById(R.id.edit_licenses_allowed);
    dateLieuEdit = view.findViewById(R.id.edit_date_lieu);
    catABox = view.findViewById(R.id.checkbox_cat_a);
    catBBox = view.findViewById(R.id.checkbox_cat_b);
    catCBox = view.findViewById(R.id.checkbox_cat_c);
    catDBox = view.findViewById(R.id.checkbox_cat_d);
    catEBox = view.findViewById(R.id.checkbox_cat_e);
    imageView = view.findViewById(R.id.image_view);
    nfcContentTextView = view.findViewById(R.id.nfc_content_textview);
    nfcTagEdit = view.findViewById(R.id.edit_nfc_tag);

        progressBar = view.findViewById(R.id.progress_bar);
        registerButton = view.findViewById(R.id.button_register);
        pickImageButton = view.findViewById(R.id.button_pick_image);
        takePhotoButton = view.findViewById(R.id.button_take_photo);

        // Date pickers for issue and expiry
        issueEdit.setFocusable(false);
        issueEdit.setOnClickListener(v -> showDatePicker(issueEdit, issueCalendar));
        expiryEdit.setFocusable(false);
        expiryEdit.setOnClickListener(v -> showDatePicker(expiryEdit, expiryCalendar));

        pickImageButton.setOnClickListener(v -> pickImage());
        takePhotoButton.setOnClickListener(v -> takePhoto());
        registerButton.setOnClickListener(v -> handleRegister());

        return view;
    }

    public void setNfcContent(String content) {
        if (getActivity() == null || getView() == null) return;
        if (nfcContentTextView != null) nfcContentTextView.setText(content);
        if (nfcTagEdit != null) nfcTagEdit.setText(content);
    }

    // Call this from MainActivity when a tag is read
    public void setNfcTag(String tag) {
        if (getActivity() == null || getView() == null) return;
        if (nfcTagEdit != null) nfcTagEdit.setText(tag);
        if (nfcContentTextView != null) {
            nfcContentTextView.setText(tag);
            nfcContentTextView.setHint(tag);
        }
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    private void takePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
            startActivityForResult(intent, TAKE_PHOTO_REQUEST);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == PICK_IMAGE_REQUEST && data != null && data.getData() != null) {
                imageUri = data.getData();
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), imageUri);
                    imageView.setImageBitmap(bitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (requestCode == TAKE_PHOTO_REQUEST && data != null && data.getExtras() != null) {
                Bitmap bitmap = (Bitmap) data.getExtras().get("data");
                imageView.setImageBitmap(bitmap);
            }
        }
    }

    private void handleRegister() {
        // Validate required fields (add more as needed)
        String name = nameEdit.getText().toString().trim();
        String surName = surNameEdit.getText().toString().trim();
        String address = addressEdit.getText().toString().trim();
        String plate = plateEdit.getText().toString().trim();
        String bloodGroup = bloodGroupEdit.getText().toString().trim();
        String licenseId = licenseIdEdit.getText().toString().trim();
        String issue = issueEdit.getText().toString().trim();
        String expiry = expiryEdit.getText().toString().trim();
        String nationality = nationalityEdit.getText().toString().trim();
        String nationalId = nationalIdEdit.getText().toString().trim();
        String phone = phoneEdit.getText().toString().trim();
        String email = emailEdit.getText().toString().trim();
    String nfcTag = (nfcTagEdit != null ? nfcTagEdit.getText().toString().trim() : "");
    String licensesAllowed = (licensesAllowedEdit != null ? licensesAllowedEdit.getText().toString().trim() : "");
    String dateLieu = (dateLieuEdit != null ? dateLieuEdit.getText().toString().trim() : "");
    // Collect allowed categories from checkboxes
    java.util.List<String> allowedCategories = new java.util.ArrayList<>();
    if (catABox != null && catABox.isChecked()) allowedCategories.add("A");
    if (catBBox != null && catBBox.isChecked()) allowedCategories.add("B");
    if (catCBox != null && catCBox.isChecked()) allowedCategories.add("C");
    if (catDBox != null && catDBox.isChecked()) allowedCategories.add("D");
    if (catEBox != null && catEBox.isChecked()) allowedCategories.add("E");
    String allowedCategoriesStr = android.text.TextUtils.join(",", allowedCategories);
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(surName) || TextUtils.isEmpty(licenseId) || TextUtils.isEmpty(plate) || TextUtils.isEmpty(issue) || TextUtils.isEmpty(expiry)) {
            Toast.makeText(getContext(), "Veuillez remplir tous les champs obligatoires", Toast.LENGTH_SHORT).show();
            return;
        }

        // Generate MD5 hash of plate number
        final String secret;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(plate.getBytes());
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            secret = sb.toString();
        } catch (NoSuchAlgorithmException e) {
            Toast.makeText(getContext(), "Erreur de hachage MD5", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get image bytes (JPEG)
        final byte[] imageBytes;
        byte[] tempImageBytes = null;
        if (imageView.getDrawable() != null) {
            imageView.setDrawingCacheEnabled(true);
            imageView.buildDrawingCache();
            Bitmap bitmap = null;
            if (imageUri != null) {
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), imageUri);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (bitmap == null && imageView.getDrawable() != null) {
                imageView.setDrawingCacheEnabled(true);
                bitmap = imageView.getDrawingCache();
            }
            if (bitmap != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
                tempImageBytes = baos.toByteArray();
            }
        }
        imageBytes = tempImageBytes;

        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            String lineEnd = "\r\n";
            String twoHyphens = "--";
            try {
                String apiUrl = com.kigaliwebartisans.traffix.ApiConstants.URL + "/register-drivers";
                java.net.URL url = new java.net.URL(apiUrl);

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                DataOutputStream dos = new DataOutputStream(conn.getOutputStream());

                // Helper to write a form field
            java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
            fields.put("name", name);
            fields.put("surname", surName);
            fields.put("address", address);
            fields.put("plateNumber", plate);
            fields.put("bloodGroup", bloodGroup);
            fields.put("licenseNumber", licenseId);
            fields.put("issueDate", issue);
            fields.put("expiryDate", expiry);
            fields.put("nationality", nationality);
            fields.put("nationalId", nationalId);
            fields.put("phone", phone);
            fields.put("email", email);
            fields.put("nfcTag", nfcTag);
            fields.put("secret", secret);
            // Add allowed categories fields.put("licensesAllowed", licensesAllowed);
            fields.put("dateLieuDelivrance", dateLieu);
            fields.put("allowedCategories", allowedCategoriesStr);
                for (java.util.Map.Entry<String, String> entry : fields.entrySet()) {
                    dos.writeBytes(twoHyphens + boundary + lineEnd);
                    dos.writeBytes("Content-Disposition: form-data; name=\"" + entry.getKey() + "\"" + lineEnd);
                    dos.writeBytes(lineEnd);
                    dos.writeBytes(entry.getValue() + lineEnd);
                }

                // Add image if present
                if (imageBytes != null) {
                    dos.writeBytes(twoHyphens + boundary + lineEnd);
                    dos.writeBytes("Content-Disposition: form-data; name=\"profileImage\"; filename=\"profile.jpg\"" + lineEnd);
                    dos.writeBytes("Content-Type: image/jpeg" + lineEnd);
                    dos.writeBytes(lineEnd);
                    dos.write(imageBytes);
                    dos.writeBytes(lineEnd);
                }
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
                dos.flush();
                dos.close();

                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                StringBuilder sbResp = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sbResp.append(line);
                reader.close();
                String response = sbResp.toString();
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (responseCode >= 200 && responseCode < 300) {
                        new AlertDialog.Builder(getContext())
                                .setTitle("Succès")
                                .setMessage("Conducteur enregistré !")
                                .setPositiveButton("OK", null)
                                .show();
                    } else {
                        new AlertDialog.Builder(getContext())
                                .setTitle("Erreur")
                                .setMessage("Erreur lors de l'enregistrement: " + response)
                                .setPositiveButton("OK", null)
                                .show();
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    new AlertDialog.Builder(getContext())
                            .setTitle("Erreur")
                            .setMessage("Erreur réseau: " + e.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        }).start();
    }

    private void showDatePicker(EditText target, Calendar cal) {
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);
        DatePickerDialog dialog = new DatePickerDialog(requireContext(), (view, y, m, d) -> {
            cal.set(Calendar.YEAR, y);
            cal.set(Calendar.MONTH, m);
            cal.set(Calendar.DAY_OF_MONTH, d);
            String dateStr = String.format("%04d-%02d-%02d", y, m+1, d);
            target.setText(dateStr);
        }, year, month, day);
        dialog.show();
    }
}
