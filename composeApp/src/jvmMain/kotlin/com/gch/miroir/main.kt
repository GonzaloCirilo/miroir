package com.gch.miroir

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.gch.miroir.infrastructure.DeviceManagerImpl
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

fun main() {
    val usbDeviceManager = DeviceManagerImpl()
    application {
        val coroutineScope = rememberCoroutineScope()
        Window(
            onCloseRequest = ::exitApplication,
            title = "miroir",
        ) {
            val devices by usbDeviceManager.getDevices().collectAsState(initial = emptyList())
            App(
                devices.toImmutableList(),
                onEvent = { event ->
                    when (event) {
                        Event.OnRefresh -> {
                            coroutineScope.launch {
                                usbDeviceManager.fetchDevices()
                            }
                        }
                    }
                }
            )
        }
    }
}