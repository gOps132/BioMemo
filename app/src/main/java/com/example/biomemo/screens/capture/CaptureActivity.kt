package com.example.biomemo.screens.capture

import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.navigation.MainBottomNav
import com.example.biomemo.navigation.MainNavDestination

class CaptureActivity : AppCompatActivity() {
    private lateinit var uploadAction: TextView
    private lateinit var statusText: TextView

    private val uploadPhotoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { openPhotoEditor(it) }
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

        statusText.text = "Choose a field photo, crop it, then upload."
        if (intent.getBooleanExtra(EXTRA_OPEN_UPLOAD_PICKER, false)) {
            window.decorView.post { openUploadPicker() }
        }
    }

    fun openUploadPicker() {
        uploadPhotoPicker.launch("image/*")
    }

    private fun openPhotoEditor(uri: Uri) {
        startActivity(BioRecordPhotoEditorActivity.intentFor(this, uri))
    }

    companion object {
        const val EXTRA_OPEN_UPLOAD_PICKER = "open_upload_picker"
    }
}
