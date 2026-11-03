package com.mixtape.player.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Reads lists of songs from files people can export for free, no developer account needed:
 *  - Spotify's "Download your data" export (the .zip, or YourLibrary.json inside it)
 *  - CSV exports (Exportify, TuneMyMusic, Soundiiz, YouTube Music via Google Takeout, ...)
 *  - Plain text, one "Artist - Title" per line (also used for pasted lists)
 */
object TrackListParser {

    class Result(val name: String, val source: PlaylistSource, val entries: List<PlaylistEntry>)

    fun parseFile(fileName: String, bytes: ByteArray): Result {
        val baseName = fileName.substringBeforeLast('.').ifBlank { "Imported list" }
        if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            return parseZip(baseName, bytes)
        }
        return parseText(baseName, bytes.toString(Charsets.UTF_8).removePrefix("﻿"))
    }

    fun parseText(name: String, text: String): Result {
        val trimmed = text.trimStart()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            parseJson(name, trimmed)?.let { return it }
        }
        val firstLine = trimmed.lineSequence().firstOrNull().orEmpty()
        if (firstLine.contains(',') && headerColumns(parseCsvLine(firstLine)) != null) {
            return parseCsv(name, trimmed)
        }
        return Result(name, PlaylistSource.LIST, parseLines(trimmed))
    }

    // ---- Spotify data export -----------------------------------------------------------

    private fun parseZip(name: String, bytes: ByteArray): Result {
        val files = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val n = entry.name
                val wanted = !entry.isDirectory &&
                    (n.endsWith("YourLibrary.json", true) || n.endsWith(".csv", true) || n.endsWith(".json", true))
                if (wanted) files[n] = zip.readBytes()
            }
        }
        val library = files.entries.firstOrNull { it.key.endsWith("YourLibrary.json", true) }
            ?: files.entries.firstOrNull { it.key.endsWith(".csv", true) }
            ?: throw IOException(
                "That zip doesn't have a song list in it. For Spotify, use the \"Account data\" export, which contains YourLibrary.json."
            )
        return parseText(library.key.substringAfterLast('/').substringBeforeLast('.').ifBlank { name },
            library.value.toString(Charsets.UTF_8).removePrefix("﻿"))
    }

    private fun parseJson(name: String, text: String): Result? {
        val root = runCatching { JSONObject(text) }.getOrNull()
        // Spotify YourLibrary.json: {"tracks":[{"artist":"..","album":"..","track":"..","uri":".."}]}
        val tracks = root?.optJSONArray("tracks") ?: runCatching { JSONArray(text) }.getOrNull() ?: return null
        val entries = (0 until tracks.length()).mapNotNull { i ->
            val t = tracks.optJSONObject(i) ?: return@mapNotNull null
            val title = t.optString("track").ifBlank { t.optString("title") }.ifBlank { t.optString("name") }
            if (title.isBlank()) return@mapNotNull null
            val artist = t.optString("artist").ifBlank { t.optString("artistName") }
            val uri = t.optString("uri")
            PlaylistEntry(
                title = title,
                artist = artist,
                externalId = uri.ifBlank { null },
                externalUrl = uri.takeIf { it.startsWith("spotify:track:") }
                    ?.let { "https://open.spotify.com/track/" + it.removePrefix("spotify:track:") },
            )
        }
        if (entries.isEmpty()) return null
        val isSpotify = root?.has("tracks") == true && (root.has("albums") || root.has("shows") || entries.any { it.externalId?.startsWith("spotify:") == true })
        return if (isSpotify) Result("Spotify likes", PlaylistSource.SPOTIFY, entries)
        else Result(name, PlaylistSource.LIST, entries)
    }

    // ---- CSV -------------------------------------------------------------------------------

    private class Columns(val title: Int, val artist: Int, val duration: Int)

    private val titleNames = setOf("track name", "song title", "title", "track", "song", "name", "song name", "track title")
    private val artistNames = setOf("artist name(s)", "artist names", "artist name", "artist", "artists", "creator", "uploader", "artist name 1")

    private fun headerColumns(header: List<String>): Columns? {
        val h = header.map { it.trim().lowercase() }
        val title = h.indexOfFirst { it in titleNames }
        if (title < 0) return null
        val artist = h.indexOfFirst { it in artistNames }.takeIf { it >= 0 }
            ?: h.indexOfFirst { it.startsWith("artist") }
        val duration = h.indexOfFirst { it.contains("duration") }
        return Columns(title, artist, duration)
    }

    private fun parseCsv(name: String, text: String): Result {
        val rows = parseCsvRows(text)
        val cols = headerColumns(rows.first())
            ?: throw IOException("Couldn't find a song-title column in that file.")
        val entries = rows.drop(1).mapNotNull { row ->
            val title = row.getOrNull(cols.title)?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            val durationMs = row.getOrNull(cols.duration)?.trim()?.let(::parseDuration) ?: 0L
            PlaylistEntry(
                title = title,
                // Some exports separate multiple artists with ";" or ","; the first is enough to match.
                artist = row.getOrNull(cols.artist)?.trim().orEmpty().split(";").first().trim(),
                durationMs = durationMs,
            )
        }
        if (entries.isEmpty()) throw IOException("That file didn't contain any songs.")
        return Result(name, PlaylistSource.LIST, entries)
    }

    private fun parseDuration(v: String): Long {
        v.toLongOrNull()?.let { return if (it < 10_000) it * 1000 else it } // seconds or ms
        val parts = v.split(":").map { it.toLongOrNull() ?: return 0 }
        return parts.fold(0L) { acc, p -> acc * 60 + p } * 1000
    }

    private fun parseCsvLine(line: String): List<String> = parseCsvRows(line).firstOrNull().orEmpty()

    /** RFC 4180 CSV: quoted fields, doubled quotes, newlines inside quotes. */
    private fun parseCsvRows(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> { row.add(field.toString()); field.clear() }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(field.toString()); field.clear()
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = ArrayList()
                }
                else -> field.append(c)
            }
            i++
        }
        row.add(field.toString())
        if (row.any { it.isNotBlank() }) rows.add(row)
        return rows
    }

    // ---- plain text ------------------------------------------------------------------------

    private val numbering = Regex("""^\s*(?:\d+[.)]\s+|[-•*]\s+)""")

    /** "Artist - Title" per line; a line without a dash is treated as just a title. */
    fun parseLines(text: String): List<PlaylistEntry> =
        text.lineSequence()
            .map { it.replace(numbering, "").trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val parts = line.split(" - ", " – ", " — ", limit = 2)
                if (parts.size == 2) PlaylistEntry(title = parts[1].trim(), artist = parts[0].trim())
                else PlaylistEntry(title = line, artist = "")
            }
            .toList()
}
