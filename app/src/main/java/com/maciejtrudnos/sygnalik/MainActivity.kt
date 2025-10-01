package com.maciejtrudnos.sygnalik

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maciejtrudnos.sygnalik.ui.theme.SygnalikTheme

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private lateinit var bleManager: BLEManager
    private lateinit var smsReceiver: SmsReceiver
    private lateinit var callReceiver: CallReceiver

    var bleText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        bleManager = BLEManager(this, bluetoothLeScanner, { message ->
            bleText = message
        })

        requestPermissions(bleManager)

        smsReceiver = SmsReceiver()
        SmsReceiver.bleManager = bleManager

        callReceiver = CallReceiver()
        CallReceiver.bleManager = bleManager



        setContent {
            SygnalikTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        modifier = Modifier.padding(innerPadding),
                        bleManager = bleManager,
                        bleText
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestPermissions(viewModel: BLEManager) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val launcher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { perms ->
            if (perms.all { it.value }) {
                viewModel.startScan()
            } else {
                Log.e("BLE", "Brak uprawnień")
            }
        }
        launcher.launch(permissions.toTypedArray())
    }
}

@Composable
fun Greeting(modifier: Modifier = Modifier, bleManager: BLEManager, bleText: String, ) {
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
                    bleManager.sendText(inputText)
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