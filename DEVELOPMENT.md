# Development Guide

## Project Structure

This is a monorepo containing two applications:

```
├── src/                  # Web application (Vite + vanilla JS)
└── android/              # Android application (Kotlin + Jetpack Compose)
```

---

## Android Signing Setup

Android requires **every APK to be cryptographically signed**, even for sideload distribution (outside Google Play). A consistent signing key is essential — if you change keys between versions, users must uninstall the old version before installing the new one, losing all app data.

### Step 1: Generate a Keystore

Run this command on your local machine:

```bash
keytool -genkeypair \
  -v \
  -storetype JKS \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass <STORE_PASSWORD> \
  -keypass <KEY_PASSWORD> \
  -alias <KEY_ALIAS> \
  -keystore my-release-key.jks \
  -dname "CN=Riz, OU=Development, O=Riz, L=Unknown, ST=Unknown, C=US"
```

Replace the `<...>` placeholders with your own values:

| Placeholder | Description | Example |
|---|---|---|
| `<STORE_PASSWORD>` | Password to protect the keystore file | `MyStr0ngP@ss!` |
| `<KEY_PASSWORD>` | Password to protect the key entry | `MyStr0ngP@ss!` |
| `<KEY_ALIAS>` | A name for your key inside the keystore | `riz-release` |

### Step 2: Encode the Keystore as Base64

The keystore is a binary file. To store it in GitHub Secrets, you need to convert it to a text string:

```bash
# macOS
base64 -i my-release-key.jks -o keystore-base64.txt

# Linux
base64 -w 0 my-release-key.jks > keystore-base64.txt
```

The contents of `keystore-base64.txt` will be pasted into GitHub.

### Step 3: Add Secrets to GitHub

Go to your GitHub repository:

**Settings → Secrets and variables → Actions → New repository secret**

Add these 4 secrets:

| Secret Name | Value | Description |
|---|---|---|
| `ANDROID_KEYSTORE_B64` | Contents of `keystore-base64.txt` | The keystore file encoded as base64 |
| `KEYSTORE_PASSWORD` | Your `<STORE_PASSWORD>` | Password to open the keystore file |
| `KEY_ALIAS` | Your `<KEY_ALIAS>` | Name of the key entry (e.g. `riz-release`) |
| `KEY_PASSWORD` | Your `<KEY_PASSWORD>` | Password for the specific key entry |

### Step 4: Verify

After adding all 4 secrets, push a version tag to trigger a release build:

```bash
git tag v0.0.1-test
git push origin v0.0.1-test
```

Check the Actions tab in your repository to confirm the build succeeds.

## Local Development

### Web App

```bash
npm install
npm run dev
```

### Android App

Open the `android/` directory in Android Studio. The app uses debug signing by default for local development — no keystore setup needed.
