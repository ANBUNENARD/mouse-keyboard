package com.darusc.mousedroid.viewmodels

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.darusc.mousedroid.getDeviceDetails
import com.darusc.mousedroid.networking.Connection
import com.darusc.mousedroid.networking.ConnectionManager

/**
 * Device list is LAN only (saved WIFI devices).
 */
class DeviceListViewModel(
    private val sharedPreferences: SharedPreferences?
): BaseViewModel<DeviceListViewModel.State, DeviceListViewModel.Event>(State(emptyList())) {

    sealed class Event: BaseViewModel.Event()
    data class State(val devices: List<Pair<String, String>>): BaseViewModel.State()

    private val connectionManager = ConnectionManager.getInstance()

    class Factory: ViewModelProvider.Factory {

        private val sharedPreferences: SharedPreferences?

        /**
         * Create the viewmodel for wifi mode
         * @param sharedPreferences The shared preferences containing the stored WIFI devices
         */
        constructor(sharedPreferences: SharedPreferences?) {
            this.sharedPreferences = sharedPreferences
        }

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if(modelClass.isAssignableFrom(DeviceListViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return DeviceListViewModel(sharedPreferences) as T
            }
            throw IllegalArgumentException("Unknown viewmodel class")
        }
    }

    init {
        updateState()
    }

    fun add(name: String, address: String) {
        sharedPreferences?.edit { putString(name, address) }
        updateState()
    }

    fun remove(name: String) {
        sharedPreferences?.edit { remove(name) }
        updateState()
    }

    fun onDeviceClick(context: Context, name: String, address: String) {
        val details = getDeviceDetails(context, Connection.Mode.WIFI)
        connectionManager.connectWIFI(address, 6969, details)
    }

    private fun updateState() {
        val devices = mutableListOf<Pair<String, String>>()
        sharedPreferences!!.all.let {
            for((name, address) in it) {
                devices.add(Pair(name, address as String))
            }
        }
        setState(State(devices))
    }
}
