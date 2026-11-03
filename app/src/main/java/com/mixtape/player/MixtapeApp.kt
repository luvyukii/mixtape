package com.mixtape.player

import android.app.Application
import com.mixtape.player.data.ImportManager
import com.mixtape.player.data.LibraryRepository
import com.mixtape.player.data.PlaylistStore
import com.mixtape.player.data.Settings
import com.mixtape.player.integrations.SoundCloudClient
import com.mixtape.player.integrations.SpotifyClient
import com.mixtape.player.integrations.YouTubeClient
import com.mixtape.player.playback.CrossfadeEngine

/** Holds the app-wide objects; playback outlives any single screen. */
class MixtapeApp : Application() {

    lateinit var settings: Settings
        private set
    lateinit var library: LibraryRepository
        private set
    lateinit var engine: CrossfadeEngine
        private set
    lateinit var playlists: PlaylistStore
        private set
    lateinit var importer: ImportManager
        private set
    lateinit var spotify: SpotifyClient
        private set
    lateinit var soundCloud: SoundCloudClient
        private set
    lateinit var youTube: YouTubeClient
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        library = LibraryRepository(this, settings)
        engine = CrossfadeEngine(this, settings)
        playlists = PlaylistStore(this)
        importer = ImportManager(this)
        spotify = SpotifyClient(settings)
        soundCloud = SoundCloudClient(settings)
        youTube = YouTubeClient()
    }
}
