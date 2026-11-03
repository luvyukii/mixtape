# Mixtape

An Android music player for the music on your phone. It finds and organizes your files automatically, has real crossfades and smart shuffle, and can import your liked songs from Spotify, YouTube and SoundCloud.

## What it does

- **Auto-organized library.** Every song on the phone shows up sorted into Songs, Albums, Artists, Genres and Folders. "feat." credits and "The" prefixes are ignored, so an artist doesn't get split into several entries. New files appear on their own.
- **Search** across songs, artists and albums.
- **Add music** by picking files or a whole folder. They're copied into `Music/Mixtape/`.
- **Crossfade** from 0 to 12 seconds. You can set it in Settings or right on the player screen. Two players blend on an equal-power curve, so the volume doesn't dip in the middle of the fade. Skipping a track fades quickly instead of cutting off.
- **Shuffle.** Smart shuffle keeps the same artist from playing twice in a row. There's also repeat all/one, play next, and add to queue.
- **Background playback** with a notification, lock-screen controls, and headphone/Bluetooth buttons. Music pauses when headphones are unplugged, and ducks for navigation prompts.
- **Playlists.** Make your own, or import your likes:
  - **Spotify / YouTube:** your likes become a playlist matched against files already on your phone. Tracks you don't have show greyed out with a link to open them.
  - **SoundCloud:** likes are imported the same way. Tracks whose artist turned on downloads can be saved to `Music/Mixtape/SoundCloud/` with one tap.

> The app doesn't rip audio from YouTube or Spotify. That breaks their terms, and Spotify audio is DRM-protected. Likes are matched to music you already own, and SoundCloud downloads only use the official download the artist chose to offer.

## Build and install

Every push to `main` builds the app on GitHub. Each finished build shows up on the repo's **Releases** page as `Mixtape.apk`.

1. On your phone, open the repo's **Releases** page and tap `Mixtape.apk` under the newest build.
2. When Android asks, allow your browser to install unknown apps.
3. Open Mixtape and allow access to music.

New builds install over the old one and keep your settings, because every build is signed with the same key from `keystore/`. It needs Android 10 or newer.

To build on a computer instead, open this folder in Android Studio and press **Run**.

## Import likes for free (no developer account)

Settings → **Import likes for free** reads files you can export yourself, then matches them to music on your phone:

- **Spotify:** spotify.com → Account → Privacy settings → *Download your data* → **Account data**. Spotify emails a `.zip` within a few days. Import the zip as-is; Mixtape reads `YourLibrary.json` from it.
- **YouTube Music:** [takeout.google.com](https://takeout.google.com), select only *YouTube and YouTube Music*, then import `music library songs.csv`.
- **Anything else:** any CSV with a title column and an artist column works (Exportify, TuneMyMusic, Soundiiz). You can also **Paste a list** with one `Artist - Title` per line.

## Hiding and deleting songs

Every song's ⋮ menu has two options:
- **Hide from library:** the file stays on the phone. Bring hidden songs back from Settings → Library.
- **Delete from phone:** permanently deletes the file. Android asks you to confirm first.

## Connecting your accounts (optional)

Each service needs a free developer app of your own. Only you will use it, so you can skip any review process.

### Spotify

1. Go to [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard) and **Create app**. As of February 2026, Spotify requires the app owner to have **Premium** for developer-mode apps.
2. Add the Redirect URI `mixtape://spotify-callback` and tick **Web API**.
3. Copy the **Client ID** into Mixtape → Settings → Spotify, tap **Connect**, then tap **Import likes**.

### YouTube

YouTube likes are private, so Google needs to know about the app once.

1. In [Google Cloud Console](https://console.cloud.google.com/), create a project and enable **YouTube Data API v3**.
2. Set up the **OAuth consent screen**: choose External, leave it in **Testing**, and add your own Google account under **Test users**.
3. **Credentials → Create credentials → OAuth client ID → Android**:
   - Package name: `com.mixtape.player`
   - SHA-1: `DB:05:B8:DA:0F:B6:D4:CB:B7:F8:13:9C:27:5C:52:33:D7:83:B0:EF` (the fixed key the builds are signed with)
4. In Mixtape, go to Settings → YouTube and tap **Sign in & import likes**.

"Music videos only" skips liked videos that YouTube doesn't categorize as music.

### SoundCloud

1. Register an app at [soundcloud.com/you/apps](https://soundcloud.com/you/apps).
2. Set the Redirect URI to `mixtape://soundcloud-callback`.
3. Paste the **Client ID** and **Client secret** into Settings → SoundCloud, then tap **Connect** and **Import likes**.
4. Open the SoundCloud likes playlist and tap **Download N the artists allow**, or use the ⬇ icon on a single track.

Sign-in tokens are stored privately on the phone. **Disconnect** removes them.

## How matching works

When a liked track is matched against your files, both titles are lower-cased and stripped of accents, bracketed parts like "(Official Video)" or "[Remastered]", "feat." credits, and suffixes like "- 2011 Remaster". The artist then has to match too. If the artist tag is missing, the song lengths have to agree within 3 seconds. Matching runs every time you open the playlist, so newly added files fill in on their own.

## Project layout

```
app/src/main/java/com/mixtape/player/
├── MixtapeApp.kt              app-wide objects
├── data/                      library scan + organizing, playlists, settings, import, matching
├── playback/
│   ├── CrossfadeEngine.kt     two-player crossfade, queue, shuffle, repeat, audio focus
│   └── PlaybackService.kt     background playback + notification controls
├── integrations/              Spotify, YouTube, SoundCloud (official APIs)
└── ui/                        Jetpack Compose screens
```

Built with Kotlin, Jetpack Compose (Material 3) and AndroidX Media3 (ExoPlayer). The only network libraries are Google sign-in and the platform HTTP client.
