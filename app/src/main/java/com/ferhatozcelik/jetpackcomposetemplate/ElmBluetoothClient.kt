package com.ferhatozcelik.jetpackcomposetemplate

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class ElmBluetoothClient(context: Context) {

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var socket: BluetoothSocket? = null
    var session: Elm327Session? = null
        private set

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Result<Elm327Session> = withContext(Dispatchers.IO) {
        try {
            bluetoothAdapter?.cancelDiscovery()

            val btSocket = try {
                device.createRfcommSocketToServiceRecord(SPP_UUID).apply { connect() }
            } catch (_: IOException) {
                // Fallback for clone ELM327 chips with malformed SDP records
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                (method.invoke(device, 1) as BluetoothSocket).apply { connect() }
            }

            socket = btSocket
            val elmSession = Elm327Session(btSocket.inputStream, btSocket.outputStream)
            session = elmSession

            elmSession.initializeForFordCan(targetHeader = "7E0")
            Result.success(elmSession)
        } catch (e: Exception) {
            disconnect()
            Result.failure(e)
        }
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (_: IOException) {
        } finally {
            socket = null
            session = null
        }
    }
}
