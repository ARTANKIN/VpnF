package com.example.vpnvkr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.vpnvkr.databinding.ActivityMainBinding
import com.example.vpnvkr.ApiService
import com.example.vpnvkr.UserCredentials
import com.example.vpnvkr.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val apiService = ApiService()
    private val tokenManager by lazy { TokenManager(this) }
    private var isVpnConnected = false // Переменная для отслеживания состояния VPN

    companion object {
        private const val VPN_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        checkAuthStatus()
    }

    private val vpnStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VpnClientService.ACTION_VPN_STOPPED) {
                isVpnConnected = false
                binding.btnStartVPN.text = "Connect"
                binding.tvStatus.text = "VPN Status: Disconnected"
                showToast("VPN fully disconnected")
                Log.d("MainActivity", "VPN service fully stopped")
            }
        }
    }

    private fun setupListeners() {
        binding.apply {
            btnLogin.setOnClickListener { loginUser() }
            btnRegister.setOnClickListener { registerUser() }
            btnStartVPN.setOnClickListener { toggleVPN() }
        }
    }

    private fun checkAuthStatus() {
        val isLoggedIn = tokenManager.getToken() != null
        binding.btnStartVPN.isEnabled = isLoggedIn
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
                    binding.btnStartVPN.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Login failed: ${e.message}")
                }
            }
        }
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
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Registration failed: ${e.message}")
                }
            }
        }
    }

    private fun toggleVPN() {
        Log.d("MainActivity", "Attempting to toggle VPN")
        val token = tokenManager.getToken()
        if (token == null) {
            showToast("Please login first")
            return
        }

        if (isVpnConnected) {
            // Отключаем VPN
            val serviceIntent = Intent(this, VpnClientService::class.java)
            stopService(serviceIntent)
            binding.tvStatus.text = "VPN Status: Disconnecting..."
            Log.d("MainActivity", "VPN disconnect requested")
        } else {
            // Подключаем VPN
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                Log.d("MainActivity", "VPN preparation needed")
                startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
            } else {
                Log.d("MainActivity", "VPN preparation not needed")
                onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            Log.d("MainActivity", "Starting VPN service")
            val serviceIntent = Intent(this, VpnClientService::class.java)
            startService(serviceIntent)
            isVpnConnected = true
            binding.tvStatus.text = "VPN Status: Connecting..."
            binding.btnStartVPN.text = "Disconnect"
        } else {
            Log.d("MainActivity", "VPN permission denied")
            showToast("VPN permission denied")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        unregisterReceiver(vpnStoppedReceiver)
        super.onDestroy()
    }
}