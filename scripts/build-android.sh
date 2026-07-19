#!/usr/bin/env bash

# Build the libre debug APK with a verified local Android toolchain.

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
readonly REQUIRED_ANDROID_API="36"
readonly MACOS_JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
readonly MACOS_ANDROID_SDK_ROOT="/opt/homebrew/share/android-commandlinetools"

run_tests=false
clean_build=false

usage() {
	cat <<'EOF'
Usage: ./scripts/build-android.sh [--tests] [--clean]

Build the libreDebug APK. Options:
  --tests  Run libreDebug unit tests before assembling the APK.
  --clean  Remove Gradle build outputs before testing/building.
  --help   Show this help.

The script never installs an APK or embeds release-signing credentials.
EOF
}

fail() {
	printf 'Error: %s\n' "$1" >&2
	exit 1
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--tests|--test)
			run_tests=true
			;;
		--clean)
			clean_build=true
			;;
		--help|-h)
			usage
			exit 0
			;;
		*)
			usage >&2
			fail "unknown option: $1"
			;;
	esac
	shift
done

[[ -f "$REPO_ROOT/settings.gradle.kts" ]] || fail "repository root is missing settings.gradle.kts: $REPO_ROOT"
[[ -f "$REPO_ROOT/gradlew" ]] || fail "Gradle wrapper is missing: $REPO_ROOT/gradlew"
[[ -x "$REPO_ROOT/gradlew" ]] || fail "Gradle wrapper is not executable; run: chmod +x '$REPO_ROOT/gradlew'"

if [[ -n "${JAVA_HOME:-}" ]]; then
	[[ -x "$JAVA_HOME/bin/java" ]] || fail "JAVA_HOME does not contain an executable bin/java: $JAVA_HOME"
elif [[ -x "$MACOS_JAVA_HOME/bin/java" ]]; then
	export JAVA_HOME="$MACOS_JAVA_HOME"
elif [[ "$(uname -s)" == "Darwin" && -x /usr/libexec/java_home ]]; then
	resolved_java_home="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
	[[ -n "$resolved_java_home" && -x "$resolved_java_home/bin/java" ]] || fail "JDK 21 was not found. Install it or set JAVA_HOME to a compatible JDK."
	export JAVA_HOME="$resolved_java_home"
elif command -v java >/dev/null 2>&1; then
	java_from_path="$(command -v java)"
else
	fail "Java was not found. Install JDK 21 or set JAVA_HOME."
fi

if [[ -n "${JAVA_HOME:-}" ]]; then
	java_command="$JAVA_HOME/bin/java"
else
	java_command="$java_from_path"
fi

java_version_line="$($java_command -version 2>&1 | sed -n '1p')"
java_major="$(printf '%s\n' "$java_version_line" | sed -E 's/.*version "([0-9]+).*/\1/')"
[[ "$java_major" =~ ^[0-9]+$ ]] || fail "could not determine the Java version from: $java_version_line"
[[ "$java_major" -ge 17 ]] || fail "Java 17 or newer is required; found: $java_version_line"

sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk_root" && -f "$REPO_ROOT/local.properties" ]]; then
	sdk_root="$(sed -n 's/^sdk\.dir=//p' "$REPO_ROOT/local.properties" | tail -n 1)"
fi
if [[ -z "$sdk_root" && -d "$MACOS_ANDROID_SDK_ROOT" ]]; then
	sdk_root="$MACOS_ANDROID_SDK_ROOT"
fi

[[ -n "$sdk_root" ]] || fail "Android SDK was not found. Set ANDROID_SDK_ROOT or add sdk.dir to local.properties."
[[ -d "$sdk_root" ]] || fail "Android SDK directory does not exist: $sdk_root"
[[ -f "$sdk_root/platforms/android-$REQUIRED_ANDROID_API/android.jar" ]] || fail "Android platform $REQUIRED_ANDROID_API is missing under $sdk_root/platforms. Install platforms;android-$REQUIRED_ANDROID_API with sdkmanager."

build_tools_binary="$(find "$sdk_root/build-tools" -mindepth 2 -maxdepth 2 -type f -name aapt2 -perm -111 2>/dev/null | sort | tail -n 1)"
[[ -n "$build_tools_binary" ]] || fail "Android build-tools are missing under $sdk_root/build-tools. Install build-tools;36.0.0 with sdkmanager."

if command -v shasum >/dev/null 2>&1; then
	checksum_command="shasum"
elif command -v sha256sum >/dev/null 2>&1; then
	checksum_command="sha256sum"
else
	fail "a SHA-256 utility is required (shasum or sha256sum)."
fi

export ANDROID_SDK_ROOT="$sdk_root"

printf 'Repository: %s\n' "$REPO_ROOT"
printf 'Java: %s\n' "$java_version_line"
printf 'Android SDK: %s\n' "$ANDROID_SDK_ROOT"
printf 'Android platform: %s\n' "$REQUIRED_ANDROID_API"
printf 'Android build tools: %s\n' "$(basename "$(dirname "$build_tools_binary")")"

gradle_tasks=()
if [[ "$clean_build" == true ]]; then
	gradle_tasks+=(clean)
fi
if [[ "$run_tests" == true ]]; then
	gradle_tasks+=(":app:testLibreDebugUnitTest")
fi
gradle_tasks+=(":app:assembleLibreDebug")

(
	cd "$REPO_ROOT"
	./gradlew --no-daemon "${gradle_tasks[@]}"
)

apk_directory="$REPO_ROOT/app/build/outputs/apk/libre/debug"
apk_list="$(find "$apk_directory" -maxdepth 1 -type f -name '*.apk' -print 2>/dev/null | sort)"
[[ -n "$apk_list" ]] || fail "Gradle completed but no libreDebug APK was found under $apk_directory"

printf '\nBuild output:\n'
while IFS= read -r apk_path; do
	printf 'APK: %s\n' "$apk_path"
	if [[ "$checksum_command" == "shasum" ]]; then
		checksum="$(shasum -a 256 "$apk_path" | awk '{print $1}')"
	else
		checksum="$(sha256sum "$apk_path" | awk '{print $1}')"
	fi
	printf 'SHA-256: %s\n' "$checksum"
done <<< "$apk_list"
