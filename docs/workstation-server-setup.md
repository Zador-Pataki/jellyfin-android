# Workstation Jellyfin server setup

This project uses Jellyfin Server on the workstation as the media backend. The Android app is a client: the workstation reads the media files and streams only the data needed for playback.

## Current local state

The workstation inspection on 2026-07-19 found:

- Jellyfin Server 10.11.11 at `/Applications/Jellyfin.app`.
- A running server responding at `http://127.0.0.1:8096`.
- A server named `Zadflix` with the setup wizard complete.
- A macOS login item that starts Jellyfin automatically after the user logs in.
- A Movies library rooted at `/Users/zadorpataki/Zadflix/Moviesa`.
- A TV Shows library rooted at `/Users/zadorpataki/Zadflix/TVShows`.
- VideoToolbox hardware decoding/encoding and tone mapping enabled on the Apple M2 Pro.
- Tailscale private remote access enabled and verified at `100.123.144.13`; UPnP remains disabled, so Jellyfin does not request a public router port mapping.
- `$HOME/Movies` contains media files alongside DaVinci Resolve and Apple TV-managed folders. It should not be selected wholesale as a Jellyfin movie library.

Existing files under `$HOME/Movies` were not moved or renamed. A small synthetic test movie is stored at `/Users/zadorpataki/Zadflix/Moviesa/TV App Test (2026)` to verify scanning and byte-range streaming; it may be removed after phone testing.

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

The configured workstation-local roots are:

```text
/Users/zadorpataki/Zadflix/Moviesa
/Users/zadorpataki/Zadflix/TVShows
```

The `Moviesa` spelling is the configured path and should not be silently corrected or renamed. Do not copy files into either root without reviewing the user's intended media placement. In particular, do not repurpose `$HOME/Movies` without reviewing its current Apple TV and video-editing contents.

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

Do not expose port 8096 directly to the public internet. Remote access is provided through Tailscale, and automatic router port mapping remains disabled.

## Connect the Android client on the local network

`127.0.0.1` works only on the workstation itself. The workstation address observed during setup was:

```text
http://192.168.1.112:8096
```

The router may change that address. Check the current Wi-Fi address with `ipconfig getifaddr en0` and append `:8096`.

The workstation must be awake, Jellyfin Server must be running, and both devices must be able to reach each other. Verify playback separately over local Wi-Fi and over Tailscale before tuning transcoding.

## Connect the Android client through Tailscale

Tailscale is signed in and running on the workstation. Both of these private server addresses were verified on 2026-07-19:

```text
http://zadors-macbook-pro.tailce1ef2.ts.net:8096
http://100.123.144.13:8096
```

Prefer the MagicDNS name because it remains readable and avoids copying the numeric address. To connect from the phone:

1. Install Tailscale from Google Play and sign in to the same `zadorpataki14@gmail.com` tailnet.
2. Confirm that Tailscale shows the phone as connected.
3. Open Zadflix and add `http://zadors-macbook-pro.tailce1ef2.ts.net:8096` as the server.
4. Sign in with the Jellyfin administrator username and the password stored in the Mac's Keychain.

The `http` URL is carried inside Tailscale's encrypted tunnel; port 8096 is not forwarded through the router. The workstation must remain awake with Jellyfin and Tailscale running. Verify either address from the Mac with:

```sh
JELLYFIN_URL="http://100.123.144.13:8096" ./scripts/jellyfin-status.sh
JELLYFIN_URL="http://zadors-macbook-pro.tailce1ef2.ts.net:8096" ./scripts/jellyfin-status.sh
```

For a real away-from-home test, leave Tailscale enabled on the phone, turn off phone Wi-Fi, and play the synthetic test movie over cellular data. If the MagicDNS name does not resolve, confirm that both devices are online in the same tailnet and try the numeric Tailscale address.

## Verified streaming behavior

The synthetic test movie was scanned as a Jellyfin movie. A direct-play request for bytes `0-1048575` returned HTTP `206` with exactly 1 MiB, demonstrating partial delivery rather than a full-file download. A forced 640x360 HLS request produced three short segments, and Jellyfin's FFmpeg command used `videotoolbox`, `h264_videotoolbox`, and `scale_vt`.
