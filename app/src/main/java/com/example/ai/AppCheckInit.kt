package com.example.ai

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val APP_CHECK_TAG = "AppCheckInit"

// App Check contract keys defined by the Firebase SDK and runtime harness.
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

// Separate flag that records whether a real harness debug token was injected via Intent extra.
// Prevents a random unregistered UUID generated on a real phone from masquerading as a harness token.
private const val HARNESS_PREFS_NAME = "appcheck_harness_state"
private const val KEY_HAS_HARNESS_TOKEN = "has_harness_debug_token"

@Volatile
private var installedAppCheckProviderMode: String? = null

/**
 * Human-readable label of the currently installed App Check provider for diagnostics.
 */
fun activeAppCheckProviderLabel(): String = installedAppCheckProviderMode ?: "Chưa khởi tạo"

/**
 * Configures Firebase App Check before Compose setContent, ViewModels, or any Firebase operation.
 * Must be called from MainActivity.onCreate(savedInstanceState) with the Activity's launch intent.
 *
 * Provider selection:
 * 1. Debug builds launched by the AI Studio emulator/test harness (carrying FIREBASE_APPCHECK_DEBUG_TOKEN
 *    in the launch intent, or previously configured by the harness) -> DebugAppCheckProviderFactory.
 * 2. Real Android devices (both sideloaded debug APKs built via GitHub Actions and release APKs) ->
 *    RecaptchaAppCheckProviderFactory using `recaptcha_site_key` from firebase_applet_config.xml,
 *    avoiding unregistered random debug UUIDs that cause "Firebase App Check token is invalid (403)".
 *
 * Idempotency: skips redundant provider re-installation across Activity recreations (e.g., rotation,
 * theme toggle, returning from Camera/Photo Picker) unless a fresh harness debug token is delivered.
 */
@SuppressLint("ApplySharedPref", "DiscouragedApi")
fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        if (FirebaseApp.getApps(context).isEmpty()) {
            Log.e(APP_CHECK_TAG, "FirebaseApp unavailable (google-services.json missing); skipping App Check.")
            installedAppCheckProviderMode = "Chưa có Firebase (thiếu google-services.json)"
            return
        }

        val harnessPrefs = context.getSharedPreferences(HARNESS_PREFS_NAME, Context.MODE_PRIVATE)
        val debugTokenFromIntent = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()

        if (BuildConfig.DEBUG && !debugTokenFromIntent.isNullOrEmpty()) {
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val debugPrefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
            // CRITICAL: synchronous commit() ensures the secret is written before
            // DebugAppCheckProviderFactory reads it.
            debugPrefs.edit()
                .putString(DEBUG_SECRET_KEY, debugTokenFromIntent)
                .commit()
            harnessPrefs.edit()
                .putBoolean(KEY_HAS_HARNESS_TOKEN, true)
                .commit()

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            installedAppCheckProviderMode = "Debug App Check (Harness)"
            Log.d(APP_CHECK_TAG, "DebugAppCheckProviderFactory installed with harness debug token.")
            return
        }

        // Avoid tearing down an already-initialized App Check provider on Activity recreation
        // (e.g., returning from Camera capture or screen rotation).
        if (installedAppCheckProviderMode != null) {
            Log.d(APP_CHECK_TAG, "App Check already initialized ($installedAppCheckProviderMode); reusing.")
            return
        }

        if (BuildConfig.DEBUG && harnessPrefs.getBoolean(KEY_HAS_HARNESS_TOKEN, false)) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            installedAppCheckProviderMode = "Debug App Check (Harness)"
            Log.d(APP_CHECK_TAG, "DebugAppCheckProviderFactory installed using persisted harness token.")
            return
        }

        // Real device (including sideloaded GitHub debug APKs & release APKs):
        // Use reCAPTCHA Enterprise App Check, which attests Android devices without requiring Play Store.
        val siteKey = resolveRecaptchaSiteKey(context)
        if (!siteKey.isNullOrBlank()) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                RecaptchaAppCheckProviderFactory.getInstance(siteKey)
            )
            installedAppCheckProviderMode = "reCAPTCHA Enterprise App Check"
            Log.d(APP_CHECK_TAG, "RecaptchaAppCheckProviderFactory installed for real device attestation.")
        } else if (BuildConfig.DEBUG) {
            Log.w(APP_CHECK_TAG, "recaptcha_site_key unavailable; falling back to DebugAppCheckProviderFactory.")
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            installedAppCheckProviderMode = "Debug App Check (Fallback)"
        } else {
            installedAppCheckProviderMode = "Thiếu recaptcha_site_key"
            Log.e(APP_CHECK_TAG, "recaptcha_site_key string resource not found or blank in release package.")
        }
    } catch (e: Throwable) {
        installedAppCheckProviderMode = "Lỗi khởi tạo (${e.javaClass.simpleName})"
        Log.e(APP_CHECK_TAG, "Failed to initialize Firebase App Check", e)
    }
}

@SuppressLint("DiscouragedApi")
private fun resolveRecaptchaSiteKey(context: Context): String? {
    val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
    if (resId == 0) return null
    return runCatching { context.getString(resId).trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
}
