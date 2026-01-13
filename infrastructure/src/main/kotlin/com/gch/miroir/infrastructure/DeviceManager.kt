package com.gch.miroir.infrastructure

import kotlinx.coroutines.flow.Flow

interface DeviceManager {
    fun getDevices(): Flow<List<Device>>

    suspend fun fetchDevices()
}