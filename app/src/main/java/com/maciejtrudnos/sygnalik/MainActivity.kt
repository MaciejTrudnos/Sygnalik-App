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
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Call
import okhttp3.Callback
import java.io.IOException

class MainActivity : ComponentActivity() {
    private var bleManager by mutableStateOf<BLEManager?>(null)
    private var service: ForegroundService? = null
    var bleText by mutableStateOf("Wyszukuję urządzenie...")

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
        bindService(intent, connection, BIND_AUTO_CREATE)

        setContent {
            SygnalikTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        SelectableList(
                            bleText
                        )
                    }
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
fun SelectableList(bleText: String) {
    fun searchNominatim(query: String, onResult: (String?) -> Unit) {
        val client = OkHttpClient()

        val url = "https://nominatim.openstreetmap.org/search?" +
                "q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                "&format=jsonv2&limit=5"

        val nominatimUserAgent = BuildConfig.NOMINATIM_USER_AGENT

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", nominatimUserAgent)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onResult(null)
                    } else {
                        val body = it.body?.string()
                        onResult(body)
                    }
                }
            }
        })
    }

    fun parsePlacesWithGson(json: String): List<NominatimPlace>? {
        val gson = Gson()
        val listType = object : TypeToken<List<NominatimPlace>>() {}.type
        return gson.fromJson<List<NominatimPlace>>(json, listType)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        var selectedItem by remember { mutableStateOf<NominatimPlace?>(null) }
        var inputText by remember { mutableStateOf("") }
        var searchResult by remember { mutableStateOf<List<NominatimPlace>>(emptyList()) }

        Text(text = "Status: $bleText")

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Miejsce docelowe") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            searchNominatim(inputText) { jsonResponse ->
                if (jsonResponse != null) {
                    Log.d("NOMINATIM", jsonResponse)
                    searchResult = parsePlacesWithGson(jsonResponse) ?: emptyList()

                } else {
                    Log.d("NOMINATIM", "quest failed or blocked")
                }
            }

        }) {
            Text("Wyszukaj")
        }

        LazyColumn {
            items(searchResult) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedItem = item
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = item == selectedItem,
                        onClick = {
                            selectedItem = item
                        }
                    )
                    Text(text = item.display_name)
                }
            }
        }

        Button(onClick = {
            Log.d("NAV-RUN", "$selectedItem")
        }) {
            Text("Rozpocznij")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SelectableListPreview() {
    SygnalikTheme {
        SelectableList(
            bleText = "Połączono"
        )
    }
}