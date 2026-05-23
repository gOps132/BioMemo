package com.example.biomemo.screens.capture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class BioRecordCaptureFlow(
    private val activity: AppCompatActivity
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var pendingPermissionAction: (() -> Unit)? = null

    private val uploadPhotoPicker = activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { openEditor(it) }
    }

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val action = pendingPermissionAction
        pendingPermissionAction = null
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
    }

    private fun processCapturedBitmap(bitmap: Bitmap) {
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { writeCameraPreview(bitmap) }
            }
            result
                .onSuccess { uri -> openEditor(uri) }
                .onFailure { error -> showFailure(error.message ?: "Capture failed.") }
        }
    }

    private fun writeCameraPreview(bitmap: Bitmap): Uri {
        val file = File(activity.cacheDir, "biomemo-capture-${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
        }
        return Uri.fromFile(file)
    }

    private fun openEditor(uri: Uri) {
        activity.startActivity(BioRecordPhotoEditorActivity.intentFor(activity, uri))
    }

    private fun showFailure(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    private fun withLocationPermission(action: () -> Unit) {
        if (BioRecordCurrentLocationProvider.hasLocationPermission(activity)) {
            action()
        } else {
            pendingPermissionAction = action
            permissionLauncher.launch(BioRecordCurrentLocationProvider.LOCATION_PERMISSIONS)
        }
    }

    private fun withUploadMetadataPermissions(action: () -> Unit) {
        val missingPermissions = uploadMetadataPermissions()
            .filter { permission -> ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED }
            .toTypedArray()
        if (missingPermissions.isEmpty()) {
            action()
        } else {
            pendingPermissionAction = action
            permissionLauncher.launch(missingPermissions)
        }
    }

    private fun uploadMetadataPermissions(): Array<String> {
        return BioRecordCurrentLocationProvider.LOCATION_PERMISSIONS + Manifest.permission.ACCESS_MEDIA_LOCATION
    }
}
