package com.gch.miroir

class DeviceManagerImpl: DeviceManager {
    override fun getAvailableDevices(): String {
        return "adb devices".runCommand().orEmpty()
    }
}