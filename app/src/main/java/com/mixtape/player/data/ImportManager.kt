package com.mixtape.player.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * Copies music into Music/Nexus/ so Android indexes it and it shows up in the library.
 * Music that's already in shared storage appears automatically and doesn't need importing.
 */
class ImportManager(context: Context) {

    private val appContext = context.applicationContext
    private val resolver get() = appContext.contentResolver

    /** Returns how many files were copied (duplicates are skipped). */
    suspend fun importFiles(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        uris.count { copyIn(it, "Imported", null, null) }
    }

    /** Copies every audio file in a folder (and its subfolders). */
    suspend fun importFolder(tree: Uri, onProgress: (Int) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(appContext, tree) ?: return@withContext 0
        var copied = 0
        fun walk(dir: DocumentFile, path: String) {
            for (f in dir.listFiles()) {
                if (f.isDirectory) {
                    walk(f, "$path/${safeName(f.name ?: "Folder")}")
                } else if (isAudio(f.name, f.type)) {
                    if (copyIn(f.uri, path, f.name, f.type)) {
                        copied++
                        onProgress(copied)
                    }
                }
            }
        }
        walk(root, "Imported/${safeName(root.name ?: "Folder")}")
        copied
    }

    private fun copyIn(src: Uri, subfolder: String, nameHint: String?, mimeHint: String?): Boolean {
        val name = safeName(nameHint ?: displayName(src) ?: "track_${System.currentTimeMillis()}")
        val mime = audioMime(name, mimeHint ?: resolver.getType(src))
        val relative = "${Environment.DIRECTORY_MUSIC}/Nexus/$subfolder/"
        if (alreadyExists(name, relative)) return false
        return runCatching {
            saveAudio(name, mime, relative) { out ->
                resolver.openInputStream(src)?.use { it.copyTo(out) }
                    ?: error("Couldn't open $name")
            }
        }.isSuccess
    }

    /**
     * Writes a new audio file into shared storage. [extra] can set TITLE/ARTIST so the
     * library looks right even before Android reads the file's own tags.
     */
    fun saveAudio(
        displayName: String,
        mime: String,
        relativePath: String,
        extra: ContentValues.() -> Unit = {},
        write: (OutputStream) -> Unit,
    ): Uri {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
            extra()
        }
        val uri = resolver.insert(collection, values) ?: error("Couldn't create $displayName")
        try {
            resolver.openOutputStream(uri)?.use(write) ?: error("Couldn't write $displayName")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun alreadyExists(name: String, relative: String): Boolean =
        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
            arrayOf(name, relative),
            null,
        )?.use { it.count > 0 } ?: false

    private fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    companion object {
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "aif", "aiff", "alac", "mka"
        )

        fun safeName(name: String): String =
            name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "Untitled" }

        fun isAudio(name: String?, mime: String?): Boolean =
            mime?.startsWith("audio/") == true ||
                name?.substringAfterLast('.', "")?.lowercase() in AUDIO_EXTENSIONS

        /** MediaStore only accepts audio MIME types in the audio collection. */
        fun audioMime(name: String, given: String?): String {
            if (given != null && given.startsWith("audio/")) return given
            val ext = name.substringAfterLast('.', "").lowercase()
            val guessed = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            return if (guessed != null && guessed.startsWith("audio/")) guessed else "audio/mpeg"
        }
    }
}
