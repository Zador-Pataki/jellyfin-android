<p align="center">
  <img src="app/src/main/res/drawable-nodpi/zadflix_icon.png" width="150" alt="Zadflix red Z logo">
</p>

<h1 align="center">Zadflix</h1>

<p align="center">
  A private Android client for streaming your own movie and TV library from your workstation.
</p>

<p align="center">
  <a href="https://github.com/Zador-Pataki/jellyfin-android/tree/codex/personal-media-app"><img alt="Development branch" src="https://img.shields.io/badge/branch-codex%2Fpersonal--media--app-e50914"></a>
  <a href="LICENSE.md"><img alt="GPL v2 license" src="https://img.shields.io/badge/license-GPLv2-e50914"></a>
  <img alt="Android 5.0 or newer" src="https://img.shields.io/badge/Android-5.0%2B-e50914">
  <img alt="Project status: personal fork" src="https://img.shields.io/badge/status-personal%20fork-555555">
</p>

Zadflix turns a workstation running Jellyfin Server into a private, Netflix-style media library. Movies and TV shows stay on the workstation. The Android app streams only the data needed for playback, keeps streamed data in a disposable buffer, and optionally stores selected titles for real offline viewing.

This is not a hosted streaming service and it does not include media. It is a customized fork of [Jellyfin for Android](https://github.com/jellyfin/jellyfin-android) for a personal media setup.

## What is different from upstream Jellyfin

- Zadflix name, original red Z icon, splash screen, and red/black interface.
- Automatic mobile-data playback policy: original quality on unmetered Wi-Fi and a 2.5 Mbps cap on metered/mobile connections by default.
- A larger mobile playback buffer tuned for cellular connections through Tailscale.
- A fixed app-managed download directory—no folder picker is required.
- Verified downloaded files are preferred automatically, including when playback starts from the regular Home screen.
- Airplane-mode handling that opens the native Downloads screen and plays completed downloads without contacting the workstation.
- An administrator-only refresh button in the header for starting a Jellyfin library scan from the app.
- A Zadflix-branded startup path without the original Jellyfin logo flash.

## How it works

```text
Moviesa / TVShows on the workstation
                │
                ▼
        Jellyfin Server :8096
          │           │
     home Wi-Fi    Tailscale
          └─────┬─────┘
                ▼
          Zadflix on Android
        stream ─┴─ download
       temporary    persistent
        buffer       offline copy
```

| Mode | What is stored on the phone | Workstation required? |
| --- | --- | --- |
| Normal streaming | A bounded temporary playback cache | Yes |
| Mobile streaming | A temporary transcoded stream, normally capped at 2.5 Mbps | Yes |
| Download | The complete selected title in Zadflix's app directory | Only while downloading |
| Airplane mode | Uses a verified completed download | No |

Closing or restarting the app does not turn streamed media into a permanent download. Use the Download action when a title must remain available offline.

## Current media-library layout

The configured workstation uses these exact library roots:

```text
/Users/zadorpataki/Zadflix/
├── Moviesa/
│   └── Movie Name (2026)/
│       └── Movie Name (2026).mkv
└── TVShows/
    └── Show Name/
        └── Season 01/
            └── Show Name S01E01.mkv
```

`Moviesa` is intentional and is already configured in Jellyfin. Renaming or moving either root requires updating the corresponding Jellyfin library.

Recommended naming:

- Movies: one folder per movie, with the release year when known.
- TV: one folder per show, then `Season 01`, `Season 02`, and so on.
- Episodes: include `SxxEyy` in the filename.
- Specials: place them in `Season 00`.
- Subtitles: use the same base filename as the video, followed by the language code.

See [the complete media naming guide](docs/media-library-layout.md) or inspect a folder without changing it:

```sh
./scripts/check-media-layout.sh movies "/path/to/Movies"
./scripts/check-media-layout.sh shows "/path/to/TV Shows"
```

The inspection script never moves, renames, creates, or deletes media.

## Workstation setup

Zadflix requires a reachable Jellyfin Server. The current setup uses the native Jellyfin macOS application, but any compatible Jellyfin Server can provide the backend.

1. Install and start [Jellyfin Server](https://jellyfin.org/downloads/server/).
2. Open `http://127.0.0.1:8096` on the workstation.
3. Create separate Movies and TV Shows libraries.
4. Point them at the intended media roots.
5. Keep the workstation awake while streaming.

For the configured Mac, these commands start Jellyfin and perform a read-only health check:

```sh
open -a Jellyfin
./scripts/jellyfin-status.sh
```

Open the server dashboard locally with:

```sh
open "http://127.0.0.1:8096"
```

Detailed instructions, including the currently configured paths and VideoToolbox transcoding, are in [docs/workstation-server-setup.md](docs/workstation-server-setup.md).

## Private access away from home

The recommended remote connection is [Tailscale](https://tailscale.com/). Install it on the workstation and phone, sign both devices into the same tailnet, and enter the workstation's Tailscale address in Zadflix:

```text
http://<workstation-name>.<tailnet>.ts.net:8096
```

The numeric Tailscale IP also works:

```text
http://100.x.y.z:8096
```

Do not expose Jellyfin port `8096` directly to the public internet. An `http://` Jellyfin address remains inside Tailscale's encrypted tunnel when both devices are connected through Tailscale.

The workstation must be awake with both Jellyfin and Tailscale running. On mobile data, Zadflix recognizes metered connections even when traffic is routed through the Tailscale VPN.

## In-app library refresh

After adding or changing media on the workstation, an administrator can press the red circular-arrow button in the Zadflix header. The button starts Jellyfin's authenticated `RefreshLibrary` task, displays progress, and returns to Home when scanning finishes.

The control is hidden from non-administrator accounts. Jellyfin also enforces the administrator permission on the server.

## Streaming quality and buffering

The default automatic policy is designed to start reliably on mobile networks without changing Wi-Fi quality:

| Setting | Mobile-data limit | Wi-Fi behavior |
| --- | ---: | --- |
| Auto | 2.5 Mbps | Original quality |
| Original quality | No cap | Original quality |
| Data saver | 1.5 Mbps | Original quality |
| Balanced | 2.5 Mbps | Original quality |
| High | 4 Mbps | Original quality |

Change it under **Zadflix settings → Video player → Mobile data quality**. The quality menu inside the player remains available as a per-playback override.

Playback begins after enough playable time is buffered—it does not wait for a percentage of the complete movie. The automatic mobile profile targets roughly five seconds of playable data before starting and a larger reserve after rebuffering.

## Offline downloads

Downloads are stored in the debug app's private external-storage directory:

```text
Android/data/com.zadorpataki.personalmedia.debug/files/Download/Zadflix
```

Important behavior:

- No broad storage permission or download-location picker is required.
- A `.nomedia` marker prevents downloaded videos from appearing in the system gallery.
- Completed downloads appear under **Profile → Downloads**.
- A verified download is used automatically when the same title is opened offline.
- Airplane mode ignores stale VPN-only connectivity and does not wait on the workstation.
- Updating with `adb install -r` preserves compatible app data and downloads.
- Uninstalling the debug app removes its app-managed download directory.

## Build the Android app

### Requirements

- JDK 17 or newer; JDK 21 is the verified version.
- Android SDK platform 36.
- Android build-tools 36.0.0.
- Android Platform-Tools for USB installation.

Clone the active Zadflix branch:

```sh
git clone --branch codex/personal-media-app \
  https://github.com/Zador-Pataki/jellyfin-android.git
cd jellyfin-android
```

Build the libre debug APK and run its unit tests:

```sh
./scripts/build-android.sh --tests
```

The script validates the local toolchain, builds `libreDebug`, and prints the APK's absolute path and SHA-256 checksum. It does not install the APK or create release-signing credentials.

The libre variant intentionally excludes the proprietary Google Cast dependency, so Chromecast support is not included in this build.

For a clean diagnostic build:

```sh
./scripts/build-android.sh --clean --tests
```

The output is written under:

```text
app/build/outputs/apk/libre/debug/
```

See [docs/android-build-install.md](docs/android-build-install.md) for the verified macOS toolchain and complete build instructions.

## Install on an Android phone

1. Enable Developer options and USB debugging on the phone.
2. Connect it with a data-capable USB cable.
3. Unlock it and approve the computer's debugging key.
4. Confirm that Android Debug Bridge sees it:

   ```sh
   adb devices -l
   ```

5. Install or update the APK without clearing app data:

   ```sh
   adb install -r "/absolute/path/to/zadflix-libre-debug.apk"
   ```

The debug package is `com.zadorpataki.personalmedia.debug`, so it can coexist with the official Jellyfin Android app. Do not uninstall it merely to resolve a signature mismatch without first deciding whether its local downloads and settings can be deleted.

## Verification status

The current development branch has been exercised on a Samsung Galaxy S23 Ultra with the workstation running Jellyfin 10.11.11:

- Local Wi-Fi direct playback works at original quality.
- Cellular playback through Tailscale uses hardware-transcoded 720p at approximately 2.3 Mbps under the automatic policy.
- A 5.3 GB downloaded 1080p movie starts in airplane mode without contacting the workstation.
- Offline playback rendered its first frame in under one second in the latest device test.
- Library refresh is available to authenticated administrators.
- The Zadflix launcher icon, native screens, WebView theme, and startup screen have been visually verified.

For source changes, run the same checks used for the verified build:

```sh
./scripts/build-android.sh --tests
./gradlew detekt :app:lintLibreDebug
node --check app/src/main/assets/native/injectionScript.js
git diff --check
```

## Branches and upstream updates

- `master` tracks upstream Jellyfin for Android.
- `codex/personal-media-app` contains the Zadflix customization.
- `origin` is the personal fork.
- `upstream` is `https://github.com/jellyfin/jellyfin-android.git`.

Bring in upstream changes without rewriting the Zadflix branch:

```sh
git status --short
git fetch upstream
git switch master
git merge --ff-only upstream/master
git push origin master
git switch codex/personal-media-app
git merge master
./scripts/build-android.sh --tests
```

Stop before switching branches when the working tree contains uncommitted changes.

## Project scope and attribution

Zadflix is a personal, independently maintained fork. It is not an official Jellyfin client release, is not distributed through the Jellyfin app-store listings, and is not affiliated with Netflix. Netflix and Jellyfin names and trademarks belong to their respective owners.

The Android client is derived from [Jellyfin for Android](https://github.com/jellyfin/jellyfin-android) and retains its upstream copyright and licensing terms. Source code is licensed under the [GNU General Public License version 2](LICENSE.md).
