package com.example.apploc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var mapView: MapView
    private var marker: Marker? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().userAgentValue = packageName

        status = TextView(this)

        mapView = MapView(this).apply {
            setMultiTouchControls(true)
            controller.setZoom(5.0)
            controller.setCenter(GeoPoint(20.5937, 78.9629))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 900
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(mapView)
        }

        setContentView(root)

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                101
            )
        } else start()
    }

    private fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, LocationForegroundService::class.java))
        } else {
            startService(Intent(this, LocationForegroundService::class.java))
        }

        handler.post(updateRunnable)
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateUI()
            handler.postDelayed(this, 2000)
        }
    }

    private fun updateUI() {
        val prefs = getSharedPreferences("LIVE_DATA", MODE_PRIVATE)
        if (!prefs.contains("lat")) {
            status.text = "Waiting for other device..."
            return
        }

        val lat = prefs.getFloat("lat", 0f).toDouble()
        val lon = prefs.getFloat("lon", 0f).toDouble()
        val time = prefs.getString("time", "")

        status.text = "Other Device\n$time\nLat: $lat\nLon: $lon"

        val point = GeoPoint(lat, lon)

        if (marker == null) {
            marker = Marker(mapView).apply {
                position = point
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(marker)
        } else {
            marker!!.position = point
        }

        mapView.controller.setZoom(18.0)
        mapView.controller.animateTo(point)
        mapView.invalidate()
    }
}
