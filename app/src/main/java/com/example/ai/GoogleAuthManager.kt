package com.example.ai

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val AUTH_TAG = "Auth"

data class GoogleUserProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null
) {
    val initials: String
        get() {
            val base = displayName.trim().ifEmpty { email.substringBefore("@") }
            val parts = base.split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                parts.size >= 2 -> "${parts.first().first().uppercaseChar()}${parts.last().first().uppercaseChar()}"
                parts.size == 1 -> parts.first().take(2).uppercase()
                else -> "G"
            }
        }
}

sealed interface GoogleSignInOutcome {
    data class Success(val user: GoogleUserProfile) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Error(val message: String) : GoogleSignInOutcome
}

/**
 * Manages Google Sign-In via Jetpack Credential Manager + Firebase Auth.
 * Strictly builds one credential option per GetCredentialRequest:
 * - GetSignInWithGoogleOption for interactive sign-in button
 * - GetGoogleIdOption for silent auto sign-in on startup
 */
object GoogleAuthManager {

    private fun FirebaseUser.toProfile(): GoogleUserProfile = GoogleUserProfile(
        uid = uid,
        displayName = displayName?.trim().orEmpty().ifEmpty {
            email?.substringBefore("@").orEmpty().ifEmpty { "Người dùng Google" }
        },
        email = email?.trim().orEmpty(),
        photoUrl = photoUrl?.toString()
    )

    private fun ensureFirebaseAuth(context: Context? = null): FirebaseAuth? {
        return runCatching {
            if (context != null && FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            Firebase.auth
        }.getOrNull()
    }

    fun currentUserProfile(context: Context? = null): GoogleUserProfile? {
        return ensureFirebaseAuth(context)?.currentUser?.toProfile()
    }

    fun observeUserProfile(context: Context): Flow<GoogleUserProfile?> = callbackFlow {
        val auth = ensureFirebaseAuth(context)
        if (auth == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toProfile())
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser?.toProfile())
        awaitClose {
            runCatching { auth.removeAuthStateListener(listener) }
        }
    }

    /**
     * Silent auto sign-in on startup using GetGoogleIdOption only.
     * Fails silently when no authorized account exists on the device.
     */
    suspend fun attemptAutoSignIn(context: Context): GoogleUserProfile? {
        val existing = currentUserProfile(context)
        if (existing != null) return existing

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return null
        }
        if (clientId.isBlank()) return null

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val credentialManager = CredentialManager.create(context)
            val cred = credentialManager.getCredential(context, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val auth = ensureFirebaseAuth(context) ?: return null
                val result = auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(idToken, null)
                ).await()
                result.user?.toProfile() ?: auth.currentUser?.toProfile()
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoCredentialException) {
            null
        } catch (e: Exception) {
            Log.d(AUTH_TAG, "Silent Google auto sign-in skipped: ${e.message}")
            null
        }
    }

    /**
     * Interactive Google Sign-In using GetSignInWithGoogleOption only.
     */
    suspend fun signInWithGoogle(activity: Activity): GoogleSignInOutcome {
        val clientId = try {
            activity.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "default_web_client_id missing", e)
            return GoogleSignInOutcome.Error("Thiếu cấu hình đăng nhập Google (default_web_client_id).")
        }
        if (clientId.isBlank()) {
            return GoogleSignInOutcome.Error("Cấu hình Web Client ID trống.")
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val credentialManager = CredentialManager.create(activity)
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val auth = ensureFirebaseAuth(activity)
                    ?: return GoogleSignInOutcome.Error("Không thể khởi tạo Firebase Auth.")
                val result = auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(idToken, null)
                ).await()
                val profile = result.user?.toProfile() ?: auth.currentUser?.toProfile()
                if (profile != null) {
                    GoogleSignInOutcome.Success(profile)
                } else {
                    GoogleSignInOutcome.Error("Không lấy được thông tin tài khoản Google sau khi đăng nhập.")
                }
            } else {
                GoogleSignInOutcome.Error("Định dạng thông tin đăng nhập không hợp lệ.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            GoogleSignInOutcome.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoCredentialException) {
            Log.w(AUTH_TAG, "No Google account available on device: ${e.message}", e)
            GoogleSignInOutcome.Error("Thiết bị chưa có tài khoản Google khả dụng. Hãy thêm tài khoản Google trong Cài đặt máy rồi thử lại.")
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Google Sign-In failed", e)
            GoogleSignInOutcome.Error(e.localizedMessage ?: "Đăng nhập Google thất bại. Vui lòng thử lại.")
        }
    }

    /**
     * Clears Credential Manager state and signs out of Firebase Auth inside NonCancellable
     * so UI unmount cannot interrupt teardown.
     */
    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            try {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(AUTH_TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                runCatching { ensureFirebaseAuth(context)?.signOut() }
            }
        }
    }
}
