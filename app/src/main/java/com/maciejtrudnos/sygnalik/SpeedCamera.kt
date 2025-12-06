package com.maciejtrudnos.sygnalik

data class SpeedCamera(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val maxspeed: String,
    val direction: String,
    val ref: String,
    val name: String,
    val enforcement: String,
    val road: String
)
