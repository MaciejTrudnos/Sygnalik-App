package com.maciejtrudnos.sygnalik

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.*

class LocationProvider(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(callback: (Double?, Double?) -> Unit) {
        fusedClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                callback(location.latitude, location.longitude)
            } else {
                // Request fresh location
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { newLocation ->
                        if (newLocation != null) {
                            callback(newLocation.latitude, newLocation.longitude)
                        } else {
                            callback(null, null)
                        }
                    }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startContinuousLocationUpdates(
        intervalMs: Long = 5000L,
        callback: (Double, Double) -> Unit
    ): LocationCallback {

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalMs
        )
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(2000)
            .setMaxUpdateDelayMillis(10000)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                callback(location.latitude, location.longitude)
            }
        }

        fusedClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            context.mainLooper
        )

        return locationCallback
    }

    fun stopContinuousLocationUpdates(callback: LocationCallback) {
        fusedClient.removeLocationUpdates(callback)
    }
}