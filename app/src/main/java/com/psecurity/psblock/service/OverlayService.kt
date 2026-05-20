package com.psecurity.psblock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.psecurity.psblock.MainActivity
import com.psecurity.psblock.PSBlockPrefs.appendLog
import com.psecurity.psblock.R
import com.psecurity.psblock.AppLanguageManager

class OverlayService : Service() {

    private var wm: WindowManager? = null
    private var view: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val CHANNEL_ID = "ps_block_overlay"
        private const val NOTIF_ID = 1003

        fun trigger(ctx: Context, pkg: String, reason: String = AppLanguageManager.text(ctx, R.string.overlay_default_reason)) {
            val i = Intent(ctx, OverlayService::class.java).apply {
                putExtra("pkg", pkg)
                putExtra("reason", reason)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try { startForeground(NOTIF_ID, buildNotification()) } catch (_: Exception) {}
        val pkg = intent?.getStringExtra("pkg") ?: "app desconhecido"
        val reason = intent?.getStringExtra("reason") ?: "acesso suspeito"
        appendLog(AppLanguageManager.text(this, R.string.overlay_log_visible, pkg, reason))
        showOverlay(pkg, reason)
        handler.postDelayed({ removeOverlay(); stopSelf() }, 5000)
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "PS.Block Overlay", NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(AppLanguageManager.text(this, R.string.overlay_notification_title))
            .setContentText(AppLanguageManager.text(this, R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_psblock)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(false)
            .build()
    }

    private fun showOverlay(pkg: String, reason: String) {
        removeOverlay()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F0050008"))
            setPadding(40, 32, 40, 32)
            gravity = Gravity.CENTER
        }

        TextView(this).apply {
            text = AppLanguageManager.text(this@OverlayService, R.string.overlay_header)
            setTextColor(Color.parseColor("#C084FC"))
            textSize = 13f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            gravity = Gravity.CENTER
            container.addView(this)
        }

        TextView(this).apply {
            text = AppLanguageManager.text(this@OverlayService, R.string.overlay_title)
            setTextColor(Color.parseColor("#E9D5FF"))
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            container.addView(this)
        }

        TextView(this).apply {
            text = "$pkg\n$reason"
            setTextColor(Color.parseColor("#A0A0C0"))
            textSize = 11f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
            container.addView(this)
        }

        view = container

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP; y = 80 }

        try { wm?.addView(view, params) } catch (_: Exception) {}
    }

    private fun removeOverlay() {
        try { if (view != null) { wm?.removeView(view); view = null } } catch (_: Exception) {}
    }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); removeOverlay(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
