package com.gch.miroir

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.staticCFunction
import kotlinx.coroutines.CoroutineScope
import platform.posix.memcpy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import win32.DBT_DEVTYP_DEVICEINTERFACE
import win32.DEV_BROADCAST_DEVICEINTERFACE_W

@OptIn(ExperimentalForeignApi::class)
actual class UsbEventMonitor() {
    private var windowHandle: HWND? = null
    private var notificationHandle: HDEVNOTIFY? = null
    private var _isMonitoring = false
    private val _usbEvents = MutableSharedFlow<UsbEvent>()
    private var monitoringJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    actual val usbEvents: SharedFlow<UsbEvent> = _usbEvents.asSharedFlow()


    // USB Device Interface GUID  {A5DCBF10-6530-11D2-901F-00C04FB951ED}
    private val USB_DEVICE_GUID_PTR = nativeHeap.alloc<GUID>().apply {
        Data1 = 0xA5DCBF10u
        Data2 = 0x6530u
        Data3 = 0x11D2u
        Data4[0] = 0x90u.toUByte()
        Data4[1] = 0x1Fu.toUByte()
        Data4[2] = 0x00u.toUByte()
        Data4[3] = 0xC0u.toUByte()
        Data4[4] = 0x4Fu.toUByte()
        Data4[5] = 0xB9u.toUByte()
        Data4[6] = 0x51u.toUByte()
        Data4[7] = 0xEDu.toUByte()
    }.ptr


    // Window procedure - non-capturing static function
    private val windowProc = staticCFunction<HWND?, UINT, WPARAM, LPARAM, LRESULT> { hwnd, msg, wParam, lParam ->
        when (msg.toInt()) {
            WM_DEVICECHANGE -> {
                // Handle device change events directly in callback
                // TODO: emit events to flow when instance callback support is added
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

    private fun initializeWindow(): Boolean {
        val className = "UsbMonitorWindow"

        memScoped {
            val classNameW = className.toWideString()
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
                lpszClassName = classNameW
                hIconSm = null
            }

            RegisterClassExW(wc.ptr)
        }

        val windowName = "USB Monitor"
        windowHandle = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = className,
            lpWindowName = windowName,
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
        return memScoped {
            val dbi = alloc<DEV_BROADCAST_DEVICEINTERFACE_W>()
            dbi.dbcc_size = sizeOf<DEV_BROADCAST_DEVICEINTERFACE_W>().convert()
            dbi.dbcc_devicetype = DBT_DEVTYP_DEVICEINTERFACE.toUInt()
            dbi.dbcc_reserved = 0u
            // Copy GUID using memcpy
            memcpy(
                dbi.dbcc_classguid.ptr,
                USB_DEVICE_GUID_PTR,
                sizeOf<GUID>().convert()
            )

            notificationHandle = RegisterDeviceNotificationW(
                hRecipient = windowHandle?.reinterpret(),
                NotificationFilter = dbi.ptr.reinterpret(),
                Flags = DEVICE_NOTIFY_WINDOW_HANDLE.toUInt()
            )

            notificationHandle != null
        }
    }

    private fun startMessageLoop() {
        monitoringJob = coroutineScope.launch(Dispatchers.Default) {
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
            PostMessageW(hwnd, WM_CLOSE.convert(), 0u, 0L)
            windowHandle = null
        }

        monitoringJob?.cancel()
        monitoringJob = null
    }

    actual fun dispose() {
        nativeHeap.free(USB_DEVICE_GUID_PTR.rawValue)
        coroutineScope.cancel()
    }

}

// Extension for string to wide string conversion
@OptIn(ExperimentalForeignApi::class)
private fun String.toWideString(): CPointer<WCHARVar> = memScoped {
    val len = this@toWideString.length
    val buffer = allocArray<WCHARVar>(len + 1)
    for (i in 0 until len) {
        buffer[i] = this@toWideString[i].code.toUShort()
    }
    buffer[len] = 0u
    buffer
}
