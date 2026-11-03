package com.mixtape.player.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.app.RecoverableSecurityException
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import com.mixtape.player.data.Song
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mixtape.player.playback.PlaybackService
import com.mixtape.player.ui.theme.MixtapeTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val youTubeAuth = YouTubeAuth(this)
    private var controller: ListenableFuture<MediaController>? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.onPermissionResult(hasAudioPermission())
        }

    private val pickFiles =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) vm.importFiles(uris)
        }

    /** Android's own "Allow Mixtape to delete this file?" confirmation. */
    private val deleteConfirm =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val songs = vm.pendingDelete
            vm.pendingDelete = emptyList()
            if (result.resultCode != RESULT_OK || songs.isEmpty()) return@registerForActivityResult
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                vm.onSongsDeleted(songs) // Android already deleted them
            } else {
                deleteSongs(songs) // Android 10 granted access; now delete for real
            }
        }

    private val pickLikesFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(vm::importLikesFile)
        }

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let(vm::importFolder)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleSignInRedirect(intent)

        val granted = hasAudioPermission()
        vm.onPermissionResult(granted)
        if (!granted && savedInstanceState == null) askForPermissions()

        val platform = PlatformActions(
            openUrl = ::openUrl,
            pickFiles = { pickFiles.launch(arrayOf("audio/*")) },
            pickFolder = { pickFolder.launch(null) },
            requestPermission = ::askForPermissions,
            importYouTube = { vm.importYouTubeLikes { youTubeAuth.accessToken() } },
            deleteSongs = ::deleteSongs,
            pickLikesFile = { pickLikesFile.launch(arrayOf("*/*")) },
        )
        setContent {
            MixtapeTheme {
                MixtapeRoot(vm, platform)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSignInRedirect(intent)
    }

    override fun onStart() {
        super.onStart()
        // Connecting a controller starts the playback service, which shows the
        // notification and keeps music going after the app is closed.
        controller = MediaController.Builder(
            this, SessionToken(this, ComponentName(this, PlaybackService::class.java))
        ).buildAsync()
    }

    override fun onStop() {
        controller?.let(MediaController::releaseFuture)
        controller = null
        super.onStop()
    }

    private fun handleSignInRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "mixtape") {
            vm.handleSignInRedirect(data)
            intent.data = null // don't handle it again after rotation
        }
    }

    private fun audioPermission() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasAudioPermission() =
        ContextCompat.checkSelfPermission(this, audioPermission()) == PackageManager.PERMISSION_GRANTED

    private fun askForPermissions() {
        val wanted = mutableListOf(audioPermission())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) wanted += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(wanted.toTypedArray())
    }

    private fun deleteSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        vm.pendingDelete = songs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val request = MediaStore.createDeleteRequest(contentResolver, songs.map { it.uri })
            deleteConfirm.launch(IntentSenderRequest.Builder(request.intentSender).build())
            return
        }
        // Android 10: files Mixtape created can be deleted directly; others need permission.
        val deleted = ArrayList<Song>()
        for (song in songs) {
            try {
                contentResolver.delete(song.uri, null, null)
                deleted += song
            } catch (e: RecoverableSecurityException) {
                vm.pendingDelete = songs - deleted.toSet()
                if (deleted.isNotEmpty()) vm.onSongsDeleted(deleted)
                deleteConfirm.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                return
            } catch (e: SecurityException) {
                vm.pendingDelete = emptyList()
                vm.showMessage("Android didn't allow deleting “${song.title}”")
                return
            }
        }
        vm.pendingDelete = emptyList()
        vm.onSongsDeleted(deleted)
    }

    private fun openUrl(url: String) {
        val uri = Uri.parse(url)
        try {
            CustomTabsIntent.Builder().build().launchUrl(this, uri)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }
}
