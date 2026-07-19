#!/usr/bin/env bash

# Inspect a proposed Jellyfin library path without modifying its contents.

set -u

usage() {
	printf 'Usage: %s <movies|shows> <library-path>\n' "${0##*/}" >&2
	printf 'Example: %s shows "$HOME/Media/TV Shows"\n' "${0##*/}" >&2
}

if [[ $# -ne 2 ]]; then
	usage
	exit 2
fi

library_type="$1"
library_path="$2"

if [[ "$library_type" != "movies" && "$library_type" != "shows" ]]; then
	printf 'Error: library type must be either movies or shows.\n' >&2
	usage
	exit 2
fi

if [[ ! -d "$library_path" ]]; then
	printf 'Error: directory does not exist: %s\n' "$library_path" >&2
	exit 3
fi

printf 'Read-only inspection of: %s\n' "$library_path"
printf 'Library type: %s\n' "$library_type"

top_level_directories="$(find "$library_path" -mindepth 1 -maxdepth 1 -type d ! -name '.*' -print | wc -l | tr -d '[:space:]')"
printf 'Top-level folders: %s\n' "$top_level_directories"

printf 'Sample folders (up to 12):\n'
find "$library_path" -mindepth 1 -maxdepth 3 -type d ! -name '.*' -print | sed -n '1,12p'

if [[ "$library_type" == "movies" ]]; then
	direct_video_count="$(find "$library_path" -mindepth 1 -maxdepth 1 -type f \( \
		-iname '*.mkv' -o -iname '*.mp4' -o -iname '*.m4v' -o -iname '*.avi' -o -iname '*.mov' -o -iname '*.webm' \
	\) -print | wc -l | tr -d '[:space:]')"
	printf 'Video files directly in library root: %s\n' "$direct_video_count"
	if [[ "$direct_video_count" -gt 0 ]]; then
		printf 'Note: one folder per movie is recommended for reliable artwork and metadata.\n'
	fi
	printf 'Expected pattern: <root>/Movie Name (Year)/Movie Name (Year).mkv\n'
else
	shallow_episode_count="$(find "$library_path" -mindepth 1 -maxdepth 2 -type f \( \
		-iname '*.mkv' -o -iname '*.mp4' -o -iname '*.m4v' -o -iname '*.avi' -o -iname '*.mov' -o -iname '*.webm' \
	\) -print | wc -l | tr -d '[:space:]')"
	printf 'Episode files without a season-folder level: %s\n' "$shallow_episode_count"
	if [[ "$shallow_episode_count" -gt 0 ]]; then
		printf 'Note: put episodes under Season 01, Season 02, and so on for predictable matching.\n'
	fi
	printf 'Expected pattern: <root>/Show Name/Season 01/Show Name S01E01.mkv\n'
fi

printf 'No files were moved, renamed, created, or deleted.\n'
