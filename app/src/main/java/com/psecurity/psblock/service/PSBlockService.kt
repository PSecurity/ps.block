package com.psecurity.psblock.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.psecurity.psblock.MainActivity
import com.psecurity.psblock.PSBlockPrefs.appendLog
import com.psecurity.psblock.PSBlockPrefs.cameraBlocked
import com.psecurity.psblock.PSBlockPrefs.clearTemporaryRelease
import com.psecurity.psblock.PSBlockPrefs.locationBlocked
import com.psecurity.psblock.PSBlockPrefs.micBlocked
import com.psecurity.psblock.PSBlockPrefs.rootEnabled
import com.psecurity.psblock.PSBlockPrefs.temporaryReleaseReason
import com.psecurity.psblock.PSBlockPrefs.temporaryReleaseUntil
import com.psecurity.psblock.R
import com.psecurity.psblock.receiver.AdminReceiver
import com.psecurity.psblock.receiver.PeriodicReapplyReceiver
import com.psecurity.psblock.receiver.ScreenStateReceiver
import android.content.IntentFilter
import com.psecurity.psblock.root.HardwareBlocker
import java.io.File
import com.psecurity.psblock.AppLanguageManager

class PSBlockService : Service() {

    companion object {
        const val ACTION_UPDATE      = "com.psecurity.psblock.UPDATE"
        const val ACTION_PANIC       = "com.psecurity.psblock.PANIC"
        const val ACTION_TEMP_RELEASE = "com.psecurity.psblock.TEMP_RELEASE"
        const val EXTRA_TEMP_MS      = "extra_temp_ms"
        const val EXTRA_REASON       = "extra_reason"
        private const val CHANNEL_ID = "ps_block_ch"
        private const val NOTIF_ID   = 1001
        // Watchdog mais agressivo — 2 s enquanto bloqueado, 5 s em standby
        private const val WATCHDOG_ACTIVE_MS  = 2000L
        private const val WATCHDOG_STANDBY_MS = 5000L

        fun start(ctx: Context)  { startSafely(ctx, Intent(ctx, PSBlockService::class.java)) }
        fun stop(ctx: Context)   { try { ctx.stopService(Intent(ctx, PSBlockService::class.java)) } catch (_: Exception) {} }
        fun update(ctx: Context) { startSafely(ctx, Intent(ctx, PSBlockService::class.java).apply { action = ACTION_UPDATE }) }
        fun panic(ctx: Context)  { startSafely(ctx, Intent(ctx, PSBlockService::class.java).apply { action = ACTION_PANIC }) }

        fun tempRelease(ctx: Context, reason: String, durationMs: Long = 5 * 60_000L) {
            startSafely(ctx, Intent(ctx, PSBlockService::class.java).apply {
                action = ACTION_TEMP_RELEASE
                putExtra(EXTRA_TEMP_MS, durationMs.coerceIn(30_000L, 30 * 60_000L))
                putExtra(EXTRA_REASON, reason.take(120))
            })
        }

        private fun startSafely(ctx: Context, intent: Intent) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
                else ctx.startService(intent)
            } catch (_: Exception) {
                try { ctx.startService(intent) } catch (_: Exception) {}
            }
        }
    }

    private val policyThread = HandlerThread("PSBlockPolicyWorker").apply { start() }
    private val handler = Handler(policyThread.looper)
    private val policyLock = Any()
    private val screenReceiver = ScreenStateReceiver()

    // --- Câmera fallback ---
    private val cameraDevices = mutableMapOf<String, CameraDevice>()
    private var cameraManager: CameraManager? = null
    private var cameraAvailabilityCallback: CameraManager.AvailabilityCallback? = null

    // --- Mic blocker (dupla camada) ---
    private var audioRec: AudioRecord? = null
    private var mediaRecorder: MediaRecorder? = null
    private var micThread: Thread? = null
    private var micActive = false

    @Volatile private var applying = false
    @Volatile private var applyPending = false
    @Volatile private var pendingFromWatchdog = true


    private val watchdog = object : Runnable {
        override fun run() {
            val tempActive = isTemporaryReleaseActive()
            val anyBlock = cameraBlocked || micBlocked || locationBlocked
            if (anyBlock || tempActive) {
                applyPolicies(fromWatchdog = true)
                val delay = if (anyBlock && !tempActive) WATCHDOG_ACTIVE_MS else WATCHDOG_STANDBY_MS
                handler.postDelayed(this, delay)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        cameraManager = getSystemService(CAMERA_SERVICE) as? CameraManager
        // Registro dinâmico do receiver de tela (mais confiável que o estático para screen)
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenReceiver, filter)
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIF_ID, buildNotif())
        } catch (e: Exception) {
            appendLog(AppLanguageManager.text(this, R.string.service_log_foreground_failed, e.message ?: ""))
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_PANIC -> doPanic()
            ACTION_TEMP_RELEASE -> {
                val duration = intent.getLongExtra(EXTRA_TEMP_MS, 5 * 60_000L)
                val reason   = intent.getStringExtra(EXTRA_REASON).orEmpty().ifBlank { "liberação temporária" }
                temporaryReleaseUntil = System.currentTimeMillis() + duration
                temporaryReleaseReason = reason
                appendLog(AppLanguageManager.text(this, R.string.service_log_temp_release, reason, duration / 1000))
                applyPolicies()
            }
            else -> applyPolicies()
        }
        scheduleWatchdog()
        PeriodicReapplyReceiver.schedule(this)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        PeriodicReapplyReceiver.cancel(this)
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        unregisterCameraAvailabilityCallback()
        releaseCam()
        releaseMic()
        policyThread.quitSafely()
        super.onDestroy()
    }

    // ─── Lógica central ───────────────────────────────────────────────────

    private fun applyPolicies(fromWatchdog: Boolean = false) {
        val shouldStartWorker = synchronized(policyLock) {
            if (applying) {
                applyPending = true
                pendingFromWatchdog = pendingFromWatchdog && fromWatchdog
                false
            } else {
                applying = true
                pendingFromWatchdog = fromWatchdog
                true
            }
        }

        if (shouldStartWorker) {
            handler.post { runPolicyApplyLoop(fromWatchdog) }
        }
    }

    private fun runPolicyApplyLoop(initialFromWatchdog: Boolean) {
        var fromWatchdog = initialFromWatchdog

        while (true) {
            try {
                applyPoliciesInternal(fromWatchdog)
            } catch (e: Exception) {
                appendLog(AppLanguageManager.text(this, R.string.service_log_policy_apply_failed, e.message ?: ""))
                scheduleWatchdog()
            }

            val nextFromWatchdog = synchronized(policyLock) {
                if (applyPending) {
                    val queuedFromWatchdog = pendingFromWatchdog
                    applyPending = false
                    pendingFromWatchdog = true
                    queuedFromWatchdog
                } else {
                    applying = false
                    return
                }
            }

            fromWatchdog = nextFromWatchdog
        }
    }

    private fun applyPoliciesInternal(fromWatchdog: Boolean = false) {
        try {
            val tempRelease    = isTemporaryReleaseActive()
            if (!tempRelease && temporaryReleaseUntil > 0L) clearTemporaryRelease()

            val useRoot           = rootEnabled
            val shouldBlockCamera = cameraBlocked && !tempRelease
            val shouldBlockMic    = micBlocked    && !tempRelease

            // ── Câmera ──────────────────────────────────────────────────
            if (shouldBlockCamera) {
                val adminOk = tryAdminCameraBlock(true)
                if (useRoot) HardwareBlocker.blockCamera()
                // Fallback de ocupação: mantém sempre ligado se sem Device Admin funcional
                blockCameraFallback()
                registerCameraAvailabilityCallback()
                if (!fromWatchdog) appendLog(AppLanguageManager.text(this, R.string.service_log_camera_blocked, adminOk, useRoot))
            } else {
                if (useRoot) HardwareBlocker.unblockCamera()
                tryAdminCameraBlock(false)
                unregisterCameraAvailabilityCallback()
                releaseCam()
                if (!fromWatchdog) {
                    val reason = if (tempRelease) temporaryReleaseReason.ifBlank { "temp" } else "manual"
                    appendLog(AppLanguageManager.text(this, R.string.service_log_camera_released, reason))
                }
            }

            // ── Microfone ───────────────────────────────────────────────
            if (shouldBlockMic) {
                if (useRoot) HardwareBlocker.blockMicrophone()
                blockMicFallback()
                if (!fromWatchdog) appendLog(AppLanguageManager.text(this, R.string.service_log_microphone_blocked, useRoot))
            } else {
                if (useRoot) HardwareBlocker.unblockMicrophone()
                releaseMic()
                if (!fromWatchdog) {
                    val reason = if (tempRelease) temporaryReleaseReason.ifBlank { "temp" } else "manual"
                    appendLog(AppLanguageManager.text(this, R.string.service_log_microphone_released, reason))
                }
            }

            // ── Localização ─────────────────────────────────────────────
            if (locationBlocked) {
                if (useRoot) HardwareBlocker.blockLocation()
                if (!fromWatchdog) appendLog(AppLanguageManager.text(this, R.string.service_log_location_blocked, useRoot))
            } else {
                if (useRoot) HardwareBlocker.unblockLocation()
                if (!fromWatchdog) appendLog(AppLanguageManager.text(this, R.string.service_log_location_released))
            }

            updateNotif()
            sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName))
        } finally {
            scheduleWatchdog()
        }
    }

    private fun doPanic() {
        clearTemporaryRelease()
        cameraBlocked   = true
        micBlocked      = true
        locationBlocked = true
        applyPolicies()
        try {
            val dpm   = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(this, AdminReceiver::class.java)
            if (dpm.isAdminActive(admin)) dpm.lockNow()
        } catch (_: Exception) {}
        appendLog(AppLanguageManager.text(this, R.string.service_log_panic_enabled))
    }

    private fun scheduleWatchdog() {
        handler.removeCallbacks(watchdog)
        val anyBlock = cameraBlocked || micBlocked || locationBlocked
        val delay = if (anyBlock && !isTemporaryReleaseActive()) WATCHDOG_ACTIVE_MS else WATCHDOG_STANDBY_MS
        if (anyBlock || isTemporaryReleaseActive()) handler.postDelayed(watchdog, delay)
    }

    private fun isTemporaryReleaseActive() = temporaryReleaseUntil > System.currentTimeMillis()

    // ─── Câmera fallback ─────────────────────────────────────────────────
    // Estratégia: abre todas as câmeras e registra callback de disponibilidade
    // para reabrir se algum app fechar. Isso satura o recurso.

    private fun blockCameraFallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            appendLog(AppLanguageManager.text(this, R.string.service_log_camera_permission_missing))
            return
        }
        val mgr = cameraManager ?: return
        for (id in try { mgr.cameraIdList } catch (_: Exception) { return }) {
            if (cameraDevices.containsKey(id)) continue
            tryOccupyCamera(id)
        }
    }

    private fun tryOccupyCamera(id: String) {
        val mgr = cameraManager ?: return
        try {
            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevices[id] = camera
                }
                override fun onDisconnected(camera: CameraDevice) {
                    try { camera.close() } catch (_: Exception) {}
                    cameraDevices.remove(id)
                    // Reocupa imediatamente se ainda em modo bloqueado
                    if (cameraBlocked && !isTemporaryReleaseActive()) {
                        handler.postDelayed({ tryOccupyCamera(id) }, 300L)
                    }
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    try { camera.close() } catch (_: Exception) {}
                    cameraDevices.remove(id)
                    if (cameraBlocked && !isTemporaryReleaseActive()) {
                        handler.postDelayed({ tryOccupyCamera(id) }, 500L)
                    }
                }
            }, handler)
        } catch (_: Exception) {}
    }

    private fun registerCameraAvailabilityCallback() {
        if (cameraAvailabilityCallback != null) return
        val mgr = cameraManager ?: return
        val cb = object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) {
                // Uma câmera ficou disponível — reocupa se devemos bloquear
                if (cameraBlocked && !isTemporaryReleaseActive() && !cameraDevices.containsKey(cameraId)) {
                    handler.postDelayed({ tryOccupyCamera(cameraId) }, 100L)
                }
            }
        }
        try { mgr.registerAvailabilityCallback(cb, handler) } catch (_: Exception) {}
        cameraAvailabilityCallback = cb
    }

    private fun unregisterCameraAvailabilityCallback() {
        val cb = cameraAvailabilityCallback ?: return
        try { cameraManager?.unregisterAvailabilityCallback(cb) } catch (_: Exception) {}
        cameraAvailabilityCallback = null
    }

    private fun releaseCam() {
        unregisterCameraAvailabilityCallback()
        val devices = cameraDevices.values.toList()
        cameraDevices.clear()
        devices.forEach { try { it.close() } catch (_: Exception) {} }
    }

    // ─── Mic fallback ────────────────────────────────────────────────────
    // Camada 1: setMicrophoneMute
    // Camada 2: AudioRecord ocupa o hardware
    // Camada 3: MediaRecorder ocupa via outra API
    // O APK base usa apenas MediaRecorder. Aqui usamos as 3 camadas.

    private fun blockMicFallback() {
        setMicMute(true)
        startAudioRecordBlocker()   // camada 2 — ocupa o hardware PCM
        startMediaRecorderBlocker() // camada 3 — ocupa via MediaRecorder API
    }

    private fun startAudioRecordBlocker() {
        if (micActive) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            appendLog(AppLanguageManager.text(this, R.string.service_log_record_audio_missing))
            return
        }
        try {
            val sampleRate = 44100
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return
            val rec = AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 4
            )
            rec.startRecording()
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                try { rec.release() } catch (_: Exception) {}
                return
            }
            audioRec  = rec
            micActive = true
            micThread = Thread {
                val buf = ByteArray(minBuf)
                while (micActive && !Thread.currentThread().isInterrupted) {
                    try { rec.read(buf, 0, buf.size) } catch (_: Exception) { break }
                }
            }.also {
                it.name      = "PSBlockMicGuard"
                it.isDaemon  = true
                it.start()
            }
            appendLog(AppLanguageManager.text(this, R.string.service_log_audio_record_active))
        } catch (_: Exception) {
            releaseAudioRecordOnly()
        }
    }

    private fun startMediaRecorderBlocker() {
        if (mediaRecorder != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        try {
            @Suppress("DEPRECATION")
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            try { rec.setOutputFile("/dev/null") }
            catch (_: Exception) {
                rec.setOutputFile(File(cacheDir, "psblock_mic_sink.3gp").absolutePath)
            }
            rec.prepare()
            rec.start()
            mediaRecorder = rec
            appendLog(AppLanguageManager.text(this, R.string.service_log_media_recorder_active))
        } catch (_: Exception) {
            try { mediaRecorder?.release() } catch (_: Exception) {}
            mediaRecorder = null
        }
    }

    private fun releaseMic() {
        setMicMute(false)
        releaseAudioRecordOnly()
        try { mediaRecorder?.stop()    } catch (_: Exception) {}
        try { mediaRecorder?.reset()   } catch (_: Exception) {}
        try { mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
        try { File(cacheDir, "psblock_mic_sink.3gp").delete() } catch (_: Exception) {}
    }

    private fun releaseAudioRecordOnly() {
        micActive = false
        try { micThread?.interrupt() } catch (_: Exception) {}
        try { micThread?.join(300)   } catch (_: Exception) {}
        micThread = null
        try { audioRec?.stop()    } catch (_: Exception) {}
        try { audioRec?.release() } catch (_: Exception) {}
        audioRec = null
    }

    private fun setMicMute(mute: Boolean) {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            am.setMicrophoneMute(mute)
        } catch (_: Exception) {}
    }

    // ─── Device Admin ────────────────────────────────────────────────────

    private fun tryAdminCameraBlock(block: Boolean): Boolean {
        return try {
            val dpm   = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(this, AdminReceiver::class.java)
            if (dpm.isAdminActive(admin)) {
                dpm.setCameraDisabled(admin, block)
                true
            } else false
        } catch (e: Exception) {
            appendLog(AppLanguageManager.text(this, R.string.service_log_device_admin_failed, e.message ?: ""))
            false
        }
    }

    // ─── Notificação ─────────────────────────────────────────────────────

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "PS.Block Guard", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                description = AppLanguageManager.text(this@PSBlockService, R.string.service_notification_description)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    private fun buildNotif(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val tempActive = isTemporaryReleaseActive()
        val parts = mutableListOf<String>()
        if (cameraBlocked   && !tempActive) parts.add("CAM")
        if (micBlocked      && !tempActive) parts.add("MIC")
        if (locationBlocked               ) parts.add("GPS")
        val txt = when {
            tempActive    -> "Liberação: ${temporaryReleaseReason.ifBlank { "ativa" }}"
            parts.isEmpty() -> "Standby"
            else           -> "LOCK: ${parts.joinToString(" / ")}"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PS.Block")
            .setContentText(txt)
            .setSmallIcon(R.drawable.ic_psblock)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotif() {
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotif())
    }
}
