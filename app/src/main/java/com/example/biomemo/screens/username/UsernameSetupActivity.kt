package com.example.biomemo.screens.username

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.screens.dashboard.DashboardActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class UsernameSetupActivity : AppCompatActivity(), UsernameSetupContract.View {
    private lateinit var presenter: UsernameSetupPresenter
    private lateinit var usernameInput: EditText
    private lateinit var errorText: TextView
    private lateinit var saveButton: Button
    private val authScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_username_setup)

        presenter = UsernameSetupPresenter(this, UsernameSetupProfileModel())
        usernameInput = findViewById(R.id.edittextUsernameSetup)
        errorText = findViewById(R.id.textviewUsernameSetupError)
        saveButton = findViewById(R.id.buttonUsernameSetupSave)

        saveButton.setOnClickListener {
            clearError()
            authScope.launch {
                presenter.onSaveClicked(usernameInput.text.toString())
            }
        }
    }

    override fun showSaving(isSaving: Boolean) {
        saveButton.isEnabled = !isSaving
        saveButton.text = if (isSaving) "Saving" else "Continue"
    }

    override fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    override fun navigateToDashboard(username: String) {
        startActivity(
            Intent(this, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("username", username)
            }
        )
        finish()
    }

    override fun onDestroy() {
        authScope.cancel()
        super.onDestroy()
    }

    private fun clearError() {
        errorText.text = ""
        errorText.visibility = View.GONE
    }
}
