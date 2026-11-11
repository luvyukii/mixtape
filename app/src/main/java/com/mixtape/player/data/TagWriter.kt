package com.mixtape.player.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.reference.PictureTypes
import java.io.File
import java.io.IOException

class UnsupportedFormatException(name: String) : IOException("Can't edit tags in $name")

/**
 * Writes edited info into the music file itself, so other apps see it too.
 * The file is copied out, edited, and copied back over the original.
 * Throws SecurityException (or RecoverableSecurityException) when Android
 * needs the person to allow NEXUS to change that file first.
 */
object TagWriter {

    init {
        // Lets the tag library handle pictures without desktop-Java image classes.
        TagOptionSingleton.getInstance().isAndroid = true
    }

    private val SUPPORTED = setOf("mp3", "m4a", "mp4", "aac", "flac", "ogg", "oga", "wav", "aif", "aiff", "wma", "dsf")

    fun write(context: Context, song: Song, edit: SongEdit) {
        val resolver = context.contentResolver
        val name = resolver.query(song.uri, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "track.mp3"
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in SUPPORTED) throw UnsupportedFormatException(name)

        // Check write access before doing any work (this is what throws for others' files).
        resolver.openFileDescriptor(song.uri, "rw")?.close()

        val tmp = File(context.cacheDir, "tagedit_${song.id}.$ext")
        try {
            resolver.openInputStream(song.uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: throw IOException("Couldn't read $name")

            val audio = AudioFileIO.read(tmp)
            val tag = audio.tagOrCreateAndSetDefault
            // Empty means "clear": remove the field instead of writing a blank one.
            fun put(key: FieldKey, value: String?) {
                when {
                    value == null -> Unit
                    value.isBlank() -> runCatching { tag.deleteField(key) }
                    else -> tag.setField(key, value)
                }
            }
            edit.title?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.TITLE, it) }
            edit.artist?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.ARTIST, it) }
            put(FieldKey.ALBUM, edit.album)
            put(FieldKey.ALBUM_ARTIST, edit.albumArtist)
            put(FieldKey.GENRE, edit.genre)
            edit.year?.let { y -> put(FieldKey.YEAR, if (y > 0) y.toString() else "") }
            edit.artwork?.let { path ->
                // Embed the chosen cover as the file's front picture.
                runCatching {
                    val bytes = File(path).readBytes()
                    val art = ArtworkFactory.getNew()
                    art.binaryData = bytes
                    art.mimeType = if (bytes.size > 3 && bytes[0] == 0x89.toByte()) "image/png" else "image/jpeg"
                    art.pictureType = PictureTypes.DEFAULT_ID
                    tag.deleteArtworkField()
                    tag.setField(art)
                }
            }
            edit.track?.let { t ->
                if (t % 1000 > 0) tag.setField(FieldKey.TRACK, (t % 1000).toString())
                if (t >= 1000) tag.setField(FieldKey.DISC_NO, (t / 1000).toString())
            }
            audio.commit()

            resolver.openOutputStream(song.uri, "wt")?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Couldn't write $name")
        } finally {
            tmp.delete()
        }

        // Update Android's index right away too, so every app shows the new info.
        val values = ContentValues().apply {
            edit.title?.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.TITLE, it) }
            edit.artist?.let { put(MediaStore.Audio.Media.ARTIST, it) }
            edit.album?.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.ALBUM, it) }
        }
        if (values.size() > 0) runCatching { resolver.update(song.uri, values, null, null) }
    }
}
