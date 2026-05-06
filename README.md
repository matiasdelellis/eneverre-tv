# Eneverre TV 📺
Android TV client for [enverrre-api](https://github.com/matiasdelellis/eneverre-api).

### ⚠️ Important
Due to the QR-based authentication flow, each implementation of eneverre-api requires a custom-built APK pointing to its own backend (`API_HOST`).

## ✨Features
* Login with QR and user code.
* View live view from all cameras
* Zoom and pan live view.

## 🚧 Roadmap
* Ptz
* Playback

## Build Eneverre TV APK 🛠️
To generate your own APK, you must fork this repository and configure your environment.

### 1. Create keystore 🔐
``` bash
$ keytool -genkey -v -keystore eneverre.keystore -alias eneverre -keyalg RSA -keysize 2048 -validity 10000
$ base64 -w 0 my-release-key.keystore > keystore.base64
```

### 2. Github Secrets 🙈
Go to `GitHub` → `Settings` → `Secrets and variables` → `Actions` → `Secrets` you must add the following keys.

| Key                 | Description                                           |
| ------------------- | ----------------------------------------------------- |
| `API_HOST`          | Your backend URL (e.g. `https://tueneverre.com/api/`) |
| `KEYSTORE_BASE64`   | Contents of `keystore.base64`                         |
| `KEYSTORE_PASSWORD` | Keystore password                                     |
| `KEY_ALIAS`         | Alias used in keytool                                 |
| `KEY_PASSWORD`      | Key password                                          |

### 3. Build 🎉
To to `Github` → `Actions` → `Build Signed APK`
1. `Run workflow` → `Run workflow`

### 🏁 …that's all folk 🛡️⭕🐷👋
