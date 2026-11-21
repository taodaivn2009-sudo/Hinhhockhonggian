package com.example.ai

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import java.security.MessageDigest
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

data class SigningDiagnosticInfo(
    val actualSha1: String?,
    val expectedSha1s: List<String>
) {
    val isMismatch: Boolean
        get() = !actualSha1.isNullOrBlank() &&
            expectedSha1s.isNotEmpty() &&
            expectedSha1s.none { it.equals(actualSha1, ignoreCase = true) }
}

sealed interface GoogleSignInOutcome {
    data class Success(val user: GoogleUserProfile) : GoogleSignInOutcome
    data class Cancelled(val diagnosticHint: String? = null) : GoogleSignInOutcome
    data class Error(val message: String) : GoogleSignInOutcome
}

/**
 * Manages Google Sign-In via Jetpack Credential Manager + Firebase Auth.
 * Strictly builds one credential option per GetCredentialRequest:
 * - GetSignInWithGoogleOption for interactive sign-in button
 * - GetGoogleIdOption for silent auto sign-in on startup
 */
object GoogleAuthManager {

    /**
     * Reads the SHA-1 signing certificate fingerprint of the currently installed APK
     * and compares it against certificate_hash entries compiled from google-services.json.
     */
    fun inspectAppSigningSha1(context: Context): SigningDiagnosticInfo {
        val expected = BuildConfig.FIREBASE_EXPECTED_SHA1S
            .split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
        val actual = readInstalledApkSha1(context)
        return SigningDiagnosticInfo(actualSha1 = actual, expectedSha1s = expected)
    }

    @Suppress("DEPRECATION")
    private fun readInstalledApkSha1(context: Context): String? {
        return runCatching {
            val pm = context.packageManager
            val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pkgInfo = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = pkgInfo.signingInfo
                val sigs = if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
                    else signingInfo.signingCertificateHistory
                } else null
                sigs?.firstOrNull()?.toByteArray()
            } else {
                val pkgInfo = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
                pkgInfo.signatures?.firstOrNull()?.toByteArray()
            }
            if (certBytes == null) return@runCatching null
            val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
            digest.joinToString(":") { b -> "%02X".format(b) }
        }.getOrNull()
    }

    private fun upgradeAvatarResolution(rawUrl: String?): String? {
        val url = rawUrl?.trim().orEmpty()
        if (url.isEmpty()) return null
        return url.replace("=s96-c", "=s256-c")
    }

    private fun FirebaseUser.toProfile(fallbackToken: GoogleIdTokenCredential? = null): GoogleUserProfile {
        val resolvedName = displayName?.trim().orEmpty()
            .ifEmpty { fallbackToken?.displayName?.trim().orEmpty() }
            .ifEmpty { email?.substringBefore("@").orEmpty() }
            .ifEmpty { fallbackToken?.id?.substringBefore("@").orEmpty() }
            .ifEmpty { "Người dùng Google" }
        val resolvedEmail = email?.trim().orEmpty()
            .ifEmpty { fallbackToken?.id?.trim().orEmpty() }
        val resolvedPhoto = upgradeAvatarResolution(
            photoUrl?.toString()?.takeIf { it.isNotBlank() }
                ?: fallbackToken?.profilePictureUri?.toString()
        )
        return GoogleUserProfile(
            uid = uid,
            displayName = resolvedName,
            email = resolvedEmail,
            photoUrl = resolvedPhoto
        )
    }

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
                val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                val auth = ensureFirebaseAuth(context) ?: return null
                val result = auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(googleCred.idToken, null)
                ).await()
                result.user?.toProfile(googleCred) ?: auth.currentUser?.toProfile(googleCred)
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
            return GoogleSignInOutcome.Error(
                "Bản APK này chưa được gắn tệp google-services.json (hãy thêm Secret GOOGLE_SERVICES_JSON trên GitHub Actions hoặc dùng API Key riêng bên dưới)."
            )
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val credentialManager = CredentialManager.create(activity)
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                val auth = ensureFirebaseAuth(activity)
                    ?: return GoogleSignInOutcome.Error(
                        "Không thể khởi tạo Firebase Auth. Hãy kiểm tra tệp google-services.json trong bản build."
                    )
                val result = auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(googleCred.idToken, null)
                ).await()
                val profile = result.user?.toProfile(googleCred) ?: auth.currentUser?.toProfile(googleCred)
                if (profile != null) {
                    GoogleSignInOutcome.Success(profile)
                } else {
                    GoogleSignInOutcome.Error("Không lấy được thông tin tài khoản Google sau khi đăng nhập.")
                }
            } else {
                GoogleSignInOutcome.Error("Định dạng thông tin đăng nhập không hợp lệ (${cred.type}).")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            val rawMsg = e.message?.trim().orEmpty()
            val signingDiag = inspectAppSigningSha1(activity)
            // On Android 14-16, Google Play Services wraps SHA-1 / OAuth config mismatches
            // (such as "[16] Account reauth failed.") inside GetCredentialCancellationException.
            val looksLikeConfigOrAuthIssue = rawMsg.contains("28436") ||
                rawMsg.contains("28444") ||
                rawMsg.contains("10:") ||
                rawMsg.contains("[16]") ||
                rawMsg.contains("DEVELOPER_ERROR", ignoreCase = true) ||
                rawMsg.contains("caller", ignoreCase = true) ||
                rawMsg.contains("reauth", ignoreCase = true)
            if (looksLikeConfigOrAuthIssue) {
                val msg = when {
                    signingDiag.isMismatch -> {
                        "Lệch chữ ký SHA-1 giữa bản APK đang cài (${signingDiag.actualSha1}) và Firebase (${signingDiag.expectedSha1s.firstOrNull()}). Hãy thêm Secret DEBUG_KEYSTORE_BASE64 trên GitHub rồi build lại, hoặc sao chép SHA-1 bên dưới dán vào Firebase Console."
                    }
                    signingDiag.actualSha1 != null && signingDiag.expectedSha1s.isNotEmpty() -> {
                        "Google từ chối xác thực tài khoản (${rawMsg.take(90)}). Chữ ký SHA-1 của APK đã khớp (${signingDiag.actualSha1}) — hãy kiểm tra Google Play Services, xóa bộ nhớ đệm ứng dụng hoặc đăng nhập lại tài khoản Google trong Cài đặt máy."
                    }
                    else -> {
                        "Google từ chối xác thực ứng dụng (lỗi chữ ký SHA-1 hoặc OAuth). Hãy sao chép mã SHA-1 bên dưới để đối chiếu trên Firebase Console hoặc thêm Secret DEBUG_KEYSTORE_BASE64 trên GitHub! (${rawMsg.take(100)})"
                    }
                }
                GoogleSignInOutcome.Error(msg)
            } else {
                GoogleSignInOutcome.Cancelled(
                    diagnosticHint = if (rawMsg.isNotEmpty()) {
                        "Chưa hoàn tất đăng nhập Google ($rawMsg)."
                    } else {
                        "Đã hủy đăng nhập Google."
                    }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoCredentialException) {
            Log.w(AUTH_TAG, "No Google account available on device: ${e.message}", e)
            GoogleSignInOutcome.Error(
                "Thiết bị chưa có tài khoản Google khả dụng. Hãy thêm tài khoản Google trong Cài đặt máy rồi thử lại."
            )
        } catch (e: FirebaseAuthException) {
            Log.e(AUTH_TAG, "FirebaseAuth error (${e.errorCode}): ${e.message}", e)
            GoogleSignInOutcome.Error(
                "Lỗi xác thực Firebase (${e.errorCode}): ${e.localizedMessage ?: "Vui lòng kiểm tra mạng và thử lại."}"
            )
        } catch (e: GetCredentialException) {
            Log.e(AUTH_TAG, "CredentialManager error (${e.type}): ${e.message}", e)
            GoogleSignInOutcome.Error(
                "Lỗi đăng nhập Google (${e.type.substringAfterLast('.')}): ${e.localizedMessage ?: "Vui lòng thử lại."}"
            )
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
