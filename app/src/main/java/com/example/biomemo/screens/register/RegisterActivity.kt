package com.example.biomemo.screens.register

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.screens.login.LoginActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity(), RegisterContract.View {

    private lateinit var presenter: RegisterPresenter
    private lateinit var edittextEmail: EditText
    private lateinit var edittextFieldName: EditText
    private lateinit var edittextPassword: EditText
    private lateinit var edittextReenterPassword: EditText
    private lateinit var textviewRegisterError: TextView
    private val authScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Init MVP
        presenter = RegisterPresenter(this, RegisterModel())

        // UI References
        edittextEmail = findViewById(R.id.edittextEmail)
        edittextFieldName = findViewById(R.id.edittextUsername)
        edittextPassword = findViewById(R.id.edittextPassword)
        edittextReenterPassword = findViewById(R.id.edittextReenterPassword)
        textviewRegisterError = findViewById(R.id.textviewRegisterError)
        val buttonSubmit = findViewById<Button>(R.id.buttonSubmit)
        val textviewRegisterGoogleSignIn = findViewById<TextView>(R.id.textviewRegisterGoogleSignIn)
        val textviewBackToLogin = findViewById<TextView>(R.id.textviewBackToLogin)

        buttonSubmit.setOnClickListener {
            clearRegisterError()
            authScope.launch {
                presenter.onRegisterClicked(
                    edittextEmail.text.toString().trim(),
                    edittextFieldName.text.toString().trim(),
                    edittextPassword.text.toString().trim(),
                    edittextReenterPassword.text.toString().trim()
                )
            }
        }

        textviewBackToLogin.setOnClickListener {
            navigateToLogin()
        }

        textviewRegisterGoogleSignIn.setOnClickListener {
            clearRegisterError()
            authScope.launch {
                presenter.onGoogleSignInClicked()
            }
        }
    }

    // --- View Interface Implementations ---

    override fun showSuccess(message: String) {
        clearRegisterError()
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showError(message: String) {
        textviewRegisterError.text = message
        textviewRegisterError.visibility = View.VISIBLE
    }

    override fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        authScope.cancel()
        super.onDestroy()
    }

    private fun clearRegisterError() {
        textviewRegisterError.text = ""
        textviewRegisterError.visibility = View.GONE
    }
}
