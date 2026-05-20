package com.psecurity.psblock

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.psecurity.psblock.PSBlockPrefs.appendLog
import com.psecurity.psblock.PSBlockPrefs.rootAvailable
import com.psecurity.psblock.PSBlockPrefs.rootEnabled
import com.psecurity.psblock.receiver.AdminReceiver
import com.psecurity.psblock.root.RootExecutor
import com.psecurity.psblock.service.NetworkWatchService
import com.psecurity.psblock.service.PSBlockService

class SplashActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    private lateinit var tvStep: TextView
    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())

    private var runtimeHandled = false
    private var overlayHandled = false
    private var adminHandled = false
    private var batteryHandled = false
    private var accessibilityHandled = false
    private var usageHandled = false
    private var dialogShowing = false
    private var started = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        runtimeHandled = true
        appendLog("[GUARD] Runtime permission flow finished")
        proceed()
    }

    private val externalLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        handler.postDelayed({ proceed() }, 250)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        tvStep = findViewById(R.id.tvSplashStep)
        progressBar = findViewById(R.id.splashProgress)
        started = true
        handler.postDelayed({ proceed() }, 500)
    }

    override fun onResume() {
        super.onResume()
        if (started && !dialogShowing) handler.postDelayed({ proceed() }, 300)
    }

    private fun proceed() {
        if (isFinishing || dialogShowing) return
        when {
            !runtimeHandled && !hasRuntimePermissions() -> requestRuntimePermissions()
            !adminHandled && !hasAdmin() -> requestAdmin()
            !batteryHandled && !isIgnoringBatteryOptimization() -> requestBatteryOptimization()
            !overlayHandled && !hasOverlay() -> requestOverlay()
            !accessibilityHandled && !hasAccessibility() -> requestAccessibility()
            !usageHandled && !hasUsageAccess() -> requestUsageAccess()
            else -> finishSetup()
        }
    }

    private fun setStep(text: String, progress: Int) {
        tvStep.text = text
        progressBar.progress = progress.coerceIn(0, 100)
    }

    private fun runtimePermissions(): List<String> {
        val required = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return required
    }

    private fun hasRuntimePermissions(): Boolean {
        return runtimePermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestRuntimePermissions() {
        setStep(getString(R.string.splash_step_runtime_permissions), 22)
        val missing = runtimePermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            runtimeHandled = true
            proceed()
        } else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun hasOverlay(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun hasAdmin(): Boolean {
        return try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.isAdminActive(ComponentName(this, AdminReceiver::class.java))
        } catch (_: Exception) { false }
    }

    private fun isIgnoringBatteryOptimization(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) true
            else (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        } catch (_: Exception) { true }
    }

    private fun hasAccessibility(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            enabled.contains(packageName, ignoreCase = true)
        } catch (_: Exception) { false }
    }

    private fun hasUsageAccess(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }

    private fun requestAdmin() {
        setStep(getString(R.string.splash_step_device_admin), 40)
        showStepDialog(
            title = getString(R.string.splash_device_admin_title),
            message = getString(R.string.splash_device_admin_message),
            positive = getString(R.string.common_activate),
            negative = getString(R.string.common_skip),
            onPositive = {
                adminHandled = true
                externalLauncher.launch(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this@SplashActivity, AdminReceiver::class.java))
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.splash_device_admin_explanation))
                })
            },
            onNegative = { adminHandled = true; proceed() }
        )
    }

    private fun requestBatteryOptimization() {
        setStep(getString(R.string.splash_step_battery), 55)
        showStepDialog(
            title = getString(R.string.splash_battery_title),
            message = getString(R.string.splash_battery_message),
            positive = getString(R.string.common_open),
            negative = getString(R.string.common_skip),
            onPositive = {
                batteryHandled = true
                try {
                    externalLauncher.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } catch (_: Exception) {
                    externalLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            },
            onNegative = { batteryHandled = true; proceed() }
        )
    }

    private fun requestOverlay() {
        setStep(getString(R.string.splash_step_overlay), 66)
        showStepDialog(
            title = getString(R.string.splash_overlay_title),
            message = getString(R.string.splash_overlay_message),
            positive = getString(R.string.common_open),
            negative = getString(R.string.common_skip),
            onPositive = {
                overlayHandled = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    externalLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else proceed()
            },
            onNegative = { overlayHandled = true; proceed() }
        )
    }

    private fun requestAccessibility() {
        setStep(getString(R.string.splash_step_accessibility), 76)
        showStepDialog(
            title = getString(R.string.splash_accessibility_title),
            message = getString(R.string.splash_accessibility_message),
            positive = getString(R.string.common_open),
            negative = getString(R.string.common_skip),
            onPositive = {
                accessibilityHandled = true
                externalLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onNegative = { accessibilityHandled = true; proceed() }
        )
    }

    private fun requestUsageAccess() {
        setStep(getString(R.string.splash_step_usage), 84)
        showStepDialog(
            title = getString(R.string.splash_usage_title),
            message = getString(R.string.splash_usage_message),
            positive = getString(R.string.common_open),
            negative = getString(R.string.splash_finish),
            onPositive = {
                usageHandled = true
                externalLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            },
            onNegative = { usageHandled = true; finishSetup() }
        )
    }

    private fun showStepDialog(
        title: String,
        message: String,
        positive: String,
        negative: String,
        onPositive: () -> Unit,
        onNegative: () -> Unit
    ) {
        dialogShowing = true
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positive) { _, _ -> dialogShowing = false; onPositive() }
            .setNegativeButton(negative) { _, _ -> dialogShowing = false; onNegative() }
            .setOnCancelListener { dialogShowing = false; onNegative() }
            .show()
    }

    private fun finishSetup() {
        if (isFinishing) return
        setStep(getString(R.string.splash_step_root_check), 94)
        Thread {
            val hasRoot = RootExecutor.isRootAvailable()
            runOnUiThread {
                rootAvailable = hasRoot
                rootEnabled = hasRoot
                appendLog(if (hasRoot) getString(R.string.splash_log_root_detected) else getString(R.string.splash_log_root_missing))
                setStep(if (hasRoot) getString(R.string.splash_step_root_online) else getString(R.string.splash_step_fallback_online), 100)
                handler.postDelayed({
                    try { PSBlockService.start(this) } catch (_: Exception) {}
                    try { startService(Intent(this, NetworkWatchService::class.java)) } catch (_: Exception) {}
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }, 650)
            }
        }.start()
    }
}
