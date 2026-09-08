package com.darusc.mousedroid.viewmodels

import android.view.View
import com.darusc.mousedroid.R
import com.darusc.mousedroid.mkinput.InputEvent
import com.darusc.mousedroid.networking.ConnectionManager

class TouchpadViewModel: BaseViewModel<TouchpadViewModel.State, TouchpadViewModel.Event>(State()) {

    class State: BaseViewModel.State()
    sealed class Event: BaseViewModel.Event()

    private val connectionManager = ConnectionManager.getInstance()

    fun sendMouseEvent(event: InputEvent) {
        connectionManager.send(event)
    }

    fun onMouseButtonClick(view: View) {
        when(view.id) {
            R.id.btnLeftClick -> connectionManager.send(InputEvent.MouseClick(InputEvent.MouseButton.LEFT))
            R.id.btnRightClick -> connectionManager.send(InputEvent.MouseClick(InputEvent.MouseButton.RIGHT))
        }
    }
}
