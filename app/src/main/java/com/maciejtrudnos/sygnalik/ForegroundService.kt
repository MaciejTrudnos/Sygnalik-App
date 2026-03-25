package com.maciejtrudnos.sygnalik

import android.annotation.SuppressLint
import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationCallback
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ForegroundService : Service() {
    lateinit var bleManager: BLEManager

    private val client = OkHttpClient()

    private lateinit var smsReceiver: SmsReceiver
    private lateinit var callReceiver: CallReceiver
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private lateinit var locationProvider: LocationProvider
    private lateinit var locationCallback: LocationCallback

    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private val binder = LocalBinder()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> get() = _ready

    private val _bleText = MutableStateFlow("")
    val bleText: StateFlow<String> get() = _bleText

    val traccarHost = BuildConfig.TRACCAR_HOST
    val traccarDeviceId = BuildConfig.TRACCAR_DEVICE_ID

    inner class LocalBinder : Binder() {
        fun getService(): ForegroundService = this@ForegroundService
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "sygnalik_channel",
                "Sygnalik Background Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val scope = CoroutineScope(Dispatchers.IO)

        val notification = NotificationCompat.Builder(this, "sygnalik_channel")
            .setContentTitle("Sygnalik Running")
            .setContentText("Sygnalik app is working in the background")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

        startForeground(1, notification)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        bleManager = BLEManager(this, bluetoothLeScanner, { message ->
            _bleText.value = message
        })

        _ready.value = true
        bleManager.startScan()

        smsReceiver = SmsReceiver()
        SmsReceiver.bleManager = bleManager

        callReceiver = CallReceiver()
        CallReceiver.bleManager = bleManager

        val speedCameras = getSpeedCameras();

        locationProvider = LocationProvider(this)

        locationCallback = locationProvider.startContinuousLocationUpdates(5000L) { lat, lon ->
            Log.d("SYGNALIK-LOCATION", "lat: $lat lon: $lon")

            scope.launch {
                sendPosition(lat, lon)
            }

            speedCameras.forEach { cam ->
                val distance = locationProvider.distanceInMeters(lat, lon, cam.lat,cam.lon )

                if (distance <= 200) {
                    bleManager.sendText("speedcamera")
                }
            }
        }

        return START_STICKY
    }

    fun getSpeedCameras() : List<SpeedCamera>  {
        val inputStream = resources.openRawResource(R.raw.speed_cameras_poland_20251203)
        val json = inputStream.bufferedReader().use { it.readText() }

        val gson = Gson()
        val listType = object : TypeToken<List<SpeedCamera>>() {}.type

        val speedCameras: List<SpeedCamera> = gson.fromJson(json, listType)

        return speedCameras;
    }

    suspend fun sendPosition(lat: Double, lon: Double) {
        Log.d("SYGNALIK-TRACCAR", "traccarHost: $traccarHost traccarDeviceId: $traccarDeviceId")

        val url = "${traccarHost}/?" +
                "id=${traccarDeviceId}" +
                "&lat=${lat}" +
                "&lon=${lon}"

        val request = Request.Builder()
            .url(url)
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()

        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    println("SYGNALIK-RESPONSE: ${response.code}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
}