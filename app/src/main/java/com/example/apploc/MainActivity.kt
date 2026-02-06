package com.example.apploc

import android.Manifest
import android.content.pm.PackageManager
import android.os.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    // 🔴 CHANGE PER DEVICE
    private val DEVICE_ID = "B" // A or B

    private val REPO_URL = "https://github.com/sapphire-2112/Location_Tracker.git"
    private val USERNAME = "sapphire-2112"
    private val TOKEN = "PAT"

    private lateinit var myStatus: TextView
    private lateinit var otherStatus: TextView
    private lateinit var mapView: MapView
    private var otherDeviceMarker: Marker? = null

    private val pushHandler = Handler(Looper.getMainLooper())
    private val fetchHandler = Handler(Looper.getMainLooper())

    private val PUSH_INTERVAL = 20_000L
    private val FETCH_INTERVAL = 10_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OSMDroid setup
        Configuration.getInstance().userAgentValue = packageName

        // -------- ROOT LAYOUT --------
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // -------- PUSH SECTION --------
        myStatus = TextView(this).apply {
            text = "My Device (PUSH)\nWaiting..."
            textSize = 15f
        }

        // -------- FETCH SECTION --------
        otherStatus = TextView(this).apply {
            text = "Other Device (FETCH)\nWaiting..."
            textSize = 15f
        }

        // -------- MAP SECTION (SMALL) --------
        mapView = MapView(this).apply {
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1400 // 👈 FIXED HEIGHT (small section)
            )
        }

        // -------- ADD TO ROOT --------
        root.addView(myStatus)
        root.addView(TextView(this).apply { text = "\n-----------------\n" })
        root.addView(otherStatus)
        root.addView(TextView(this).apply { text = "\n-----------------\n" })
        root.addView(mapView)

        setContentView(root)
        // --------------------------------

        // Permission
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                101
            )
            return
        }

        startAutoPush()
        startAutoFetch()
    }

    // 🔁 AUTO PUSH
    private fun startAutoPush() {
        pushHandler.post(object : Runnable {
            override fun run() {

                val locationClient =
                    LocationServices.getFusedLocationProviderClient(this@MainActivity)

                locationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        thread {
                            pushLocation(location.latitude, location.longitude)
                        }
                    }
                }

                pushHandler.postDelayed(this, PUSH_INTERVAL)
            }
        })
    }

    // 🔁 AUTO FETCH
    private fun startAutoFetch() {
        fetchHandler.post(object : Runnable {
            override fun run() {
                thread {
                    fetchAndUpdateMap()
                }
                fetchHandler.postDelayed(this, FETCH_INTERVAL)
            }
        })
    }

    // ⬆️ PUSH MY LOCATION
    private fun pushLocation(lat: Double, lon: Double) {

        val repoDir = File(filesDir, "repo")
        val myFile =
            if (DEVICE_ID == "A") "nodeA.json" else "nodeB.json"

        try {
            val git = if (repoDir.exists()) {
                Git.open(repoDir)
            } else {
                Git.cloneRepository()
                    .setURI(REPO_URL)
                    .setDirectory(repoDir)
                    .setCredentialsProvider(
                        UsernamePasswordCredentialsProvider(USERNAME, TOKEN)
                    )
                    .call()
            }

            git.pull()
                .setCredentialsProvider(
                    UsernamePasswordCredentialsProvider(USERNAME, TOKEN)
                )
                .call()

            val jsonFile = File(repoDir, myFile)
            if (!jsonFile.exists()) return

            val json = JSONObject(jsonFile.readText().ifBlank { "{}" })
            val time = System.currentTimeMillis().toString()

            json.put(time, JSONObject().apply {
                put("lat", lat)
                put("lon", lon)
            })

            jsonFile.writeText(json.toString(2))

            git.add().addFilepattern(myFile).call()
            git.commit().setMessage("Device $DEVICE_ID update $time").call()
            git.push()
                .setCredentialsProvider(
                    UsernamePasswordCredentialsProvider(USERNAME, TOKEN)
                )
                .call()

            runOnUiThread {
                myStatus.text =
                    "My Device (PUSH)\nTime: $time\nLat=$lat\nLon=$lon"
            }

        } catch (e: Exception) {
            runOnUiThread {
                myStatus.text = "Push error: ${e.message}"
            }
        }
    }

    // ⬇️ FETCH + MAP UPDATE
    private fun fetchAndUpdateMap() {

        val repoDir = File(filesDir, "repo")
        val otherFile =
            if (DEVICE_ID == "A") "nodeB.json" else "nodeA.json"

        try {
            val git = Git.open(repoDir)

            git.pull()
                .setCredentialsProvider(
                    UsernamePasswordCredentialsProvider(USERNAME, TOKEN)
                )
                .call()

            val file = File(repoDir, otherFile)
            if (!file.exists()) return

            val json = JSONObject(file.readText())
            if (json.length() == 0) return

            val latestKey = json.keys().asSequence().maxOrNull()!!
            val obj = json.getJSONObject(latestKey)

            val lat = obj.getDouble("lat")
            val lon = obj.getDouble("lon")

            runOnUiThread {
                otherStatus.text =
                    "Other Device (FETCH)\nTime: $latestKey\nLat=$lat\nLon=$lon"
                updateMap(lat, lon)
            }

        } catch (e: Exception) {
            runOnUiThread {
                otherStatus.text = "Fetch error: ${e.message}"
            }
        }
    }

    // 🗺️ UPDATE MAP MARKER
    private fun updateMap(lat: Double, lon: Double) {

        val point = GeoPoint(lat, lon)

        if (otherDeviceMarker == null) {
            otherDeviceMarker = Marker(mapView).apply {
                position = point
                title = "Other Device"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(otherDeviceMarker)
            mapView.controller.setCenter(point)
        } else {
            otherDeviceMarker!!.position = point
            mapView.controller.animateTo(point)
        }

        mapView.invalidate()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 101 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startAutoPush()
            startAutoFetch()
        }
    }
}
