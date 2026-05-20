package com.psecurity.psblock

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import com.psecurity.psblock.PSBlockPrefs.appLockEnabled
import com.psecurity.psblock.PSBlockPrefs.lockPasswordHash

/**
 * Gate central para proteger o console do app por senha/biometria.
 * Mantém a tela protegida sem criar loop entre MainActivity, Settings e LockScreenActivity.
 */
object SecurityGate {
    private const val SESSION_TIMEOUT_MS = 5 * 60 * 1000L
    private var unlockedAt: Long = 0L

    fun markUnlocked() {
        unlockedAt = SystemClock.elapsedRealtime()
    }

    fun lockSession() {
        unlockedAt = 0L
    }

    fun isUnlocked(): Boolean {
        return unlockedAt > 0L && SystemClock.elapsedRealtime() - unlockedAt <= SESSION_TIMEOUT_MS
    }

    fun shouldLock(activity: Activity): Boolean {
        if (activity is LockScreenActivity) return false
        val enabled = activity.appLockEnabled
        val hasPassword = !activity.lockPasswordHash.isNullOrBlank()
        return enabled && hasPassword && !isUnlocked()
    }

    fun enforce(activity: AppCompatActivity): Boolean {
        if (!shouldLock(activity)) return true
        activity.startActivity(Intent(activity, LockScreenActivity::class.java))
        return false
    }
}
