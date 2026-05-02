package com.example.biomemo.screens.capture

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.config.AppConfig

class CaptureActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capture)

        findViewById<TextView>(R.id.textviewCaptureBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.textviewCaptureConfigStatus).text = if (AppConfig.hasAiIdentificationApiKey()) {
            "AI identification key detected. Camera + analysis comes next."
        } else {
            "Add AI_IDENTIFICATION_API_KEY to local.properties when the AI capture phase starts."
        }
    }
}
