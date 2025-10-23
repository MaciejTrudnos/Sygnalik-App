package com.maciejtrudnos.sygnalik

import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.maciejtrudnos.sygnalik.ui.theme.SygnalikTheme
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var bleManager by mutableStateOf<BLEManager?>(null)
    private var service: ForegroundService? = null
    var bleText by mutableStateOf("")

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d("MainActivity", "onServiceConnected called")
            val localBinder = binder as ForegroundService.LocalBinder
            service = localBinder.getService()

            lifecycleScope.launch {
                service?.ready?.collect { ready ->
                    if (ready) {
                        bleManager = service?.bleManager
                        Log.d("MainActivity", "BLEManager initialized")
                    }
                }
            }

            lifecycleScope.launch {
                service?.bleText?.collect { text ->
                    bleText = text
                    Log.d("MainActivity", "BLE Text updated: $text")
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("MainActivity", "onServiceDisconnected called")
            service = null
            bleManager = null
            bleText = ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val intent = Intent(this, ForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        setContent {
            SygnalikTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        modifier = Modifier.padding(innerPadding),
                        bleManager = bleManager,  // This will update automatically
                        bleText
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(connection)
    }
}


@Composable
fun Greeting(modifier: Modifier = Modifier, bleManager: BLEManager?, bleText: String, ) {
    var inputText by remember { mutableStateOf("") }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Status: $bleText")

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Wpisz wiadomość") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                if (inputText.isNotEmpty()) {
                    bleManager?.sendText(inputText)
                    inputText = ""
                }
            }) {
                Text("Wyślij")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SygnalikTheme {
        Greeting(
            bleManager = BLEManager(
                context = android.content.ContextWrapper(null), // dummy context
                bluetoothLeScanner = null,
                onMessage = {} // lambda nic nie robi w preview
            ),
            bleText = "Hello Preview"
        )
    }
}