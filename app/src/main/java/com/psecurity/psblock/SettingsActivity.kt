package com.psecurity.psblock

import android.Manifest
import com.psecurity.psblock.receiver.AdminReceiver
import android.content.ComponentName
import android.app.admin.DevicePolicyManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import com.psecurity.psblock.PSBlockPrefs.appendLog
import com.psecurity.psblock.PSBlockPrefs.autoBlockOnGeofenceExit
import com.psecurity.psblock.PSBlockPrefs.autoBlockOnUnknownWifi
import com.psecurity.psblock.PSBlockPrefs.appLockEnabled
import com.psecurity.psblock.PSBlockPrefs.biometricEnabled
import com.psecurity.psblock.PSBlockPrefs.blockDuringCallsEnabled
import com.psecurity.psblock.PSBlockPrefs.clearLogs
import com.psecurity.psblock.PSBlockPrefs.clearTemporaryRelease
import com.psecurity.psblock.PSBlockPrefs.getLogs
import com.psecurity.psblock.PSBlockPrefs.getSafeWifiNetworks
import com.psecurity.psblock.PSBlockPrefs.getTrustedApps
import com.psecurity.psblock.PSBlockPrefs.hashPassword
import com.psecurity.psblock.PSBlockPrefs.lockPasswordHash
import com.psecurity.psblock.PSBlockPrefs.panicSmsEnabled
import com.psecurity.psblock.PSBlockPrefs.panicSmsNumber
import com.psecurity.psblock.PSBlockPrefs.remoteWipeCode
import com.psecurity.psblock.PSBlockPrefs.remoteWipeEnabled
import com.psecurity.psblock.PSBlockPrefs.rootEnabled
import com.psecurity.psblock.PSBlockPrefs.scheduleBlockEnabled
import com.psecurity.psblock.PSBlockPrefs.scheduleDaysMask
import com.psecurity.psblock.PSBlockPrefs.scheduleEndMinutes
import com.psecurity.psblock.PSBlockPrefs.scheduleStartMinutes
import com.psecurity.psblock.PSBlockPrefs.setSafeWifiNetworks
import com.psecurity.psblock.PSBlockPrefs.setTrustedApps
import com.psecurity.psblock.PSBlockPrefs.temporaryReleaseReason
import com.psecurity.psblock.PSBlockPrefs.temporaryReleaseUntil
import com.psecurity.psblock.PSBlockPrefs.whitelistReleaseEnabled
import com.psecurity.psblock.root.HardwareBlocker
import com.psecurity.psblock.root.RootExecutor
import com.psecurity.psblock.service.NetworkWatchService
import com.psecurity.psblock.service.PSBlockService

class SettingsActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    private val smsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        toast(if (granted) getString(R.string.settings_sms_allowed) else getString(R.string.settings_sms_denied))
        appendLog(if (granted) getString(R.string.settings_log_sms_granted) else getString(R.string.settings_log_sms_denied))
    }

    private val phonePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        toast(if (granted) getString(R.string.settings_call_monitored) else getString(R.string.settings_call_denied))
        appendLog(if (granted) getString(R.string.settings_log_call_granted) else getString(R.string.settings_log_call_denied))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }
        findViewById<View>(R.id.btnSupportProjectSettings)?.setOnClickListener {
            startActivity(Intent(this, com.psecurity.psblock.support.SupportActivity::class.java))
        }

        expand(R.id.tvInterface, R.id.layInterface)
        expand(R.id.tvSecBlock, R.id.laySecBlock)
        expand(R.id.tvNetwork, R.id.layNetwork)
        expand(R.id.tvPolicy, R.id.layPolicy)
        expand(R.id.tvPanic, R.id.layPanic)
        expand(R.id.tvRoot, R.id.layRoot)
        expand(R.id.tvTools, R.id.layTools)

        setupLanguage()
        setupSecurity()
        setupNetwork()
        setupPolicy()
        setupPanic()
        setupRoot()
        setupTools()
        refreshTemporaryReleaseStatus()
    }

    override fun onResume() {
        super.onResume()
        SecurityGate.enforce(this)
        refreshTemporaryReleaseStatus()
    }


    private fun setupLanguage() {
        val group = findViewById<RadioGroup>(R.id.radioLanguageGroup) ?: return
        group.setOnCheckedChangeListener(null)
        group.check(
            if (AppLanguageManager.currentLanguage(this) == AppLanguageManager.LANG_PT_BR)
                R.id.radioLanguagePortuguese
            else
                R.id.radioLanguageEnglish
        )

        group.setOnCheckedChangeListener { _, checkedId ->
            val selected = if (checkedId == R.id.radioLanguagePortuguese)
                AppLanguageManager.LANG_PT_BR
            else
                AppLanguageManager.LANG_EN

            if (AppLanguageManager.setLanguage(this, selected)) {
                toast(getString(R.string.settings_language_saved))
                recreate()
            }
        }
    }

    private fun setupSecurity() {
        click(R.id.btnSetPassword) { showSetPasswordDialog() }

        val swAppLock = findViewById<Switch>(R.id.switchAppLock)
        swAppLock?.isChecked = appLockEnabled && !lockPasswordHash.isNullOrBlank()
        swAppLock?.setOnCheckedChangeListener { button, enabled ->
            if (enabled) {
                if (lockPasswordHash.isNullOrBlank()) {
                    button.isChecked = false
                    showSetPasswordDialog {
                        appLockEnabled = true
                        button.isChecked = true
                        SecurityGate.lockSession()
                        toast(getString(R.string.settings_app_lock_enabled))
                    }
                } else {
                    appLockEnabled = true
                    SecurityGate.lockSession()
                    appendLog(getString(R.string.settings_log_app_lock_enabled))
                }
            } else {
                appLockEnabled = false
                biometricEnabled = false
                findViewById<Switch>(R.id.switchBiometric)?.isChecked = false
                SecurityGate.markUnlocked()
                appendLog(getString(R.string.settings_log_app_lock_disabled))
            }
        }

        val swBio = findViewById<Switch>(R.id.switchBiometric)
        swBio?.isChecked = biometricEnabled && appLockEnabled && !lockPasswordHash.isNullOrBlank()
        swBio?.setOnCheckedChangeListener { button, enabled ->
            if (enabled) {
                when {
                    lockPasswordHash.isNullOrBlank() -> { button.isChecked = false; toast(getString(R.string.settings_define_password_first)) }
                    !appLockEnabled -> { button.isChecked = false; toast(getString(R.string.settings_enable_app_lock_first)) }
                    !canUseBiometric() -> { button.isChecked = false; toast(getString(R.string.settings_biometrics_unavailable_device)) }
                    else -> { biometricEnabled = true; appendLog(getString(R.string.settings_log_biometric_enabled)) }
                }
            } else {
                biometricEnabled = false
                appendLog(getString(R.string.settings_log_biometric_disabled))
            }
        }

        click(R.id.btnSensorOff) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_sensor_off_title))
                .setMessage(
                    getString(R.string.settings_sensor_off_message)
                )
                .setPositiveButton(getString(R.string.settings_open_settings)) { _, _ -> startActivity(Intent(Settings.ACTION_SETTINGS)) }
                .setNegativeButton(getString(R.string.common_ok), null)
                .show()
        }

        click(R.id.btnDisableUsb) {
            if (rootEnabled) {
                val ok = HardwareBlocker.disableUsbDebugging()
                toast(if (ok) getString(R.string.settings_adb_disabled) else getString(R.string.settings_adb_disable_error))
                appendLog("[USB] ADB desabilitado via root")
            } else {
                openUrl("https://developer.android.com/studio/run/device")
                toast(getString(R.string.settings_root_required_manual_guide))
            }
        }
    }

    private fun showSetPasswordDialog(onSuccess: (() -> Unit)? = null) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 12, 32, 0) }
        val pass1 = EditText(this).apply {
            hint = getString(R.string.settings_new_password_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val pass2 = EditText(this).apply {
            hint = getString(R.string.settings_repeat_password_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        box.addView(pass1); box.addView(pass2)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_password_dialog_title))
            .setMessage(getString(R.string.settings_password_dialog_message))
            .setView(box)
            .setPositiveButton(getString(R.string.common_save), null)
            .setNegativeButton(getString(R.string.common_cancel), null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val p1 = pass1.text.toString()
                        val p2 = pass2.text.toString()
                        when {
                            p1.length < 6 -> toast(getString(R.string.settings_password_too_short))
                            p1 != p2 -> { toast(getString(R.string.settings_password_mismatch)); pass2.text?.clear() }
                            else -> {
                                lockPasswordHash = hashPassword(p1)
                                appLockEnabled = true
                                SecurityGate.lockSession()
                                appendLog(getString(R.string.settings_log_local_password_set))
                                toast(getString(R.string.settings_password_saved))
                                onSuccess?.invoke()
                                dialog.dismiss()
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun canUseBiometric(): Boolean = try {
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    } catch (_: Exception) { false }

    private fun setupNetwork() {
        val swAutoWifi = findViewById<Switch>(R.id.switchAutoBlockWifi)
        swAutoWifi?.isChecked = autoBlockOnUnknownWifi
        swAutoWifi?.setOnCheckedChangeListener { _, enabled ->
            autoBlockOnUnknownWifi = enabled
            NetworkWatchService.start(this)
            appendLog(if (enabled) getString(R.string.settings_log_wifi_auto_enabled) else getString(R.string.settings_log_wifi_auto_disabled))
        }

        click(R.id.btnAddSafeWifi) { addCurrentWifiToSafe() }
        click(R.id.btnViewSafeWifis) { showSafeWifis() }
        click(R.id.btnMacRandom) {
            if (rootEnabled) {
                Thread {
                    val (ok, mac) = HardwareBlocker.randomizeMac()
                    runOnUiThread {
                        if (ok) { toast(getString(R.string.settings_mac_changed, mac)); appendLog("[MAC] Randomizado: $mac") }
                        else toast(getString(R.string.settings_mac_error))
                    }
                }.start()
            } else toast(getString(R.string.settings_root_required_mac))
        }

        val swGeofence = findViewById<Switch>(R.id.switchAutoGeofence)
        swGeofence?.isChecked = autoBlockOnGeofenceExit
        swGeofence?.setOnCheckedChangeListener { _, enabled ->
            autoBlockOnGeofenceExit = enabled
            if (enabled && !hasLocationPermission()) toast(getString(R.string.settings_location_permission_needed))
            NetworkWatchService.start(this)
            appendLog(if (enabled) getString(R.string.settings_log_geofence_enabled) else getString(R.string.settings_log_geofence_disabled))
        }
        click(R.id.btnManageGeofences) { startActivity(Intent(this, com.psecurity.psblock.ui.GeofenceListActivity::class.java)) }
    }

    private fun setupPolicy() {
        val swSchedule = findViewById<Switch>(R.id.switchScheduleBlock)
        swSchedule?.isChecked = scheduleBlockEnabled
        swSchedule?.setOnCheckedChangeListener { _, enabled ->
            scheduleBlockEnabled = enabled
            NetworkWatchService.start(this)
            appendLog(if (enabled) getString(R.string.settings_log_schedule_enabled) else getString(R.string.settings_log_schedule_disabled))
        }
        click(R.id.btnScheduleWindow) { showScheduleDialog() }

        val swCall = findViewById<Switch>(R.id.switchCallBlock)
        swCall?.isChecked = blockDuringCallsEnabled
        swCall?.setOnCheckedChangeListener { button, enabled ->
            if (enabled && !hasPhonePermission()) {
                button.isChecked = false
                phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                return@setOnCheckedChangeListener
            }
            blockDuringCallsEnabled = enabled
            appendLog(if (enabled) getString(R.string.settings_log_call_block_enabled) else getString(R.string.settings_log_call_block_disabled))
        }

        val swWhitelist = findViewById<Switch>(R.id.switchWhitelistRelease)
        swWhitelist?.isChecked = whitelistReleaseEnabled
        swWhitelist?.setOnCheckedChangeListener { _, enabled ->
            whitelistReleaseEnabled = enabled
            appendLog(if (enabled) getString(R.string.settings_log_trusted_release_enabled) else getString(R.string.settings_log_trusted_release_disabled))
        }
        click(R.id.btnAddTrustedApp) { showAddTrustedAppDialog() }
        click(R.id.btnViewTrustedApps) { showTrustedApps() }
        click(R.id.btnUseCameraNow) {
            PSBlockService.tempRelease(this, getString(R.string.settings_temp_release_reason_quick), 5 * 60 * 1000L)
            refreshTemporaryReleaseStatus()
            toast(getString(R.string.settings_temp_release_done))
        }
        click(R.id.btnCancelTempRelease) {
            clearTemporaryRelease()
            PSBlockService.update(this)
            refreshTemporaryReleaseStatus()
            toast(getString(R.string.settings_temp_release_cancelled))
        }
    }

    private fun showScheduleDialog() {
        val start = scheduleStartMinutes
        TimePickerDialog(this, { _, sh, sm ->
            val end = scheduleEndMinutes
            TimePickerDialog(this, { _, eh, em ->
                scheduleStartMinutes = sh * 60 + sm
                scheduleEndMinutes = eh * 60 + em
                scheduleDaysMask = 0b1111111
                scheduleBlockEnabled = true
                findViewById<Switch>(R.id.switchScheduleBlock)?.isChecked = true
                NetworkWatchService.start(this)
                appendLog(getString(R.string.settings_log_schedule_defined, formatMinutes(scheduleStartMinutes), formatMinutes(scheduleEndMinutes)))
                toast(getString(R.string.settings_schedule_saved))
            }, end / 60, end % 60, true).show()
        }, start / 60, start % 60, true).show()
    }

    private fun refreshTemporaryReleaseStatus() {
        val tv = findViewById<TextView>(R.id.tvTempReleaseStatus) ?: return
        val active = temporaryReleaseUntil > System.currentTimeMillis()
        tv.text = if (active) getString(R.string.settings_temp_release_active, temporaryReleaseReason.ifBlank { getString(R.string.settings_temp_release_default_reason) }) else getString(R.string.settings_no_temp_release)
        tv.setTextColor(android.graphics.Color.parseColor(if (active) "#4ADE80" else "#8B6CA8"))
    }

    private fun addCurrentWifiToSafe() {
        val wifiMgr = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ssid = wifiMgr.connectionInfo?.ssid?.removeSurrounding("\"")
        if (ssid.isNullOrEmpty() || ssid == "<unknown ssid>") { toast(getString(R.string.settings_no_wifi_connected)); return }
        val current = getSafeWifiNetworks().toMutableSet()
        current.add(ssid)
        setSafeWifiNetworks(current)
        toast(getString(R.string.settings_wifi_saved, ssid))
        appendLog(getString(R.string.settings_log_wifi_added, ssid))
    }

    private fun showSafeWifis() {
        val networks = getSafeWifiNetworks()
        if (networks.isEmpty()) { toast(getString(R.string.settings_no_safe_networks)); return }
        val items = networks.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_safe_networks_title))
            .setItems(items) { _, i ->
                val toRemove = items[i]
                AlertDialog.Builder(this)
                    .setMessage(getString(R.string.settings_remove_item_question, toRemove))
                    .setPositiveButton(getString(R.string.common_remove)) { _, _ ->
                        val updated = getSafeWifiNetworks().toMutableSet()
                        updated.remove(toRemove)
                        setSafeWifiNetworks(updated)
                        toast(getString(R.string.settings_removed))
                    }
                    .setNegativeButton(getString(R.string.common_cancel), null)
                    .show()
            }
            .setNegativeButton(getString(R.string.common_close), null)
            .show()
    }

    private fun showAddTrustedAppDialog() {
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null && it.packageName != packageName }
            .sortedBy { labelOf(it).lowercase() }
            .take(120)
        if (apps.isEmpty()) { toast(getString(R.string.settings_no_apps_found)); return }
        val labels = apps.map { "${labelOf(it)}\n${it.packageName}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_add_trusted_app_title))
            .setItems(labels) { _, index ->
                val pkg = apps[index].packageName
                val current = getTrustedApps().toMutableSet()
                current.add(pkg)
                setTrustedApps(current)
                appendLog(getString(R.string.settings_log_trusted_app_added, pkg))
                toast(getString(R.string.settings_app_added))
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun showTrustedApps() {
        val apps = getTrustedApps()
        if (apps.isEmpty()) { toast(getString(R.string.settings_no_trusted_apps)); return }
        val items = apps.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_trusted_apps_title))
            .setItems(items) { _, index ->
                val pkg = items[index]
                AlertDialog.Builder(this)
                    .setMessage(getString(R.string.settings_remove_item_question, pkg))
                    .setPositiveButton(getString(R.string.common_remove)) { _, _ ->
                        val updated = getTrustedApps().toMutableSet()
                        updated.remove(pkg)
                        setTrustedApps(updated)
                        toast(getString(R.string.settings_removed))
                    }
                    .setNegativeButton(getString(R.string.common_cancel), null)
                    .show()
            }
            .setNegativeButton(getString(R.string.common_close), null)
            .show()
    }

    private fun setupPanic() {
        val swWipe = findViewById<Switch>(R.id.switchRemoteWipe)
        swWipe?.isChecked = remoteWipeEnabled
        swWipe?.setOnCheckedChangeListener { button, enabled ->
            if (enabled) {
                if (!hasSmsPermission()) { button.isChecked = false; requestSmsPermission() }
                else confirmEnableWipe()
            } else {
                remoteWipeEnabled = false
                appendLog(getString(R.string.settings_log_wipe_disabled))
            }
        }

        val etWipeCode = findViewById<EditText>(R.id.etWipeCode)
        etWipeCode?.setText(remoteWipeCode)
        etWipeCode?.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && etWipeCode.text.toString().trim().isNotEmpty() && etWipeCode.text.toString().trim().length < 8) {
                toast(getString(R.string.settings_code_min_8))
            }
        }

        val swPanicSms = findViewById<Switch>(R.id.switchPanicSms)
        swPanicSms?.isChecked = panicSmsEnabled
        swPanicSms?.setOnCheckedChangeListener { button, enabled ->
            if (enabled && !hasSmsPermission()) { button.isChecked = false; requestSmsPermission(); panicSmsEnabled = false }
            else { panicSmsEnabled = enabled; appendLog(if (enabled) getString(R.string.settings_log_panic_sms_enabled) else getString(R.string.settings_log_panic_sms_disabled)) }
        }

        val etPanicNumber = findViewById<EditText>(R.id.etPanicNumber)
        etPanicNumber?.setText(panicSmsNumber)
        etPanicNumber?.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) panicSmsNumber = etPanicNumber.text.toString().trim() }
    }

    private fun confirmEnableWipe() {
        if (!isDeviceAdminActive()) {
            findViewById<Switch>(R.id.switchRemoteWipe)?.isChecked = false
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_device_admin_required_title))
                .setMessage(getString(R.string.settings_device_admin_required_message))
                .setPositiveButton(getString(R.string.settings_activate_device_admin)) { _, _ -> launchDeviceAdminSetup() }
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show()
            return
        }

        val current = findViewById<EditText>(R.id.etWipeCode)?.text?.toString()?.trim().orEmpty()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 12, 32, 0) }
        val code1 = EditText(this).apply {
            hint = getString(R.string.settings_wipe_code_dialog_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(current)
        }
        val code2 = EditText(this).apply {
            hint = getString(R.string.settings_repeat_wipe_code_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val number = EditText(this).apply {
            hint = getString(R.string.settings_authorized_number_optional_hint)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(panicSmsNumber)
        }
        val confirm = EditText(this).apply {
            hint = getString(R.string.settings_confirm_wipe_phrase_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        box.addView(code1); box.addView(code2); box.addView(number); box.addView(confirm)

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_enable_wipe_title))
            .setMessage(getString(R.string.settings_enable_wipe_message))
            .setView(box)
            .setPositiveButton(getString(R.string.common_activate), null)
            .setNegativeButton(getString(R.string.common_cancel)) { _, _ -> findViewById<Switch>(R.id.switchRemoteWipe)?.isChecked = false }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val c1 = code1.text.toString().trim()
                val c2 = code2.text.toString().trim()
                val phrase = confirm.text.toString().trim()
                when {
                    c1.length < 8 -> toast(getString(R.string.settings_code_too_short))
                    c1 != c2 -> { toast(getString(R.string.settings_codes_mismatch)); code2.text?.clear() }
                    !phrase.equals(getString(R.string.settings_confirm_wipe_phrase), ignoreCase = true) -> toast(getString(R.string.settings_invalid_confirmation))
                    else -> {
                        remoteWipeCode = c1
                        panicSmsNumber = number.text.toString().trim()
                        remoteWipeEnabled = true
                        findViewById<EditText>(R.id.etWipeCode)?.setText(c1)
                        findViewById<EditText>(R.id.etPanicNumber)?.setText(panicSmsNumber)
                        appendLog(getString(R.string.settings_log_wipe_enabled))
                        toast(getString(R.string.settings_wipe_enabled))
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun setupRoot() {
        val swRoot = findViewById<Switch>(R.id.switchRootMode)
        swRoot?.isChecked = rootEnabled
        swRoot?.setOnCheckedChangeListener { button, enabled ->
            if (enabled) {
                Thread {
                    val ok = RootExecutor.isRootAvailable()
                    runOnUiThread {
                        if (ok) { rootEnabled = true; PSBlockService.update(this); toast(getString(R.string.settings_root_enabled)); appendLog(getString(R.string.settings_log_root_enabled)) }
                        else { button.isChecked = false; rootEnabled = false; toast(getString(R.string.settings_root_unavailable)) }
                    }
                }.start()
            } else {
                rootEnabled = false
                HardwareBlocker.unblockAll()
                PSBlockService.update(this)
                toast(getString(R.string.settings_root_disabled))
                appendLog(getString(R.string.settings_log_root_disabled))
            }
        }

        click(R.id.btnTestRoot) {
            Thread {
                val result = RootExecutor.exec("id")
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.settings_root_test_title))
                        .setMessage(if (result.success && result.output.contains("uid=0")) getString(R.string.settings_root_ok, result.output) else getString(R.string.settings_root_failed, result.error))
                        .setPositiveButton(getString(R.string.common_ok), null)
                        .show()
                }
            }.start()
        }
        click(R.id.btnOpenTermux) {
            val i = packageManager.getLaunchIntentForPackage("com.termux")
            if (i != null) startActivity(i) else { openUrl("https://f-droid.org/packages/com.termux/"); toast(getString(R.string.settings_termux_not_installed)) }
        }
    }

    private fun setupTools() {
        click(R.id.btnReapply) { PSBlockService.update(this); NetworkWatchService.start(this); toast(getString(R.string.settings_policies_reapplied)); appendLog(getString(R.string.settings_log_sync_reapplied)) }
        click(R.id.btnViewLogs) { startActivity(Intent(this, com.psecurity.psblock.ui.LogViewerActivity::class.java)) }
        click(R.id.btnExportLogs) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, getLogs())
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.settings_security_log_subject))
            }
            startActivity(Intent.createChooser(intent, getString(R.string.settings_export_log_chooser)))
        }
        click(R.id.btnClearLogs) {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.settings_clear_logs_question))
                .setPositiveButton(getString(R.string.common_delete)) { _, _ -> clearLogs(); toast(getString(R.string.settings_logs_deleted)) }
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show()
        }
        click(R.id.btnIntegrity) {
            Thread { val result = checkIntegrity(); runOnUiThread { AlertDialog.Builder(this).setTitle(getString(R.string.settings_integrity_title)).setMessage(result).setPositiveButton(getString(R.string.common_ok), null).show() } }.start()
        }
        click(R.id.btnAccessibility) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        click(R.id.btnGitHub) { openUrl("https://github.com/PSecurity") }
        click(R.id.btnTikTok) { openUrl("https://www.tiktok.com/@PeekSecurity") }
    }

    private fun checkIntegrity(): String {
        val sb = StringBuilder()
        val hasRoot = RootExecutor.isRootAvailable()
        sb.appendLine(if (hasRoot) getString(R.string.settings_integrity_root_available) else getString(R.string.settings_integrity_root_unavailable))
        val magisk = listOf("/sbin/.magisk", "/data/adb/magisk", "/system/xbin/magisk").any { java.io.File(it).exists() }
        sb.appendLine(if (magisk) getString(R.string.settings_integrity_magisk_found) else getString(R.string.settings_integrity_magisk_missing))
        val xposed = listOf("/system/framework/XposedBridge.jar", "/data/data/de.robv.android.xposed.installer").any { java.io.File(it).exists() }
        sb.appendLine(if (xposed) getString(R.string.settings_integrity_xposed_found) else getString(R.string.settings_integrity_xposed_missing))
        val su = listOf("/system/app/Superuser.apk", "/system/xbin/su", "/sbin/su", "/su/bin/su").any { java.io.File(it).exists() }
        sb.appendLine(if (su) getString(R.string.settings_integrity_su_found) else getString(R.string.settings_integrity_su_missing))
        if (rootEnabled) {
            val adbResult = RootExecutor.exec("settings get global adb_enabled")
            val adbEnabled = adbResult.output.trim() == "1"
            sb.appendLine(if (adbEnabled) getString(R.string.settings_integrity_adb_enabled) else getString(R.string.settings_integrity_adb_disabled))
            sb.appendLine(getString(R.string.settings_integrity_camera_nodes, HardwareBlocker.getCameraDeviceStatus()))
        }
        appendLog(getString(R.string.settings_log_integrity_check))
        return sb.toString()
    }


    private fun isDeviceAdminActive(): Boolean {
        return try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.isAdminActive(ComponentName(this, AdminReceiver::class.java))
        } catch (_: Exception) { false }
    }

    private fun launchDeviceAdminSetup() {
        try {
            startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this@SettingsActivity, AdminReceiver::class.java))
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.settings_device_admin_explanation))
            })
        } catch (_: Exception) { toast(getString(R.string.settings_device_admin_open_error)) }
    }

    private fun hasSmsPermission(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    private fun hasPhonePermission(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    private fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestSmsPermission() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_sms_permission_title))
            .setMessage(getString(R.string.settings_sms_permission_message))
            .setPositiveButton(getString(R.string.common_allow)) { _, _ -> smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS) }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun labelOf(app: ApplicationInfo): String = try { packageManager.getApplicationLabel(app).toString() } catch (_: Exception) { app.packageName }
    private fun formatMinutes(value: Int): String = "%02d:%02d".format(value / 60, value % 60)
    private fun expand(titleId: Int, contentId: Int) {
        val t = findViewById<TextView>(titleId) ?: return
        val c = findViewById<View>(contentId) ?: return
        t.setOnClickListener { c.visibility = if (c.visibility == View.GONE) View.VISIBLE else View.GONE }
    }
    private fun click(id: Int, action: () -> Unit) { findViewById<View>(id)?.setOnClickListener { action() } }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun openUrl(url: String) { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { toast(getString(R.string.settings_no_browser)) } }
}
