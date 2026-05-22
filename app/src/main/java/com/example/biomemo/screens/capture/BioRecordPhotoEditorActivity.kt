package com.example.biomemo.screens.capture

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.data.BioRecordPhotoMetadata
import com.example.biomemo.data.BioRecordPhotoUpload
import com.example.biomemo.data.BioRecordUseCases
import com.example.biomemo.screens.bio.BioRecordDetailActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BioRecordPhotoEditorActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val bioRecordUseCases = BioRecordUseCases()
    private val locationProvider = BioRecordCurrentLocationProvider(this)
    private lateinit var cropView: CropPhotoView
    private lateinit var uploadAction: TextView
    private lateinit var progressView: View
    private lateinit var imageBytes: ByteArray
    private lateinit var contentType: String
    private lateinit var sourceMetadata: BioRecordPhotoMetadata

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bio_record_photo_editor)

        cropView = findViewById(R.id.cropviewBioRecordPhoto)
        uploadAction = findViewById(R.id.textviewPhotoEditorUpload)
        progressView = findViewById(R.id.layoutPhotoEditorProgress)

        findViewById<View>(R.id.textviewPhotoEditorCancel).setOnClickListener { finish() }
        uploadAction.setOnClickListener { uploadCroppedPhoto() }

        val imageUri = intent.getStringExtra(EXTRA_IMAGE_URI)?.let(Uri::parse)
        if (imageUri == null) {
            showFailureAndFinish("Photo could not be opened.")
            return
        }
        loadPhoto(imageUri)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun loadPhoto(imageUri: Uri) {
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val metadataUri = BioRecordPhotoMetadataExtractor.originalUri(this@BioRecordPhotoEditorActivity, imageUri)
                    contentType = contentResolver.getType(metadataUri) ?: contentResolver.getType(imageUri) ?: "image/jpeg"
                    imageBytes = runCatching {
                        contentResolver.openInputStream(metadataUri)?.use { it.readBytes() }
                    }.getOrNull()
                        ?: contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                        ?: error("Could not read selected photo.")
                    sourceMetadata = BioRecordPhotoMetadataExtractor.fromUploadUri(
                        context = this@BioRecordPhotoEditorActivity,
                        uri = metadataUri,
                        bytes = imageBytes,
                        contentType = contentType
                    )
                    BioRecordPhotoCompressor.previewBitmapFromBytes(imageBytes)
                }
            }
            result
                .onSuccess { bitmap -> cropView.setBitmap(bitmap) }
                .onFailure { showFailureAndFinish(it.message ?: "Photo could not be opened.") }
        }
    }

    private fun uploadCroppedPhoto() {
        setBusy(true)
        scope.launch {
            val result = runCatching {
                val metadata = BioRecordLocationMetadataMerger.withFallbackLocation(
                    metadata = sourceMetadata,
                    location = locationProvider.currentLocation()
                )
                val compressedPhoto = withContext(Dispatchers.IO) {
                    BioRecordPhotoCompressor.fromBytes(
                        bytes = imageBytes,
                        contentType = contentType,
                        cropBounds = cropView.cropBounds(),
                        metadata = metadata
                    )
                }
                bioRecordUseCases.createDraftRecord(
                    BioRecordPhotoUpload(
                        bytes = compressedPhoto.bytes,
                        contentType = compressedPhoto.contentType,
                        metadata = compressedPhoto.metadata
                    )
                )
            }

            setBusy(false)
            result
                .onSuccess { entry ->
                    startActivity(
                        Intent(this@BioRecordPhotoEditorActivity, BioRecordDetailActivity::class.java)
                            .putExtra(BioRecordDetailActivity.EXTRA_ENTRY_ID, entry.id)
                    )
                    finish()
                }
                .onFailure { error ->
                    Toast.makeText(this@BioRecordPhotoEditorActivity, error.message ?: "Upload failed.", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun setBusy(isBusy: Boolean) {
        progressView.visibility = if (isBusy) View.VISIBLE else View.GONE
        uploadAction.isEnabled = !isBusy
    }

    private fun showFailureAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        const val EXTRA_IMAGE_URI = "image_uri"

        fun intentFor(activity: AppCompatActivity, uri: Uri): Intent {
            return Intent(activity, BioRecordPhotoEditorActivity::class.java)
                .putExtra(EXTRA_IMAGE_URI, uri.toString())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
