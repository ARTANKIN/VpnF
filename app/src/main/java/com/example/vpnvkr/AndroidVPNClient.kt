package com.example.vpnvkr


import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

class AndroidVPNClient : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var socket: DatagramSocket? = null
    private lateinit var cryptoManager: CryptoManager
    private lateinit var executorService: ExecutorService

    companion object {
        private const val SERVER_ADDRESS = "10.0.2.2"
        private const val SERVER_PORT = 5555
        private const val ENCRYPTION_KEY = "32-char-key-for-AES-256-GCM-exam"
        private const val VPN_MTU = 1500
        private const val TAG = "VPNClient"
    }

    override fun onCreate() {
        super.onCreate()
        cryptoManager = CryptoManager(ENCRYPTION_KEY)
        executorService = Executors.newFixedThreadPool(2)
        Log.d(TAG, "VPN Service Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        setupVpnConnection()
        return START_STICKY
    }

    private fun setupVpnConnection() {
        val builder = Builder()
            .setMtu(VPN_MTU)
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("1.1.1.1")
            .setSession("MyVPNService")

        vpnInterface = builder.establish()
        socket = DatagramSocket()
        protect(socket!!)

        executorService.submit { receivePackets() }
        executorService.submit { sendPackets() }

        Log.d(TAG, "VPN Connection Setup Complete")
    }

    private fun receivePackets() {
        val buffer = ByteArray(4096)
        while (!Thread.currentThread().isInterrupted) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket?.receive(packet)

                val decryptedData = cryptoManager.decrypt(
                    buffer.copyOfRange(0, packet.length)
                )

                vpnInterface?.fileDescriptor?.let { fd ->
                    android.system.Os.write(fd, decryptedData, 0, decryptedData.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Receive Packet Error", e)
            }
        }
    }

    private fun sendPackets() {
        val buffer = ByteArray(4096)
        while (!Thread.currentThread().isInterrupted) {
            try {
                val bytesRead = vpnInterface?.fileDescriptor?.let { fd ->
                    android.system.Os.read(fd, buffer, 0, buffer.size)
                } ?: -1

                if (bytesRead > 0) {
                    val encryptedData = cryptoManager.encrypt(
                        buffer.copyOfRange(0, bytesRead)
                    )

                    val packet = DatagramPacket(
                        encryptedData,
                        encryptedData.size,
                        InetAddress.getByName(SERVER_ADDRESS),
                        SERVER_PORT
                    )
                    socket?.send(packet)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send Packet Error", e)
            }
        }
    }

    override fun onDestroy() {
        executorService.shutdownNow()
        vpnInterface?.close()
        socket?.close()
        super.onDestroy()
        Log.d(TAG, "VPN Service Destroyed")
    }

    private class CryptoManager(private val key: String) {
        private val cipher: Cipher = Cipher.getInstance("AES/GCM/NoPadding")
        private val secretKey: SecretKeySpec
        private val messageDigest: MessageDigest = MessageDigest.getInstance("SHA-256")

        init {
            // Используем первые 32 байта хеша ключа
            val keyBytes = messageDigest.digest(key.toByteArray())
            val truncatedKey = keyBytes.copyOfRange(0, 32)
            secretKey = SecretKeySpec(truncatedKey, "AES")
        }

        fun encrypt(data: ByteArray): ByteArray {
            val iv = ByteArray(12)
            val random = java.security.SecureRandom()
            random.nextBytes(iv)

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val encryptedData = cipher.doFinal(data)

            // Возвращаем IV + зашифрованные данные
            return iv + encryptedData
        }

        fun decrypt(data: ByteArray): ByteArray {
            // Проверка минимальной длины данных
            if (data.size < 12) {
                throw IllegalArgumentException("Данные слишком короткие для дешифрования")
            }

            val iv = data.copyOfRange(0, 12)
            val encryptedData = data.copyOfRange(12, data.size)

            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            return cipher.doFinal(encryptedData)
        }
    }
}