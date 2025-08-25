package com.gch.miroir

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DeviceManagerImpl: DeviceManager {
    override fun getAvailableDevices(): Flow<List<Device>> = flow {
        emit("adb devices -l".runCommand().orEmpty().lines().drop(1).mapNotNull { line ->
            parseDevice(line)
        })
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