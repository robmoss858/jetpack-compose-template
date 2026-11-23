package com.ferhatozcelik.jetpackcomposetemplate

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LogEntry(
    val direction: String,
    val message: String,
    val isError: Boolean = false
)

data class ObdUiState(
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val activeHeader: String = "7E0",
    val activeSession: String? = null,
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val logs: List<LogEntry> = emptyList()
)

class EscapeObdViewModel(
    private val btClient: ElmBluetoothClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(ObdUiState())
    val uiState: StateFlow<ObdUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var heartbeatJob: Job? = null

    fun refreshPairedDevices() {
        _uiState.update { it.copy(pairedDevices = btClient.getPairedDevices()) }
    }

    fun connect(device: BluetoothDevice) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true) }
            addLog("SYS", "Connecting to ${device.name ?: device.address}...")

            val result = btClient.connect(device)
            result.onSuccess {
                _uiState.update { it.copy(isConnected = true, isConnecting = false) }
                addLog("SYS", "Connected & initialized for 2005 Escape HS-CAN (7E0)")
            }.onFailure { err ->
                _uiState.update { it.copy(isConnected = false, isConnecting = false) }
                addLog("SYS", "Connection failed: ${err.message}", isError = true)
            }
        }
    }

    fun disconnect() {
        scanJob?.cancel()
        heartbeatJob?.cancel()
        btClient.disconnect()
        _uiState.update {
            it.copy(isConnected = false, activeSession = null, isScanning = false)
        }
        addLog("SYS", "Disconnected")
    }

    fun setTargetModule(header: String) {
        viewModelScope.launch {
            val session = btClient.session ?: return@launch
            heartbeatJob?.cancel()
            val resp = session.sendCommand("AT SH $header")
            _uiState.update { it.copy(activeHeader = header, activeSession = null) }
            addLog("TX", "AT SH $header")
            addLog("RX", resp)
        }
    }

    fun sendRawCommand(rawHex: String) {
        val cleanCmd = rawHex.trim().uppercase()
        if (cleanCmd.isEmpty()) return

        viewModelScope.launch {
            val session = btClient.session ?: return@launch
            addLog("TX", cleanCmd)
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
    }
}
