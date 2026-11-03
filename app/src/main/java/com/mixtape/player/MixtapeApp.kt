package com.mixtape.player

import android.app.Application
import com.mixtape.player.data.ImportManager
import com.mixtape.player.data.LibraryRepository
import com.mixtape.player.data.MetadataStore
import com.mixtape.player.data.PlaylistStore
import com.mixtape.player.data.Settings
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
    lateinit var metadata: MetadataStore
        private set

    /** The startup animation plays once per launch, not on every screen rotation. */
    var bootShown = false

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        metadata = MetadataStore(this)
        library = LibraryRepository(this, settings, metadata)
        engine = CrossfadeEngine(this, settings)
        playlists = PlaylistStore(this)
        importer = ImportManager(this)
    }
}
