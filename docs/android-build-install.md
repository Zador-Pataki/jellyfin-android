# Build and install the Android client

The repository builds a `libreDebug` APK by default. This variant excludes proprietary Google Cast dependencies, uses the debug application ID `com.zadorpataki.personalmedia.debug`, and can coexist with the official Jellyfin app.

The build script checks prerequisites, runs only repository Gradle tasks, and prints the absolute APK path and SHA-256 checksum. It does not install the app, create a release key, or embed release-signing credentials.

## Verified macOS toolchain

This setup was verified on Apple silicon macOS with:

- JDK 21 at `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
- Android SDK at `/opt/homebrew/share/android-commandlinetools`.
- Android platform 36 and build-tools 36.0.0.
- Android SDK Platform-Tools containing `adb`.

The script uses `JAVA_HOME`, `ANDROID_SDK_ROOT`, or `ANDROID_HOME` when they are already set. On this Mac it can also detect the Homebrew paths above. The project-local `local.properties` may contain an `sdk.dir` entry and is ignored by Git.

For an interactive terminal session, the equivalent environment is:

```sh
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export ANDROID_SDK_ROOT="/opt/homebrew/share/android-commandlinetools"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"
```

If SDK components are missing, install them with the SDK manager after reviewing its prompts and licenses:

```sh
"$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" \
  "platforms;android-36" \
  "build-tools;36.0.0" \
  "platform-tools"
```

## Build the APK

From the repository root, run:

```sh
./scripts/build-android.sh
```

Run the `libreDebug` unit tests before building:

```sh
./scripts/build-android.sh --tests
```

Request a clean build when diagnosing stale generated output:

```sh
./scripts/build-android.sh --clean --tests
```

The APK is written under `app/build/outputs/apk/libre/debug/`. Use the exact absolute path printed by the script rather than assuming the versioned filename.

## Prepare an Android device

Installing to a physical phone is an explicit manual step:

1. Enable Developer options by tapping **Build number** seven times in the phone's system settings.
2. Enable **USB debugging** under Developer options.
3. Connect the phone with a data-capable USB cable.
4. Unlock the phone and approve the computer's debugging key when prompted.
5. Confirm that the device state is `device`:

   ```sh
   "$ANDROID_SDK_ROOT/platform-tools/adb" devices -l
   ```

An `unauthorized` state means the approval prompt on the phone is still pending. An empty list usually means USB debugging, the cable, or the selected USB mode needs attention.

## Install the debug APK

First save the path printed by the successful build command:

```sh
APK_PATH="/absolute/path/printed/by/the/build/script.apk"
```

Then install or update the debug build:

```sh
"$ANDROID_SDK_ROOT/platform-tools/adb" install -r "$APK_PATH"
```

The `-r` option preserves the debug app's existing data during an upgrade when the installed and new APKs have compatible signatures. If Android reports a signature mismatch, do not automatically uninstall the existing app: uninstalling clears that app's local data. Review which package and data are on the device first.

After installation, open the app from the launcher and connect it to the workstation's Jellyfin server address. The workstation must be awake, Jellyfin Server must be running, and the phone must be able to reach the workstation over the selected network.

## Current device audit

`adb` is installed and functional on the verified Mac. The audit performed while preparing this guide found no connected Android device, and no APK was installed to a physical device.
