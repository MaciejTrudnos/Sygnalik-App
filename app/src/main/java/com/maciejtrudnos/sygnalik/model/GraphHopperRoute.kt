package com.maciejtrudnos.sygnalik.model

data class GraphHopperResponse(
    val paths: List<GraphHopperPath> = emptyList()
)

data class GraphHopperPath(
    val distance: Double = 0.0,
    val time: Long = 0,
    val points: GraphHopperGeoJson? = null,
    val instructions: List<GraphHopperInstruction> = emptyList()
)

data class GraphHopperGeoJson(
    val type: String? = null,
    val coordinates: List<List<Double>> = emptyList()
)

data class GraphHopperInstruction(
    val distance: Double = 0.0,
    val sign: Int = 0,
    val interval: List<Int> = emptyList(),
    val text: String = "",
    val time: Long = 0
)