package com.gch.miroir

import kotlinx.coroutines.flow.SharedFlow

sealed class UsbEvent {
    data object onDeviceDiscovered : UsbEvent()
    data object onDeviceDisconnected : UsbEvent()
    data object onConnectFailed : UsbEvent()
}


expect class UsbEventMonitor {
    // Start observing Usb changes
    suspend fun startMonitoring(): Boolean

    suspend fun stopMonitoring()

    val usbEvents: SharedFlow<UsbEvent>
}