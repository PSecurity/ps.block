package com.psecurity.psblock.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.psecurity.psblock.*
import com.psecurity.psblock.PSBlockPrefs.appendLog
import com.psecurity.psblock.PSBlockPrefs.clearLogs
import com.psecurity.psblock.PSBlockPrefs.getLogs
import com.psecurity.psblock.PSBlockPrefs.getGeofencesJson
import com.psecurity.psblock.PSBlockPrefs.setGeofencesJson
import org.json.JSONArray
import org.json.JSONObject

// Log viewer
class LogViewerActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = android.widget.ScrollView(this)
        val tv = TextView(this).apply {
            text = getLogs().ifEmpty { getString(R.string.logs_empty) }
            setTextColor(android.graphics.Color.parseColor("#C084FC"))
            textSize = 11f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            setPadding(24, 24, 24, 24)
            setBackgroundColor(android.graphics.Color.parseColor("#0A0010"))
        }
        root.addView(tv)
        setContentView(root)

        title = getString(R.string.log_viewer_title)
    }
}

// Geofence list
class GeofenceListActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }


    private lateinit var listView: ListView
    private val geofences = mutableListOf<JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#0A0010"))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
        }

        val title = TextView(this).apply {
            text = getString(R.string.geofence_title)
            setTextColor(android.graphics.Color.parseColor("#C084FC"))
            textSize = 16f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnAdd = Button(this).apply {
            text = "+"
            setOnClickListener { showAddGeofenceDialog() }
        }

        header.addView(title)
        header.addView(btnAdd)

        listView = ListView(this)
        layout.addView(header)
        layout.addView(listView)
        setContentView(layout)

        loadGeofences()
    }

    private fun loadGeofences() {
        geofences.clear()
        val arr = try { JSONArray(getGeofencesJson()) } catch (_: Exception) { JSONArray() }
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { geofences.add(it) }
        }

        val names = geofences.map {
            "${it.optString("name")} - r:${it.optInt("radius")}m"
        }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.geofence_remove_question))
                .setPositiveButton(getString(R.string.common_remove)) { _, _ ->
                    geofences.removeAt(pos)
                    saveGeofences()
                    loadGeofences()
                }
                .setNegativeButton(getString(R.string.common_cancel), null).show()
            true
        }
    }

    private fun showAddGeofenceDialog() {
        val etName   = EditText(this).apply { hint = getString(R.string.geofence_name_hint) }
        val etLat    = EditText(this).apply { hint = getString(R.string.geofence_lat_hint); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED }
        val etLng    = EditText(this).apply { hint = getString(R.string.geofence_lng_hint); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED }
        val etRadius = EditText(this).apply { hint = getString(R.string.geofence_radius_hint); inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val btnCurrentLocation = Button(this).apply {
            text = getString(R.string.geofence_use_current_location)
            setOnClickListener {
                val current = getLastKnownLocationSafe()
                if (current != null) {
                    etLat.setText(current.latitude.toString())
                    etLng.setText(current.longitude.toString())
                    if (etName.text.isNullOrBlank()) etName.setText(getString(R.string.geofence_current_name))
                    Toast.makeText(this@GeofenceListActivity, getString(R.string.geofence_current_loaded), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@GeofenceListActivity, getString(R.string.geofence_no_recent_location), Toast.LENGTH_SHORT).show()
                }
            }
        }

        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
            addView(etName); addView(etLat); addView(etLng); addView(etRadius); addView(btnCurrentLocation)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.geofence_new_title))
            .setView(ll)
            .setPositiveButton(getString(R.string.geofence_add)) { _, _ ->
                try {
                    val geo = JSONObject().apply {
                        put("name", etName.text.toString().ifEmpty { getString(R.string.geofence_default_name, geofences.size + 1) })
                        put("lat", etLat.text.toString().toDouble())
                        put("lng", etLng.text.toString().toDouble())
                        put("radius", etRadius.text.toString().toIntOrNull() ?: 200)
                    }
                    geofences.add(geo)
                    saveGeofences()
                    loadGeofences()
                    appendLog(getString(R.string.geofence_log_added, geo.optString("name")))
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.geofence_invalid_data), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.common_cancel), null).show()
    }

    private fun getLastKnownLocationSafe(): android.location.Location? {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        return try {
            val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            manager.getProviders(true).mapNotNull { provider ->
                try { manager.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            }.maxByOrNull { it.time }
        } catch (_: Exception) { null }
    }

    private fun saveGeofences() {
        val arr = JSONArray()
        geofences.forEach { arr.put(it) }
        setGeofencesJson(arr.toString())
    }
}

// Wi-Fi list
class WifiListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Redireciona para SettingsActivity que já tem a gestão de Wi-Fi
        finish()
        startActivity(Intent(this, com.psecurity.psblock.SettingsActivity::class.java))
    }
}
