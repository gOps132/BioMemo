package com.example.biomemo.screens.login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.screens.dashboard.DashboardActivity
import com.example.biomemo.screens.register.RegisterActivity

class LoginActivity : AppCompatActivity(), LoginContract.View {

    private lateinit var presenter: LoginPresenter
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        presenter = LoginPresenter(this, LoginModel())

        etUsername = findViewById(R.id.edittextUsername)
        etPassword = findViewById(R.id.edittextPassword)
        val btnLogin = findViewById<Button>(R.id.buttonLogin)
        val tvGoogleSignIn = findViewById<TextView>(R.id.textviewGoogleSignIn)
        val tvCreateAccount = findViewById<TextView>(R.id.textviewCreateAccount)

        btnLogin.setOnClickListener {
            presenter.onLoginClicked(
                etUsername.text.toString().trim(),
                etPassword.text.toString().trim()
            )
        }

        tvCreateAccount.setOnClickListener {
            presenter.onRegisterClicked()
        }

        tvGoogleSignIn.setOnClickListener {
            presenter.onGoogleSignInClicked()
        }
    }

    override fun showLoginSuccess(username: String) {
        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
    }

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun navigateToRegister() {
        startActivity(Intent(this, RegisterActivity::class.java))
    }

    override fun navigateToDashboard(username: String) {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.putExtra("username", username)
        startActivity(intent)
        finish()
    }
}
