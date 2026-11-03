package com.mixtape.player.ui

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
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
            waiting.completeExceptionally(IOException("Google sign-in failed: ${e.message}", e))
        }
    }

    suspend fun accessToken(): String {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(YouTubeClient.SCOPE)))
            .build()
        val result = try {
            Identity.getAuthorizationClient(activity).authorize(request).await()
        } catch (e: Exception) {
            throw IOException(
                "Google sign-in isn't set up for this app yet. See the README's YouTube section. (${e.message})", e
            )
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
