package com.example.biomemo.navigation

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
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
        applySystemBarInsets(activity)
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

    private fun applySystemBarInsets(activity: AppCompatActivity) {
        applyBottomNavInset(activity)
        applyBottomPaddingInset(activity, R.id.layoutDashboardRecentContent)
        applyBottomPaddingInset(activity, R.id.layoutBioCollectionContent)
        applyBottomPaddingInset(activity, R.id.layoutSearchContent)
        applyScrollContentInset(activity)
    }

    private fun applyBottomNavInset(activity: AppCompatActivity) {
        val bottomNavContainer = activity.findViewById<View>(R.id.bottomNavContainer) ?: return
        val originalHeight = bottomNavContainer.layoutParams.height
        val originalPaddingBottom = bottomNavContainer.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(bottomNavContainer) { view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.updatePadding(bottom = originalPaddingBottom + bottomInset)
            if (originalHeight > 0) {
                view.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = originalHeight + bottomInset
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(bottomNavContainer)
    }

    private fun applyBottomPaddingInset(activity: AppCompatActivity, viewId: Int) {
        val view = activity.findViewById<View>(viewId) ?: return
        val originalPaddingBottom = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            target.updatePadding(bottom = originalPaddingBottom + bottomInset)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    private fun applyScrollContentInset(activity: AppCompatActivity) {
        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val scrollViews = mutableListOf<ScrollView>()
        collectScrollViews(contentRoot, scrollViews)

        scrollViews.forEach { scrollView ->
            val originalPaddingBottom = scrollView.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
                val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                view.updatePadding(bottom = originalPaddingBottom + bottomInset)
                insets
            }
            ViewCompat.requestApplyInsets(scrollView)
        }
    }

    private fun collectScrollViews(view: View, scrollViews: MutableList<ScrollView>) {
        if (view is ScrollView) {
            scrollViews += view
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectScrollViews(view.getChildAt(index), scrollViews)
            }
        }
    }

    private fun applyBottomMarginInset(activity: AppCompatActivity, viewId: Int) {
        val bottomAnchoredView = activity.findViewById<View>(viewId) ?: return
        val originalBottomMargin = (bottomAnchoredView.layoutParams as? ViewGroup.MarginLayoutParams)
            ?.bottomMargin
            ?: return

        ViewCompat.setOnApplyWindowInsetsListener(bottomAnchoredView) { view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = originalBottomMargin + bottomInset
            }
            insets
        }
        ViewCompat.requestApplyInsets(bottomAnchoredView)
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
