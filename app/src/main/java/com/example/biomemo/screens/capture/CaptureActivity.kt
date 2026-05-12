package com.example.biomemo.screens.capture

import android.content.Intent
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
            uploadPhotoPicker.launch("image/*")
        }

        statusText.text = "Upload a field photo to create a private BioRecord draft."
    }

    private fun uploadSelectedPhoto(uri: Uri) {
        uploadAction.isEnabled = false
        statusText.text = "Uploading private BioRecord photo..."

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
                        contentType = contentType
                    )
                )
            }

            uploadAction.isEnabled = true
            result
                .onSuccess { entry ->
                    statusText.text = "Draft saved. Ready for AI identification next."
                    Toast.makeText(this@CaptureActivity, "BioRecord draft saved.", Toast.LENGTH_SHORT).show()
                    startActivity(
                        Intent(this@CaptureActivity, BioRecordDetailActivity::class.java)
                            .putExtra(BioRecordDetailActivity.EXTRA_ENTRY_ID, entry.id)
                    )
                }
                .onFailure { error ->
                    statusText.text = "Upload failed. Check sign-in and try again."
                    Toast.makeText(this@CaptureActivity, error.message ?: "Upload failed.", Toast.LENGTH_LONG).show()
                }
        }
    }

    override fun onDestroy() {
        captureScope.cancel()
        super.onDestroy()
    }
}
