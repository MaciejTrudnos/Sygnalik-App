package com.maciejtrudnos.sygnalik

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.maciejtrudnos.sygnalik.ui.theme.SygnalikTheme
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
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
    var navText by mutableStateOf("")

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

            lifecycleScope.launch {
                service?.navText?.collect { text ->
                    navText = text
                    Log.d("MainActivity", "Nav Text updated: $text")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("MainActivity", "onServiceDisconnected called")
            bleManager?.disconnect()  // ← DODAJ
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
                            bleText = bleText,
                            navText = navText,
                            onStartNavigation = { lat, lon ->
                                service?.startNavigation(lat, lon)
                            },
                            onStopNavigation = {
                                service?.stopNavigation()
                            },
                            onExitApp = {
                                service?.shutdown()
                                finishAndRemoveTask()
                            }
                        )
                    }
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        super.onDestroy()
        bleManager?.disconnect()
        unbindService(connection)
    }
}

@Composable
fun SelectableList(
    bleText: String,
    navText: String,
    onStartNavigation: (Double, Double) -> Unit,
    onStopNavigation: () -> Unit,
    onExitApp: () -> Unit
) {
    val searchClient = remember { OkHttpClient() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun searchNominatim(query: String, onResult: (String?) -> Unit) {
        val url = "https://nominatim.openstreetmap.org/search?" +
                "q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                "&format=jsonv2&limit=5"

        val nominatimUserAgent = BuildConfig.NOMINATIM_USER_AGENT

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", nominatimUserAgent)
            .build()

        searchClient.newCall(request).enqueue(object : Callback {
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
        var isSearching by remember { mutableStateOf(false) }
        var searchGeneration by remember { mutableIntStateOf(0) }

        LaunchedEffect(inputText) {
            val query = inputText.trim()
            if (query.length < 3 || query == selectedItem?.display_name) {
                return@LaunchedEffect
            }
            selectedItem = null
            delay(1000)
            val generation = ++searchGeneration
            isSearching = true
            searchNominatim(query) { jsonResponse ->
                if (generation == searchGeneration) {
                    isSearching = false
                    if (jsonResponse != null) {
                        Log.d("NOMINATIM", jsonResponse)
                        searchResult = parsePlacesWithGson(jsonResponse) ?: emptyList()
                    } else {
                        Log.d("NOMINATIM", "quest failed or blocked")
                    }
                }
            }
        }

        Text(text = "Status: $bleText")

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Miejsce docelowe") }
        )

        if (isSearching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(searchResult) { item ->
                val selected = item == selectedItem
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable {
                            selectedItem = item
                            inputText = item.display_name
                            searchResult = emptyList()
                            keyboardController?.hide()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = item.display_name)
                }
            }
        }

        if (navText.isNotEmpty()) {
            Text(text = navText)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = {
                val destLat = selectedItem?.lat?.toDoubleOrNull()
                val destLon = selectedItem?.lon?.toDoubleOrNull()
                if (destLat != null && destLon != null) {
                    onStartNavigation(destLat, destLon)
                } else {
                    Log.d("NAV-RUN", "No destination selected")
                }
            }) {
                Text("Rozpocznij")
            }

            Button(
                onClick = onStopNavigation,
                enabled = navText.isNotEmpty()
            ) {
                Text(stringResource(R.string.nav_stop))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onExitApp,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text(stringResource(R.string.app_exit))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SelectableListPreview() {
    SygnalikTheme {
        SelectableList(
            bleText = "Połączono",
            navText = "Skręć w prawo\nZa 300 m\nDo celu: 4.2 km",
            onStartNavigation = { _, _ -> },
            onStopNavigation = { },
            onExitApp = { }
        )
    }
}