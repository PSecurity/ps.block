package com.psecurity.psblock

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.psecurity.psblock.PSBlockPrefs.appendLog
import com.psecurity.psblock.PSBlockPrefs.bluetoothBlocked
import com.psecurity.psblock.PSBlockPrefs.cameraBlocked
import com.psecurity.psblock.PSBlockPrefs.locationBlocked
import com.psecurity.psblock.PSBlockPrefs.micBlocked
import com.psecurity.psblock.PSBlockPrefs.rootEnabled
import com.psecurity.psblock.PSBlockPrefs.blockDuringCallsEnabled
import com.psecurity.psblock.receiver.AdminReceiver
import com.psecurity.psblock.service.PSBlockService
import com.psecurity.psblock.service.NetworkWatchService

class MainActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    private lateinit var switchCamera: Switch
    private lateinit var switchMic: Switch
    private lateinit var switchLocation: Switch
    private lateinit var switchBluetooth: Switch
    private lateinit var switchAll: Switch
    private lateinit var tvRootStatus: TextView
    private lateinit var tvProtectionStatus: TextView
    private lateinit var tvPermissionSummary: TextView
    private lateinit var tvAdminStatus: TextView
    private lateinit var tvRuntimeStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvBatteryStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView

    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        appendLog("Permissoes runtime verificadas")
        if (setupInProgress) continuePermissionSetup() else refreshPermissionCenter()
        NetworkWatchService.start(this)
    }

    private val externalSetupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPermissionCenter()
        PSBlockService.update(this)
        NetworkWatchService.start(this)
        if (setupInProgress) continuePermissionSetup()
    }

    private var setupInProgress = false
    private var runtimePrompted = false
    private var adminPrompted = false
    private var batteryPrompted = false
    private var overlayPrompted = false
    private var accessibilityPrompted = false
    private var usagePrompted = false
    private var appliedLanguage: String = AppLanguageManager.LANG_EN

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            refreshSwitches()
            refreshPermissionCenter()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appliedLanguage = AppLanguageManager.currentLanguage(this)
        setContentView(R.layout.activity_main)

        bindViews()
        setupSwitches()
        setupButtons()
        refreshSwitches()
        refreshRootStatus()
        refreshPermissionCenter()
    }

    override fun onResume() {
        super.onResume()
        val currentLanguage = AppLanguageManager.currentLanguage(this)
        if (currentLanguage != appliedLanguage) {
            recreate()
            return
        }
        if (!SecurityGate.enforce(this)) return
        refreshSwitches()
        refreshRootStatus()
        refreshPermissionCenter()
        ContextCompat.registerReceiver(
            this,
            updateReceiver,
            IntentFilter(PSBlockService.ACTION_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(updateReceiver) } catch (_: Exception) {}
    }

    private fun bindViews() {
        switchCamera = findViewById(R.id.switchCamera)
        switchMic = findViewById(R.id.switchMicrophone)
        switchLocation = findViewById(R.id.switchLocation)
        switchBluetooth = findViewById(R.id.switchBluetooth)
        switchAll = findViewById(R.id.switchBlockAll)
        tvRootStatus = findViewById(R.id.tvRootStatus)
        tvProtectionStatus = findViewById(R.id.tvProtectionStatus)
        tvPermissionSummary = findViewById(R.id.tvPermissionSummary)
        tvAdminStatus = findViewById(R.id.tvAdminStatus)
        tvRuntimeStatus = findViewById(R.id.tvRuntimeStatus)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
    }

    private fun refreshRootStatus() {
        val root = rootEnabled
        tvRootStatus.text = if (root) getString(R.string.main_root_status_root) else getString(R.string.main_root_status_fallback)
        tvRootStatus.setTextColor(color(if (root) "#4ADE80" else "#FACC15"))
    }

    private fun refreshSwitches() {
        switchCamera.setOnCheckedChangeListener(null)
        switchMic.setOnCheckedChangeListener(null)
        switchLocation.setOnCheckedChangeListener(null)
        switchBluetooth.setOnCheckedChangeListener(null)
        switchAll.setOnCheckedChangeListener(null)

        switchCamera.isChecked = cameraBlocked
        switchMic.isChecked = micBlocked
        switchLocation.isChecked = locationBlocked
        switchBluetooth.isChecked = bluetoothBlocked
        switchAll.isChecked = cameraBlocked && micBlocked && locationBlocked && bluetoothBlocked

        val active = mutableListOf<String>()
        if (cameraBlocked) active.add("CAM")
        if (micBlocked) active.add("MIC")
        if (locationBlocked) active.add("GPS")
        if (bluetoothBlocked) active.add("BT")

        if (active.isEmpty()) {
            tvProtectionStatus.text = getString(R.string.status_standby_waiting_command)
            tvProtectionStatus.setTextColor(color("#8B6CA8"))
        } else {
            tvProtectionStatus.text = getString(R.string.main_status_online, active.joinToString("  /  "))
            tvProtectionStatus.setTextColor(color("#4ADE80"))
        }

        setupSwitches()
    }

    private fun setupSwitches() {
        switchCamera.setOnCheckedChangeListener { _, enabled ->
            cameraBlocked = enabled
            if (enabled) {
                if (!isAdminActive()) askDeviceAdminForRealCameraBlock()
                ensureServiceFriendlySetup()
            }
            PSBlockService.update(this)
            updateAllSwitch()
        }

        switchMic.setOnCheckedChangeListener { _, enabled ->
            micBlocked = enabled
            if (enabled) ensureServiceFriendlySetup()
            PSBlockService.update(this)
            updateAllSwitch()
        }

        switchLocation.setOnCheckedChangeListener { _, enabled ->
            locationBlocked = enabled
            if (enabled) requestRuntimePermissionsIfNeeded()
            PSBlockService.update(this)
            updateAllSwitch()
        }

        switchBluetooth.setOnCheckedChangeListener { _, enabled ->
            bluetoothBlocked = enabled
            try {
                if (enabled) com.psecurity.psblock.root.HardwareBlocker.disableBluetooth()
                else com.psecurity.psblock.root.HardwareBlocker.enableBluetooth()
            } catch (_: Exception) {}
            appendLog(if (enabled) getString(R.string.main_bluetooth_blocked_log) else getString(R.string.main_bluetooth_released_log))
            updateAllSwitch()
        }

        switchAll.setOnCheckedChangeListener { _, enabled ->
            cameraBlocked = enabled
            micBlocked = enabled
            locationBlocked = enabled
            if (enabled) {
                if (!isAdminActive()) askDeviceAdminForRealCameraBlock()
                ensureServiceFriendlySetup()
            }
            PSBlockService.update(this)
            refreshSwitches()
        }
    }

    private fun updateAllSwitch() {
        val all = cameraBlocked && micBlocked && locationBlocked && bluetoothBlocked
        switchAll.setOnCheckedChangeListener(null)
        switchAll.isChecked = all
        setupSwitches()
        refreshSwitches()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnSetupPermissions).setOnClickListener {
            setupInProgress = true
            runtimePrompted = false
            adminPrompted = false
            batteryPrompted = false
            overlayPrompted = false
            accessibilityPrompted = false
            usagePrompted = false
            continuePermissionSetup()
        }

        findViewById<Button>(R.id.btnPanic).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.main_panic_title))
                .setMessage(getString(R.string.main_panic_message))
                .setPositiveButton(getString(R.string.main_panic_activate)) { _, _ ->
                    PSBlockService.panic(this)
                    appendLog(getString(R.string.main_panic_log))
                    refreshSwitches()
                }
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show()
        }

        findViewById<Button>(R.id.btnLock).setOnClickListener {
            try {
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = ComponentName(this, AdminReceiver::class.java)
                if (dpm.isAdminActive(admin)) {
                    SecurityGate.lockSession()
                    dpm.lockNow()
                } else askDeviceAdminForRealCameraBlock()
            } catch (e: Exception) {
                toast(getString(R.string.main_error_message, e.message ?: ""))
            }
        }

        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<View>(R.id.btnLogs)?.setOnClickListener {
            startActivity(Intent(this, com.psecurity.psblock.ui.LogViewerActivity::class.java))
        }


        findViewById<View>(R.id.btnSupportProject)?.setOnClickListener {
            startActivity(Intent(this, com.psecurity.psblock.support.SupportActivity::class.java))
        }

        tvRootStatus.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.main_root_dialog_title))
                .setMessage(
                    if (rootEnabled)
                        getString(R.string.main_root_dialog_root)
                    else
                        getString(R.string.main_root_dialog_fallback)
                )
                .setPositiveButton(getString(R.string.common_ok), null)
                .show()
        }
    }

    private fun refreshPermissionCenter() {
        val admin = isAdminActive()
        val runtime = hasRuntimePermissions()
        val overlay = hasOverlayPermission()
        val battery = isIgnoringBatteryOptimization()
        val accessibility = hasAccessibilityService()
        val usage = hasUsageAccess()

        tvAdminStatus.text = statusLine(admin, getString(R.string.main_device_admin_status).removePrefix("[--] ").removePrefix("[OK] "), getString(R.string.main_status_desc_camera_policy))
        tvRuntimeStatus.text = statusLine(runtime, "Runtime", getString(R.string.main_status_desc_runtime))
        tvOverlayStatus.text = statusLine(overlay, "Overlay", getString(R.string.main_status_desc_overlay))
        tvBatteryStatus.text = statusLine(battery, getString(R.string.main_battery), getString(R.string.main_status_desc_battery))
        tvAccessibilityStatus.text = statusLine(accessibility, getString(R.string.settings_accessibility), getString(R.string.main_status_desc_accessibility)) +
            if (usage) getString(R.string.main_usage_access_ok) else getString(R.string.main_usage_access_missing)

        val criticalOk = admin && runtime && battery
        tvPermissionSummary.text = if (criticalOk) {
            getString(R.string.main_permission_summary_ok)
        } else {
            getString(R.string.main_permission_summary_missing)
        }
        tvPermissionSummary.setTextColor(color(if (criticalOk) "#4ADE80" else "#FACC15"))
    }

    private fun statusLine(ok: Boolean, label: String, desc: String): String {
        return if (ok) getString(R.string.main_status_line_ok, label, desc) else getString(R.string.main_status_line_missing, label, desc)
    }

    private fun continuePermissionSetup() {
        refreshPermissionCenter()
        when {
            !hasRuntimePermissions() && !runtimePrompted -> requestRuntimePermissionsIfNeeded()
            !isAdminActive() && !adminPrompted -> launchDeviceAdmin()
            !isIgnoringBatteryOptimization() && !batteryPrompted -> requestBatteryOptimizationBypass()
            !hasOverlayPermission() && !overlayPrompted -> requestOverlayPermission()
            !hasAccessibilityService() && !accessibilityPrompted -> requestAccessibilityPermission()
            !hasUsageAccess() && !usagePrompted -> requestUsageAccessPermission()
            else -> {
                setupInProgress = false
                refreshPermissionCenter()
                toast(getString(R.string.main_setup_complete))
                appendLog(getString(R.string.main_setup_complete_log))
                PSBlockService.start(this)
                NetworkWatchService.start(this)
            }
        }
    }

    private fun ensureServiceFriendlySetup() {
        if (!hasRuntimePermissions()) requestRuntimePermissionsIfNeeded()
        if (!isIgnoringBatteryOptimization()) requestBatteryOptimizationBypass()
        if (!isAdminActive()) askDeviceAdminForRealCameraBlock()
        refreshPermissionCenter()
    }

    private fun requestRuntimePermissionsIfNeeded() {
        val missing = requiredRuntimePermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            runtimePrompted = true
            runtimePermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun requiredRuntimePermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (blockDuringCallsEnabled) {
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }
        return permissions
    }

    private fun hasRuntimePermissions(): Boolean {
        return requiredRuntimePermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isAdminActive(): Boolean {
        return try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(this, AdminReceiver::class.java)
            dpm.isAdminActive(admin)
        } catch (_: Exception) { false }
    }

    private fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun isIgnoringBatteryOptimization(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) true
            else (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        } catch (_: Exception) { true }
    }

    private fun hasAccessibilityService(): Boolean {
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

    private fun askDeviceAdminForRealCameraBlock() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.main_real_camera_block_title))
            .setMessage(getString(R.string.main_real_camera_block_message))
            .setPositiveButton(getString(R.string.common_activate)) { _, _ -> launchDeviceAdmin() }
            .setNegativeButton(getString(R.string.common_later), null)
            .show()
    }

    private fun launchDeviceAdmin() {
        adminPrompted = true
        try {
            externalSetupLauncher.launch(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(this@MainActivity, AdminReceiver::class.java)
                )
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.main_device_admin_explanation)
                )
            })
        } catch (e: Exception) {
            toast(getString(R.string.main_device_admin_open_error))
        }
    }

    private fun requestBatteryOptimizationBypass() {
        batteryPrompted = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            externalSetupLauncher.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: Exception) {
            try {
                externalSetupLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {}
        }
    }

    private fun requestOverlayPermission() {
        overlayPrompted = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            externalSetupLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (_: Exception) { toast(getString(R.string.main_overlay_open_error)) }
    }

    private fun requestAccessibilityPermission() {
        try {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.main_accessibility_enable_title))
                .setMessage(getString(R.string.main_accessibility_enable_message))
                .setPositiveButton(getString(R.string.common_open)) { _, _ ->
                    accessibilityPrompted = true
                    externalSetupLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton(getString(R.string.common_later)) { _, _ ->
                    accessibilityPrompted = true
                    continuePermissionSetup()
                }
                .show()
        } catch (_: Exception) {}
    }

    private fun requestUsageAccessPermission() {
        try {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.main_usage_access_title))
                .setMessage(getString(R.string.main_usage_access_message))
                .setPositiveButton(getString(R.string.common_open)) { _, _ ->
                    usagePrompted = true
                    externalSetupLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                .setNegativeButton(getString(R.string.splash_finish)) { _, _ ->
                    usagePrompted = true
                    setupInProgress = false
                    refreshPermissionCenter()
                    PSBlockService.start(this)
                    NetworkWatchService.start(this)
                }
                .show()
        } catch (_: Exception) {
            usagePrompted = true
            setupInProgress = false
            PSBlockService.start(this)
        }
    }

    private fun color(hex: String) = android.graphics.Color.parseColor(hex)
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
