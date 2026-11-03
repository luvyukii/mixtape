package com.mixtape.player.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Album downloads from stores like Bandcamp arrive as a .zip. They're downloaded into the
 * app's private folder first; when finished, the music inside is copied into
 * Music/Nexus/Downloads/<album> and the zip is removed.
 */
object DownloadUnpacker {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = HashMap<Long, String>() // download id -> album folder name
    private var registered = false

    fun track(context: Context, downloadId: Long, folderName: String) {
        val app = context.applicationContext
        synchronized(pending) { pending[downloadId] = folderName }
        if (!registered) {
            registered = true
            ContextCompat.registerReceiver(
                app,
                object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                        val folder = synchronized(pending) { pending.remove(id) } ?: return
                        scope.launch { unpack(app, id, folder) }
                    }
                },
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }
    }

    private suspend fun unpack(context: Context, id: Long, folder: String) {
        val dm = context.getSystemService(DownloadManager::class.java)
        val file = dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return@use null
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) return@use null
            c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))?.let { File(android.net.Uri.parse(it).path!!) }
        } ?: return

        val importer = ImportManager(context)
        var count = 0
        try {
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.substringAfterLast('/')
                    if (entry.isDirectory || !ImportManager.isAudio(name, null)) continue
                    importer.saveAudio(
                        displayName = ImportManager.safeName(name),
                        mime = ImportManager.audioMime(name, null),
                        relativePath = "Music/Nexus/Downloads/${ImportManager.safeName(folder)}/",
                    ) { out -> zip.copyTo(out) }
                    count++
                }
            }
        } catch (e: Exception) {
            Log.w("DownloadUnpacker", "Couldn't unpack $file", e)
        } finally {
            file.delete()
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                if (count > 0) "Added $count songs from “$folder”" else "No music found in “$folder”",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
