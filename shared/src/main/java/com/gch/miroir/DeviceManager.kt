package com.gch.miroir

import kotlinx.coroutines.flow.Flow

interface DeviceManager {
    fun getAvailableDevices(): Flow<List<Device>>
}