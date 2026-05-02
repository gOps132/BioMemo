package com.example.biomemo.screens.register

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.screens.login.LoginActivity

class RegisterActivity : AppCompatActivity(), RegisterContract.View {

    private lateinit var presenter: RegisterPresenter
    private lateinit var edittextUsername: EditText
    private lateinit var edittextPassword: EditText
    private lateinit var edittextReenterPassword: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Init MVP
        presenter = RegisterPresenter(this, RegisterModel())

        // UI References
        edittextUsername = findViewById(R.id.edittextUsername)
        edittextPassword = findViewById(R.id.edittextPassword)
        edittextReenterPassword = findViewById(R.id.edittextReenterPassword)
        val buttonSubmit = findViewById<Button>(R.id.buttonSubmit)
        val textviewRegisterGoogleSignIn = findViewById<TextView>(R.id.textviewRegisterGoogleSignIn)
        val textviewBackToLogin = findViewById<TextView>(R.id.textviewBackToLogin)

        buttonSubmit.setOnClickListener {
            presenter.onRegisterClicked(
                edittextUsername.text.toString().trim(),
                edittextPassword.text.toString().trim(),
                edittextReenterPassword.text.toString().trim()
            )
        }

        textviewBackToLogin.setOnClickListener {
            navigateToLogin()
        }

        textviewRegisterGoogleSignIn.setOnClickListener {
            showError("Google sign-in will be enabled with Supabase auth.")
        }
    }

    // --- View Interface Implementations ---

    override fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
