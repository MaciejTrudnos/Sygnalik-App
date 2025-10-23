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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ForegroundService : Service() {
    lateinit var bleManager: BLEManager

    private lateinit var smsReceiver: SmsReceiver
    private lateinit var callReceiver: CallReceiver
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private val binder = LocalBinder()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> get() = _ready

    private val _bleText = MutableStateFlow("")
    val bleText: StateFlow<String> get() = _bleText

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

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder
}