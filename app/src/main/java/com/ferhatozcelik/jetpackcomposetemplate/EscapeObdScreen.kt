package com.ferhatozcelik.jetpackcomposetemplate

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EscapeObdScreen(viewModel: EscapeObdViewModel) {
    val state by viewModel.uiState.collectAsState()
    var rawInput by remember { mutableStateOf("") }
    var startDid by remember { mutableStateOf("0100") }
    var endDid by remember { mutableStateOf("01FF") }
    var showDeviceDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            listState.animateScrollToItem(state.logs.lastIndex)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) {
            viewModel.refreshPairedDevices()
            showDeviceDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("05 Escape UDS/OBD Lab", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = if (state.isConnected)
                                "Connected • Header: ${state.activeHeader} • Session: ${state.activeSession ?: "Default"}"
                            else "Disconnected",
                            fontSize = 12.sp,
                            color = if (state.isConnected) Color(0xFF4CAF50) else Color.Gray
                        )
                    }
                },
                actions = {
                    if (state.isConnected) {
                        Button(
                            onClick = { viewModel.disconnect() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                        ) { Text("Disconnect") }
                    } else {
                        Button(
                            onClick = {
                                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                                } else {
                                    arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                                permissionLauncher.launch(perms)
                            },
                            enabled = !state.isConnecting
                        ) {
                            Text(if (state.isConnecting) "Connecting..." else "Connect ELM")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val modules = listOf("7E0" to "PCM", "760" to "ABS", "720" to "IC", "761" to "4WD")
                modules.forEach { (header, label) ->
                    FilterChip(
                        selected = state.activeHeader == header,
                        onClick = { viewModel.setTargetModule(header) },
                        enabled = state.isConnected,
                        label = { Text("$label ($header)", fontSize = 12.sp) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.probeMode10Sessions() },
                    enabled = state.isConnected && !state.isScanning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Probe Mode $10", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { viewModel.sendRawCommand("0801") },
                    enabled = state.isConnected && !state.isScanning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Mode $08 EVAP", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { viewModel.sendRawCommand("22F190") },
                    enabled = state.isConnected && !state.isScanning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Read VIN", fontSize = 12.sp)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mode $22 DID Discovery Sweep", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = startDid,
                            onValueChange = { if (it.length <= 4) startDid = it.uppercase() },
                            label = { Text("Start Hex") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = endDid,
                            onValueChange = { if (it.length <= 4) endDid = it.uppercase() },
                            label = { Text("End Hex") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        if (state.isScanning) {
                            Button(
                                onClick = { viewModel.stopDidSweep() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) { Text("Stop") }
                        } else {
                            Button(
                                onClick = { viewModel.startDidSweep(startDid, endDid) },
                                enabled = state.isConnected
                            ) { Text("Sweep") }
                        }
                    }
                    if (state.isScanning) {
                        LinearProgressIndicator(
                            progress = { state.scanProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF121212), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.logs) { log ->
                        val color = when {
                            log.isError -> Color(0xFFFF5252)
                            log.direction == "TX" -> Color(0xFF64B5F6)
                            log.direction == "RX" -> Color(0xFF81C784)
                            else -> Color(0xFFFFD54F)
                        }
                        Text(
                            text = "[${log.direction}] ${log.message}",
                            color = color,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
                TextButton(
                    onClick = { viewModel.clearLogs() },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text("Clear", color = Color.Gray, fontSize = 11.sp)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it.uppercase() },
                    placeholder = { Text("Raw Hex (e.g. 220200 or 1087)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = {
                        viewModel.sendRawCommand(rawInput)
                        rawInput = ""
                    },
                    enabled = state.isConnected && rawInput.isNotBlank()
                ) {
Use code with caution.
Text("Send")
}
}
}
}
if (showDeviceDialog) {
AlertDialog(
onDismissRequest = { showDeviceDialog = false },
title = { Text("Select ELM327 Adapter") },
text = {
if (state.pairedDevices.isEmpty()) {
Text("No paired Bluetooth devices found. Pair your ELM327 in Android Bluetooth settings first.")
} else {
LazyColumn {
items(state.pairedDevices) { device ->
TextButton(
onClick = {
showDeviceDialog = false
viewModel.connect(device)
},
modifier = Modifier.fillMaxWidth()
) {
Text("${device.name ?: "Unknown"} (${device.address})")
}
}
}
}
},
confirmButton = {
TextButton(onClick = { showDeviceDialog = false }) { Text("Cancel") }
}
)
}
}

---

### Step 4: Replace `MainActivity.kt`
1. Open the existing **`MainActivity.kt`** inside `app/src/main/java/com/ferhatozcelik/jetpackcomposetemplate/`.
2. Tap the **Pencil (Edit)** icon, select all, replace it with this code, and tap **Commit changes**:

```kotlin
package com.ferhatozcelik.jetpackcomposetemplate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val btClient = ElmBluetoothClient(applicationContext)
        val viewModel = EscapeObdViewModel(btClient)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                EscapeObdScreen(viewModel = viewModel)
            }
        }
    }
}
