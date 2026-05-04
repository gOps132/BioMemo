package com.example.biomemo.navigation

import androidx.annotation.IdRes
import com.example.biomemo.R

enum class MainNavDestination(
    val label: String,
    @param:IdRes val itemId: Int,
    @param:IdRes val iconId: Int,
    @param:IdRes val labelId: Int
) {
    HOME("Home", R.id.navHome, R.id.navHomeIcon, R.id.navHomeLabel),
    RECORDS("Records", R.id.navRecords, R.id.navRecordsIcon, R.id.navRecordsLabel),
    CAPTURE("Capture", R.id.navCapture, R.id.navCaptureIcon, R.id.navCaptureLabel),
    SEARCH("Search", R.id.navSearch, R.id.navSearchIcon, R.id.navSearchLabel),
    PROFILE("Profile", R.id.navProfile, R.id.navProfileIcon, R.id.navProfileLabel)
}
