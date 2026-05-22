package com.example.biomemo

import com.example.biomemo.screens.map.BioMapClusterer
import com.example.biomemo.screens.map.BioMapPin
import com.example.biomemo.screens.map.BioMapProjectedPin
import org.junit.Assert.assertEquals
import org.junit.Test

class BioMapClustererTest {
    @Test
    fun groupsProjectedPinsInsideRadius() {
        val clusters = BioMapClusterer.buildClusters(
            pins = listOf(
                projectedPin("a", x = 0, y = 0, latitude = 10.0, longitude = 20.0),
                projectedPin("b", x = 3, y = 4, latitude = 12.0, longitude = 22.0),
                projectedPin("c", x = 100, y = 100, latitude = 50.0, longitude = 60.0)
            ),
            radiusPx = 5
        )

        assertEquals(listOf(listOf("a", "b"), listOf("c")), clusters.map { cluster -> cluster.pins.map { it.id } })
        assertEquals(11.0, clusters.first().latitude, 0.001)
        assertEquals(21.0, clusters.first().longitude, 0.001)
    }

    @Test
    fun returnsOnePinClustersWhenClusteringDisabled() {
        val clusters = BioMapClusterer.buildSinglePinClusters(listOf(pin("a"), pin("b")))

        assertEquals(listOf(listOf("a"), listOf("b")), clusters.map { cluster -> cluster.pins.map { it.id } })
    }

    private fun projectedPin(
        id: String,
        x: Int,
        y: Int,
        latitude: Double,
        longitude: Double
    ) = BioMapProjectedPin(
        pin = pin(id, latitude = latitude, longitude = longitude),
        x = x,
        y = y
    )

    private fun pin(
        id: String,
        latitude: Double = 10.0,
        longitude: Double = 20.0
    ) = BioMapPin(
        id = id,
        commonName = id,
        scientificName = id,
        category = "BioRecord",
        photoUrl = "",
        latitude = latitude,
        longitude = longitude,
        primaryMetadata = "",
        locationMetadata = "",
        tagsLabel = ""
    )
}
