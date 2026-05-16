package com.example.biomemo.navigation

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R

object CaptureActionSheet {
    fun show(activity: AppCompatActivity, onTakePhoto: () -> Unit, onUploadPhoto: () -> Unit) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_capture_actions)
        val sheetNavigationColor = activity.getColor(R.color.bio_surface_warm)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.42f)
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            extendBehindNavigationBar(sheetNavigationColor)
        }

        val sheetRoot = dialog.findViewById<View>(R.id.captureActionSheetRoot)
        val sheet = dialog.findViewById<View>(R.id.captureActionSheet)
        sheetRoot.setOnClickListener { dismissWithAnimation(dialog, sheet) }
        sheet.setOnClickListener { }
        dialog.findViewById<View>(R.id.actionTakePhoto).setOnClickListener {
            dismissWithAnimation(dialog, sheet)
            onTakePhoto()
        }
        dialog.findViewById<View>(R.id.actionUploadPhoto).setOnClickListener {
            dismissWithAnimation(dialog, sheet)
            onUploadPhoto()
        }

        dialog.show()
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            attributes = attributes.apply { y = 0 }
            extendBehindNavigationBar(sheetNavigationColor)
        }
        sheet.translationY = activity.resources.displayMetrics.density * 80
        sheet.alpha = 0f
        sheet.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(220L)
            .start()
    }

    private fun dismissWithAnimation(dialog: Dialog, sheet: View) {
        sheet.animate()
            .translationY(sheet.resources.displayMetrics.density * 80)
            .alpha(0f)
            .setDuration(160L)
            .withEndAction { dialog.dismiss() }
            .start()
    }

    private fun Window.extendBehindNavigationBar(navigationColor: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
        addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        navigationBarColor = navigationColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isNavigationBarContrastEnforced = false
        }
    }
}
