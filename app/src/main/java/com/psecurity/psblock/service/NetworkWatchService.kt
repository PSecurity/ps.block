package com.psecurity.psblock.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.psecurity.psblock.MainActivity
import com.psecurity.psblock.PSBlockPrefs.appendLog
import com.psecurity.psblock.PSBlockPrefs.autoReasonSchedule
import com.psecurity.psblock.PSBlockPrefs.autoReasonGeofence
import com.psecurity.psblock.PSBlockPrefs.autoReasonWifi
import com.psecurity.psblock.PSBlockPrefs.releaseAutoBlock
import com.psecurity.psblock.PSBlockPrefs.activateAutoBlock
import com.psecurity.psblock.PSBlockPrefs.autoBlockOnGeofenceExit
import com.psecurity.psblock.PSBlockPrefs.autoBlockOnUnknownWifi
import com.psecurity.psblock.PSBlockPrefs.cameraBlocked
import com.psecurity.psblock.PSBlockPrefs.getGeofencesJson
import com.psecurity.psblock.PSBlockPrefs.getSafeWifiNetworks
import com.psecurity.psblock.PSBlockPrefs.locationBlocked
import com.psecurity.psblock.PSBlockPrefs.micBlocked
import com.psecurity.psblock.PSBlockPrefs.scheduleBlockEnabled
import com.psecurity.psblock.PSBlockPrefs.scheduleDaysMask
import com.psecurity.psblock.PSBlockPrefs.scheduleEndMinutes
import com.psecurity.psblock.PSBlockPrefs.scheduleStartMinutes
import com.psecurity.psblock.R
import org.json.JSONArray
import java.util.Calendar
import com.psecurity.psblock.AppLanguageManager

class NetworkWatchService : Service() {

    companion object {
        private const val CHANNEL_ID = "ps_block_watch_ch"
        private const val NOTIF_ID = 1002

        fun start(ctx: Context) {
            try {
                val intent = Intent(ctx, NetworkWatchService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
                else ctx.startService(intent)
            } catch (_: Exception) {}
        }
    }

    private lateinit var connMgr: ConnectivityManager
    private val handler = Handler(Looper.getMainLooper())
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastOutsideGeofence: Boolean? = null
    private var lastScheduleActive: Boolean? = null
    private var locationManager: LocationManager? = null

    private val periodicCheck = object : Runnable {
        override fun run() {
            checkCurrentNetwork()
            checkGeofenceState()
            checkScheduleState()
            handler.postDelayed(this, 60_000L)
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) { checkGeofenceState(location) }
        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) { checkGeofenceState() }
        override fun onProviderDisabled(provider: String) { appendLog(AppLanguageManager.text(applicationContext, R.string.network_log_gps_provider_disabled, provider)) }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        connMgr = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        registerNetworkCallback()
        registerLocationUpdates()
        schedulePeriodicCheck(immediate = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try { startForeground(NOTIF_ID, buildNotification()) } catch (_: Exception) {}
        registerLocationUpdates()
        schedulePeriodicCheck(immediate = true)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { networkCallback?.let { connMgr.unregisterNetworkCallback(it) } } catch (_: Exception) {}
        try { locationManager?.removeUpdates(locationListener) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun schedulePeriodicCheck(immediate: Boolean) {
        handler.removeCallbacks(periodicCheck)
        if (immediate) {
            handler.post(periodicCheck)
        } else {
            handler.postDelayed(periodicCheck, 2_000L)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "PS.Block Watch", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
                description = "Monitor de rede, agenda e geofence"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val pi = PendingIntent.getActivity(
            this, 10, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(AppLanguageManager.text(this, R.string.network_watch_channel_name))
            .setContentText(AppLanguageManager.text(this, R.string.network_watch_notification_text))
            .setSmallIcon(R.drawable.ic_psblock)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { handler.postDelayed({ checkCurrentNetwork() }, 1200L) }
            override fun onLost(network: Network) { appendLog(AppLanguageManager.text(applicationContext, R.string.network_log_wifi_disconnected)) }
        }
        try { connMgr.registerNetworkCallback(request, networkCallback!!) } catch (_: Exception) {}
    }

    private fun registerLocationUpdates() {
        try { locationManager?.removeUpdates(locationListener) } catch (_: Exception) {}
        if (!autoBlockOnGeofenceExit) return
        if (!hasLocationPermission()) return
        try {
            val lm = locationManager ?: return
            lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 60_000L, 30f, locationListener)
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 120_000L, 50f, locationListener)
            }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 180_000L, 80f, locationListener)
            }
        } catch (_: Exception) {}
    }

    private fun checkCurrentNetwork() {
        if (!autoBlockOnUnknownWifi) {
            releaseAutoBlock(autoReasonWifi())
            PSBlockService.update(applicationContext)
            return
        }
        val ssid = getCurrentSsid()
        val safeNetworks = getSafeWifiNetworks()
        if (ssid.isNullOrBlank() || safeNetworks.isEmpty()) {
            releaseAutoBlock(autoReasonWifi())
            PSBlockService.update(applicationContext)
            return
        }
        val cleanSsid = ssid.removeSurrounding("\"")
        if (!safeNetworks.contains(cleanSsid)) {
            appendLog(AppLanguageManager.text(applicationContext, R.string.network_log_unknown_wifi, cleanSsid))
            activateAutoBlock(autoReasonWifi())
        } else {
            appendLog(AppLanguageManager.text(applicationContext, R.string.network_log_safe_wifi, cleanSsid))
            releaseAutoBlock(autoReasonWifi())
        }
        PSBlockService.update(applicationContext)
    }

    private fun getCurrentSsid(): String? {
        return try {
            val wifiMgr = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiMgr.connectionInfo?.ssid?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
        } catch (_: Exception) { null }
    }

    private fun checkGeofenceState(location: Location? = null) {
        if (!autoBlockOnGeofenceExit) {
            releaseAutoBlock(autoReasonGeofence())
            PSBlockService.update(applicationContext)
            return
        }
        if (!hasLocationPermission()) return
        val geofences = try { JSONArray(getGeofencesJson()) } catch (_: Exception) { JSONArray() }
        if (geofences.length() == 0) {
            releaseAutoBlock(autoReasonGeofence())
            PSBlockService.update(applicationContext)
            return
        }
        val current = location ?: getLastKnownLocation() ?: return

        var insideAny = false
        for (i in 0 until geofences.length()) {
            val geo = geofences.optJSONObject(i) ?: continue
            val lat = geo.optDouble("lat", Double.NaN)
            val lng = geo.optDouble("lng", Double.NaN)
            val radius = geo.optInt("radius", 200).coerceAtLeast(50)
            if (lat.isNaN() || lng.isNaN()) continue
            val result = FloatArray(1)
            Location.distanceBetween(current.latitude, current.longitude, lat, lng, result)
            if (result[0] <= radius) {
                insideAny = true
                break
            }
        }

        val outside = !insideAny
        if (outside && lastOutsideGeofence != true) {
            appendLog(AppLanguageManager.text(applicationContext, R.string.network_log_outside_safe_zone))
            activateAutoBlock(autoReasonGeofence())
            PSBlockService.update(applicationContext)
        } else if (!outside && lastOutsideGeofence == true) {
            appendLog(AppLanguageManager.text(applicationContext, R.string.network_log_safe_zone_detected))
            releaseAutoBlock(autoReasonGeofence())
            PSBlockService.update(applicationContext)
        }
        lastOutsideGeofence = outside
    }

    private fun checkScheduleState() {
        if (!scheduleBlockEnabled) {
            releaseAutoBlock(autoReasonSchedule())
            PSBlockService.update(applicationContext)
            return
        }
        val now = Calendar.getInstance()
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val dayBit = when (now.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
        val dayAllowed = (scheduleDaysMask and (1 shl dayBit)) != 0
        val start = scheduleStartMinutes
        val end = scheduleEndMinutes
        val inWindow = if (start <= end) minute in start until end else minute >= start || minute < end
        val active = dayAllowed && inWindow
        if (active && lastScheduleActive != true) {
            appendLog(AppLanguageManager.text(applicationContext, R.string.network_log_schedule_active))
            activateAutoBlock(autoReasonSchedule())
            PSBlockService.update(applicationContext)
        }
        if (!active && lastScheduleActive == true) {
            appendLog(AppLanguageManager.text(applicationContext, R.string.network_log_schedule_finished))
            releaseAutoBlock(autoReasonSchedule())
            PSBlockService.update(applicationContext)
        }
        lastScheduleActive = active
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun getLastKnownLocation(): Location? {
        return try {
            val lm = locationManager ?: return null
            lm.getProviders(true).mapNotNull { provider ->
                try { lm.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            }.maxByOrNull { it.time }
        } catch (_: Exception) { null }
    }

    private fun activateCoreProtection() {
        cameraBlocked = true
        micBlocked = true
        locationBlocked = true
        PSBlockService.update(applicationContext)
    }
}
