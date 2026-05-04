package com.example.biomemo

import com.example.biomemo.navigation.MainNavDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavDestinationTest {
    @Test
    fun bottomNavDestinationsKeepStableOrderLabelsAndViewIds() {
        val destinations = MainNavDestination.entries

        assertEquals(
            listOf("Home", "Records", "Capture", "Search", "Profile"),
            destinations.map { it.label }
        )
        assertEquals(
            listOf(
                R.id.navHome,
                R.id.navRecords,
                R.id.navCapture,
                R.id.navSearch,
                R.id.navProfile
            ),
            destinations.map { it.itemId }
        )
    }

    @Test
    fun bottomNavDestinationsExposeOnlyStableIconIdsForIconOnlyNavigation() {
        val destinations = MainNavDestination.entries

        assertEquals(
            listOf(
                R.id.navHomeIcon,
                R.id.navRecordsIcon,
                R.id.navCaptureIcon,
                R.id.navSearchIcon,
                R.id.navProfileIcon
            ),
            destinations.map { it.iconId }
        )
    }
}
