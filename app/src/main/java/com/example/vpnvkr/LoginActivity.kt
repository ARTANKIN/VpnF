package com.example.vpnvkr

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.vpnvkr.databinding.ActivityLoginBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val apiService = ApiService()
    private val tokenManager by lazy { TokenManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener { loginUser() }
        binding.btnBack.setOnClickListener { finish() } // Кнопка "Назад"
    }

    private fun loginUser() {
        val username = binding.etUsername.text.toString()
        val password = binding.etPassword.text.toString()

        if (username.isBlank() || password.isBlank()) {
            showToast("Please fill all fields")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.login(UserCredentials(username, password))
                withContext(Dispatchers.Main) {
                    tokenManager.saveToken(response.token)
                    showToast("Login successful")
                    finish() // Закрываем LoginActivity и возвращаемся в MainActivity
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Login failed: ${e.message}")
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}