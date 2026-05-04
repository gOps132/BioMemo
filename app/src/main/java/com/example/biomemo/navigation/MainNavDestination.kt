package com.example.biomemo.navigation

import androidx.annotation.IdRes
import com.example.biomemo.R

enum class MainNavDestination(
    val label: String,
    @param:IdRes val itemId: Int,
    @param:IdRes val iconId: Int
) {
    HOME("Home", R.id.navHome, R.id.navHomeIcon),
    RECORDS("Records", R.id.navRecords, R.id.navRecordsIcon),
    CAPTURE("Capture", R.id.navCapture, R.id.navCaptureIcon),
    SEARCH("Search", R.id.navSearch, R.id.navSearchIcon),
    PROFILE("Profile", R.id.navProfile, R.id.navProfileIcon)
}
