package com.gch.miroir.infrastructure

import kotlinx.coroutines.flow.Flow

interface DeviceManager {
    fun getAvailableDevices(): Flow<List<Device>>
}