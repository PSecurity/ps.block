package com.psecurity.psblock.root

import android.util.Log

/**
 * Bloqueios reais de hardware via root.
 * Usa chmod nos device nodes para impedir acesso a câmera, microfone e GPS.
 */
object HardwareBlocker {

    private const val TAG = "PS_HW"

    // ── CÂMERA ────────────────────────────────────────────────────────────

    fun blockCamera(): Boolean {
        Log.d(TAG, "Bloqueando câmera via root...")
        val result = RootExecutor.execAll(listOf(
            // Bloqueia todos os nós de vídeo V4L2
            "chmod 000 /dev/video* 2>/dev/null || true",
            // Bloqueia nós específicos de câmera (Qualcomm / MediaTek / Samsung)
            "chmod 000 /dev/msm_camera 2>/dev/null || true",
            "chmod 000 /dev/msm_camsensor 2>/dev/null || true",
            "chmod 000 /dev/camera 2>/dev/null || true",
            "chmod 000 /dev/cam_isp* 2>/dev/null || true",
            "chmod 000 /dev/cam_fd 2>/dev/null || true",
            // Bloqueia via appops para todos os UIDs
            "appops set --all android:camera deny 2>/dev/null || true"
        ))
        Log.d(TAG, "Camera block result: ${result.success} | ${result.output}")
        return result.success
    }

    fun unblockCamera(): Boolean {
        Log.d(TAG, "Liberando câmera via root...")
        val result = RootExecutor.execAll(listOf(
            "chmod 660 /dev/video* 2>/dev/null || true",
            "chmod 660 /dev/msm_camera 2>/dev/null || true",
            "chmod 660 /dev/msm_camsensor 2>/dev/null || true",
            "chmod 660 /dev/camera 2>/dev/null || true",
            "chmod 660 /dev/cam_isp* 2>/dev/null || true",
            "chmod 660 /dev/cam_fd 2>/dev/null || true",
            "appops set --all android:camera allow 2>/dev/null || true"
        ))
        return result.success
    }

    // ── MICROFONE ─────────────────────────────────────────────────────────

    fun blockMicrophone(): Boolean {
        Log.d(TAG, "Bloqueando microfone via root...")
        val result = RootExecutor.execAll(listOf(
            // Bloqueia nós de áudio de entrada
            "chmod 000 /dev/snd/pcmC*D*c 2>/dev/null || true",
            "chmod 000 /dev/snd/controlC* 2>/dev/null || true",
            // Muta o microfone via ALSA (se disponível)
            "amixer set Mic 0% mute 2>/dev/null || true",
            "amixer set 'Mic Boost' 0% mute 2>/dev/null || true",
            // Bloqueia via appops
            "appops set --all android:record_audio deny 2>/dev/null || true"
        ))
        Log.d(TAG, "Mic block result: ${result.success}")
        return result.success
    }

    fun unblockMicrophone(): Boolean {
        Log.d(TAG, "Liberando microfone via root...")
        val result = RootExecutor.execAll(listOf(
            "chmod 660 /dev/snd/pcmC*D*c 2>/dev/null || true",
            "chmod 660 /dev/snd/controlC* 2>/dev/null || true",
            "amixer set Mic 100% unmute 2>/dev/null || true",
            "amixer set 'Mic Boost' 100% unmute 2>/dev/null || true",
            "appops set --all android:record_audio allow 2>/dev/null || true"
        ))
        return result.success
    }

    // ── GPS / LOCALIZAÇÃO ─────────────────────────────────────────────────

    fun blockLocation(): Boolean {
        Log.d(TAG, "Bloqueando localização via root...")
        val result = RootExecutor.execAll(listOf(
            // Desativa o daemon de GPS
            "stop gpsd 2>/dev/null || true",
            "stop vendor.gnss-hal-2-0 2>/dev/null || true",
            "stop vendor.gnss-hal 2>/dev/null || true",
            // Bloqueia o nó do GPS
            "chmod 000 /dev/ttyS* 2>/dev/null || true",
            "chmod 000 /dev/gnss* 2>/dev/null || true",
            // Bloqueia via appops
            "appops set --all android:fine_location deny 2>/dev/null || true",
            "appops set --all android:coarse_location deny 2>/dev/null || true",
            // Desativa via settings
            "settings put secure location_mode 0 2>/dev/null || true"
        ))
        Log.d(TAG, "Location block result: ${result.success}")
        return result.success
    }

    fun unblockLocation(): Boolean {
        Log.d(TAG, "Liberando localização via root...")
        val result = RootExecutor.execAll(listOf(
            "start gpsd 2>/dev/null || true",
            "start vendor.gnss-hal-2-0 2>/dev/null || true",
            "start vendor.gnss-hal 2>/dev/null || true",
            "chmod 660 /dev/ttyS* 2>/dev/null || true",
            "chmod 660 /dev/gnss* 2>/dev/null || true",
            "appops set --all android:fine_location allow 2>/dev/null || true",
            "appops set --all android:coarse_location allow 2>/dev/null || true",
            "settings put secure location_mode 3 2>/dev/null || true"
        ))
        return result.success
    }

    // ── BLUETOOTH ─────────────────────────────────────────────────────────

    fun disableBluetooth(): Boolean {
        val result = RootExecutor.execAll(listOf(
            "service call bluetooth_manager 8 2>/dev/null || true",
            "settings put global bluetooth_on 0 2>/dev/null || true",
            "svc bluetooth disable 2>/dev/null || true"
        ))
        return result.success
    }

    fun enableBluetooth(): Boolean {
        val result = RootExecutor.execAll(listOf(
            "service call bluetooth_manager 6 2>/dev/null || true",
            "settings put global bluetooth_on 1 2>/dev/null || true",
            "svc bluetooth enable 2>/dev/null || true"
        ))
        return result.success
    }

    // ── WI-FI ─────────────────────────────────────────────────────────────

    fun disableWifi(): Boolean {
        val result = RootExecutor.exec("svc wifi disable 2>/dev/null || settings put global wifi_on 0")
        return result.success
    }

    fun enableWifi(): Boolean {
        val result = RootExecutor.exec("svc wifi enable 2>/dev/null || settings put global wifi_on 1")
        return result.success
    }

    // ── MAC RANDOMIZATION ─────────────────────────────────────────────────

    fun randomizeMac(iface: String = "wlan0"): Pair<Boolean, String> {
        val newMac = generateRandomMac()
        val result = RootExecutor.execAll(listOf(
            "ip link set $iface down",
            "ip link set $iface address $newMac",
            "ip link set $iface up"
        ))
        return Pair(result.success, newMac)
    }

    private fun generateRandomMac(): String {
        val r = java.util.Random()
        // Bit 0 do primeiro octeto = 0 (unicast), bit 1 = 1 (locally administered)
        val first = (r.nextInt(256) and 0xFE) or 0x02
        return String.format(
            "%02x:%02x:%02x:%02x:%02x:%02x",
            first,
            r.nextInt(256), r.nextInt(256),
            r.nextInt(256), r.nextInt(256), r.nextInt(256)
        )
    }

    // ── USB DEBUG ─────────────────────────────────────────────────────────

    fun disableUsbDebugging(): Boolean {
        val result = RootExecutor.execAll(listOf(
            "settings put global adb_enabled 0",
            "setprop service.adb.enabled 0",
            "stop adbd 2>/dev/null || true"
        ))
        return result.success
    }

    // ── BLOQUEIO TOTAL ────────────────────────────────────────────────────

    fun blockAll(): Boolean {
        val cam = blockCamera()
        val mic = blockMicrophone()
        val loc = blockLocation()
        return cam && mic && loc
    }

    fun unblockAll(): Boolean {
        val cam = unblockCamera()
        val mic = unblockMicrophone()
        val loc = unblockLocation()
        return cam && mic && loc
    }

    // ── STATUS ────────────────────────────────────────────────────────────

    fun getCameraDeviceStatus(): String {
        val result = RootExecutor.exec("ls -la /dev/video* 2>/dev/null || echo 'nenhum'")
        return result.output.trim()
    }

    fun getAppopsStatus(app: String): String {
        val result = RootExecutor.exec("appops get $app android:camera 2>/dev/null")
        return result.output.trim()
    }
}
