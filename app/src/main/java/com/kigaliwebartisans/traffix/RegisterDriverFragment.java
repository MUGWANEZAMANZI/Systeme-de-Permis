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
import android.util.Log;
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

import org.json.JSONObject;

import java.io.IOException;
import java.util.Calendar;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

public class RegisterDriverFragment extends Fragment {
    
    private static final String TAG = "RegisterDriverFragment";
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int TAKE_PHOTO_REQUEST = 2;
    
    private EditText nameEdit, surNameEdit, addressEdit, plateEdit, bloodGroupEdit, licenseIdEdit, issueEdit, expiryEdit, nationalityEdit, nationalIdEdit, phoneEdit, emailEdit, nfcTagEdit;
    private EditText dateLieuEdit;
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

        issueEdit.setFocusable(false);
        issueEdit.setOnClickListener(v -> showDatePicker(issueEdit, issueCalendar));
        expiryEdit.setFocusable(false);
        expiryEdit.setOnClickListener(v -> showDatePicker(expiryEdit, expiryCalendar));

        pickImageButton.setOnClickListener(v -> pickImage());
        takePhotoButton.setOnClickListener(v -> takePhoto());
        registerButton.setOnClickListener(v -> handleRegister());

        return view;
    }

    public void setNfcTag(String tag) {
        Log.d(TAG, "NFC Tag received in fragment: " + tag);
        if (getActivity() == null || getView() == null) return;
        getActivity().runOnUiThread(() -> {
            if (nfcTagEdit != null) nfcTagEdit.setText(tag);
            if (nfcContentTextView != null) {
                nfcContentTextView.setText("Carte détectée: " + tag);
                nfcContentTextView.setBackgroundColor(0xFFCCFFCC); // Light green
            }
            Toast.makeText(getContext(), "Carte NFC capturée!", Toast.LENGTH_SHORT).show();
        });
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
        String dateLieu = (dateLieuEdit != null ? dateLieuEdit.getText().toString().trim() : "");

        java.util.List<String> allowedCategories = new java.util.ArrayList<>();
        if (catABox != null && catABox.isChecked()) allowedCategories.add("A");
        if (catBBox != null && catBBox.isChecked()) allowedCategories.add("B");
        if (catCBox != null && catCBox.isChecked()) allowedCategories.add("C");
        if (catDBox != null && catDBox.isChecked()) allowedCategories.add("D");
        if (catEBox != null && catEBox.isChecked()) allowedCategories.add("E");
        String allowedCategoriesStr = android.text.TextUtils.join(",", allowedCategories);

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(surName) || TextUtils.isEmpty(licenseId) || TextUtils.isEmpty(plate)) {
            Toast.makeText(getContext(), "Veuillez remplir les champs obligatoires", Toast.LENGTH_SHORT).show();
            return;
        }

        final String secret;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(plate.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
            secret = sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return;
        }

        final byte[] imageBytes;
        byte[] tempImageBytes = null;
        if (imageView.getDrawable() != null) {
            Bitmap bitmap = null;
            try {
                if (imageUri != null) {
                    bitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), imageUri);
                } else {
                    imageView.setDrawingCacheEnabled(true);
                    bitmap = Bitmap.createBitmap(imageView.getDrawingCache());
                    imageView.setDrawingCacheEnabled(false);
                }
                if (bitmap != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                    tempImageBytes = baos.toByteArray();
                }
            } catch (Exception e) {
                Log.e(TAG, "Image conversion error: " + e.getMessage());
            }
        }
        imageBytes = tempImageBytes;

        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            String lineEnd = "\r\n";
            String twoHyphens = "--";
            try {
                java.net.URL url = new java.net.URL(ApiConstants.URL + "/register-drivers");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                
                DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
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
                fields.put("dateLieuDelivrance", dateLieu);
                fields.put("allowedCategories", allowedCategoriesStr);

                for (java.util.Map.Entry<String, String> entry : fields.entrySet()) {
                    dos.writeBytes(twoHyphens + boundary + lineEnd);
                    dos.writeBytes("Content-Disposition: form-data; name=\"" + entry.getKey() + "\"" + lineEnd);
                    dos.writeBytes("Content-Type: text/plain; charset=UTF-8" + lineEnd);
                    dos.writeBytes(lineEnd);
                    dos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    dos.writeBytes(lineEnd);
                }

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
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sbResp = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sbResp.append(line);
                reader.close();

                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (responseCode >= 200 && responseCode < 300) {
                        Toast.makeText(getContext(), "Enregistrement réussi!", Toast.LENGTH_LONG).show();
                        clearFields();
                    } else {
                        try {
                            JSONObject errJson = new JSONObject(sbResp.toString());
                            String msg = errJson.optString("message", "Erreur d'enregistrement");
                            new AlertDialog.Builder(getContext()).setTitle("Erreur").setMessage(msg).show();
                        } catch (Exception e2) {
                            new AlertDialog.Builder(getContext()).setTitle("Erreur").setMessage("Échec de l'enregistrement (Code: " + responseCode + ")").show();
                        }
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Erreur réseau: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void clearFields() {
        EditText[] fields = {nameEdit, surNameEdit, addressEdit, plateEdit, bloodGroupEdit, licenseIdEdit, issueEdit, expiryEdit, nationalityEdit, nationalIdEdit, phoneEdit, emailEdit, nfcTagEdit, dateLieuEdit};
        for (EditText f : fields) if (f != null) f.setText("");
        if (imageView != null) imageView.setImageResource(0);
        if (nfcContentTextView != null) nfcContentTextView.setText("Approchez une carte de permit...");
    }

    private void showDatePicker(EditText target, Calendar cal) {
        new DatePickerDialog(requireContext(), (view, y, m, d) -> {
            cal.set(y, m, d);
            target.setText(String.format("%04d-%02d-%02d", y, m + 1, d));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }
}