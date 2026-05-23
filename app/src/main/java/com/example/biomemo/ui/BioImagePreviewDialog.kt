package com.example.biomemo.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object BioImagePreviewDialog {
    fun show(activity: AppCompatActivity, photoRef: String, signedUrlResolver: suspend (String) -> String) {
        if (photoRef.isBlank()) return
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = FrameLayout(activity).apply {
            setBackgroundColor(Color.argb(220, 0, 0, 0))
            isClickable = true
        }
        val imageView = ZoomableImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
                setMargins(0, activity.dp(34), 0, activity.dp(34))
            }
            visibility = View.INVISIBLE
            setOnClickListener { }
        }
        val progress = ProgressBar(activity).apply {
            layoutParams = FrameLayout.LayoutParams(activity.dp(46), activity.dp(46), Gravity.CENTER)
            isIndeterminate = true
        }
        root.setOnClickListener { dialog.dismiss() }
        root.addView(imageView)
        root.addView(progress)

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        CoroutineScope(Dispatchers.Main).launchWhenDialogShowing(dialog) {
            val bitmap = BioImageLoader.loadBitmap(
                photoRef = photoRef,
                targetWidthPx = activity.resources.displayMetrics.widthPixels * 2,
                targetHeightPx = activity.resources.displayMetrics.heightPixels * 2,
                signedUrlResolver = signedUrlResolver
            )
            if (!dialog.isShowing) return@launchWhenDialogShowing
            progress.visibility = View.GONE
            if (bitmap != null) {
                imageView.setBitmap(bitmap)
                imageView.visibility = View.VISIBLE
            } else {
                dialog.dismiss()
            }
        }
    }

    private fun AppCompatActivity.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun CoroutineScope.launchWhenDialogShowing(
        dialog: Dialog,
        block: suspend () -> Unit
    ) {
        launch {
            if (dialog.isShowing) block()
        }
    }
}
