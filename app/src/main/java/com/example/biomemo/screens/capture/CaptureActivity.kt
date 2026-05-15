package com.example.biomemo.screens.capture

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.data.BioRecordPhotoUpload
import com.example.biomemo.data.BioRepository
import com.example.biomemo.navigation.MainBottomNav
import com.example.biomemo.navigation.MainNavDestination
import com.example.biomemo.screens.bio.BioRecordDetailActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class CaptureActivity : AppCompatActivity() {
    private val bioRepository = BioRepository()
    private val captureScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var uploadAction: TextView
    private lateinit var statusText: TextView

    private val uploadPhotoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadSelectedPhoto(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capture)

        MainBottomNav.setup(this, MainNavDestination.CAPTURE, intent.getStringExtra(MainBottomNav.EXTRA_USERNAME))

        findViewById<TextView>(R.id.textviewTakePhotoAction).setOnClickListener {
            Toast.makeText(this, "Camera capture will create a BioRecord draft next.", Toast.LENGTH_SHORT).show()
        }
        uploadAction = findViewById(R.id.textviewUploadPhotoAction)
        statusText = findViewById(R.id.textviewCaptureConfigStatus)
        uploadAction.setOnClickListener {
            openUploadPicker()
        }

        statusText.text = "Upload a field photo to create a private BioRecord draft."
        if (intent.getBooleanExtra(EXTRA_OPEN_UPLOAD_PICKER, false)) {
            window.decorView.post { openUploadPicker() }
        }
    }

    fun openUploadPicker() {
        uploadPhotoPicker.launch("image/*")
    }

    private fun uploadSelectedPhoto(uri: Uri) {
        uploadAction.isEnabled = false
        statusText.text = "Uploading and identifying private BioRecord photo..."

        captureScope.launch {
            val result = runCatching {
                val contentType = contentResolver.getType(uri) ?: "image/jpeg"
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Could not read selected photo.")
                }
                bioRepository.createDraftUploadRecord(
                    BioRecordPhotoUpload(
                        bytes = bytes,
                        contentType = contentType,
                        metadata = extractPhotoMetadata(bytes, contentType)
                    )
                )
            }

            uploadAction.isEnabled = true
            result
                .onSuccess { entry ->
                    val identified = entry.scientificName != "Awaiting identification"
                    statusText.text = if (identified) {
                        "BioRecord identified. Review candidate match."
                    } else {
                        "Draft saved. Identification still pending."
                    }
                    Toast.makeText(this@CaptureActivity, statusText.text, Toast.LENGTH_SHORT).show()
                    startActivity(
                        Intent(this@CaptureActivity, BioRecordDetailActivity::class.java)
                            .putExtra(BioRecordDetailActivity.EXTRA_ENTRY_ID, entry.id)
                    )
                }
                .onFailure { error ->
                    val message = error.message ?: "Upload failed."
                    statusText.text = message
                    Toast.makeText(this@CaptureActivity, message, Toast.LENGTH_LONG).show()
                }
        }
    }

    override fun onDestroy() {
        captureScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN_UPLOAD_PICKER = "open_upload_picker"
        private val EXIF_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)
    }

    private fun extractPhotoMetadata(bytes: ByteArray, contentType: String): com.example.biomemo.data.BioRecordPhotoMetadata {
        val dimensions = BitmapFactory.Options().run {
            inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
            outWidth.takeIf { it > 0 } to outHeight.takeIf { it > 0 }
        }

        val exif = runCatching { ExifInterface(ByteArrayInputStream(bytes)) }.getOrNull()
        val gps = FloatArray(2)
        val hasGps = exif?.getLatLong(gps) == true
        val capturedAt = exif?.capturedAtIso()
        val orientation = exif
            ?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            ?.takeIf { it != ExifInterface.ORIENTATION_UNDEFINED }

        val latitude = if (hasGps) gps[0].toDouble() else null
        val longitude = if (hasGps) gps[1].toDouble() else null
        val raw = buildMap {
            put("file_type", contentType)
            capturedAt?.let { put("captured_at", it) }
            latitude?.let { put("latitude", it.toString()) }
            longitude?.let { put("longitude", it.toString()) }
            orientation?.let { put("orientation", it.toString()) }
            dimensions.first?.let { put("width", it.toString()) }
            dimensions.second?.let { put("height", it.toString()) }
        }

        return com.example.biomemo.data.BioRecordPhotoMetadata(
            capturedAt = capturedAt,
            latitude = latitude,
            longitude = longitude,
            orientation = orientation,
            width = dimensions.first,
            height = dimensions.second,
            metadataAvailability = metadataAvailability(capturedAt, latitude, longitude),
            raw = raw
        )
    }

    private fun ExifInterface.capturedAtIso(): String? {
        val rawDate = getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
            ?: getAttribute(ExifInterface.TAG_DATETIME)
        return rawDate
            ?.let { runCatching { LocalDateTime.parse(it, EXIF_DATE_FORMATTER) }.getOrNull() }
            ?.atOffset(ZoneOffset.UTC)
            ?.toString()
    }

    private fun metadataAvailability(capturedAt: String?, latitude: Double?, longitude: Double?): String {
        val hasCapturedAt = !capturedAt.isNullOrBlank()
        val hasGps = latitude != null && longitude != null
        return when {
            hasCapturedAt && hasGps -> "capture time and GPS available"
            hasCapturedAt -> "capture time available"
            hasGps -> "GPS available"
            else -> "unknown"
        }
    }
}
