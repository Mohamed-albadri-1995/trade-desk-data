package com.ratib.saada

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

/** Reminder settings: which reminders are on, calculation method, and location. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var swMaster: SwitchCompat
    private lateinit var swAsas: SwitchCompat
    private lateinit var swMorning: SwitchCompat
    private lateinit var swEvening: SwitchCompat
    private lateinit var swSuhur: SwitchCompat
    private lateinit var spMethod: Spinner
    private lateinit var etLat: EditText
    private lateinit var etLng: EditText

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { it }) fetchLocation()
            else Toast.makeText(this, R.string.location_failed, Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        swMaster = findViewById(R.id.swMaster)
        swAsas = findViewById(R.id.swAsas)
        swMorning = findViewById(R.id.swMorning)
        swEvening = findViewById(R.id.swEvening)
        swSuhur = findViewById(R.id.swSuhur)
        spMethod = findViewById(R.id.spMethod)
        etLat = findViewById(R.id.etLat)
        etLng = findViewById(R.id.etLng)

        spMethod.adapter = ArrayAdapter(
            this, R.layout.spinner_item, resources.getStringArray(R.array.methods_array)
        ).also { it.setDropDownViewResource(R.layout.spinner_item) }

        // Load current values.
        swMaster.isChecked = ReminderPrefs.master(this)
        swAsas.isChecked = ReminderPrefs.asas(this)
        swMorning.isChecked = ReminderPrefs.morning(this)
        swEvening.isChecked = ReminderPrefs.evening(this)
        swSuhur.isChecked = ReminderPrefs.suhur(this)
        spMethod.setSelection(ReminderPrefs.method(this))
        ReminderPrefs.lat(this)?.let { etLat.setText(it.toString()) }
        ReminderPrefs.lng(this)?.let { etLng.setText(it.toString()) }

        findViewById<Button>(R.id.btnUseLocation).setOnClickListener { requestLocation() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }

        // Ask for location up-front (like notifications) when reminders are on and
        // we don't have a location yet.
        if (ReminderPrefs.master(this) && !hasLocationPermission() && !ReminderPrefs.hasLocation(this)) {
            locationPermission.launch(locationPerms)
        }
    }

    private val locationPerms = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestLocation() {
        if (hasLocationPermission()) fetchLocation()
        else locationPermission.launch(locationPerms)
    }

    private fun fill(loc: Location) {
        etLat.setText(loc.latitude.toString())
        etLng.setText(loc.longitude.toString())
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
    }

    private fun fetchLocation() {
        if (!hasLocationPermission()) { requestLocation(); return }
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            // Best cached fix first.
            var loc: Location? = null
            for (p in listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )) {
                if (!lm.isProviderEnabled(p)) continue
                @Suppress("MissingPermission")
                val l = lm.getLastKnownLocation(p)
                if (l != null && (loc == null || l.time > loc.time)) loc = l
            }
            if (loc != null) { fill(loc); return }
            // No cache — request one live fix.
            requestLiveFix(lm)
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.location_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun requestLiveFix(lm: LocationManager) {
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null) {
            Toast.makeText(this, R.string.location_failed, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.locating, Toast.LENGTH_SHORT).show()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.getCurrentLocation(provider, null, mainExecutor) { loc ->
                    runOnUiThread {
                        if (loc != null) fill(loc)
                        else Toast.makeText(this, R.string.location_failed, Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, object : LocationListener {
                    override fun onLocationChanged(location: Location) { fill(location) }
                    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {}
                }, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.location_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun save() {
        try {
            ReminderPrefs.setFlag(this, "master", swMaster.isChecked)
            ReminderPrefs.setFlag(this, "asas", swAsas.isChecked)
            ReminderPrefs.setFlag(this, "morning", swMorning.isChecked)
            ReminderPrefs.setFlag(this, "evening", swEvening.isChecked)
            ReminderPrefs.setFlag(this, "suhur", swSuhur.isChecked)
            ReminderPrefs.setMethod(this, spMethod.selectedItemPosition)

            val lat = etLat.text.toString().trim().toDoubleOrNull()
            val lng = etLng.text.toString().trim().toDoubleOrNull()
            if (lat != null && lng != null) ReminderPrefs.setLocation(this, lat, lng)

            ReminderScheduler.rescheduleNext(this)

            val msg = if (swMaster.isChecked && !ReminderPrefs.hasLocation(this))
                R.string.need_location else R.string.saved
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            android.util.Log.e("SettingsActivity", "save failed", t)
        }
        finish()
    }
}
