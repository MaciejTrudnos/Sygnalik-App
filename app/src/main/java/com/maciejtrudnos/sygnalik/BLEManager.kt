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

    fun setStatus(message: String) {
        onMessage(message)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        // Filter by your service UUID to find only your ESP32
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
                // Optional: Add device name check
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
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        // Change autoConnect to false for faster, more reliable connection
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BLE", "Połączono, rozpoczynam discovery...")
                // Small delay before service discovery can help on some devices
                handler.postDelayed({
                    gatt.discoverServices()
                }, 600)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("BLE", "Rozłączono")
                setStatus("Rozłączono")
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
                    setStatus(message)
                }
            } else {
                Log.e("BLE", "Service discovery failed: $status")
                setStatus("Błąd discovery: $status")
            }
        }
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