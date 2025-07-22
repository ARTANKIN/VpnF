package com.example.vpnvkr

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ServerSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server)

        findViewById<Button>(R.id.btnServer1).setOnClickListener {
            VpnClientService.SERVER_ADDRESS = "172.20.10.2"
            finish()
        }

        findViewById<Button>(R.id.btnServer2).setOnClickListener {
            VpnClientService.SERVER_ADDRESS = "185.240.50.43"
            finish()
        }

        findViewById<Button>(R.id.btnServer3).setOnClickListener {
            VpnClientService.SERVER_ADDRESS = "213.148.15.194"
            finish()
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }
}