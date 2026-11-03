# NEXUS

An Android music player for the music on your phone. It finds and organizes your files automatically, with crossfades, smart shuffle, a volume booster and a live visualizer.

## What it does

- **Auto-organized library.** Every song on the phone shows up sorted into Songs, Albums, Artists, Genres and Folders. "feat." credits and "The" prefixes are ignored, so an artist doesn't get split into several entries. New files appear on their own.
- **Search** across songs, artists and albums.
- **Add music** by picking files or a whole folder. They're copied into `Music/Nexus/`.
- **Crossfade** from 0 to 12 seconds. You can set it in Settings or right on the player screen. Two players blend on an equal-power curve, so the volume doesn't dip in the middle of the fade. Skipping a track fades quickly instead of cutting off.
- **Shuffle.** Smart shuffle keeps the same artist from playing twice in a row. There's also repeat all/one, play next, and add to queue.
- **Background playback** with a notification, lock-screen controls, and headphone/Bluetooth buttons. Music pauses when headphones are unplugged, and ducks for navigation prompts.
- **Playlists.** Make your own playlists from your local music.


## Build and install

Every push to `main` builds the app on GitHub. Each finished build shows up on the repo's **Releases** page as `NEXUS.apk`.

1. On your phone, open the repo's **Releases** page and tap `NEXUS.apk` under the newest build.
2. When Android asks, allow your browser to install unknown apps.
3. Open NEXUS and allow access to music.

New builds install over the old one and keep your settings, because every build is signed with the same key from `keystore/`. It needs Android 10 or newer.

To build on a computer instead, open this folder in Android Studio and press **Run**.

## Browse

The **Browse** tab opens each site's own web player inside NEXUS, so you can search and play there.
- **Bandcamp:** buy or grab free/name-your-price music, then tap Download. Album zips are unpacked into `Music/Nexus/Downloads/<album>` automatically.
- **Signing in:** Google blocks sign-in inside apps' built-in browsers, so YouTube works signed out. Spotify's web player needs DRM support and may only play previews on some phones.

## Editing song info

Tap ⋮ → **Edit info…** on any song, or the ✏️ on an album page to change the whole album. Changes show in NEXUS right away. With "Also save into the file" checked, NEXUS also writes them into the file's tags so other apps see them; Android will ask permission for files NEXUS didn't create.

## Hiding and deleting songs

Every song's ⋮ menu has two options:
- **Hide from library:** the file stays on the phone. Bring hidden songs back from Settings → Library.
- **Delete from phone:** permanently deletes the file. Android asks you to confirm first.

## Project layout

```
app/src/main/java/com/mixtape/player/
├── NEXUSApp.kt              app-wide objects
├── data/                      library scan + organizing, playlists, settings, tag editing
├── playback/
│   ├── CrossfadeEngine.kt     two-player crossfade, queue, shuffle, repeat, audio focus
│   └── PlaybackService.kt     background playback + notification controls
└── ui/                        Jetpack Compose screens
```

Built with Kotlin, Jetpack Compose (Material 3) and AndroidX Media3 (ExoPlayer). 
