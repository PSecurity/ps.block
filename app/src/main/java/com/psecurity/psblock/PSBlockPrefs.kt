package com.psecurity.psblock

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * Fonte única de verdade para o estado do PS.Block.
 * Mantém o app independente do app principal e concentra toggles, políticas e logs.
 */
object PSBlockPrefs {

    private const val PREFS = "psblock_prefs"
    private const val MAX_LOG_LINES = 250
    private val logLock = Any()
    private val logDateFormatter = ThreadLocal.withInitial {
        java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.getDefault())
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Idioma do app: inglês por padrão, independente do idioma do Android.
    var Context.appLanguage: String
        get() = prefs(this).getString("app_language", "en") ?: "en"
        set(v) {
            val normalized = if (v == "pt-BR" || v == "pt" || v == "pt_BR") "pt-BR" else "en"
            prefs(this).edit().putString("app_language", normalized).apply()
        }

    // Sensores principais
    var Context.cameraBlocked: Boolean
        get() = prefs(this).getBoolean("camera_blocked", false)
        set(v) { prefs(this).edit().putBoolean("camera_blocked", v).apply() }

    var Context.micBlocked: Boolean
        get() = prefs(this).getBoolean("microphone_blocked", false)
        set(v) { prefs(this).edit().putBoolean("microphone_blocked", v).apply() }

    var Context.locationBlocked: Boolean
        get() = prefs(this).getBoolean("location_blocked", false)
        set(v) { prefs(this).edit().putBoolean("location_blocked", v).apply() }

    var Context.bluetoothBlocked: Boolean
        get() = prefs(this).getBoolean("bluetooth_blocked", false)
        set(v) { prefs(this).edit().putBoolean("bluetooth_blocked", v).apply() }

    // Modo / root
    var Context.psBlockMode: String
        get() = prefs(this).getString("psblock_mode", "ROOT") ?: "ROOT"
        set(v) { prefs(this).edit().putString("psblock_mode", v).apply() }

    var Context.rootAvailable: Boolean
        get() = prefs(this).getBoolean("root_available", false)
        set(v) { prefs(this).edit().putBoolean("root_available", v).apply() }

    var Context.rootEnabled: Boolean
        get() = prefs(this).getBoolean("root_enabled", false)
        set(v) { prefs(this).edit().putBoolean("root_enabled", v).apply() }

    // Bloqueio do app
    var Context.lockPasswordHash: String?
        get() = prefs(this).getString("lock_password_hash", null)
        set(v) { prefs(this).edit().putString("lock_password_hash", v).apply() }

    var Context.biometricEnabled: Boolean
        get() = prefs(this).getBoolean("biometric_enabled", false)
        set(v) { prefs(this).edit().putBoolean("biometric_enabled", v).apply() }

    var Context.appLockEnabled: Boolean
        get() = prefs(this).getBoolean("app_lock_enabled", false)
        set(v) { prefs(this).edit().putBoolean("app_lock_enabled", v).apply() }

    // Políticas automáticas inspiradas no APK funcional: Wi-Fi, geofence, agenda, chamadas e whitelist.
    var Context.autoBlockOnUnknownWifi: Boolean
        get() = prefs(this).getBoolean("auto_block_unknown_wifi", false)
        set(v) { prefs(this).edit().putBoolean("auto_block_unknown_wifi", v).apply() }

    var Context.autoBlockOnGeofenceExit: Boolean
        get() = prefs(this).getBoolean("auto_block_geofence_exit", false)
        set(v) { prefs(this).edit().putBoolean("auto_block_geofence_exit", v).apply() }

    var Context.scheduleBlockEnabled: Boolean
        get() = prefs(this).getBoolean("schedule_block_enabled", false)
        set(v) { prefs(this).edit().putBoolean("schedule_block_enabled", v).apply() }

    var Context.scheduleStartMinutes: Int
        get() = prefs(this).getInt("schedule_start_minutes", 22 * 60)
        set(v) { prefs(this).edit().putInt("schedule_start_minutes", v.coerceIn(0, 1439)).apply() }

    var Context.scheduleEndMinutes: Int
        get() = prefs(this).getInt("schedule_end_minutes", 7 * 60)
        set(v) { prefs(this).edit().putInt("schedule_end_minutes", v.coerceIn(0, 1439)).apply() }

    var Context.scheduleDaysMask: Int
        get() = prefs(this).getInt("schedule_days_mask", 0b1111111)
        set(v) { prefs(this).edit().putInt("schedule_days_mask", v and 0b1111111).apply() }

    var Context.blockDuringCallsEnabled: Boolean
        get() = prefs(this).getBoolean("block_during_calls_enabled", false)
        set(v) { prefs(this).edit().putBoolean("block_during_calls_enabled", v).apply() }

    var Context.whitelistReleaseEnabled: Boolean
        get() = prefs(this).getBoolean("whitelist_release_enabled", false)
        set(v) { prefs(this).edit().putBoolean("whitelist_release_enabled", v).apply() }

    fun Context.getSafeWifiNetworks(): Set<String> =
        prefs(this).getStringSet("safe_ssids", emptySet()) ?: emptySet()

    fun Context.setSafeWifiNetworks(ssids: Set<String>) {
        prefs(this).edit().putStringSet("safe_ssids", ssids).apply()
    }

    fun Context.getTrustedApps(): Set<String> =
        prefs(this).getStringSet("trusted_apps", emptySet()) ?: emptySet()

    fun Context.setTrustedApps(packages: Set<String>) {
        prefs(this).edit().putStringSet("trusted_apps", packages).apply()
    }

    fun Context.getGeofencesJson(): String =
        prefs(this).getString("geofences", "[]") ?: "[]"

    fun Context.setGeofencesJson(json: String) {
        prefs(this).edit().putString("geofences", json).apply()
    }

    // Liberação temporária autorizada para uso rápido da câmera/mic ou app confiável.
    var Context.temporaryReleaseUntil: Long
        get() = prefs(this).getLong("temporary_release_until", 0L)
        set(v) { prefs(this).edit().putLong("temporary_release_until", v).apply() }

    var Context.temporaryReleaseReason: String
        get() = prefs(this).getString("temporary_release_reason", "") ?: ""
        set(v) { prefs(this).edit().putString("temporary_release_reason", v).apply() }

    fun Context.clearTemporaryRelease() {
        prefs(this).edit()
            .putLong("temporary_release_until", 0L)
            .putString("temporary_release_reason", "")
            .apply()
    }



    // Coordenação de políticas automáticas: Wi-Fi desconhecido, geofence, agenda e chamada.
    // A primeira política automática salva o estado manual atual; a última política encerrada restaura esse estado.
    private const val AUTO_REASON_WIFI = "wifi"
    private const val AUTO_REASON_GEOFENCE = "geofence"
    private const val AUTO_REASON_SCHEDULE = "schedule"
    private const val AUTO_REASON_CALL = "call"

    fun Context.getAutoBlockReasons(): Set<String> =
        prefs(this).getStringSet("auto_block_reasons", emptySet()) ?: emptySet()

    private fun Context.setAutoBlockReasons(reasons: Set<String>) {
        prefs(this).edit().putStringSet("auto_block_reasons", reasons).apply()
    }

    var Context.autoSavedCameraBlocked: Boolean
        get() = prefs(this).getBoolean("auto_saved_camera_blocked", false)
        set(v) { prefs(this).edit().putBoolean("auto_saved_camera_blocked", v).apply() }

    var Context.autoSavedMicBlocked: Boolean
        get() = prefs(this).getBoolean("auto_saved_mic_blocked", false)
        set(v) { prefs(this).edit().putBoolean("auto_saved_mic_blocked", v).apply() }

    var Context.autoSavedLocationBlocked: Boolean
        get() = prefs(this).getBoolean("auto_saved_location_blocked", false)
        set(v) { prefs(this).edit().putBoolean("auto_saved_location_blocked", v).apply() }

    fun Context.activateAutoBlock(reason: String) {
        val current = getAutoBlockReasons().toMutableSet()
        if (current.isEmpty()) {
            autoSavedCameraBlocked = cameraBlocked
            autoSavedMicBlocked = micBlocked
            autoSavedLocationBlocked = locationBlocked
        }
        if (current.add(reason)) {
            setAutoBlockReasons(current)
            cameraBlocked = true
            micBlocked = true
            locationBlocked = true
            appendLog(AppLanguageManager.text(this, R.string.prefs_log_auto_applied, reason))
        }
    }

    fun Context.releaseAutoBlock(reason: String) {
        val current = getAutoBlockReasons().toMutableSet()
        if (!current.remove(reason)) return
        setAutoBlockReasons(current)
        if (current.isEmpty()) {
            cameraBlocked = autoSavedCameraBlocked
            micBlocked = autoSavedMicBlocked
            locationBlocked = autoSavedLocationBlocked
            appendLog(AppLanguageManager.text(this, R.string.prefs_log_auto_restored, reason))
        } else {
            appendLog(AppLanguageManager.text(this, R.string.prefs_log_auto_still_active, reason))
        }
    }

    fun Context.resetAutoBlockState() {
        setAutoBlockReasons(emptySet())
    }

    fun autoReasonWifi(): String = AUTO_REASON_WIFI
    fun autoReasonGeofence(): String = AUTO_REASON_GEOFENCE
    fun autoReasonSchedule(): String = AUTO_REASON_SCHEDULE
    fun autoReasonCall(): String = AUTO_REASON_CALL

    // Panic / SMS crítico
    var Context.panicSmsEnabled: Boolean
        get() = prefs(this).getBoolean("panic_sms_enabled", false)
        set(v) { prefs(this).edit().putBoolean("panic_sms_enabled", v).apply() }

    var Context.panicSmsNumber: String
        get() = prefs(this).getString("panic_sms_number", "") ?: ""
        set(v) { prefs(this).edit().putString("panic_sms_number", v).apply() }

    var Context.remoteWipeEnabled: Boolean
        get() = prefs(this).getBoolean("remote_wipe_enabled", false)
        set(v) { prefs(this).edit().putBoolean("remote_wipe_enabled", v).apply() }

    var Context.remoteWipeCode: String
        get() = prefs(this).getString("remote_wipe_code", "") ?: ""
        set(v) { prefs(this).edit().putString("remote_wipe_code", v).apply() }

    // Logs
    fun Context.appendLog(entry: String) {
        val timestamp = logDateFormatter.get()?.format(java.util.Date()) ?: ""
        val line = "[$timestamp] $entry"

        synchronized(logLock) {
            val existing = prefs(this).getString("security_log", "") ?: ""
            val trimmedExisting = existing
                .lineSequence()
                .filter { it.isNotBlank() }
                .take(MAX_LOG_LINES - 1)
                .joinToString("\n")

            val newLog = if (trimmedExisting.isBlank()) {
                line
            } else {
                "$line\n$trimmedExisting"
            }

            prefs(this).edit().putString("security_log", newLog).apply()
        }
    }

    fun Context.getLogs(): String =
        prefs(this).getString("security_log", AppLanguageManager.text(this, R.string.logs_empty)) ?: ""

    fun Context.clearLogs() {
        prefs(this).edit().putString("security_log", "").apply()
    }

    fun hashPassword(password: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest("PeekSecurity::PSBlock::v6::$password".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
