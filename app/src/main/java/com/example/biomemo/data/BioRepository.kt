package com.example.biomemo.data

class BioRepository {
    fun getAllEntries(): List<BioEntry> = entries

    fun getRecentEntries(limit: Int = 2): List<BioEntry> = entries.take(limit)

    fun getStats(): BioStats = BioStats(
        sightings = entries.size,
        species = entries.map { it.scientificName }.distinct().size,
        streak = "5d"
    )

    fun search(query: String): List<BioEntry> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) return entries

        return entries.filter { entry ->
            listOf(
                entry.commonName,
                entry.scientificName,
                entry.category,
                entry.location,
                entry.notes,
                entry.tags.joinToString(" ")
            ).any { value -> value.lowercase().contains(normalizedQuery) }
        }
    }

    companion object {
        private val entries = listOf(
            BioEntry(
                id = "red-fox",
                commonName = "Red Fox",
                scientificName = "Vulpes vulpes",
                category = "Mammal",
                date = "Mar 5, 2026",
                location = "Cascade Trail, Oregon",
                latitude = 44.0582,
                longitude = -121.3153,
                confidence = 98,
                notes = "Spotted foraging near the forest edge at dusk with a white-tipped tail.",
                tags = listOf("Carnivore", "Nocturnal", "Common")
            ),
            BioEntry(
                id = "monarch-butterfly",
                commonName = "Monarch Butterfly",
                scientificName = "Danaus plexippus",
                category = "Insect",
                date = "Mar 3, 2026",
                location = "Sunflower Meadow, California",
                latitude = 38.5781,
                longitude = -121.4944,
                confidence = 95,
                notes = "Resting on milkweed mid-migration with bright orange and black wing pattern.",
                tags = listOf("Migratory", "Pollinator", "Threatened")
            ),
            BioEntry(
                id = "red-squirrel",
                commonName = "American Red Squirrel",
                scientificName = "Tamiasciurus hudsonicus",
                category = "Mammal",
                date = "Feb 28, 2026",
                location = "Pine Ridge, Washington",
                latitude = 47.7511,
                longitude = -120.7401,
                confidence = 97,
                notes = "Caching pine cones near a Douglas fir stand and chattering from above.",
                tags = listOf("Diurnal", "Arboreal", "Common")
            ),
            BioEntry(
                id = "tree-frog",
                commonName = "Pacific Tree Frog",
                scientificName = "Pseudacris regilla",
                category = "Amphibian",
                date = "Feb 20, 2026",
                location = "Elk Creek, Oregon",
                latitude = 43.2165,
                longitude = -123.3417,
                confidence = 92,
                notes = "Clinging to a sword fern near water after rain, calling loudly at dusk.",
                tags = listOf("Nocturnal", "Indicator Species", "Common")
            ),
            BioEntry(
                id = "barn-owl",
                commonName = "Barn Owl",
                scientificName = "Tyto alba",
                category = "Bird",
                date = "Feb 15, 2026",
                location = "Old Mill Road, Idaho",
                latitude = 43.615,
                longitude = -116.2023,
                confidence = 99,
                notes = "Perched on a fence post at twilight with a heart-shaped facial disk.",
                tags = listOf("Nocturnal", "Raptor", "Near Threatened")
            ),
            BioEntry(
                id = "white-tailed-deer",
                commonName = "White-tailed Deer",
                scientificName = "Odocoileus virginianus",
                category = "Mammal",
                date = "Feb 10, 2026",
                location = "Maple Grove, Montana",
                latitude = 46.8797,
                longitude = -110.3626,
                confidence = 96,
                notes = "Doe with two yearlings grazing at sunrise along the meadow edge.",
                tags = listOf("Herbivore", "Crepuscular", "Abundant")
            ),
            BioEntry(
                id = "black-bear",
                commonName = "American Black Bear",
                scientificName = "Ursus americanus",
                category = "Mammal",
                date = "Feb 2, 2026",
                location = "Ridgeline Loop, Washington",
                latitude = 48.7519,
                longitude = -121.812,
                confidence = 99,
                notes = "Observed at a safe distance near a berry thicket in early morning.",
                tags = listOf("Omnivore", "Solitary", "Least Concern")
            )
        )
    }
}
