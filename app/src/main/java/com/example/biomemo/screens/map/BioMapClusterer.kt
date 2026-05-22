package com.example.biomemo.screens.map

data class BioMapProjectedPin(
    val pin: BioMapPin,
    val x: Int,
    val y: Int
)

data class BioMapCluster(
    val pins: List<BioMapPin>,
    val latitude: Double,
    val longitude: Double
)

object BioMapClusterer {
    fun buildSinglePinClusters(pins: List<BioMapPin>): List<BioMapCluster> {
        return pins.map { pin -> BioMapCluster(listOf(pin), pin.latitude, pin.longitude) }
    }

    fun buildClusters(pins: List<BioMapProjectedPin>, radiusPx: Int): List<BioMapCluster> {
        val assigned = mutableSetOf<String>()
        val clusters = mutableListOf<BioMapCluster>()

        pins.forEach { projectedPin ->
            val pin = projectedPin.pin
            if (pin.id in assigned) return@forEach
            val group = pins
                .filter { candidate ->
                    candidate.pin.id !in assigned &&
                        distanceSquared(projectedPin, candidate) <= radiusPx * radiusPx
                }
                .map { it.pin }
            assigned += group.map { it.id }
            clusters += BioMapCluster(
                pins = group,
                latitude = group.map { it.latitude }.average(),
                longitude = group.map { it.longitude }.average()
            )
        }
        return clusters
    }

    private fun distanceSquared(first: BioMapProjectedPin, second: BioMapProjectedPin): Int {
        val dx = first.x - second.x
        val dy = first.y - second.y
        return dx * dx + dy * dy
    }
}
