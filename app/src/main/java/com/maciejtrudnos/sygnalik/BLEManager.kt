package com.maciejtrudnos.sygnalik

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import java.util.UUID

class BLEManager(private val context: Context, private val bluetoothLeScanner: BluetoothLeScanner?, private val onMessage: (String) -> Unit)
{
    private var bluetoothGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())

    private val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val CHARACTERISTIC_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    private var targetCharacteristic: BluetoothGattCharacteristic? = null

    private var lastConnectedDevice: BluetoothDevice? = null
    private var reconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 30
    private var reconnectRunnable: Runnable? = null
    private var isReconnecting = false

    fun setStatus(message: String) {
        onMessage(message)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        val filter = ScanFilter.Builder()
            .setServiceUuid(android.os.ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothLeScanner?.startScan(listOf(filter), settings, scanCallback)

        handler.postDelayed({
            bluetoothLeScanner?.stopScan(scanCallback)
        }, 30000)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.device?.let { device ->
                val deviceName = device.name
                Log.i("BLE", "Znaleziono urządzenie: ${device.address}, nazwa: $deviceName")

                bluetoothLeScanner?.stopScan(this)
                setStatus("Łączenie z urządzeniem...")
                connectToDevice(device)
            }
        }

        override fun onScanFailed(error: Int) {
            super.onScanFailed(error)
            Log.e("BLE", "Błąd skanowania: $error")
            setStatus("Błąd skanowania: $error")
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        lastConnectedDevice = device
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun cancelReconnect() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectRunnable = null
        reconnectAttempts = 0
        isReconnecting = false
        Log.i("BLE", "Anulowano reconnect")
        setStatus("Anulowano reconnect")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        cancelReconnect()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        targetCharacteristic = null
        lastConnectedDevice = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BLE", "Połączono (stan GATT: CONNECTED), rozpoczynam discovery...")
                setStatus("Połączono - discovery...")
                handler.postDelayed({ gatt.discoverServices() }, 600)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("BLE", "Rozłączono")
                targetCharacteristic = null
                setStatus("Rozłączono")
                reconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BLE", "Znaleziono ${gatt.services.size} serwisów")

                val service = gatt.getService(SERVICE_UUID)
                if (service == null) {
                    Log.e("BLE", "Nie znaleziono serwisu o UUID: $SERVICE_UUID")
                    setStatus("Brak serwisu")
                    return
                }

                targetCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                if (targetCharacteristic == null) {
                    val message = "Brak charakterystyki"
                    Log.e("BLE", message)
                    setStatus(message)
                } else {
                    val message = "Połączono"
                    Log.i("BLE", message)

                    isReconnecting = false
                    reconnectAttempts = 0
                    reconnectRunnable?.let { handler.removeCallbacks(it) }
                    reconnectRunnable = null

                    setStatus(message)
                }
            } else {
                Log.e("BLE", "Service discovery failed: $status")
                setStatus("Błąd discovery: $status")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun reconnect() {
        val device = lastConnectedDevice
        if (device == null) {
            Log.e("BLE", "Brak zapamiętanego urządzenia do reconnect")
            setStatus("Brak urządzenia do reconnect")
            return
        }

        if (!isReconnecting) {
            isReconnecting = true
            reconnectAttempts = 0
        }

        attemptReconnect(device)
    }

    @SuppressLint("MissingPermission")
    private fun attemptReconnect(device: BluetoothDevice) {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e("BLE", "Przekroczono maksymalną liczbę prób reconnect dla ${device.address}")
            setStatus("Reconnect nie powiódł się po $MAX_RECONNECT_ATTEMPTS próbach")
            isReconnecting = false
            reconnectRunnable?.let { handler.removeCallbacks(it) }
            reconnectRunnable = null
            return
        }

        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectRunnable = null

        reconnectAttempts++
        Log.i("BLE", "Próba reconnect #$reconnectAttempts/$MAX_RECONNECT_ATTEMPTS dla ${device.address}")
        setStatus("Reconnect ($reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)…")

        val delayMs = (1000 * Math.pow(2.0, (reconnectAttempts - 1).toDouble())).toLong().coerceAtMost(30000L)

        try {
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.w("BLE", "Błąd przy zamykaniu bluetoothGatt: ${e.message}")
        }
        bluetoothGatt = null
        targetCharacteristic = null

        reconnectRunnable = Runnable {
            connectToDevice(device)
        }
        handler.postDelayed(reconnectRunnable!!, delayMs)
    }

    @SuppressLint("MissingPermission")
    fun sendText(text: String) {
        if (targetCharacteristic == null) {
            Log.e("BLE", "Brak charakterystyki")
            return
        }

        val characteristic = targetCharacteristic

        characteristic?.value = text.toByteArray()
        val success = bluetoothGatt?.writeCharacteristic(characteristic) ?: false
        Log.i("BLE", "Wysyłanie: $text, sukces=$success")
    }
}