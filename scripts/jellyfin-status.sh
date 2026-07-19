#!/usr/bin/env bash

# Read-only Jellyfin Server installation and health check.

set -u

readonly DEFAULT_JELLYFIN_URL="http://127.0.0.1:8096"
readonly JELLYFIN_APP="/Applications/Jellyfin.app"

jellyfin_url="${JELLYFIN_URL:-$DEFAULT_JELLYFIN_URL}"
jellyfin_url="${jellyfin_url%/}"
public_info_url="${jellyfin_url}/System/Info/Public"

if ! command -v curl >/dev/null 2>&1; then
	printf 'Error: curl is required for the Jellyfin health check.\n' >&2
	exit 2
fi

printf 'Jellyfin URL: %s\n' "$jellyfin_url"

if [[ "$(uname -s)" == "Darwin" ]]; then
	if [[ -d "$JELLYFIN_APP" ]]; then
		if command -v plutil >/dev/null 2>&1; then
			installed_version="$(plutil -extract CFBundleShortVersionString raw "$JELLYFIN_APP/Contents/Info.plist" 2>/dev/null || true)"
		else
			installed_version=""
		fi

		if [[ -n "$installed_version" ]]; then
			printf 'macOS app: installed (%s)\n' "$installed_version"
		else
			printf 'macOS app: installed\n'
		fi
	else
		printf 'macOS app: not found at %s\n' "$JELLYFIN_APP"
	fi
fi

response_file="$(mktemp "${TMPDIR:-/tmp}/jellyfin-status.XXXXXX")"
cleanup() {
	rm -f "$response_file"
}
trap cleanup EXIT

if ! http_status="$(curl \
	--silent \
	--show-error \
	--connect-timeout 3 \
	--max-time 8 \
	--output "$response_file" \
	--write-out '%{http_code}' \
	"$public_info_url")"; then
	printf 'Health: unreachable\n' >&2
	printf 'On macOS, start the server with: open -a Jellyfin\n' >&2
	exit 3
fi

if [[ ! "$http_status" =~ ^2[0-9][0-9]$ ]]; then
	printf 'Health: unexpected HTTP status %s\n' "$http_status" >&2
	exit 4
fi

printf 'Health: responding (HTTP %s)\n' "$http_status"

if command -v jq >/dev/null 2>&1; then
	jq -r '
		"Server: \(.ServerName // "unknown")",
		"Product: \(.ProductName // "Jellyfin Server")",
		"Server version: \(.Version // "unknown")",
		"Setup wizard completed: \(.StartupWizardCompleted // "unknown")"
	' "$response_file"
elif command -v python3 >/dev/null 2>&1; then
	python3 - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as response:
    info = json.load(response)

print(f"Server: {info.get('ServerName', 'unknown')}")
print(f"Product: {info.get('ProductName', 'Jellyfin Server')}")
print(f"Server version: {info.get('Version', 'unknown')}")
completed = info.get("StartupWizardCompleted", "unknown")
print(f"Setup wizard completed: {str(completed).lower() if isinstance(completed, bool) else completed}")
PY
else
	printf 'Server details: JSON parser unavailable (install jq or python3 to display them)\n'
fi
