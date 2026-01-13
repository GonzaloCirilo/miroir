package com.gch.miroir

import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.staticCFunction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.posix.GUID
import platform.windows.CreateWindowExW
import platform.windows.DEVICE_NOTIFY_WINDOW_HANDLE
import platform.windows.DefWindowProcW
import platform.windows.DestroyWindow
import platform.windows.DispatchMessageW
import platform.windows.GetMessageW
import platform.windows.GetModuleHandleW
import platform.windows.HDEVNOTIFY
import platform.windows.HWND
import platform.windows.HWND_MESSAGE
import platform.windows.LPARAM
import platform.windows.LRESULT
import platform.windows.MSG
import platform.windows.PostMessageW
import platform.windows.PostQuitMessage
import platform.windows.RegisterClassExW
import platform.windows.RegisterDeviceNotificationW
import platform.windows.TranslateMessage
import platform.windows.UINT
import platform.windows.UnregisterDeviceNotification
import platform.windows.WCHARVar
import platform.windows.WM_CLOSE
import platform.windows.WM_DESTROY
import platform.windows.WM_DEVICECHANGE
import platform.windows.WNDCLASSEXW
import platform.windows.WPARAM
import win32.DBT_DEVICEARRIVAL
import win32.DBT_DEVICEREMOVECOMPLETE
import win32.DBT_DEVTYP_DEVICEINTERFACE
import win32.DEV_BROADCAST_DEVICEINTERFACE_W

@OptIn(ExperimentalForeignApi::class)
actual class UsbEventMonitor() {
    private var windowHandle: HWND? = null
    private var notificationHandle: HDEVNOTIFY? = null
    private var _isMonitoring = false
    private val _usbEvents = MutableSharedFlow<UsbEvent>()
    private var monitoringJob: Job? = null

    actual val usbEvents: SharedFlow<UsbEvent> = _usbEvents.asSharedFlow()

    // USB Device Interface GUID
    private val USB_DEVICE_GUID = nativeHeap.alloc<GUID>().apply {
        Data1 = 0xA5DCBF10u
        Data2 = 0x6530u
        Data3 = 0x11D2u
        Data4[0] = 0x90u.toByte()
        Data4[1] = 0x1Fu.toByte()
        Data4[2] = 0x00u.toByte()
        Data4[3] = 0xC0u.toByte()
        Data4[4] = 0x4Fu.toByte()
        Data4[5] = 0xB9u.toByte()
        Data4[6] = 0x51u.toByte()
        Data4[7] = 0xEDu.toByte()
    }

    // Window procedure
    private val windowProc = staticCFunction<HWND?, UINT, WPARAM, LPARAM, LRESULT> { hwnd, msg, wParam, lParam ->
        when (msg.toInt()) {
            WM_DEVICECHANGE -> {
                handleDeviceChangeMessage(wParam, lParam)
                0L
            }
            WM_CLOSE -> {
                DestroyWindow(hwnd)
                0L
            }
            WM_DESTROY -> {
                PostQuitMessage(0)
                0L
            }
            else -> DefWindowProcW(hwnd, msg, wParam, lParam)
        }
    }

    private fun handleDeviceChangeMessage(wParam: WPARAM, lParam: LPARAM) {
        val eventType = wParam.convert<UInt>()

        when (eventType.toInt()) {
            DBT_DEVICEARRIVAL -> {
                val devicePath = extractDevicePath(lParam)
                if (devicePath.isNotEmpty()) {
                    GlobalScope.launch {
                        _usbEvents.emit(UsbEvent.onDeviceDiscovered)
                    }
                }
            }
            DBT_DEVICEREMOVECOMPLETE -> {
                val devicePath = extractDevicePath(lParam)
                if (devicePath.isNotEmpty()) {
                    GlobalScope.launch {
                        _usbEvents.emit(UsbEvent.onDeviceDisconnected)
                    }
                }
            }
        }
    }

    private fun extractDevicePath(lParam: LPARAM): String {
        if (lParam == 0L) return ""

        val header = lParam.reinterpret<DEV_BROADCAST_HDR>().pointed

        return if (header.dbch_devicetype == DBT_DEVTYP_DEVICEINTERFACE) {
            try {
                val namePtr = lParam.plus(sizeOf<DEV_BROADCAST_DEVICEINTERFACE_W>().toLong())
                    .reinterpret<WCHARVar>()

                buildString {
                    var i = 0
                    while (true) {
                        val char = namePtr[i]
                        if (char.toInt() == 0) break
                        append(char.toInt().toChar())
                        i++
                    }
                }
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
    }

    private fun initializeWindow(): Boolean {
        val className = "UsbMonitorWindow"
        memScoped {
            val wc = alloc<WNDCLASSEXW>().apply {
                cbSize = sizeOf<WNDCLASSEXW>().convert()
                style = 0u
                lpfnWndProc = windowProc
                cbClsExtra = 0
                cbWndExtra = 0
                hInstance = GetModuleHandleW(null)
                hIcon = null
                hCursor = null
                hbrBackground = null
                lpszMenuName = null
                lpszClassName = className.wcstr.ptr
                hIconSm = null
            }

            RegisterClassExW(wc.ptr)
        }

        windowHandle = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = className.wcstr.ptr,
            lpWindowName = "USB Monitor".wcstr.ptr,
            dwStyle = 0u,
            X = 0, Y = 0, nWidth = 0, nHeight = 0,
            hWndParent = HWND_MESSAGE,
            hMenu = null,
            hInstance = GetModuleHandleW(null),
            lpParam = null
        )

        return windowHandle != null
    }

    private fun registerForNotifications(): Boolean {
        memScoped {
            val dbi = alloc<DEV_BROADCAST_DEVICEINTERFACE_W>().apply {
                dbcc_size = sizeOf<DEV_BROADCAST_DEVICEINTERFACE_W>().convert()
                dbcc_devicetype = DBT_DEVTYP_DEVICEINTERFACE
                dbcc_reserved = 0u
                dbcc_classguid = USB_DEVICE_GUID
                dbcc_name = 0u.toUShort()
            }

            notificationHandle = RegisterDeviceNotificationW(
                hRecipient = windowHandle,
                NotificationFilter = dbi.ptr,
                Flags = DEVICE_NOTIFY_WINDOW_HANDLE.toUInt()
            )

            return notificationHandle != null
        }
    }

    private fun startMessageLoop() {
        monitoringJob = GlobalScope.launch(Dispatchers.Default) {
            memScoped {
                val msg = alloc<MSG>()
                while (_isMonitoring && GetMessageW(msg.ptr, windowHandle, 0u, 0u) > 0) {
                    TranslateMessage(msg.ptr)
                    DispatchMessageW(msg.ptr)
                }
            }
        }
    }

    actual suspend fun startMonitoring(): Boolean  = withContext(Dispatchers.Default) {
        if (_isMonitoring) return@withContext true

        try {
            if (!initializeWindow()) {
                _usbEvents.emit(UsbEvent.onConnectFailed)
                return@withContext false
            }

            if (!registerForNotifications()) {
                _usbEvents.emit(UsbEvent.onConnectFailed)
                return@withContext false
            }

            _isMonitoring = true
            startMessageLoop()
            true
        } catch (e: Exception) {
            _usbEvents.emit(UsbEvent.onConnectFailed)
            false
        }
    }

    actual suspend fun stopMonitoring() {
        if (!_isMonitoring) return

        _isMonitoring = false

        notificationHandle?.let {
            UnregisterDeviceNotification(it)
            notificationHandle = null
        }

        windowHandle?.let { hwnd ->
            PostMessageW(hwnd, WM_CLOSE.toUInt(), 0u, 0L)
            windowHandle = null
        }

        monitoringJob?.cancel()
        monitoringJob = null
    }

}

// Extension for string conversion
@OptIn(ExperimentalForeignApi::class)
val String.wcstr: CValuesRef<WCHARVar>
    get() = this.encodeToByteArray().decodeToString().cstr.getPointer(MemScope()).reinterpret()