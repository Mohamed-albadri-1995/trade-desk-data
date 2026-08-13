package com.ratib.saada

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
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

        spMethod.adapter = ArrayAdapter.createFromResource(
            this, R.array.methods_array, android.R.layout.simple_spinner_dropdown_item
        )

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
    }

    private fun requestLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            fetchLocation()
        } else {
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun fetchLocation() {
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            var loc: android.location.Location? = null
            for (p in providers) {
                if (lm.isProviderEnabled(p)) {
                    @Suppress("MissingPermission")
                    val l = lm.getLastKnownLocation(p)
                    if (l != null && (loc == null || l.time > loc.time)) loc = l
                }
            }
            if (loc != null) {
                etLat.setText(loc.latitude.toString())
                etLng.setText(loc.longitude.toString())
                Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.location_failed, Toast.LENGTH_LONG).show()
            }
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.location_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun save() {
        ReminderPrefs.setFlag(this, "master", swMaster.isChecked)
        ReminderPrefs.setFlag(this, "asas", swAsas.isChecked)
        ReminderPrefs.setFlag(this, "morning", swMorning.isChecked)
        ReminderPrefs.setFlag(this, "evening", swEvening.isChecked)
        ReminderPrefs.setFlag(this, "suhur", swSuhur.isChecked)
        ReminderPrefs.setMethod(this, spMethod.selectedItemPosition)

        val lat = etLat.text.toString().toDoubleOrNull()
        val lng = etLng.text.toString().toDoubleOrNull()
        if (lat != null && lng != null) ReminderPrefs.setLocation(this, lat, lng)

        ReminderScheduler.rescheduleNext(this)

        if (swMaster.isChecked && !ReminderPrefs.hasLocation(this)) {
            Toast.makeText(this, R.string.need_location, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
