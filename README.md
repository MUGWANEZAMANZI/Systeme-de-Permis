# Système de Permis (Traffic App)

Android application for registering driver license cards and verifying drivers at road checkpoints using **NFC**, **QR**, or manual search.

## What this app does

- Register a driver and link them to an NFC card/tag
- Read NFC tags from the phone and search the driver record
- Search by QR code or by license/plate text input
- View driver details and associated penalties
- Add penalties from the checkpoint screen

Backend API used by the app: `https://traffic.up.railway.app/api`

---

## NFC hardware requirements

For a hardware/NFC operator, you need:

1. **Android phone with NFC** (NFC is required by the app manifest)
2. **NFC cards/tags** (NDEF-capable recommended)
3. Optional: USB NFC writer/reader (ACR122U or similar) + tag writing software, if you pre-encode cards outside the phone

### Tag compatibility

The app supports:

- **NDEF text tags** (preferred)
- **Blank/formattable NDEF tags** (fallback)
- **Raw tag UID fallback** (if no readable NDEF text is found)

So in practice, if text payload cannot be read, the app uses the tag/card ID in hexadecimal.

---

## Software prerequisites

- Android Studio (latest stable)
- Android SDK for compile/target SDK 36
- JDK 11 (project is configured for Java 11)
- Internet access from the device to reach the backend API

---

## Build and run

1. Open project folder in Android Studio:
   - `/home/runner/work/Systeme-de-Permis/Systeme-de-Permis`
2. Let Gradle sync complete.
3. Connect an NFC-capable Android device (or use a compatible emulator setup).
4. Run the `app` module.

> Note: `gradlew test lint assembleDebug` currently fails in this environment because Android Gradle plugin `com.android.application` version `8.11.2` could not be resolved from configured repositories.

---

## How to use (NFC workflow)

### 1) Register a driver with NFC card

1. Open **Enregistrer** tab.
2. Fill driver information (name, license, plate, issue/expiry, etc.).
3. Keep app on this screen and bring NFC card close to the phone.
4. The scanned value is auto-filled in the hidden NFC field.
5. Add photo if required.
6. Tap **Enregistrer** to send data to backend (`/register-drivers`).

### 2) Check driver at checkpoint via NFC

1. Open **Contrôle** tab.
2. Tap **Scanner**.
3. Bring NFC card near the phone.
4. App reads NDEF text or UID and queries backend (`/driver-by-card/{tag}`).
5. Driver details and penalties are displayed.

### 3) Add penalty

1. On checkpoint results, tap the floating **+** button.
2. Choose infraction and amount.
3. Confirm to send to backend (`/add-penalty`).

---

## QR and manual fallback

- **Code QR** button supports QR scan result handling via app flow
- Manual input supports license number or plate text search

Use these when NFC card is unavailable or damaged.

---

## Troubleshooting for NFC operators

- **No scan detected**: verify device NFC is enabled in Android settings.
- **Wrong driver returned**: ensure card/tag content matches the backend field expected by registration/checkpoint.
- **Some cards work, others not**: standardize on one tag type and encoding strategy (recommended: NDEF text).
- **Network errors**: verify internet access and backend availability.
- **No NFC hardware**: app declares NFC as required and is intended for NFC-capable devices.

---

## Main technical files

- `app/src/main/java/com/kigaliwebartisans/traffix/MainActivity.java` (foreground NFC dispatch and tag routing)
- `app/src/main/java/com/kigaliwebartisans/traffix/RegisterDriverFragment.java` (driver registration + NFC capture)
- `app/src/main/java/com/kigaliwebartisans/traffix/TraffickingCheckpointFragment.java` (checkpoint NFC/QR search + penalties)
- `app/src/main/res/xml/nfc_tech_filter.xml` (NFC tech filter)
- `app/src/main/java/com/kigaliwebartisans/traffix/ApiConstants.java` (base API URL)
