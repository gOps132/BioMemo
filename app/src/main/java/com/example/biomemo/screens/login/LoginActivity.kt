package com.example.biomemo.screens.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.biomemo.R
import com.example.biomemo.config.AppConfig
import com.example.biomemo.screens.dashboard.DashboardActivity
import com.example.biomemo.screens.register.RegisterActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity(), LoginContract.View {

    private lateinit var presenter: LoginPresenter
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var textviewLoginError: TextView
    private val authScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        presenter = LoginPresenter(this, LoginModel())

        etUsername = findViewById(R.id.edittextUsername)
        etPassword = findViewById(R.id.edittextPassword)
        textviewLoginError = findViewById(R.id.textviewLoginError)
        val btnLogin = findViewById<Button>(R.id.buttonLogin)
        val tvGoogleSignIn = findViewById<TextView>(R.id.textviewGoogleSignIn)
        val tvCreateAccount = findViewById<TextView>(R.id.textviewCreateAccount)

        tvGoogleSignIn.visibility = if (AppConfig.canUseGoogleSignIn()) View.VISIBLE else View.GONE

        btnLogin.setOnClickListener {
            clearLoginError()
            authScope.launch {
                presenter.onLoginClicked(
                    etUsername.text.toString().trim(),
                    etPassword.text.toString().trim()
                )
            }
        }

        tvCreateAccount.setOnClickListener {
            presenter.onRegisterClicked()
        }

        tvGoogleSignIn.setOnClickListener {
            clearLoginError()
            authScope.launch {
                presenter.onGoogleSignInClicked()
            }
        }
    }

    override fun showLoginSuccess(username: String) {
        clearLoginError()
        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
    }

    override fun showGoogleAuthStarted() {
        clearLoginError()
        Toast.makeText(this, "Continue in your browser to finish Google sign-in.", Toast.LENGTH_LONG).show()
    }

    override fun showError(message: String) {
        textviewLoginError.text = message
        textviewLoginError.visibility = View.VISIBLE
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

    override fun onDestroy() {
        authScope.cancel()
        super.onDestroy()
    }

    private fun clearLoginError() {
        textviewLoginError.text = ""
        textviewLoginError.visibility = View.GONE
    }
}
