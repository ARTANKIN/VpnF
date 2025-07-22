package com.example.vpnvkr

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.vpnvkr.databinding.ActivityRegisterBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private val apiService = ApiService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener { registerUser() }
        binding.btnBack.setOnClickListener { finish() } // Кнопка "Назад"
    }

    private fun registerUser() {
        val username = binding.etUsername.text.toString()
        val password = binding.etPassword.text.toString()
        val email = binding.etEmail.text.toString()

        if (username.isBlank() || password.isBlank() || email.isBlank()) {
            showToast("Please fill all fields")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                apiService.register(UserCredentials(username, password, email))
                withContext(Dispatchers.Main) {
                    showToast("Registration successful")
                    finish() // Закрываем RegisterActivity и возвращаемся в MainActivity
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Registration failed: ${e.message}")
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}