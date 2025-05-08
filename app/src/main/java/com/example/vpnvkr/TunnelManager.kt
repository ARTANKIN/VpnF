package com.example.vpnvkr

import android.util.Log
import java.net.Socket
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TunnelManager(
    private val serverIp: String,
    private val serverPort: Int,
    private val crypto: CryptoHelper
) {
    private var socket: Socket? = null

    suspend fun connect() = withContext(Dispatchers.IO) {
        socket = Socket(serverIp, serverPort)
    }

    suspend fun sendData(data: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // Шифрование данных
            val encrypted = crypto.encrypt(data)

            // Отправка данных
            socket?.getOutputStream()?.write(encrypted)
            socket?.getOutputStream()?.flush()

            // Чтение ответа
            val buffer = ByteArray(4096)
            val bytesRead = socket?.getInputStream()?.read(buffer)

            if (bytesRead != null && bytesRead > 0) {
                val response = buffer.copyOf(bytesRead)
                return@withContext crypto.decrypt(response)
            }
        } catch (e: Exception) {
            Log.e("TunnelManager", "Error: ${e.message}")
        }
        null
    }

    fun disconnect() {
        socket?.close()
    }
}