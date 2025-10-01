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

open class BLEManager(private val context: Context, private val bluetoothLeScanner: BluetoothLeScanner?, private val onMessage: (String) -> Unit)
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
        val filter = ScanFilter.Builder().build()
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
                Log.i("BLE", "Znaleziono urządzenie: ${device.address}")
                bluetoothLeScanner?.stopScan(this)
                connectToDevice(device)
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        bluetoothGatt = device.connectGatt(context, true, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BLE", "Połączono, rozpoczynam discovery...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("BLE", "Rozłączono")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                targetCharacteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                if (targetCharacteristic == null) {
                    val message = "Brak charakterystyki"
                    Log.e("BLE", message)
                    setStatus(message)
                } else {
                    val message = "Gotowa charakterystyka do wysyłania"
                    Log.i("BLE", message)
                    setStatus(message)
                }
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