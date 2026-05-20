package com.psecurity.psblock.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.psecurity.psblock.PSBlockPrefs.appendLog
import com.psecurity.psblock.PSBlockPrefs.cameraBlocked
import com.psecurity.psblock.PSBlockPrefs.getTrustedApps
import com.psecurity.psblock.PSBlockPrefs.micBlocked
import com.psecurity.psblock.PSBlockPrefs.whitelistReleaseEnabled
import com.psecurity.psblock.AppLanguageManager
import com.psecurity.psblock.R

class AppMonitorService : AccessibilityService() {

    private val knownSensorApps = setOf(
        "com.android.camera", "com.android.camera2", "com.sec.android.app.camera",
        "com.huawei.camera", "com.motorola.camera", "com.google.android.GoogleCamera",
        "com.instagram.android", "com.whatsapp", "com.snapchat.android",
        "org.telegram.messenger", "com.facebook.katana", "com.facebook.orca",
        "com.tiktok.musically", "com.zhiliaoapp.musically", "com.skype.raider",
        "com.microsoft.teams", "us.zoom.videomeetings", "com.google.android.apps.meetings"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val trustedApps = getTrustedApps()
        val trusted = trustedApps.contains(pkg)
        if (trusted && whitelistReleaseEnabled && (cameraBlocked || micBlocked)) {
            appendLog(AppLanguageManager.text(applicationContext, R.string.monitor_log_trusted_app_foreground, pkg))
            PSBlockService.tempRelease(applicationContext, AppLanguageManager.text(applicationContext, R.string.monitor_temp_release_reason, pkg), 90_000L)
            return
        }

        if ((cameraBlocked || micBlocked) && knownSensorApps.any { pkg.startsWith(it) }) {
            val reason = when {
                cameraBlocked && micBlocked -> AppLanguageManager.text(applicationContext, R.string.monitor_sensor_reason_both)
                cameraBlocked -> AppLanguageManager.text(applicationContext, R.string.monitor_sensor_reason_camera)
                else -> AppLanguageManager.text(applicationContext, R.string.monitor_sensor_reason_microphone)
            }
            appendLog(AppLanguageManager.text(applicationContext, R.string.monitor_log_sensor_app, pkg))
            OverlayService.trigger(applicationContext, pkg, reason)
        }
    }

    override fun onInterrupt() {}
}
