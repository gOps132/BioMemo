package com.example.biomemo.navigation

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.biomemo.R
import com.example.biomemo.screens.bio.BioCollectionActivity
import com.example.biomemo.screens.capture.BioRecordCaptureFlow
import com.example.biomemo.screens.dashboard.DashboardActivity
import com.example.biomemo.screens.profile.ProfileActivity
import com.example.biomemo.screens.search.SearchActivity

object MainBottomNav {
    const val EXTRA_USERNAME = "username"

    fun setup(activity: AppCompatActivity, activeDestination: MainNavDestination, username: String?) {
        val captureFlow = BioRecordCaptureFlow(activity)
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                captureFlow.dispose()
            }
        })
        MainNavDestination.entries.forEach { destination ->
            val item = activity.findViewById<View>(destination.itemId)
            val icon = activity.findViewById<ImageView>(destination.iconId)
            val isActive = destination == activeDestination
            val color = ContextCompat.getColor(
                activity,
                if (isActive || destination == MainNavDestination.CAPTURE) R.color.bio_forest_900 else R.color.bio_ink_muted
            )

            item.isSelected = isActive
            item.contentDescription = destination.label
            icon.imageTintList = ColorStateList.valueOf(color)
            if (destination != MainNavDestination.CAPTURE) {
                item.setBackgroundResource(if (isActive) R.drawable.bg_bottom_nav_item_active else 0)
            }
            item.setOnClickListener {
                if (destination == MainNavDestination.CAPTURE) {
                    CaptureActionSheet.show(
                        activity = activity,
                        onTakePhoto = { captureFlow.openCamera() },
                        onUploadPhoto = { captureFlow.openUploadPicker() }
                    )
                } else if (!isActive) {
                    activity.openDestination(destination, username)
                }
            }
        }
    }

    private fun Activity.openDestination(destination: MainNavDestination, username: String?) {
        val targetClass = when (destination) {
            MainNavDestination.HOME -> DashboardActivity::class.java
            MainNavDestination.RECORDS -> BioCollectionActivity::class.java
            MainNavDestination.CAPTURE -> DashboardActivity::class.java
            MainNavDestination.SEARCH -> SearchActivity::class.java
            MainNavDestination.PROFILE -> ProfileActivity::class.java
        }
        val intent = Intent(this, targetClass).apply {
            username?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_USERNAME, it) }
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
    }
}
