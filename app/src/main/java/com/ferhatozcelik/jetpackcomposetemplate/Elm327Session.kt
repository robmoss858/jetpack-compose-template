kotlin
package com.ferhatozcelik.jetpackcomposetemplate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.io.OutputStream

class Elm327Session(
    private val input: InputStream,
    private val output: OutputStream
) {
    private val mutex = Mutex()

    suspend fun sendCommand(cmd: String, timeoutMs: Long = 2500L): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            withTimeout(timeoutMs) {
                while (input.available() > 0) input.read()

                output.write("$cmd\r".toByteArray(Charsets.US_ASCII))
                output.flush()

                val response = StringBuilder()
                val buffer = ByteArray(1024)
                while (true) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    val chunk = String(buffer, 0, bytesRead, Charsets.US_ASCII)
                    response.append(chunk)
                    if (response.contains(">")) break
                }

                response.toString()
                    .replace(">", "")
                    .trim()
            }
        }
    }

    suspend fun initializeForFordCan(targetHeader: String = "7E0") {
        sendCommand("AT Z", timeoutMs = 3500L)
        val initCommands = listOf(
            "AT E0",
            "AT L0",
            "AT S0",
            "AT H1",
            "AT CAF1",
            "AT SP 6",
            "AT SH $targetHeader"
        )
        for (cmd in initCommands) {
            sendCommand(cmd)
        }
    }
}

        try {
            socket?.close()
        } catch (_: IOException) {
        } finally {
            socket = null
            session = null
        }
    }
}

            try {
                val resp = session.sendCommand(cleanCmd)
                val isNrc = resp.contains("7F") || resp.contains("ERROR") || resp.contains("NODATA")
                addLog("RX", resp, isError = isNrc)
            } catch (e: Exception) {
                addLog("SYS", "Timeout/Error: ${e.message}", isError = true)
            }
        }
    }

    fun probeMode10Sessions() {
        viewModelScope.launch {
            val session = btClient.session ?: return@launch
            addLog("SYS", "Probing Mode $10 sessions on ${_uiState.value.activeHeader}...")
            val candidates = listOf("87", "85", "03", "81")

            for (mode in candidates) {
                addLog("TX", "10$mode")
                val resp = session.sendCommand("10$mode")
                addLog("RX", resp, isError = resp.contains("7F"))

                if (resp.contains("50$mode")) {
                    _uiState.update { it.copy(activeSession = mode) }
                    startHeartbeat()
                    addLog("SYS", "Unlocked Session 0x$mode! TesterPresent active.")
                    return@launch
                }
            }
            addLog("SYS", "No extended session accepted.", isError = true)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                delay(2000)
                btClient.session?.sendCommand("3E")
            }
        }
    }

    fun startDidSweep(startHex: String, endHex: String) {
        val start = startHex.toIntOrNull(16) ?: return
        val end = endHex.toIntOrNull(16) ?: return
        if (start > end) return

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val session = btClient.session ?: return@launch
            val total = (end - start + 1).coerceAtLeast(1)
            _uiState.update { it.copy(isScanning = true, scanProgress = 0f) }
            addLog("SYS", "Scanning DIDs 0x$startHex -> 0x$endHex...")

            for ((index, did) in (start..end).withIndex()) {
                if (!isActive) break
                val hexDid = did.toString(16).uppercase().padStart(4, '0')
                val resp = session.sendCommand("22$hexDid")

                if (resp.contains("62$hexDid")) {
                    addLog("RX", "FOUND DID $hexDid -> $resp")
                }

                _uiState.update { it.copy(scanProgress = (index + 1) / total.toFloat()) }
                delay(35)
            }

            _uiState.update { it.copy(isScanning = false) }
            addLog("SYS", "DID Scan Complete.")
        }
    }

    fun stopDidSweep() {
        scanJob?.cancel()
        _uiState.update { it.copy(isScanning = false) }
        addLog("SYS", "Scan aborted by user.")
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    private fun addLog(dir: String, msg: String, isError: Boolean = false) {
        _uiState.update { state ->
            state.copy(logs = (state.logs + LogEntry(dir, msg, isError)).takeLast(250))
        }
.lastIndex)
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


