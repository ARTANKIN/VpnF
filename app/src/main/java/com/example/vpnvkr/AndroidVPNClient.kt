package com.example.vpnvkr

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.net.VpnService.Builder
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class VpnClientService : VpnService() {

    companion object {
        private const val TAG = "VpnClientService"
        var SERVER_ADDRESS = "172.25.15.2"
        private const val SERVER_PORT = 5555
        const val ACTION_VPN_STOPPED = "com.example.vpnvkr.VPN_STOPPED"
        const val ACTION_VPN_TRAFFIC_STATS = "com.example.vpnvkr.VPN_TRAFFIC_STATS"
    }

    // Управление состоянием сервиса
    private val isRunning = AtomicBoolean(false)
    private val isStopping = AtomicBoolean(false)

    // Корутины и каналы
    private val serviceScope = CoroutineScope(
        Dispatchers.IO +
                SupervisorJob() +
                CoroutineExceptionHandler { _, throwable ->
                    Log.e(TAG, "Coroutine error", throwable)
                }
    )
    private val packetChannel = Channel<ByteArray>(Channel.BUFFERED)

    // Ресурсы VPN
    private var vpnInterface: ParcelFileDescriptor? = null
    private var socket: DatagramSocket? = null

    // Менеджеры
    private val cryptoManager by lazy { CryptoManager() }
    private val tokenManager by lazy { TokenManager(this) }

    // Статистика трафика
    private val sentBytesTotal = AtomicLong(0)
    private val receivedBytesTotal = AtomicLong(0)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VPN Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP_VPN" -> {
                stopVpnService()
                return START_NOT_STICKY
            }
            else -> {
                if (isRunning.get()) {
                    Log.d(TAG, "VPN Service already running")
                    return START_NOT_STICKY
                }
                isRunning.set(true)
                isStopping.set(false)

                serviceScope.launch {
                    try {
                        setupVpnConnection()
                        startPacketProcessing()
                    } catch (e: Exception) {
                        Log.e(TAG, "VPN setup failed", e)
                        stopVpnService()
                    }
                }
                return START_STICKY
            }
        }
    }

    private suspend fun setupVpnConnection() = withContext(Dispatchers.IO) {
        val token = tokenManager.getToken() ?: throw IllegalStateException("No token available")

        // Настройка VPN
        val builder = Builder()
            .setSession("MyVPNClient")
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("1.1.1.1")
            .setMtu(1500)

        vpnInterface = builder.establish()

        // Создание и защита сокета
        socket = DatagramSocket().apply {
            protect(this)
            soTimeout = 1000
        }

        // Отправка начального пакета аутентификации
        sendAuthPacket(token)
    }

    private suspend fun sendAuthPacket(token: String) {
        val authPacket = JSONObject().apply {
            put("type", "auth")
            put("token", token)
        }.toString()

        val serverAddress = InetAddress.getByName(SERVER_ADDRESS)
        val packet = DatagramPacket(
            authPacket.toByteArray(Charsets.UTF_8),
            authPacket.length,
            serverAddress,
            SERVER_PORT
        )

        socket?.send(packet)
        Log.d(TAG, "Auth packet sent")
    }

    private suspend fun startPacketProcessing() {
        val sendJob = serviceScope.launch { sendPackets() }
        val receiveJob = serviceScope.launch { receivePackets() }
        val statsJob = serviceScope.launch { broadcastTrafficStats() }

        // Ожидание завершения всех задач
        listOf(sendJob, receiveJob, statsJob).joinAll()
    }

    private suspend fun sendPackets() {
        val inputStream = FileInputStream(vpnInterface?.fileDescriptor)
        val buffer = ByteArray(32767)

        try {
            while (isRunning.get() && !isStopping.get()) {
                withContext(Dispatchers.IO) {
                    val length = inputStream.read(buffer)
                    if (length > 0) {
                        val encryptedData = cryptoManager.encrypt(buffer.copyOfRange(0, length))
                        val packet = DatagramPacket(
                            encryptedData,
                            encryptedData.size,
                            InetAddress.getByName(SERVER_ADDRESS),
                            SERVER_PORT
                        )
                        socket?.let { if (!it.isClosed) it.send(packet) }
                        sentBytesTotal.addAndGet(encryptedData.size.toLong())
                    }
                }
            }
        } catch (e: Exception) {
            if (isRunning.get() && !isStopping.get()) {
                Log.e(TAG, "Send packet error", e)
            }
        } finally {
            try {
                inputStream.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing input stream", e)
            }
        }
        Log.d(TAG, "Send packets loop stopped")
    }

    private suspend fun receivePackets() {
        val outputStream = FileOutputStream(vpnInterface?.fileDescriptor)
        val buffer = ByteArray(32767)

        while (isRunning.get() && !isStopping.get()) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket?.receive(packet)

                val decryptedData = cryptoManager.decrypt(
                    buffer.copyOfRange(0, packet.length)
                )
                outputStream.write(decryptedData)
                receivedBytesTotal.addAndGet(packet.length.toLong())
            } catch (e: Exception) {
                if (!isRunning.get()) break
                if (e !is SocketTimeoutException) {
                    Log.e(TAG, "Receive packet error", e)
                }
            }
        }
        Log.d(TAG, "Receive packets loop stopped")
    }

    private suspend fun broadcastTrafficStats() {
        while (isRunning.get() && !isStopping.get()) {
            try {
                if (!isStopping.get()) {
                    val intent = Intent(ACTION_VPN_TRAFFIC_STATS).apply {
                        putExtra("sentBytes", sentBytesTotal.get())
                        putExtra("receivedBytes", receivedBytesTotal.get())
                    }
                    Log.d(TAG, "Broadcasting traffic stats: Sent=${sentBytesTotal.get()}, Received=${receivedBytesTotal.get()}")
                    LocalBroadcastManager.getInstance(this@VpnClientService).sendBroadcast(intent)
                }
                delay(1000)
            } catch (e: Exception) {
                if (!isStopping.get()) {
                    Log.e(TAG, "Traffic stats broadcast error", e)
                }
            }
        }
    }

    // Make stopVpnService public
    fun stopVpnService() {
        if (isStopping.getAndSet(true)) return

        Log.d(TAG, "Stopping VPN service")
        isRunning.set(false)

        serviceScope.coroutineContext.cancelChildren()

        // Close and nullify the VPN interface
        vpnInterface?.let {
            try {
                it.close()
            } catch (e: Exception) {
                Log.e(TAG, "VPN interface close error", e)
            } finally {
                vpnInterface = null
            }
        }

        // Close the socket
        socket?.let {
            try {
                it.close()
            } catch (e: Exception) {
                Log.e(TAG, "Socket close error", e)
            } finally {
                socket = null
            }
        }

        // Reset traffic statistics
        sentBytesTotal.set(0)
        receivedBytesTotal.set(0)

        sendBroadcast(Intent(ACTION_VPN_STOPPED))
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "VPN Service destroyed")
        stopVpnService()
        super.onDestroy()
    }

    override fun onRevoke() {
        Log.d(TAG, "VPN Service revoked")
        stopVpnService()
        super.onRevoke()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "VPN Service task removed")
        stopVpnService()
        super.onTaskRemoved(rootIntent)
    }

    inner class LocalBinder : Binder() {
        fun getService(): VpnClientService = this@VpnClientService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
}
