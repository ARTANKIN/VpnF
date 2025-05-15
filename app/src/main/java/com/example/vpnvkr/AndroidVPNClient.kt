package com.example.vpnvkr

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class VpnClientService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var socket: DatagramSocket? = null
    private val cryptoManager by lazy { CryptoManager() }
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private val tokenManager by lazy { TokenManager(this) }
    private var isRunning = true

    companion object {
//        private const val SERVER_ADDRESS = "95.181.174.237"
//        private const val SERVER_ADDRESS = "172.20.10.2"
private const val SERVER_ADDRESS = "87.228.77.67"
        private const val SERVER_PORT = 5555
        private const val TAG = "VPNClientService"
        const val ACTION_VPN_STOPPED = "com.example.vpnvkr.VPN_STOPPED"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "VPN Service starting...")
        setupVpnConnection()
        return START_NOT_STICKY // Используем NOT_STICKY, чтобы сервис не перезапускался
    }

    private fun setupVpnConnection() {
        try {
            val builder = Builder()
                .setSession("MyVPNClient")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
                .setMtu(1200)

            vpnInterface = builder.establish()
            Log.d(TAG, "VPN interface established")

            socket = DatagramSocket().apply {
                protect(this)
            }
            Log.d(TAG, "UDP socket created and protected")

            coroutineScope.launch(Dispatchers.IO) {
                sendInitialPacket()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Setup VPN connection failed", e)
            stopSelf()
        }
    }

    private suspend fun sendInitialPacket() {
        try {
            val token = tokenManager.getToken()
            if (token == null) {
                Log.e(TAG, "No token available")
                withContext(Dispatchers.Main) {
                    stopSelf()
                }
                return
            }

            val authPacket = JSONObject().apply {
                put("type", "auth")
                put("token", token)
            }.toString()

            Log.d(TAG, "Sending auth packet content: $authPacket")
            val serverAddress = InetAddress.getByName(SERVER_ADDRESS)
            val packet = DatagramPacket(
                authPacket.toByteArray(Charsets.UTF_8),
                authPacket.length,
                serverAddress,
                SERVER_PORT
            )

            socket?.send(packet)
            Log.d(TAG, "Initial auth packet sent")

            receiveAuthResponse()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send initial packet", e)
            withContext(Dispatchers.Main) {
                stopSelf()
            }
        }
    }

    private suspend fun receiveAuthResponse() {
        val receiveBuffer = ByteArray(1024)
        val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)

        try {
            socket?.soTimeout = 10000
            Log.d(TAG, "Waiting for auth response from server...")
            socket?.receive(packet)
            val response = String(packet.data, 0, packet.length, Charsets.UTF_8)
            Log.d(TAG, "Received auth response: $response")
            val jsonResponse = JSONObject(response)
            if (jsonResponse.getString("type") == "auth_response" &&
                jsonResponse.getString("token") == "success"
            ) {
                Log.d(TAG, "Authentication successful")
                handlePackets()
            } else {
                Log.e(TAG, "Authentication failed: $response")
                withContext(Dispatchers.Main) {
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving auth response", e)
            withContext(Dispatchers.Main) {
                stopSelf()
            }
        }
    }

    private suspend fun handlePackets() {
        val receiveBuffer = ByteArray(32767)
        val sendBuffer = ByteArray(32767)

        coroutineScope.launch(Dispatchers.IO) {
            while (isRunning) {
                try {
                    val inputStream = FileInputStream(vpnInterface?.fileDescriptor)
                    val length = inputStream.read(sendBuffer)
                    if (length > 0) {
                        val encryptedData = cryptoManager.encrypt(sendBuffer.copyOfRange(0, length))
                        val packet = DatagramPacket(
                            encryptedData,
                            encryptedData.size,
                            InetAddress.getByName(SERVER_ADDRESS),
                            SERVER_PORT
                        )
                        socket?.send(packet)
                        Log.d(TAG, "Sent packet to server, size: ${encryptedData.size}, original size: $length")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending packet", e)
                    if (!isRunning) break
                }
            }
            Log.d(TAG, "Send loop stopped")
        }

        coroutineScope.launch(Dispatchers.IO) {
            while (isRunning) {
                try {
                    val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
                    socket?.receive(packet)
                    Log.d(TAG, "Received packet from server, size: ${packet.length}")

                    val decryptedData = cryptoManager.decrypt(
                        receiveBuffer.copyOfRange(0, packet.length)
                    )

                    Log.d(TAG, "Decrypted packet size: ${decryptedData.size}")

                    val outputStream = FileOutputStream(vpnInterface?.fileDescriptor)
                    outputStream.write(decryptedData)
                    Log.d(TAG, "Wrote decrypted packet to VPN interface")
                } catch (e: Exception) {
                    Log.e(TAG, "Error receiving packet", e)
                    if (!isRunning) break
                }
            }
            Log.d(TAG, "Receive loop stopped")
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "VPN Service destroying...")
        isRunning = false
        // Отменяем корутины и ждем их завершения
        runBlocking {
            coroutineScope.cancel("Service is stopping")
            Log.d(TAG, "Coroutines cancelled")
        }
        try {
            vpnInterface?.close()
            Log.d(TAG, "VPN interface closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        try {
            socket?.close()
            Log.d(TAG, "UDP socket closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket", e)
        }
        // Отправляем broadcast о том, что сервис остановлен
        val intent = Intent(ACTION_VPN_STOPPED)
        sendBroadcast(intent)
        Log.d(TAG, "Broadcast sent: VPN stopped")
        super.onDestroy()
        Log.d(TAG, "VPN Service destroyed")
    }

    override fun onRevoke() {
        Log.d(TAG, "VPN Service revoked by system")
        isRunning = false
        runBlocking {
            coroutineScope.cancel("Service revoked by system")
            Log.d(TAG, "Coroutines cancelled due to revoke")
        }
        try {
            vpnInterface?.close()
            Log.d(TAG, "VPN interface closed on revoke")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface on revoke", e)
        }
        try {
            socket?.close()
            Log.d(TAG, "UDP socket closed on revoke")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket on revoke", e)
        }
        // Отправляем broadcast о том, что сервис остановлен
        val intent = Intent(ACTION_VPN_STOPPED)
        sendBroadcast(intent)
        Log.d(TAG, "Broadcast sent: VPN stopped due to revoke")
        stopSelf()
        super.onRevoke()
        Log.d(TAG, "VPN Service revoked completed")
    }
}