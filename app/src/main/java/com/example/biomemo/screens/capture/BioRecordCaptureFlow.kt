package com.example.biomemo.screens.capture

import android.app.Dialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.biomemo.R
import com.example.biomemo.data.BioRecordPhotoUpload
import com.example.biomemo.data.BioRecordPhotoMetadata
import com.example.biomemo.data.BioRepository
import com.example.biomemo.screens.bio.BioRecordDetailActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class BioRecordCaptureFlow(
    private val activity: AppCompatActivity,
    private val repository: BioRepository = BioRepository()
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val locationProvider = BioRecordCurrentLocationProvider(activity)
    private var processingDialog: Dialog? = null
    private var pendingLocationAction: (() -> Unit)? = null

    private val uploadPhotoPicker = activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { processUpload(it) }
    }

    private val locationPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val action = pendingLocationAction
        pendingLocationAction = null
        action?.invoke()
    }

    private val cameraPreview = activity.registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { processCapturedBitmap(it) }
    }

    fun openUploadPicker() {
        withUploadMetadataPermissions { uploadPhotoPicker.launch("image/*") }
    }

    fun openCamera() {
        withLocationPermission { cameraPreview.launch(null) }
    }

    fun dispose() {
        scope.cancel()
        processingDialog?.dismiss()
        processingDialog = null
    }

    private fun processUpload(uri: Uri) {
        showProcessingDialog("Uploading photo", "Creating private BioRecord and identifying species...")
        scope.launch {
            val result = runCatching {
                val contentType = activity.contentResolver.getType(uri) ?: "image/jpeg"
                val metadataUri = BioRecordPhotoMetadataExtractor.originalUri(activity, uri)
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        activity.contentResolver.openInputStream(metadataUri)?.use { it.readBytes() }
                    }.getOrNull()
                        ?: activity.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Could not read selected photo.")
                }
                repository.createDraftUploadRecord(
                    BioRecordPhotoUpload(
                        bytes = bytes,
                        contentType = contentType,
                        metadata = withDeviceLocationFallback(
                            BioRecordPhotoMetadataExtractor.fromUploadUri(activity, metadataUri, bytes, contentType)
                        )
                    )
                )
            }
            hideProcessingDialog()
            result
                .onSuccess { entry -> openBioRecord(entry.id) }
                .onFailure { error -> showFailure(error.message ?: "Upload failed.") }
        }
    }

    private fun processCapturedBitmap(bitmap: Bitmap) {
        showProcessingDialog("Processing photo", "Saving camera capture and identifying species...")
        scope.launch {
            val result = runCatching {
                val contentType = "image/jpeg"
                val bytes = withContext(Dispatchers.IO) {
                    ByteArrayOutputStream().use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
                        output.toByteArray()
                    }
                }
                repository.createDraftUploadRecord(
                    BioRecordPhotoUpload(
                        bytes = bytes,
                        contentType = contentType,
                        metadata = withDeviceLocationFallback(
                            BioRecordPhotoMetadataExtractor.fromBitmap(bitmap, contentType)
                        )
                    )
                )
            }
            hideProcessingDialog()
            result
                .onSuccess { entry -> openBioRecord(entry.id) }
                .onFailure { error -> showFailure(error.message ?: "Capture failed.") }
        }
    }

    private fun showProcessingDialog(title: String, message: String) {
        processingDialog?.dismiss()
        processingDialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(false)
            setContentView(processingView(title, message))
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            show()
            window?.setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.86f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun processingView(title: String, message: String): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundResource(R.drawable.bg_card_elevated)
            setPadding(dp(24), dp(22), dp(24), dp(22))
            addView(text(title, 20, R.color.bio_ink, true))
            addView(text(message, 14, R.color.bio_ink_muted, false).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(16))
            })
            addView(ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(10)
                )
            })
        }
    }

    private fun text(value: String, sizeSp: Int, colorRes: Int, bold: Boolean): TextView {
        return TextView(activity).apply {
            text = value
            textSize = sizeSp.toFloat()
            setTextColor(activity.getColor(colorRes))
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
    }

    private fun hideProcessingDialog() {
        processingDialog?.dismiss()
        processingDialog = null
    }

    private fun openBioRecord(entryId: String) {
        activity.startActivity(
            Intent(activity, BioRecordDetailActivity::class.java)
                .putExtra(BioRecordDetailActivity.EXTRA_ENTRY_ID, entryId)
        )
    }

    private fun showFailure(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    private fun withLocationPermission(action: () -> Unit) {
        if (BioRecordCurrentLocationProvider.hasLocationPermission(activity)) {
            action()
        } else {
            pendingLocationAction = action
            locationPermissionLauncher.launch(BioRecordCurrentLocationProvider.LOCATION_PERMISSIONS)
        }
    }

    private fun withUploadMetadataPermissions(action: () -> Unit) {
        val missingPermissions = uploadMetadataPermissions()
            .filter { permission -> ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED }
            .toTypedArray()
        if (missingPermissions.isEmpty()) {
            action()
        } else {
            pendingLocationAction = action
            locationPermissionLauncher.launch(missingPermissions)
        }
    }

    private fun uploadMetadataPermissions(): Array<String> {
        return BioRecordCurrentLocationProvider.LOCATION_PERMISSIONS + Manifest.permission.ACCESS_MEDIA_LOCATION
    }

    private suspend fun withDeviceLocationFallback(metadata: BioRecordPhotoMetadata): BioRecordPhotoMetadata {
        return BioRecordLocationMetadataMerger.withFallbackLocation(
            metadata = metadata,
            location = locationProvider.currentLocation()
        )
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
