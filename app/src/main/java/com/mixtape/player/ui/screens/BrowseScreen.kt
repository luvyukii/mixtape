package com.mixtape.player.ui.screens

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mixtape.player.data.DownloadUnpacker
import com.mixtape.player.data.ImportManager
import com.mixtape.player.data.PlaylistSource

/** The three sites you can browse, each in its own official web player. */
enum class BrowseSite(
    val label: String,
    val home: String,
    val source: PlaylistSource,
    /** Hosts allowed inside the in-app browser; anything else opens in your normal browser. */
    val hosts: List<String>,
    /** Spotify's web player only runs with a desktop browser identity. */
    val desktop: Boolean,
) {
    YouTube(
        "YouTube", "https://m.youtube.com", PlaylistSource.YOUTUBE,
        listOf("youtube.com", "youtu.be", "google.com", "gstatic.com", "googlevideo.com", "ytimg.com"), false,
    ),
    SoundCloud(
        "SoundCloud", "https://m.soundcloud.com", PlaylistSource.SOUNDCLOUD,
        listOf("soundcloud.com", "sndcdn.com", "soundcloud.cloud"), false,
    ),
    Bandcamp(
        "Bandcamp", "https://bandcamp.com", PlaylistSource.LIST,
        listOf("bandcamp.com", "bcbits.com"), false,
    ),
    Spotify(
        "Spotify", "https://open.spotify.com", PlaylistSource.SPOTIFY,
        listOf("spotify.com", "scdn.co", "spotifycdn.com"), true,
    ),
}

/** Keeps each site's page alive while you switch tabs. */
@Stable
class BrowseState {
    var site by mutableIntStateOf(0)
    val views = HashMap<BrowseSite, WebView>()
    val titles = HashMap<BrowseSite, String>()
    var progress by mutableIntStateOf(100)
    var canGoBack by mutableStateOf(false)
    var pageTitle by mutableStateOf("")
}

private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    state: BrowseState,
    onMessage: (String) -> Unit,
    onSaveToWishlist: (title: String, artist: String, url: String, source: PlaylistSource) -> Unit,
) {
    val site = BrowseSite.entries[state.site]

    BackHandler(enabled = state.canGoBack) { state.views[site]?.goBack() }

    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            BrowseSite.entries.forEachIndexed { i, s ->
                SegmentedButton(
                    selected = state.site == i,
                    onClick = { state.site = i },
                    shape = SegmentedButtonDefaults.itemShape(i, BrowseSite.entries.size),
                ) { Text(s.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = { state.views[site]?.goBack() }, enabled = state.canGoBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
            Text(
                state.pageTitle.ifBlank { site.label },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                val view = state.views[site] ?: return@IconButton
                val parsed = parsePageTitle(site, view.title.orEmpty())
                if (parsed == null) onMessage("Open a song's page first, then tap save.")
                else onSaveToWishlist(parsed.first, parsed.second, view.url.orEmpty(), site.source)
            }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Save this song to your Wishlist") }
            IconButton(onClick = { state.views[site]?.reload() }) { Icon(Icons.Rounded.Refresh, "Reload") }
        }
        if (state.progress < 100) {
            LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
        }

        AndroidView(
            factory = { ctx -> android.widget.FrameLayout(ctx) },
            modifier = Modifier.fillMaxSize(),
            update = { frame ->
                val view = state.views.getOrPut(site) { createWebView(frame.context, site, state, onMessage) }
                if (frame.getChildAt(0) !== view) {
                    frame.removeAllViews()
                    (view.parent as? ViewGroup)?.removeView(view)
                    frame.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
                state.canGoBack = view.canGoBack()
                state.pageTitle = view.title.orEmpty()
            },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(context: Context, site: BrowseSite, state: BrowseState, onMessage: (String) -> Unit): WebView {
    val web = WebView(context)
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
    web.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        loadWithOverviewMode = true
        useWideViewPort = true
        if (site.desktop) userAgentString = DESKTOP_UA
    }

    fun isActive() = BrowseSite.entries[state.site] == site

    web.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val host = uri.host.orEmpty()
            val scheme = uri.scheme.orEmpty()
            if ((scheme == "http" || scheme == "https") && site.hosts.any { host == it || host.endsWith(".$it") }) {
                return false // stay in the in-app browser
            }
            // Other sites, app links (intent://, vnd.youtube:, spotify:) open outside Mixtape.
            if (scheme == "intent") return true // "open in app" prompts; ignore them
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
            }
            return true
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (isActive()) {
                state.canGoBack = view.canGoBack()
                state.pageTitle = view.title.orEmpty()
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            if (isActive()) state.canGoBack = view.canGoBack()
        }
    }

    web.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (isActive()) state.progress = newProgress
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (isActive()) state.pageTitle = title.orEmpty()
        }

        // Spotify's player needs protected-media (DRM) playback.
        override fun onPermissionRequest(request: PermissionRequest) {
            val host = request.origin.host.orEmpty()
            if (site.hosts.any { host == it || host.endsWith(".$it") } &&
                PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID in request.resources
            ) {
                request.grant(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))
            } else {
                request.deny()
            }
        }
    }

    // Saves files the site itself offers to download (e.g. a SoundCloud track whose
    // artist enabled downloads) into Music/Mixtape/Downloads.
    web.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
        if (!url.startsWith("http")) {
            onMessage("This download can't be saved from here.")
            return@setDownloadListener
        }
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val isZip = name.endsWith(".zip", ignoreCase = true) || mimeType == "application/zip"
        if (!isZip && !ImportManager.isAudio(name, mimeType)) {
            onMessage("Only music files can be saved.")
            return@setDownloadListener
        }
        val request = DownloadManager.Request(Uri.parse(url))
            .addRequestHeader("User-Agent", userAgent)
            .apply { CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) } }
            .setTitle(name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val dm = context.getSystemService(DownloadManager::class.java)
        if (isZip) {
            // Albums come zipped: download privately, then unpack the music into the library.
            request.setDestinationInExternalFilesDir(context, null, "downloads/$name")
            DownloadUnpacker.track(context, dm.enqueue(request), name.removeSuffix(".zip").removeSuffix(".ZIP"))
            onMessage("Downloading “$name”. The songs will appear in your library when it's done.")
        } else {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Mixtape/Downloads/$name")
            dm.enqueue(request)
            onMessage("Downloading “$name” to Music/Mixtape/Downloads")
        }
    }

    web.loadUrl(site.home)
    return web
}

/** Pulls song title and artist out of the page title, or null if this isn't a song page. */
fun parsePageTitle(site: BrowseSite, pageTitle: String): Pair<String, String>? {
    val t = pageTitle.trim()
    return when (site) {
        BrowseSite.SoundCloud -> // "Stream Song by Artist | Listen online for free on SoundCloud"
            Regex("""^(?:Stream )?(.+?) by (.+?) \|.*SoundCloud""").find(t)?.let { it.groupValues[1] to it.groupValues[2] }
        BrowseSite.Bandcamp -> // "Song | Artist" (track pages) or "Album | Artist"
            t.split(" | ").takeIf { it.size == 2 && "bandcamp" !in t.lowercase() }?.let { it[0].trim() to it[1].trim() }
        BrowseSite.Spotify -> // "Song - song and lyrics by Artist | Spotify"
            Regex("""^(.+?) - song(?: and lyrics)? by (.+?) \| Spotify""").find(t)?.let { it.groupValues[1] to it.groupValues[2] }
        BrowseSite.YouTube -> { // "Artist - Song (Official Video) - YouTube"
            if (!t.endsWith(" - YouTube") || t == "YouTube") null else {
                val video = t.removeSuffix(" - YouTube")
                val parts = video.split(" - ", " – ", " — ", limit = 2)
                if (parts.size == 2) parts[1].trim() to parts[0].trim() else video to ""
            }
        }
    }
}
