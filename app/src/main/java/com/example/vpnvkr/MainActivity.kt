package com.example.vpnvkr

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.vpnvkr.databinding.ActivityMainBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.navigation.NavigationView
import androidx.drawerlayout.widget.DrawerLayout

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private val tokenManager by lazy { TokenManager(this) }
    private var isVpnConnected = false
    private lateinit var trafficChart: LineChart
    private var sentDataEntries = ArrayList<Entry>()
    private var receivedDataEntries = ArrayList<Entry>()
    private var timeIndex = 0f

    companion object {
        private const val VPN_REQUEST_CODE = 1
        const val ACTION_VPN_TRAFFIC_STATS = "com.example.vpnvkr.VPN_TRAFFIC_STATS"
        private const val TAG = "MainActivity"
    }

    private var vpnService: VpnClientService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            vpnService = (service as VpnClientService.LocalBinder).getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            vpnService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize DrawerLayout and NavigationView
        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)

        // Set up the Toolbar as the ActionBar
        setSupportActionBar(binding.toolbar)

        // Drawer toggle setup
        toggle = ActionBarDrawerToggle(this, drawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Enable the home button in the Toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Set NavigationView item selection listener
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_login -> {
                    startActivity(Intent(this, LoginActivity::class.java))
                }
                R.id.nav_register -> {
                    startActivity(Intent(this, RegisterActivity::class.java))
                }
                R.id.nav_server_selection -> {
                    startActivity(Intent(this, ServerSelectionActivity::class.java))
                }
            }
            drawerLayout.closeDrawers()
            true
        }

        // Initialize the traffic chart
        trafficChart = binding.trafficChart
        Log.d(TAG, "Chart initial visibility: ${trafficChart.visibility == View.VISIBLE}")
        setupChart()

        // Register broadcast receivers and setup listeners
        registerBroadcastReceivers()
        setupListeners()
        checkAuthStatus()

        Log.d(TAG, "MainActivity onCreate completed")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (toggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun registerBroadcastReceivers() {
        val filter = IntentFilter(ACTION_VPN_TRAFFIC_STATS).apply {
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
        val vpnStoppedFilter = IntentFilter(VpnClientService.ACTION_VPN_STOPPED)

        try {
            LocalBroadcastManager.getInstance(this).registerReceiver(trafficReceiver, filter)
            LocalBroadcastManager.getInstance(this).registerReceiver(vpnStoppedReceiver, vpnStoppedFilter)
            Log.d(TAG, "Local Broadcast receivers registered successfully for action: $ACTION_VPN_TRAFFIC_STATS")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering broadcast receivers", e)
        }
    }

    private val trafficReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Traffic Receiver onReceive called with action: ${intent?.action}")
            if (intent?.action == ACTION_VPN_TRAFFIC_STATS) {
                val sentBytes = intent.getLongExtra("sentBytes", 0)
                val receivedBytes = intent.getLongExtra("receivedBytes", 0)
                Log.d(TAG, "Traffic Receiver - Sent: $sentBytes, Received: $receivedBytes")
                runOnUiThread {
                    updateTrafficChart(sentBytes, receivedBytes)
                }
            } else {
                Log.w(TAG, "Unexpected action received: ${intent?.action}")
            }
        }
    }

    private val vpnStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VpnClientService.ACTION_VPN_STOPPED) {
                isVpnConnected = false
                binding.btnStartVPN.text = "Connect"
                binding.tvStatus.text = "VPN Status: Disconnected"
                showToast("VPN fully disconnected")
                Log.d(TAG, "VPN service fully stopped. Resetting chart data.")
                runOnUiThread {
                    sentDataEntries.clear()
                    receivedDataEntries.clear()
                    timeIndex = 0f
                    updateChartData()
                }
            }
        }
    }

    private fun updateTrafficChart(currentSentTotal: Long, currentReceivedTotal: Long) {
        val sentKB = (currentSentTotal / 1024f) // Конвертируем байты в килобайты
        val receivedKB = (currentReceivedTotal / 1024f) // Конвертируем байты в килобайты

        timeIndex += 1f

        sentDataEntries.add(Entry(timeIndex, sentKB))
        receivedDataEntries.add(Entry(timeIndex, receivedKB))

        // Ограничение количества точек на графике (например, последние 60 секунд)
        if (sentDataEntries.size > 60) {
            sentDataEntries.removeAt(0)
            receivedDataEntries.removeAt(0)
        }

        updateChartData()
    }

    private fun setupChart() {
        trafficChart.apply {
            visibility = View.VISIBLE
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setPinchZoom(true)

            // Настройка осей
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                granularity = 1f
            }

            axisRight.isEnabled = false

            // Установка пустых данных при инициализации
            data = LineData()
            invalidate()
        }
    }

    private fun updateChartData() {
        if (!::trafficChart.isInitialized || trafficChart == null) {
            Log.e(TAG, "trafficChart is not initialized in updateChartData!")
            return
        }

        val sentDataSet = LineDataSet(ArrayList(sentDataEntries), "Sent Traffic (KB)").apply {
            color = Color.BLUE
            setDrawCircles(false)
            lineWidth = 2f
            setDrawValues(false)
        }

        val receivedDataSet = LineDataSet(ArrayList(receivedDataEntries), "Received Traffic (KB)").apply {
            color = Color.RED
            setDrawCircles(false)
            lineWidth = 2f
            setDrawValues(false)
        }

        val lineData = LineData(sentDataSet, receivedDataSet)
        trafficChart.data = lineData
        trafficChart.invalidate()
        Log.d(TAG, "Chart updated with data: Sent entries=${sentDataEntries.size}, Received entries=${receivedDataEntries.size}")
    }

    private fun setupListeners() {
        binding.apply {
            btnStartVPN.setOnClickListener { toggleVPN() }
//            btnLogin.setOnClickListener { startActivity(Intent(this@MainActivity, LoginActivity::class.java)) }
//            btnRegister.setOnClickListener { startActivity(Intent(this@MainActivity, RegisterActivity::class.java)) }
        }
    }

    private fun checkAuthStatus() {
        val isLoggedIn = tokenManager.getToken() != null
        binding.btnStartVPN.isEnabled = isLoggedIn
    }

    private fun toggleVPN() {
        if (isVpnConnected) {
            vpnService?.stopVpnService()
            binding.btnStartVPN.text = "Подключение"
            binding.tvStatus.text = "VPN Status: Disconnected"
            isVpnConnected = false
            sentDataEntries.clear()
            receivedDataEntries.clear()
            timeIndex = 0f
            updateChartData()
        } else {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
            } else {
                onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            val serviceIntent = Intent(this, VpnClientService::class.java)
            startService(serviceIntent)
            isVpnConnected = true
            binding.tvStatus.text = "VPN Status: Connecting..."
            binding.btnStartVPN.text = "Отключение"
            Log.d(TAG, "VPN Connection Approved. Resetting chart data.")
            sentDataEntries.clear()
            receivedDataEntries.clear()
            timeIndex = 0f
            updateChartData()
        } else {
            showToast("VPN permission denied")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onStart() {
        super.onStart()
        Intent(this, VpnClientService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            isBound = true
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onDestroy() {
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(trafficReceiver)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(vpnStoppedReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receivers", e)
        }
        super.onDestroy()
    }
}