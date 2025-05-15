package com.example.vpnvkr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class ApiService {
    private val client = OkHttpClient()

    private val baseUrl = "http://87.228.77.67:8080"
//    private val baseUrl = "http://95.181.174.237:8080"
//    private val baseUrl = "http://10.0.2.2:8080"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun login(credentials: UserCredentials): LoginResponse = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("username", credentials.username)
            put("password", credentials.password)
        }

        val requestBody = json.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/login")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Login failed")

            val responseBody = response.body?.string() ?: throw IOException("Empty response")
            val jsonResponse = JSONObject(responseBody)
            LoginResponse(jsonResponse.getString("token"))
        }
    }

    suspend fun register(credentials: UserCredentials) = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("username", credentials.username)
            put("password", credentials.password)
            put("email", credentials.email ?: "")
        }

        val requestBody = json.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/register")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Registration failed")
        }
    }
}

data class UserCredentials(
    val username: String,
    val password: String,
    val email: String? = null
)

data class LoginResponse(val token: String)