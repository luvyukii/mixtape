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
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.mixtape.player.data.DownloadUnpacker
import com.mixtape.player.data.ImportManager
import com.mixtape.player.data.PlaylistSource
import com.mixtape.player.ui.theme.Tac
import com.mixtape.player.ui.components.TacTabsRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/** The sites you can browse, each in its own official web player. */
enum class BrowseSite(
    val label: String,
    val mobileHome: String,
    val desktopHome: String,
    val source: PlaylistSource,
    /** Hosts allowed inside the in-app browser; anything else opens in your normal browser. */
    val hosts: List<String>,
    /** Which view a site opens in. Spotify's player only runs in its desktop view. */
    val startDesktop: Boolean,
) {
    YouTube(
        "YouTube", "https://m.youtube.com", "https://www.youtube.com", PlaylistSource.YOUTUBE,
        listOf("youtube.com", "youtu.be", "google.com", "gstatic.com", "googlevideo.com", "ytimg.com"), false,
    ),
    SoundCloud(
        "SoundCloud", "https://m.soundcloud.com", "https://soundcloud.com", PlaylistSource.SOUNDCLOUD,
        listOf("soundcloud.com", "sndcdn.com", "soundcloud.cloud"), false,
    ),
    Bandcamp(
        "Bandcamp", "https://bandcamp.com", "https://bandcamp.com", PlaylistSource.LIST,
        listOf("bandcamp.com", "bcbits.com"), false,
    ),
    Spotify(
        "Spotify", "https://open.spotify.com", "https://open.spotify.com", PlaylistSource.SPOTIFY,
        listOf("spotify.com", "scdn.co", "spotifycdn.com"), true,
    ),
    /** A general-purpose browser that starts at Google; any site stays inside it. */
    Web(
        "Web", "https://www.google.com", "https://www.google.com", PlaylistSource.LIST,
        emptyList(), false,
    );

    fun allows(host: String) = hosts.isEmpty() || hosts.any { host == it || host.endsWith(".$it") }
}

/** Keeps each site's page alive while you switch tabs. */
@Stable
class BrowseState {
    var site by mutableIntStateOf(0)
    val views = HashMap<BrowseSite, WebView>()
    val desktop = mutableStateMapOf<BrowseSite, Boolean>()
    var progress by mutableIntStateOf(100)
    var canGoBack by mutableStateOf(false)
    var pageTitle by mutableStateOf("")
    var url by mutableStateOf("")

    fun isDesktop(site: BrowseSite) = desktop[site] ?: site.startDesktop
}

private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

/** Width desktop pages are laid out at; the page is then scaled to fit the screen exactly. */
private const val DESKTOP_WIDTH = 1100

@Composable
fun BrowseScreen(
    state: BrowseState,
    onMessage: (String) -> Unit,
    onSaveToWishlist: (title: String, artist: String, url: String, source: PlaylistSource) -> Unit,
) {
    val site = BrowseSite.entries[state.site]
    val desktop = state.isDesktop(site)

    BackHandler(enabled = state.canGoBack) { state.views[site]?.goBack() }

    Column(Modifier.fillMaxSize()) {
        TacTabsRow(
            labels = BrowseSite.entries.map { it.label },
            selected = state.site,
            onSelect = { state.site = it },
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tac.GreenFaint))

        if (site == BrowseSite.Web) {
            AddressBar(
                url = state.url,
                onGo = { input -> state.views[site]?.loadUrl(toUrl(input)) },
            )
        }

        // Toolbar
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = { state.views[site]?.goBack() }, enabled = state.canGoBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = if (state.canGoBack) Tac.Green else Tac.GreenFaint)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    state.pageTitle.ifBlank { site.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = Tac.Green,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (desktop) "DESKTOP VIEW" else "PHONE VIEW",
                    style = MaterialTheme.typography.labelSmall,
                    color = Tac.GreenMid,
                )
            }
            IconButton(onClick = {
                state.desktop[site] = !desktop
                state.views[site]?.let { applyMode(it, site, !desktop, reload = true) }
            }) {
                Icon(
                    if (desktop) Icons.Rounded.Smartphone else Icons.Rounded.Computer,
                    if (desktop) "Switch to phone view" else "Switch to desktop view",
                    tint = Tac.GreenMid,
                )
            }
            IconButton(onClick = {
                val view = state.views[site] ?: return@IconButton
                val parsed = parsePageTitle(site, view.title.orEmpty())
                if (parsed == null) onMessage("Open a song's page first, then tap save.")
                else onSaveToWishlist(parsed.first, parsed.second, view.url.orEmpty(), site.source)
            }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Save this song to your Wishlist", tint = Tac.GreenMid) }
            IconButton(onClick = { state.views[site]?.reload() }) { Icon(Icons.Rounded.Refresh, "Reload", tint = Tac.GreenMid) }
        }

        if (site == BrowseSite.SoundCloud) {
            Text(
                if (desktop) "DOWNLOAD: open a track → ••• More → Download file (only if the artist allows it)"
                else "Downloads are only offered in desktop view. Tap the screen icon above to switch.",
                style = MaterialTheme.typography.bodySmall,
                color = Tac.GreenMid,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        // Thin loading line instead of a chunky bar
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(1.dp)
                .background(Tac.GreenFaint)
        ) {
            if (state.progress < 100) {
                Box(
                    Modifier
                        .fillMaxWidth(state.progress / 100f)
                        .height(1.dp)
                        .background(Tac.Green)
                )
            }
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
                state.url = view.url.orEmpty()
            },
        )
    }
}

/**
 * Sets up phone or desktop view. Phone view uses the site's own mobile layout at the
 * screen's width. Desktop view lays the page out at a fixed desktop width and scales it to
 * fit the screen exactly, so there's no sideways scrolling; pinch to zoom in.
 */
private fun applyMode(web: WebView, site: BrowseSite, desktop: Boolean, reload: Boolean) {
    web.settings.apply {
        userAgentString = if (desktop) DESKTOP_UA else null // null restores the phone's default
        useWideViewPort = true
        loadWithOverviewMode = true
        textZoom = if (desktop) 115 else 100
    }
    if (!reload) return
    val current = web.url
    val target = when {
        current == null -> if (desktop) site.desktopHome else site.mobileHome
        site == BrowseSite.SoundCloud && desktop -> current.replace("://m.soundcloud.com", "://soundcloud.com")
        site == BrowseSite.SoundCloud && !desktop -> current.replace("://soundcloud.com", "://m.soundcloud.com")
        site == BrowseSite.YouTube && desktop -> current.replace("://m.youtube.com", "://www.youtube.com")
        site == BrowseSite.YouTube && !desktop -> current.replace("://www.youtube.com", "://m.youtube.com")
        else -> current
    }
    web.loadUrl(target)
}

/** Forces a desktop page's layout width so it fits the screen instead of overflowing. */
private val FIT_DESKTOP_JS = """
(function() {
  var m = document.querySelector('meta[name=viewport]');
  if (!m) { m = document.createElement('meta'); m.name = 'viewport'; document.head.appendChild(m); }
  m.content = 'width=$DESKTOP_WIDTH, initial-scale=' + (window.screen.width / $DESKTOP_WIDTH) + ', user-scalable=yes';
})();
"""

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(context: Context, site: BrowseSite, state: BrowseState, onMessage: (String) -> Unit): WebView {
    val web = WebView(context)
    web.setBackgroundColor(android.graphics.Color.BLACK)
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
    web.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        // Pinch-to-zoom, without the old on-screen +/- buttons.
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        cacheMode = WebSettings.LOAD_DEFAULT
    }
    // Ask sites for their dark theme (and darken those without one) to match the app.
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(web.settings, true)
    }
    applyMode(web, site, state.isDesktop(site), reload = false)

    fun isActive() = BrowseSite.entries[state.site] == site

    web.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val host = uri.host.orEmpty()
            val scheme = uri.scheme.orEmpty()
            if ((scheme == "http" || scheme == "https") && site.allows(host)) {
                return false // stay in the in-app browser
            }
            // Other sites and app links (vnd.youtube:, spotify:) open outside the app.
            if (scheme == "intent") return true // "open in app" prompts; ignore them
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
            }
            return true
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (state.isDesktop(site)) view.evaluateJavascript(FIT_DESKTOP_JS, null)
            if (isActive()) {
                state.canGoBack = view.canGoBack()
                state.pageTitle = view.title.orEmpty()
                state.url = view.url.orEmpty()
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            // Single-page sites (Spotify, SoundCloud) change pages without a full load.
            if (state.isDesktop(site)) view.evaluateJavascript(FIT_DESKTOP_JS, null)
            if (isActive()) {
                state.canGoBack = view.canGoBack()
                state.url = view.url.orEmpty()
            }
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
            if (site.hosts.isNotEmpty() && site.allows(host) &&
                PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID in request.resources
            ) {
                request.grant(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))
            } else {
                request.deny()
            }
        }
    }

    // Saves files the site itself offers to download (e.g. a SoundCloud track whose
    // artist enabled downloads, or a Bandcamp purchase) into Music/Nexus/Downloads.
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
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Nexus/Downloads/$name")
            dm.enqueue(request)
            onMessage("Downloading “$name” to Music/Nexus/Downloads")
        }
    }

    web.loadUrl(if (state.isDesktop(site)) site.desktopHome else site.mobileHome)
    return web
}

/** Turns what was typed into a web address, or a Google search. */
private fun toUrl(input: String): String {
    val t = input.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") -> t
        !t.contains(' ') && t.contains('.') -> "https://$t"
        else -> "https://www.google.com/search?q=" + Uri.encode(t)
    }
}

@Composable
private fun AddressBar(url: String, onGo: (String) -> Unit) {
    var text by remember(url) { mutableStateOf(url) }
    val focus = LocalFocusManager.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .border(1.dp, Tac.GreenDim, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("›", style = MaterialTheme.typography.titleLarge, color = Tac.GreenMid)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = Tac.Green),
            cursorBrush = SolidColor(Tac.Bright),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
            keyboardActions = KeyboardActions(onGo = {
                focus.clearFocus()
                if (text.isNotBlank()) onGo(text)
            }),
            modifier = Modifier.weight(1f),
        )
    }
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
        BrowseSite.Web -> t.split(" - ", " | ", limit = 2).takeIf { it.size == 2 && t.length < 120 }
            ?.let { it[1].trim() to it[0].trim() }
        BrowseSite.YouTube -> { // "Artist - Song (Official Video) - YouTube"
            if (!t.endsWith(" - YouTube") || t == "YouTube") null else {
                val video = t.removeSuffix(" - YouTube")
                val parts = video.split(" - ", " – ", " — ", limit = 2)
                if (parts.size == 2) parts[1].trim() to parts[0].trim() else video to ""
            }
        }
    }
}
