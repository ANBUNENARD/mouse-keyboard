package com.darusc.mousedroid.viewmodels

import android.content.Context
import com.darusc.mousedroid.getDeviceDetails
import com.darusc.mousedroid.networking.Connection
import com.darusc.mousedroid.networking.ConnectionManager
import com.darusc.mousedroid.networking.hasUsbConnection
import com.darusc.mousedroid.networking.hasWifiConnection

class ConnectionViewModel :
    BaseViewModel<ConnectionViewModel.State, ConnectionViewModel.Event>(State.Idle),
    ConnectionManager.ConnectionStateCallback {

    sealed class State : BaseViewModel.State() {
        object Idle : State()
        data class Connecting(val message: String) : State()
        data class Connected(val connectionMode: Connection.Mode, val hostName: String) : State()
    }

    sealed class Event : BaseViewModel.Event() {
        object NavigateToInput : Event()
        object NavigateToMain : Event()
        data class NavigateToDeviceList(val mode: Connection.Mode) : Event()

        data class ConnectionFailed(val connectionMode: Connection.Mode, val reason: String? = null) : Event()
        data class ConnectionDisconnected(val connectionMode: Connection.Mode, val hostName: String) : Event()
    }

    private val connectionManager = ConnectionManager.getInstance(this)

    override fun onConnectionInitiated(mode: Connection.Mode) {
        if (state.value is State.Idle) {
            setState(State.Connecting("Connecting..."))
        }
    }

    override fun onConnectionSuccessful(connectionMode: Connection.Mode, hostName: String) {
        setState(State.Connected(connectionMode, hostName))
        sendEvent(Event.NavigateToInput)
    }

    override fun onConnectionFailed(connectionMode: Connection.Mode, reason: String?) {
        setState(State.Idle)
        sendEvent(Event.ConnectionFailed(connectionMode, reason))
    }

    override fun onDisconnected(connectionMode: Connection.Mode, hostName: String) {
        // Hardware link was lost (e.g server was turned off)
        setState(State.Idle)
        sendEvent(Event.ConnectionDisconnected(connectionMode, hostName))
        sendEvent(Event.NavigateToMain)
    }

    /**
     * Start server mode with autodetect
     */
    fun startServerMode(context: Context) {
        if (hasUsbConnection(context)) {
            // If app starts in server mode, check if there is a USB connection
            // If it is attempt to connect in USB mode (over ADB)
            connectionManager.connectUSB(6969, getDeviceDetails(context, Connection.Mode.USB))
        } else if (hasWifiConnection(context)) {
            // Otherwise if it has an active wifi connection, go to the
            // device list fragment to allow the user to choose the device to connect to
            sendEvent(Event.NavigateToDeviceList(Connection.Mode.WIFI))
        }
    }

    /**
     * Should be called only when the user requests a manual disconnect
     */
    fun disconnect() {
        connectionManager.disconnect()
        setState(State.Idle)
        sendEvent(Event.NavigateToMain)
    }
}
