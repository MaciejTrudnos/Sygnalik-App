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
import com.maciejtrudnos.sygnalik.model.Warning
import com.maciejtrudnos.sygnalik.model.GraphHopperPath
import com.maciejtrudnos.sygnalik.model.GraphHopperResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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

    private val navigationManager = NavigationManager()
    private var lastNavDeviceMessage: String? = null

    private val _navText = MutableStateFlow("")
    val navText: StateFlow<String> get() = _navText

    private val scope = CoroutineScope(Dispatchers.IO)

    val traccarHost = BuildConfig.TRACCAR_HOST
    val traccarDeviceId = BuildConfig.TRACCAR_DEVICE_ID

    val warningGatewayHost = BuildConfig.WARNING_GATEWAY_HOST
    val warningGatewayApiKey = BuildConfig.WARNING_GATEWAY_API_KEY

    val graphHopperHost = BuildConfig.GRAPHHOPPER_HOST

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

                val warning = getWarning(lat, lon)
                if (warning != null) {
                    Log.d("SYGNALIK-WARNING", warning.type)
                    _bleText.value = warning.type
                    bleManager.sendText(warning.type)
                }
            }

            speedCameras.forEach { cam ->
                val distance = locationProvider.distanceInMeters(lat, lon, cam.lat,cam.lon )

                if (distance <= 200) {
                    bleManager.sendText("speedcamera")
                }
            }

            if (navigationManager.isActive) {
                val step = navigationManager.onLocationUpdate(lat, lon)
                if (step != null) {
                    val appText = formatNavigationStep(step)
                    _navText.value = appText

                    val deviceMessage = "nav:" + toAsciiText(appText)
                    if (deviceMessage != lastNavDeviceMessage) {
                        lastNavDeviceMessage = deviceMessage
                        bleManager.sendText(deviceMessage)
                    }
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

    suspend fun getWarning(lat: Double, lon: Double): Warning? {
        val url = "${warningGatewayHost}/warning?" +
                "latitude=${lat}" +
                "&longitude=${lon}"

        println("SYGNALIK-URL: ${url}")

        val request = Request.Builder()
            .url(url)
            .addHeader("X-API-Key", warningGatewayApiKey)
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    Log.d("SYGNALIK-WARNING", "Response code: ${response.code}")

                    if (response.isSuccessful) {
                        val body = response.body?.string()

                        Log.d("SYGNALIK-WARNING", "Body: $body")

                        body?.let {
                            Gson().fromJson(it, Warning::class.java)
                        }
                    } else {
                        Log.d("SYGNALIK-WARNING", "Not found: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun startNavigation(destLat: Double, destLon: Double) {
        navigationManager.clear()
        _navText.value = ""
        lastNavDeviceMessage = null

        scope.launch {
            val origin = getCurrentLocationSuspend()
            if (origin == null) {
                _navText.value = getString(R.string.nav_no_location)
                return@launch
            }

            val path = fetchRoute(origin.first, origin.second, destLat, destLon)
            if (path == null) {
                _navText.value = getString(R.string.nav_route_error)
                return@launch
            }

            navigationManager.setRoute(path)
            if (!navigationManager.isActive) {
                _navText.value = getString(R.string.nav_route_error)
            } else {
                val firstStep = navigationManager.onLocationUpdate(origin.first, origin.second)
                if (firstStep != null) {
                    val appText = formatNavigationStep(firstStep)
                    _navText.value = appText

                    val deviceMessage = "nav:" + toAsciiText(appText)
                    if (deviceMessage != lastNavDeviceMessage) {
                        lastNavDeviceMessage = deviceMessage
                        bleManager.sendText(deviceMessage)
                    }
                } else {
                    _navText.value = getString(R.string.nav_route_planned, formatDistance(path.distance))
                }
            }
        }
    }

    fun stopNavigation() {
        navigationManager.clear()
        _navText.value = ""
        lastNavDeviceMessage = null
    }

    fun shutdown() {
        stopNavigation()

        if (::locationProvider.isInitialized && ::locationCallback.isInitialized) {
            locationProvider.stopContinuousLocationUpdates(locationCallback)
        }

        if (::bleManager.isInitialized) {
            bleManager.disconnect()
        }

        SmsReceiver.bleManager = null
        CallReceiver.bleManager = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun getCurrentLocationSuspend(): Pair<Double, Double>? =
        suspendCoroutine { cont ->
            locationProvider.getCurrentLocation { lat, lon ->
                if (lat != null && lon != null) {
                    cont.resume(lat to lon)
                } else {
                    cont.resume(null)
                }
            }
        }

    private suspend fun fetchRoute(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double
    ): GraphHopperPath? {
        val payload = mapOf(
            "points" to listOf(listOf(originLon, originLat), listOf(destLon, destLat)),
            "profile" to "car",
            "locale" to "pl",
            "points_encoded" to false,
            "instructions" to true
        )
        val jsonBody = Gson().toJson(payload)

        val request = Request.Builder()
            .url("${graphHopperHost.trimEnd('/')}/route")
            .post(okhttp3.RequestBody.create(
                "application/json; charset=utf-8".toMediaType(),
                jsonBody
            ))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    Log.d("SYGNALIK-NAVIGATION", "Response code: ${response.code}")

                    if (!response.isSuccessful) {
                        null
                    } else {
                        val body = response.body?.string()
                        body?.let {
                            Gson().fromJson(it, GraphHopperResponse::class.java)?.paths?.firstOrNull()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun formatNavigationStep(step: NavigationStep): String =
        if (step.arrived) {
            getString(R.string.nav_arrived)
        } else {
            val arrow = maneuverArrow(step.sign)
            val instructionLine = if (arrow == null) {
                step.instructionText
            } else {
                "$arrow\n${step.instructionText}"
            }
            getString(
                R.string.nav_instruction_summary,
                instructionLine,
                formatDistance(step.distanceToManeuverMeters),
                formatDistance(step.remainingDistanceMeters)
            )
        }

    override fun onBind(intent: Intent?): IBinder = binder
}