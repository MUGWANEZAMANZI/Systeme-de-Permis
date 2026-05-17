package com.kigaliwebartisans.traffix;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
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
import java.nio.charset.StandardCharsets;

public class SearchFrament extends Fragment {

	private ProgressBar progressBar;
	private TextView infoText;
	private LinearLayout cardsContainer;
	private TextView licenseNumberInput;

	@Nullable
	@Override
	public View onCreateView(@NonNull android.view.LayoutInflater inflater,
							 @Nullable android.view.ViewGroup container,
							 @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_search_card, container, false);

		progressBar = view.findViewById(R.id.progress_bar);
		infoText = view.findViewById(R.id.info_text);
		cardsContainer = view.findViewById(R.id.cards_container);

		Button searchButton = view.findViewById(R.id.button_search);
		licenseNumberInput = view.findViewById(R.id.input_license_number);

		searchButton.setOnClickListener(v -> {
			String licenseNumber = licenseNumberInput.getText().toString().trim();
			if (licenseNumber.isEmpty()) {
				Toast.makeText(getContext(), "Please enter a license number", Toast.LENGTH_SHORT).show();
			} else {
				fetchCardData(licenseNumber);
			}
		});

		return view;
	}

	public void searchByNfcTag(String tagId) {
		if (getActivity() == null) return;
		getActivity().runOnUiThread(() -> {
			if (licenseNumberInput != null) {
				licenseNumberInput.setText(tagId);
			}
			fetchCardData(tagId);
		});
	}

	private void fetchCardData(String query) {
		progressBar.setVisibility(View.VISIBLE);
		infoText.setText("Searching for: " + query);

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
					throw new Exception("No driver data found for this query.");
				}

				JSONObject driverData = driversArray.getJSONObject(0);
				
				// Handle Laravel nested structure if present
				JSONObject driver = driverData.optJSONObject("driver");
				if (driver != null && driver.has("App\\Models\\Driver")) {
					driver = driver.getJSONObject("App\\Models\\Driver");
				} else if (driver == null) {
					driver = driverData.getJSONObject("driver");
				}

				JSONObject license = driverData.optJSONObject("license");
				if (license != null && license.has("App\\Models\\License")) {
					license = license.getJSONObject("App\\Models\\License");
				} else if (license == null) {
					license = driverData.getJSONObject("license");
				}

				// Pass all relevant data to PrintCardFragment via Bundle
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
				
				if (driverData.has("card")) {
					JSONObject card = driverData.getJSONObject("card");
					if (card.has("App\\Models\\Card")) card = card.getJSONObject("App\\Models\\Card");
					bundle.putString("cardNumber", card.optString("cardNumber", "N/A"));
				}

				requireActivity().runOnUiThread(() -> {
					progressBar.setVisibility(View.GONE);
					infoText.setText("Card found. Opening card view...");
					// Navigate to PrintCardFragment and pass the bundle
					Fragment printCardFragment = new PrintCardFragment();
					printCardFragment.setArguments(bundle);
					requireActivity().getSupportFragmentManager()
						.beginTransaction()
						.replace(((ViewGroup)getView().getParent()).getId(), printCardFragment)
						.addToBackStack(null)
						.commit();
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
}