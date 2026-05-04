package com.example.biomemo.screens.capture

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.navigation.MainBottomNav
import com.example.biomemo.navigation.MainNavDestination
import com.example.biomemo.config.AppConfig

class CaptureActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capture)

        MainBottomNav.setup(this, MainNavDestination.CAPTURE, intent.getStringExtra(MainBottomNav.EXTRA_USERNAME))

        findViewById<TextView>(R.id.textviewTakePhotoAction).setOnClickListener {
            Toast.makeText(this, "Camera capture will create a BioRecord draft next.", Toast.LENGTH_SHORT).show()
        }
        findViewById<TextView>(R.id.textviewUploadPhotoAction).setOnClickListener {
            Toast.makeText(this, "Photo upload and metadata extraction are planned next.", Toast.LENGTH_SHORT).show()
        }
        findViewById<TextView>(R.id.textviewCaptureConfigStatus).text = if (AppConfig.hasAiIdentificationApiKey()) {
            "AI identification key detected. Ready for the photo analysis integration phase."
        } else {
            "Add AI_IDENTIFICATION_API_KEY to local.properties before enabling organism analysis."
        }
    }
}
