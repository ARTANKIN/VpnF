package com.example.vpnvkr

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {
    private lateinit var socket: DatagramSocket
    private lateinit var serverAddress: InetAddress
    private val port = 5555
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    // AES-GCM параметры
    private val key = "32-char-key-for-AES-256-GCM-exam".toByteArray()
    private val ivLength = 12
    private val tagLength = 128 // 128 бит для GCM

    companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_ALGORITHM = "AES"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Показываем индикатор загрузки
        findViewById<ProgressBar>(R.id.progressBar).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnSend).isEnabled = false

        // Инициализация сети в фоновом потоке
        Thread {
            try {
                serverAddress = InetAddress.getByName("10.0.2.2")
                socket = DatagramSocket().apply {
                    soTimeout = 5000
                }

                handler.post {
                    findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
                    findViewById<Button>(R.id.btnSend).isEnabled = true
                    startListening()
                }
            } catch (e: Exception) {
                handler.post {
                    findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
                    updateUI("Initialization error: ${e.message}")
                    e.printStackTrace()
                }
            }
        }.start()

        findViewById<Button>(R.id.btnSend).setOnClickListener {
            val message = findViewById<EditText>(R.id.etMessage).text.toString()
            if (message.isNotEmpty()) {
                sendMessage(message)
            }
        }
    }

    private fun sendMessage(message: String) {
        Thread {
            try {
                val encrypted = encrypt(message.toByteArray())
                val packet = DatagramPacket(
                    encrypted,
                    encrypted.size,
                    serverAddress,
                    port
                )
                socket.send(packet)

                updateUI("Sent: $message")
            } catch (e: Exception) {
                updateUI("Send error: ${e.message}")
                e.printStackTrace()
            }
        }.start()
    }

    private fun startListening() {
        isRunning = true
        Thread {
            val buffer = ByteArray(4096)
            while (isRunning) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    if (packet.length == 0) continue

                    val decrypted = decrypt(buffer.copyOf(packet.length))
                    updateUI("Received: ${String(decrypted)}")
                } catch (e: Exception) {
                    if (isRunning) {
                        updateUI("Receive error: ${e.message}")
                    }
                }
            }
        }.start()
    }

    private fun encrypt(data: ByteArray): ByteArray {
        val iv = ByteArray(ivLength).apply {
            SecureRandom().nextBytes(this)
        }

        val keySpec = SecretKeySpec(key, KEY_ALGORITHM)
        val gcmSpec = GCMParameterSpec(tagLength, iv)

        return Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            iv + doFinal(data)
        }
    }

    private fun decrypt(data: ByteArray): ByteArray {
        if (data.size < ivLength) {
            throw IllegalArgumentException("Invalid data length")
        }

        val iv = data.copyOfRange(0, ivLength)
        val encrypted = data.copyOfRange(ivLength, data.size)

        val keySpec = SecretKeySpec(key, KEY_ALGORITHM)
        val gcmSpec = GCMParameterSpec(tagLength, iv)

        return Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            doFinal(encrypted)
        }
    }

    private fun updateUI(newMessage: String) {
        handler.post {
            val tvLog = findViewById<TextView>(R.id.tvLog)
            val currentText = tvLog.text.toString()

            val updatedText = if (currentText.isEmpty()) {
                newMessage
            } else {
                "$newMessage\n$currentText"
            }

            // Ограничиваем количество строк и переворачиваем порядок
            val lines = updatedText.split("\n")
            val maxLines = 50
            val processedText = if (lines.size > maxLines) {
                lines.take(maxLines).joinToString("\n")
            } else {
                updatedText
            }

            tvLog.text = processedText
            tvLog.scrollTo(0, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        socket.close()
    }
}