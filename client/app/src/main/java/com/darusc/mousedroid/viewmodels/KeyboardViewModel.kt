package com.darusc.mousedroid.viewmodels

import androidx.lifecycle.viewModelScope
import com.darusc.mousedroid.layouts.KeyboardLayout
import com.darusc.mousedroid.layouts.Keycode
import com.darusc.mousedroid.layouts.languages.KeyboardLayoutES
import com.darusc.mousedroid.layouts.languages.KeyboardLayoutFR
import com.darusc.mousedroid.layouts.languages.KeyboardLayoutRO
import com.darusc.mousedroid.layouts.languages.KeyboardLayoutUS
import com.darusc.mousedroid.mkinput.InputEvent
import com.darusc.mousedroid.networking.ConnectionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class KeyboardViewModel : BaseViewModel<KeyboardViewModel.State, KeyboardViewModel.Event>(State()) {

    sealed class Event : BaseViewModel.Event()
    class State : BaseViewModel.State()

    private val connectionManager = ConnectionManager.getInstance()
    private var repeatJob: Job? = null

    private val layoutMap: Map<String, Class<out KeyboardLayout>> = mapOf(
        KeyboardLayoutUS.NAME to KeyboardLayoutUS::class.java,
        KeyboardLayoutES.NAME to KeyboardLayoutES::class.java,
        KeyboardLayoutFR.NAME to KeyboardLayoutFR::class.java,
        KeyboardLayoutRO.NAME to KeyboardLayoutRO::class.java,
    )

    val layouts: Set<String>
        get() = layoutMap.keys

    var activeKeyboardLayout: KeyboardLayout = KeyboardLayoutUS()

    fun setKeyboardLayout(layout: String): Boolean {
        val layoutClass = layoutMap[layout]

        if (layoutClass != null) {
            try {
                activeKeyboardLayout = layoutClass.getDeclaredConstructor().newInstance()
                return true
            } catch (e: Exception) {
                return false
            }
        }

        return false
    }

    fun handleKeypress(chars: CharArray) {
        for (char in chars) {
            val mapping = activeKeyboardLayout.getMapping(char)
            if (mapping != null) {
                connectionManager.send(InputEvent.KeyPress(mapping))
            }
        }
    }

    /**
     * Sticky modifier bits (Shift/Ctrl/Alt/Win) for shortcuts like Ctrl+C.
     * Tap a modifier to arm it, tap the key to send the combo. Cleared after use.
     */
    var stickyModifier: Byte = Keycode.MOD_NONE
        private set

    fun toggleSticky(modifier: Byte) {
        stickyModifier = if ((stickyModifier.toInt() and modifier.toInt()) != 0) {
            (stickyModifier.toInt() and modifier.toInt().inv()).toByte()
        } else {
            (stickyModifier.toInt() or modifier.toInt()).toByte()
        }
    }

    fun clearSticky() {
        stickyModifier = Keycode.MOD_NONE
    }

    /** Send a raw PC keycode (Esc, arrows, F-keys, ...) with armed modifiers. */
    fun tapKey(code: Byte) {
        stopKeyRepeat()
        connectionManager.send(InputEvent.KeyPress(listOf(KeyboardLayout.Key(stickyModifier, code))))
        clearSticky()
    }

    /** Send a character from the active layout (letters, digits, punctuation). */
    fun tapChar(char: Char) {
        val mapping = activeKeyboardLayout.getMapping(char) ?: return
        val keys = if (stickyModifier == Keycode.MOD_NONE) {
            mapping
        } else {
            mapping.map {
                it.copy(modifier = (it.modifier.toInt() or stickyModifier.toInt()).toByte())
            }
        }
        connectionManager.send(InputEvent.KeyPress(keys))
        clearSticky()
    }

    /**
     * Held-key repeat entry point: Backspace and arrows keep firing while the
     * button is held, so holding them behaves like a real PC keyboard.
     * Tap first so a quick tap still sends exactly one keypress.
     * Repeat keeps the modifiers armed at press time; a second press of the
     * same key extends them until the finger is lifted.
     */
    fun startKeyRepeat(code: Byte) {
        stopKeyRepeat()
        sendKeyWithSticky(code)
        val armed = stickyModifier
        repeatJob = viewModelScope.launch {
            delay(400)
            while (isActive) {
                connectionManager.send(InputEvent.KeyPress(listOf(KeyboardLayout.Key(armed, code))))
                delay(50)
            }
        }
    }

    private fun sendKeyWithSticky(code: Byte) {
        connectionManager.send(InputEvent.KeyPress(listOf(KeyboardLayout.Key(stickyModifier, code))))
    }

    fun stopKeyRepeat() {
        repeatJob?.cancel()
        repeatJob = null
        clearSticky()
    }
}