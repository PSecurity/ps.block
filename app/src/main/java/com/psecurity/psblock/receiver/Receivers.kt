package com.psecurity.psblock.receiver

import android.app.PendingIntent
import android.app.admin.DeviceAdminReceiver
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.SmsMessage
import android.telephony.TelephonyManager
import android.widget.RemoteViews
import com.psecurity.psblock.MainActivity
import com.psecurity.psblock.PSBlockPrefs.appendLog
import com.psecurity.psblock.PSBlockPrefs.autoReasonCall
import com.psecurity.psblock.PSBlockPrefs.releaseAutoBlock
import com.psecurity.psblock.PSBlockPrefs.activateAutoBlock
import com.psecurity.psblock.PSBlockPrefs.blockDuringCallsEnabled
import com.psecurity.psblock.PSBlockPrefs.cameraBlocked
import com.psecurity.psblock.PSBlockPrefs.locationBlocked
import com.psecurity.psblock.PSBlockPrefs.micBlocked
import com.psecurity.psblock.PSBlockPrefs.panicSmsEnabled
import com.psecurity.psblock.PSBlockPrefs.panicSmsNumber
import com.psecurity.psblock.PSBlockPrefs.remoteWipeCode
import com.psecurity.psblock.PSBlockPrefs.remoteWipeEnabled
import com.psecurity.psblock.R
import com.psecurity.psblock.service.NetworkWatchService
import com.psecurity.psblock.service.PSBlockService
import com.psecurity.psblock.AppLanguageManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val restartActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.LOCKED_BOOT_COMPLETED",
            "android.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.REBOOT",
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED
        )
        if (action in restartActions || action.startsWith("android.intent.action")) {
            ctx.appendLog(AppLanguageManager.text(ctx, R.string.receiver_boot_reapply_log, action ?: ""))
            PSBlockService.start(ctx)
            NetworkWatchService.start(ctx)
        }
    }
}

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        context.appendLog(AppLanguageManager.text(context, R.string.receiver_admin_enabled_log))
    }
    override fun onDisabled(context: Context, intent: Intent) {
        context.appendLog(AppLanguageManager.text(context, R.string.receiver_admin_disabled_log))
    }
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Desativar o Device Admin reduz o bloqueio real de câmera do PS.Block."
    }
    override fun onPasswordFailed(context: Context, intent: Intent, user: android.os.UserHandle) {
        context.appendLog(AppLanguageManager.text(context, R.string.receiver_password_failed_log))
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action != "android.provider.Telephony.SMS_RECEIVED") return
        if (!ctx.remoteWipeEnabled && !ctx.panicSmsEnabled) return

        val bundle = intent.extras ?: return
        val pdus = bundle["pdus"] as? Array<*> ?: return
        val format = bundle.getString("format")

        for (pdu in pdus) {
            val msg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                SmsMessage.createFromPdu(pdu as ByteArray, format)
            } else {
                @Suppress("DEPRECATION")
                SmsMessage.createFromPdu(pdu as ByteArray)
            }
            val body = msg?.messageBody?.trim().orEmpty()
            val sender = msg?.originatingAddress.orEmpty()
            if (!isTrustedSender(ctx, sender)) continue

            val wipeCode = ctx.remoteWipeCode.trim()
            val normalizedBody = body.trim()
            val wipeCommand = normalizedBody.equals("PSBLOCK WIPE $wipeCode", ignoreCase = true) || normalizedBody == wipeCode
            if (ctx.remoteWipeEnabled && wipeCode.length >= 8 && wipeCommand) {
                ctx.appendLog(AppLanguageManager.text(ctx, R.string.receiver_wipe_authorized_log))
                performWipe(ctx)
                break
            }
            val panicCommand = normalizedBody.equals("PSBLOCK PANIC", ignoreCase = true) || normalizedBody.equals("##PANIC##", ignoreCase = true)
            if (ctx.panicSmsEnabled && panicCommand) {
                ctx.appendLog(AppLanguageManager.text(ctx, R.string.receiver_panic_authorized_log))
                PSBlockService.panic(ctx)
                break
            }
        }
    }

    private fun isTrustedSender(ctx: Context, sender: String): Boolean {
        val trusted = ctx.panicSmsNumber.trim()
        if (trusted.isBlank()) return true
        val normalizedSender = sender.filter { it.isDigit() }
        val normalizedTrusted = trusted.filter { it.isDigit() }
        if (normalizedSender.isBlank() || normalizedTrusted.isBlank()) return false
        return normalizedSender.endsWith(normalizedTrusted) || normalizedTrusted.endsWith(normalizedSender)
    }

    private fun performWipe(ctx: Context) {
        try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val admin = ComponentName(ctx, AdminReceiver::class.java)
            if (dpm.isAdminActive(admin)) dpm.wipeData(0)
            else ctx.appendLog(AppLanguageManager.text(ctx, R.string.receiver_wipe_no_admin_log))
        } catch (e: Exception) {
            ctx.appendLog(AppLanguageManager.text(ctx, R.string.receiver_wipe_error_log, e.message ?: ""))
        }
    }
}

class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (!ctx.blockDuringCallsEnabled) return
        if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE).orEmpty()
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING, TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                ctx.appendLog(AppLanguageManager.text(ctx, R.string.receiver_call_detected_log))
                ctx.activateAutoBlock(autoReasonCall())
                PSBlockService.update(ctx)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                ctx.appendLog(AppLanguageManager.text(ctx, R.string.receiver_call_ended_log))
                ctx.releaseAutoBlock(autoReasonCall())
                PSBlockService.update(ctx)
            }
        }
    }
}

class AirplaneModeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_AIRPLANE_MODE_CHANGED) {
            ctx.appendLog(AppLanguageManager.text(ctx, R.string.receiver_airplane_changed_log))
            NetworkWatchService.start(ctx)
            PSBlockService.update(ctx)
        }
    }
}

class PSBlockWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_WIDGET_TOGGLE_ALL = "com.psecurity.psblock.WIDGET_TOGGLE_ALL"
        const val ACTION_WIDGET_TEMP_ALLOW = "com.psecurity.psblock.WIDGET_TEMP_ALLOW"

        fun updateWidget(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_psblock)
            val anyBlocked = ctx.cameraBlocked || ctx.micBlocked || ctx.locationBlocked
            views.setTextViewText(R.id.widgetStatus, if (anyBlocked) AppLanguageManager.text(ctx, R.string.widget_online).removeSurrounding("[", "]") else AppLanguageManager.text(ctx, R.string.widget_standby))
            val parts = mutableListOf<String>()
            if (ctx.cameraBlocked) parts.add("CAM")
            if (ctx.micBlocked) parts.add("MIC")
            if (ctx.locationBlocked) parts.add("GPS")
            views.setTextViewText(R.id.widgetDetails, parts.joinToString("  ").ifEmpty { AppLanguageManager.text(ctx, R.string.widget_no_blocks) })

            val openPi = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val togglePi = PendingIntent.getBroadcast(
                ctx, 1, Intent(ctx, PSBlockWidgetProvider::class.java).setAction(ACTION_WIDGET_TOGGLE_ALL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val tempPi = PendingIntent.getBroadcast(
                ctx, 2, Intent(ctx, PSBlockWidgetProvider::class.java).setAction(ACTION_WIDGET_TEMP_ALLOW),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, openPi)
            views.setOnClickPendingIntent(R.id.widgetStatus, togglePi)
            views.setOnClickPendingIntent(R.id.widgetDetails, tempPi)
            mgr.updateAppWidget(id, views)
        }
    }

    override fun onReceive(ctx: Context, intent: Intent?) {
        super.onReceive(ctx, intent)
        when (intent?.action) {
            ACTION_WIDGET_TOGGLE_ALL -> {
                val next = !(ctx.cameraBlocked || ctx.micBlocked || ctx.locationBlocked)
                ctx.cameraBlocked = next
                ctx.micBlocked = next
                ctx.locationBlocked = next
                ctx.appendLog(if (next) AppLanguageManager.text(ctx, R.string.receiver_widget_block_all_enabled_log) else AppLanguageManager.text(ctx, R.string.receiver_widget_block_all_disabled_log))
                PSBlockService.update(ctx)
                refreshAllWidgets(ctx)
            }
            ACTION_WIDGET_TEMP_ALLOW -> {
                PSBlockService.tempRelease(ctx, "widget use camera now", 5 * 60 * 1000L)
                refreshAllWidgets(ctx)
            }
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(ctx, mgr, id)
    }

    private fun refreshAllWidgets(ctx: Context) {
        try {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PSBlockWidgetProvider::class.java))
            for (id in ids) updateWidget(ctx, mgr, id)
        } catch (_: Exception) {}
    }
}

/**
 * Inspirado no CameraDisableService$ScreenReceiver do APK base.
 * Reaplica políticas quando a tela é ligada/desligada — janela de vulnerabilidade fechada.
 */
class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SCREEN_ON -> {
                // Tela ligada: reaplica imediatamente para fechar qualquer janela
                if (ctx.cameraBlocked || ctx.micBlocked || ctx.locationBlocked) {
                    PSBlockService.update(ctx)
                }
            }
            Intent.ACTION_USER_PRESENT -> {
                // Dispositivo desbloqueado: watchdog já está rodando, força update
                PSBlockService.update(ctx)
            }
        }
    }
}

/**
 * Equivalente ao GarbageCollectorAlarmReceiver + TimeAlarmReceiver do APK base.
 * Reaplica políticas via AlarmManager periódico para resistir a kills do sistema.
 */
class PeriodicReapplyReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_REAPPLY = "com.psecurity.psblock.PERIODIC_REAPPLY"
        private const val INTERVAL_MS = 60_000L  // 1 minuto, como o base APK (ONE_MINUTES)

        fun schedule(ctx: Context) {
            try {
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val pi = android.app.PendingIntent.getBroadcast(
                    ctx, 42,
                    Intent(ctx, PeriodicReapplyReceiver::class.java).setAction(ACTION_REAPPLY),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                val trigger = System.currentTimeMillis() + INTERVAL_MS
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, trigger, pi)
                } else {
                    am.setExact(android.app.AlarmManager.RTC_WAKEUP, trigger, pi)
                }
            } catch (_: Exception) {}
        }

        fun cancel(ctx: Context) {
            try {
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val pi = android.app.PendingIntent.getBroadcast(
                    ctx, 42,
                    Intent(ctx, PeriodicReapplyReceiver::class.java).setAction(ACTION_REAPPLY),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_NO_CREATE
                )
                pi?.let { am.cancel(it) }
            } catch (_: Exception) {}
        }
    }

    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action != ACTION_REAPPLY) return
        if (ctx.cameraBlocked || ctx.micBlocked || ctx.locationBlocked) {
            PSBlockService.update(ctx)
            ctx.appendLog(AppLanguageManager.text(ctx, R.string.receiver_alarm_reapply_log))
        }
        // Reagenda para o próximo ciclo
        schedule(ctx)
    }
}
