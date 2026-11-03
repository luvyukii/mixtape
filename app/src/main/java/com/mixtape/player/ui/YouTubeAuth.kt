package com.mixtape.player.ui

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.mixtape.player.integrations.YouTubeClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import java.io.IOException

/**
 * Gets a short-lived YouTube read-only token with Google's account picker. The first time,
 * Google shows a consent screen; after that it's silent. Must be created while the
 * activity is being constructed (it registers an activity-result callback).
 */
class YouTubeAuth(private val activity: ComponentActivity) {

    private var pending: CompletableDeferred<String>? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val waiting = pending ?: return@registerForActivityResult
        pending = null
        if (result.resultCode != Activity.RESULT_OK) {
            waiting.completeExceptionally(IOException("Google sign-in was cancelled."))
            return@registerForActivityResult
        }
        try {
            val auth = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(result.data)
            val token = auth.accessToken
            if (token != null) waiting.complete(token)
            else waiting.completeExceptionally(IOException("Google didn't return a token."))
        } catch (e: Exception) {
            waiting.completeExceptionally(explain(e))
        }
    }

    /** Turns Google's numeric error codes into something a person can act on. */
    private fun explain(e: Exception): IOException {
        val code = (e as? ApiException)?.statusCode
        val text = when (code) {
            CommonStatusCodes.DEVELOPER_ERROR ->
                "Google doesn't recognize Mixtape yet. In Google Cloud, create an Android OAuth client with package " +
                    "com.mixtape.player and SHA-1 DB:05:B8:DA:0F:B6:D4:CB:B7:F8:13:9C:27:5C:52:33:D7:83:B0:EF (see README). " +
                    "Or use Settings → Import likes for free instead."
            CommonStatusCodes.NETWORK_ERROR -> "Couldn't reach Google. Check your connection and try again."
            CommonStatusCodes.CANCELED, 12501 -> "Google sign-in was cancelled."
            CommonStatusCodes.SIGN_IN_REQUIRED -> "Sign in to a Google account on this phone first."
            null -> "Google sign-in failed: ${e.message}"
            else -> "Google sign-in failed (code $code): ${e.message}"
        }
        return IOException(text, e)
    }

    suspend fun accessToken(): String {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(YouTubeClient.SCOPE)))
            .build()
        val result = try {
            Identity.getAuthorizationClient(activity).authorize(request).await()
        } catch (e: Exception) {
            throw explain(e)
        }
        val pendingIntent = result.pendingIntent
        if (result.hasResolution() && pendingIntent != null) {
            val waiting = CompletableDeferred<String>()
            pending = waiting
            launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            return waiting.await()
        }
        return result.accessToken ?: throw IOException("Google didn't return a token.")
    }
}
