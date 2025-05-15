package com.example.vpnvkr

import android.content.Context

class TokenManager(context: Context) {
    private val prefs = context.getSharedPreferences("VPNPrefs", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        prefs.edit().putString("jwt_token", token).apply()
    }

    fun getToken(): String? {
        return prefs.getString("jwt_token", null)
    }

    fun clearToken() {
        prefs.edit().remove("jwt_token").apply()
    }
}