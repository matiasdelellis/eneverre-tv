# Eneverre TV 📺
Android TV client for [enverrre-api](https://github.com/matiasdelellis/eneverre-api).

### ⚠️ Important
Due to the QR-based authentication flow, each implementation of eneverre-api requires a custom-built APK pointing to its own backend (`API_HOST`).

## ✨Features
* Login with QR and user code.
* View live view from all cameras.
* Zoom and pan live view.
* Auto-update: the app checks for new builds on cold start, shows a dialog, downloads, verifies the SHA-256 and installs.

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

### 2. Generate the publish token 🔑
The publish endpoint of `eneverre-api` is gated by a dedicated Bearer token (different from user credentials). Generate one and store it as a GitHub secret — it will be used by CI to push new builds.

``` bash
$ openssl rand -hex 32
```

Copy the output (a 64-char hex string) — that is your `UPDATE_PUBLISH_TOKEN`. Set the same value server-side as `[updates] publish_token` (or `ENEVERRE_UPDATES_PUBLISH_TOKEN`).

### 3. Github Secrets 🙈
Go to `GitHub` → `Settings` → `Secrets and variables` → `Actions` → `Secrets` and add the following keys.

| Key                   | Description                                                            |
| --------------------- | ---------------------------------------------------------------------- |
| `API_HOST`            | Your backend URL (e.g. `https://tueneverre.com/api/`)                  |
| `KEYSTORE_BASE64`     | Contents of `keystore.base64`                                          |
| `KEYSTORE_PASSWORD`   | Keystore password                                                      |
| `KEY_ALIAS`           | Alias used in keytool                                                  |
| `KEY_PASSWORD`        | Key password                                                           |
| `UPDATE_PUBLISH_TOKEN`| Random token (e.g. `openssl rand -hex 32`) that matches the server's `[updates] publish_token`. |

### 4. Build & publish a new version 🚀
Go to `GitHub` → `Actions` → `Build Signed APK` → `Run workflow` and fill the inputs:

| Input           | Required | Description                                                                                       |
| --------------- | -------- | ------------------------------------------------------------------------------------------------- |
| `version_name`  | yes      | The new release version in `MAJOR.MINOR.PATCH` form (e.g. `1.0.1`). The workflow computes `versionCode` as `MAJOR*10000 + MINOR*100 + PATCH`. |
| `release_notes` | no       | Free-text notes shown in the update dialog. Leave empty for "no body".                            |
| `mandatory`     | no       | `true` hides the *Later* / *Skip* buttons in the dialog. Default `false`.                          |

The workflow will:
1. Build and sign the APK (universal + arm64 + armv7).
2. Upload the three APKs as artifacts.
3. `POST` the universal APK to `${API_HOST}admin/app/updates/tv` with the Bearer token. Any non-2xx (including 401 / 503) fails the build.

Once the publish call returns 2xx, the server has written the new manifest + APK to its storage dir and the next cold start of any installed client with a lower `versionCode` will offer the update.

### How the client handles updates 🔄
* On every cold start, **in parallel** with the auth flow, the app `GET`s `${API_HOST}app/tv/update` (the `tv` track is hard-coded in this build).
* The check happens **once per process** — the server sends `Cache-Control: no-store`, so we never re-GET.
* Responses are handled as:
  * `200` with `versionCode > BuildConfig.VERSION_CODE` and not in the skip list → show the update dialog.
  * `204` / `503` / any non-2xx → silently skip (server is "off" or transient).
* On *Install* a `WorkManager` job downloads `manifest.url` to `cacheDir/updates/`, verifies SHA-256 against `manifest.sha256`, and on match fires `ACTION_INSTALL_PACKAGE` via `FileProvider`.
* On *Skip* the `manifest.versionName` is persisted in `SharedPreferences("updates").skipped_version`. A higher `versionName` later implicitly un-skips.
* On SHA-256 mismatch the file is deleted and the worker fails — the next cold start will offer the update again.

### 🏁 …that's all folk 🛡️⭕🐷👋
