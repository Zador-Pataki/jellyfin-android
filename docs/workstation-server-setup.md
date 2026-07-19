# Workstation Jellyfin server setup

This project uses Jellyfin Server on the workstation as the media backend. The Android app is a client: the workstation reads the media files and streams only the data needed for playback.

## Current local state

The workstation inspection on 2026-07-19 found:

- Jellyfin Server 10.11.11 at `/Applications/Jellyfin.app`.
- A running server responding at `http://127.0.0.1:8096`.
- A server named `Personal Media` with the setup wizard complete.
- A Movies library rooted at `/Users/zadorpataki/Media/Movies`.
- A TV Shows library rooted at `/Users/zadorpataki/Media/TVShows`.
- VideoToolbox hardware decoding/encoding and tone mapping enabled on the Apple M2 Pro.
- UPnP and direct remote access disabled until the private-network phase is complete.
- `$HOME/Movies` contains media files alongside DaVinci Resolve and Apple TV-managed folders. It should not be selected wholesale as a Jellyfin movie library.

Existing files under `$HOME/Movies` were not moved or renamed. A small synthetic test movie was generated at `/Users/zadorpataki/Media/Movies/TV App Test (2026)` to verify scanning and byte-range streaming; it may be removed after phone testing.

## Start and verify on macOS

Start Jellyfin from Finder, Spotlight, or Terminal:

```sh
open -a Jellyfin
```

Then run the read-only status check from this repository:

```sh
./scripts/jellyfin-status.sh
```

The script defaults to `http://127.0.0.1:8096`. To check another address:

```sh
JELLYFIN_URL="http://192.168.1.20:8096" ./scripts/jellyfin-status.sh
```

Open the local web interface with:

```sh
open "http://127.0.0.1:8096"
```

Use the Jellyfin menu-bar item to stop or restart the server. Avoid force-killing it during library scans or database updates.

## Choose library paths without moving existing media

Do not move, rename, or reorganize existing media automatically. First identify the folders that already contain the actual movie and episode files. Use separate, explicit library roots rather than a broad folder containing unrelated application data.

Example placeholders for a disk attached to the workstation:

```text
/Volumes/<media-disk>/Media/Movies
/Volumes/<media-disk>/Media/TV Shows
```

Example placeholders for workstation-local storage:

```text
$HOME/Media/Movies
$HOME/Media/TV Shows
```

These are examples only. Do not create them or copy files into them unless that is the user's chosen storage plan. In particular, do not repurpose `$HOME/Movies` without reviewing its current Apple TV and video-editing contents.

Inspect a proposed library path without modifying it:

```sh
./scripts/check-media-layout.sh movies "/path/to/Movies"
./scripts/check-media-layout.sh shows "/path/to/TV Shows"
```

## Administrator credential

The administrator username is `zadorpataki`. Its generated password is stored in the macOS login Keychain under service `tv-app-jellyfin-admin`, not in this repository. To reveal it locally when signing in on the phone:

```sh
security find-generic-password -a zadorpataki -s tv-app-jellyfin-admin -w
```

That command prints the password, so run it only in a private terminal and do not paste the result into chat, documentation, or Git.

Do not expose port 8096 directly to the public internet. Initial Android testing should happen with the phone and workstation on the same trusted network. Remote access can be added later through a private VPN such as Tailscale.

## Connect the Android client on the local network

`127.0.0.1` works only on the workstation itself. The workstation address observed during setup was:

```text
http://192.168.1.112:8096
```

The router may change that address. Check the current Wi-Fi address with `ipconfig getifaddr en0` and append `:8096`.

The workstation must be awake, Jellyfin Server must be running, and both devices must be able to reach each other. Verify playback on local Wi-Fi before configuring remote access or transcoding.

## Verified streaming behavior

The synthetic test movie was scanned as a Jellyfin movie. A direct-play request for bytes `0-1048575` returned HTTP `206` with exactly 1 MiB, demonstrating partial delivery rather than a full-file download. A forced 640x360 HLS request produced three short segments, and Jellyfin's FFmpeg command used `videotoolbox`, `h264_videotoolbox`, and `scale_vt`.
