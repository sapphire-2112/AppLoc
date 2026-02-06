package com.example.apploc

import android.Manifest
import android.app.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class LocationForegroundService : Service() {

    private val DEVICE_ID = "B" // CHANGE TO B ON OTHER PHONE

    private val REPO_URL = "https://github.com/sapphire-2112/Location_Tracker.git"
    private val USERNAME = "sapphire-2112"
    private val TOKEN = ""

    private val handler = Handler(Looper.getMainLooper())
    private val INTERVAL = 15_000L

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        startLoop()
    }

    override fun onBind(intent: android.content.Intent?) = null

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    // ---------- FOREGROUND NOTIFICATION ----------
    private fun startForegroundNotification() {
        val channelId = "loc_sync"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Location Sync",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Location Sync Running")
            .setContentText("Sharing location in background")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

        startForeground(1, notification)
    }

    // ---------- MAIN LOOP ----------
    private fun startLoop() {
        handler.post(object : Runnable {
            override fun run() {
                syncOnce()
                handler.postDelayed(this, INTERVAL)
            }
        })
    }

    private fun syncOnce() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val client = LocationServices.getFusedLocationProviderClient(this)

        client.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            null
        ).addOnSuccessListener { location ->
            if (location == null) return@addOnSuccessListener

            thread {
                try {
                    pullRepoSafe()
                    fetchOtherAndSave()
                    pushMyLocation(location.latitude, location.longitude)
                } catch (_: Exception) { }
            }
        }
    }

    // ---------- GIT ----------
    private fun pullRepoSafe() {
        val dir = File(filesDir, "repo")
        resetIfMerging(dir)

        val git = if (dir.exists()) Git.open(dir)
        else Git.cloneRepository()
            .setURI(REPO_URL)
            .setDirectory(dir)
            .setCredentialsProvider(creds())
            .call()

        git.pull().setCredentialsProvider(creds()).call()
    }

    private fun pushMyLocation(lat: Double, lon: Double) {
        val repoDir = File(filesDir, "repo")
        val myFile = if (DEVICE_ID == "A") "nodeA.json" else "nodeB.json"
        val file = File(repoDir, myFile)
        if (!file.exists()) return

        val json = JSONObject(file.readText().ifBlank { "{}" })
        val key = System.currentTimeMillis().toString()

        json.put(key, JSONObject().apply {
            put("lat", lat)
            put("lon", lon)
            put("time", readableTime())
        })

        file.writeText(json.toString(2))

        val git = Git.open(repoDir)
        git.add().addFilepattern(myFile).call()
        git.commit().setMessage("Device $DEVICE_ID @ $key").call()
        git.push().setCredentialsProvider(creds()).call()
    }

    private fun fetchOtherAndSave() {
        val repoDir = File(filesDir, "repo")
        val otherFile = if (DEVICE_ID == "A") "nodeB.json" else "nodeA.json"
        val file = File(repoDir, otherFile)
        if (!file.exists()) return

        val json = JSONObject(file.readText())
        if (json.length() == 0) return

        val latestKey = json.keys().asSequence()
            .mapNotNull { it.toLongOrNull() }
            .maxOrNull()?.toString() ?: return

        val obj = json.getJSONObject(latestKey)

        getSharedPreferences("LIVE_DATA", Context.MODE_PRIVATE)
            .edit()
            .putFloat("lat", obj.getDouble("lat").toFloat())
            .putFloat("lon", obj.getDouble("lon").toFloat())
            .putString("time", obj.getString("time"))
            .apply()
    }

    private fun resetIfMerging(dir: File) {
        val mergeHead = File(dir, ".git/MERGE_HEAD")
        if (mergeHead.exists()) dir.deleteRecursively()
    }

    private fun creds() =
        UsernamePasswordCredentialsProvider(USERNAME, TOKEN)

    private fun readableTime(): String =
        SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault())
            .format(Date())
}
