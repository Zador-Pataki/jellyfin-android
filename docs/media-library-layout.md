# Jellyfin media library naming

Good names let Jellyfin match metadata reliably without changing the media itself. The layouts below are targets and examples, not instructions to move existing files without review.

## Movies

Use one folder per movie and include the release year when known:

```text
Movies/
├── Arrival (2016)/
│   ├── Arrival (2016).mkv
│   └── Arrival (2016).en.srt
└── Spirited Away (2001)/
    └── Spirited Away (2001).mp4
```

Keep extras inside the movie folder in a recognized subfolder such as `extras/`, rather than mixing them into the library root.

## TV shows

Use a folder per show, a folder per season, and `SxxEyy` episode numbers:

```text
TV Shows/
└── Example Show (2024)/
    ├── Season 00/
    │   └── Example Show S00E01.mkv
    ├── Season 01/
    │   ├── Example Show S01E01.mkv
    │   ├── Example Show S01E01.en.srt
    │   └── Example Show S01E02.mkv
    └── Season 02/
        └── Example Show S02E01.mkv
```

Use `Season 00` for specials. A combined episode can use a name such as `Example Show S01E01-E02.mkv`. The title after the episode number is optional; the season and episode code is the important part.

## Before changing names

1. Run `./scripts/check-media-layout.sh` against the existing folder.
2. Review the mismatches and confirm the media path with the user.
3. Back up or test a small sample before any bulk rename.
4. Keep subtitle base names aligned with their video names.

The repository scripts are deliberately read-only. They report layout characteristics but never move, rename, create, or delete media.
