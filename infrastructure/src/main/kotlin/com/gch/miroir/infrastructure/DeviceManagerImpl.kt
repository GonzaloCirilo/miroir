package com.gch.miroir.infrastructure

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceManagerImpl: DeviceManager {
    private val devices = MutableStateFlow(listOf<Device>())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        scope.launch {
            fetchDevices()
        }
    }

    override fun getDevices(): Flow<List<Device>> = devices

    override suspend fun fetchDevices() {
        withContext(Dispatchers.IO) {
            devices.emit("adb devices -l".runCommand().orEmpty().lines().drop(1).mapNotNull { line ->
                parseDevice(line)
            })
        }
    }

    private suspend fun parseDevice(input: String): Device? {
        val parts = input.trim().split("\\s+".toRegex())

        if (parts.isEmpty() || input.isBlank()) return null

        val serialNumber = parts[0]
        val attributes = mutableMapOf<String, String>()

        // Parse key:value pairs
        parts.drop(2).forEach { part ->
            if (part.contains(":")) {
                val (key, value) = part.split(":", limit = 2)
                attributes[key] = value
            }
        }

        return Device(
            serialNumber = serialNumber,
            model = attributes["model"] ?: "",
            deviceName = attributes["device"] ?: "",
            manufacturer = "adb -s $serialNumber shell getprop ro.product.manufacturer".runCommand()?.replace("\n","").orEmpty()
        )
    }
}