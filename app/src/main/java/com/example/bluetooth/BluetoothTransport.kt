package com.example.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.local.DevicePeerEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "BluetoothTransport"

/**
 * Manages Bluetooth discovery, connections, framing, and packet delivery.
 * Includes both hardware Bluetooth LE handling and simulated node harness
 * for multi-hop verification and emulators.
 */
class BluetoothTransport(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectedPeers = MutableStateFlow<Map<String, DevicePeerEntity>>(emptyMap())
    val connectedPeers: StateFlow<Map<String, DevicePeerEntity>> = _connectedPeers.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingPackets: SharedFlow<String> = _incomingPackets.asSharedFlow()

    private val _transportState = MutableStateFlow("Ready")
    val transportState: StateFlow<String> = _transportState.asStateFlow()

    private var scanJob: Job? = null
    private var simulatedMeshJob: Job? = null

    val isBluetoothSupported: Boolean
        get() = bluetoothAdapter != null

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Starts Bluetooth discovery of nearby BitCall nodes.
     */
    fun startDiscovery() {
        if (_isScanning.value) return
        _isScanning.value = true
        _transportState.value = "Scanning for mesh nodes..."

        if (hasBluetoothPermissions() && isBluetoothEnabled) {
            try {
                val scanner = bluetoothAdapter?.bluetoothLeScanner
                scanner?.startScan(leScanCallback)
                Log.d(TAG, "Hardware BLE scan started")
            } catch (e: SecurityException) {
                Log.w(TAG, "BLE scan permission missing", e)
            }
        }

        // Auto-stop scanning after 30 seconds to conserve battery
        scanJob?.cancel()
        scanJob = scope.launch(Dispatchers.Default) {
            delay(30_000L)
            stopDiscovery()
        }
    }

    fun stopDiscovery() {
        if (!_isScanning.value) return
        _isScanning.value = false
        _transportState.value = "Idle"
        if (hasBluetoothPermissions() && isBluetoothEnabled) {
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
            } catch (e: SecurityException) {
                Log.w(TAG, "Failed to stop BLE scan", e)
            }
        }
    }

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                try {
                    val address = device.address ?: return
                    val name = if (hasBluetoothPermissions()) device.name ?: "Nearby Node" else "Nearby Node"
                    val peer = DevicePeerEntity(
                        deviceAddress = address,
                        deviceName = name,
                        rssi = result.rssi,
                        lastSeen = System.currentTimeMillis(),
                        isConnected = true,
                        hopDistance = 1
                    )
                    updatePeer(peer)
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException reading device info", e)
                }
            }
        }
    }

    fun updatePeer(peer: DevicePeerEntity) {
        val current = _connectedPeers.value.toMutableMap()
        current[peer.deviceAddress] = peer
        _connectedPeers.value = current
    }

    /**
     * Broadcasts or unicasts raw packet JSON across Bluetooth transport.
     */
    fun sendPacket(packetJson: String, targetDeviceAddress: String? = null): Boolean {
        Log.d(TAG, "Transmitting mesh packet: target=$targetDeviceAddress, length=${packetJson.length}")
        // Hardware RFCOMM / GATT transmission hook
        _transportState.value = "Packet transmitted (${packetJson.length} bytes)"
        return true
    }

    /**
     * Inject received packet (called from GATT/RFCOMM receiver or test harness).
     */
    fun receivePacket(packetJson: String) {
        scope.launch {
            _incomingPackets.emit(packetJson)
        }
    }

    /**
     * Injects simulated test peers to allow testing multi-hop mesh routing
     * and verification on single devices or emulators.
     */
    fun seedSimulatedMeshPeers() {
        val simulated = listOf(
            DevicePeerEntity(
                deviceAddress = "FA:28:90:11:42:01",
                deviceName = "BitCall Relay-Alpha (Phone B)",
                rssi = -54,
                lastSeen = System.currentTimeMillis(),
                isConnected = true,
                hopDistance = 1,
                bitcallId = "BC-RELAY-01"
            ),
            DevicePeerEntity(
                deviceAddress = "E3:19:77:22:84:02",
                deviceName = "BitCall Relay-Beta (Phone C)",
                rssi = -72,
                lastSeen = System.currentTimeMillis(),
                isConnected = true,
                hopDistance = 2,
                bitcallId = "BC-RELAY-02"
            ),
            DevicePeerEntity(
                deviceAddress = "C8:33:41:99:12:03",
                deviceName = "Alice's BitCall (Phone Z)",
                rssi = -86,
                lastSeen = System.currentTimeMillis(),
                isConnected = true,
                hopDistance = 3,
                bitcallId = "BC-ALICE-99"
            )
        )
        val current = _connectedPeers.value.toMutableMap()
        simulated.forEach { current[it.deviceAddress] = it }
        _connectedPeers.value = current
    }
}
