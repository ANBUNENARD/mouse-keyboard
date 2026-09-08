package com.darusc.mousedroid.networking

import com.darusc.mousedroid.mkinput.InputEvent
import kotlin.math.abs

/**
 * HID usage IDs used in translating the events into raw bytes for the HID protocol
 * are taken from the official usb specification
 *
 * https://www.usb.org/sites/default/files/documents/hut1_12v2.pdf
 */


/**
 * Socket events for the TCP/UDP communication
 */
private object RawSocketEvents {
    const val LCLICK: Byte = 0x01
    const val RCLICK: Byte = 0x02
    const val DOWN: Byte = 0x03
    const val UP: Byte = 0x04
    const val MOVE: Byte = 0x05
    const val SCROLL: Byte = 0x06
    const val KEYPRESS: Byte = 0x07
    const val SCROLL_H: Byte = 0x08
    const val ZOOM: Byte = 0x09
    const val MEDIA: Byte = 0x0A
}

/**
 * Transforms a media action into its corresponding bitmask.
 * The activated bit is the corresponding to the position
 * of the action in the descriptor listing
 */
private fun getMediaActionHIDBitmask(action: InputEvent.MediaAction): Short {
    return when(action) {
        InputEvent.MediaAction.FORWARD       -> 0b0000000000000001 // First in the descriptor listing
        InputEvent.MediaAction.REPLAY        -> 0b0000000000000010 // Second in the descriptor listing
        InputEvent.MediaAction.NEXT          -> 0b0000000000000100 // ...
        InputEvent.MediaAction.PREVIOUS      -> 0b0000000000001000
        InputEvent.MediaAction.PLAY_PAUSE    -> 0b0000000000010000
        InputEvent.MediaAction.VOLUME_MUTE   -> 0b0000000000100000
        InputEvent.MediaAction.VOLUME_UP     -> 0b0000000001000000
        InputEvent.MediaAction.VOLUME_DOWN   -> 0b0000000010000000
    }
}

private fun socketReport(vararg bytes: Byte): Array<ByteArray> {
    return arrayOf(bytes)
}

/**
 * Translate the input event to raw socket bytes
 */
fun InputEvent.toSocketReport(): Array<ByteArray> {
    return when (this) {
        is InputEvent.MouseMove -> {
            socketReport(RawSocketEvents.MOVE, this.dx.toByte(), this.dy.toByte())
        }

        is InputEvent.MouseScroll -> {
            // Socket protocol takes one tick per byte; split into single-tick
            // packets so Wi-Fi scroll stays proportional to finger speed.
            val packets = arrayListOf<ByteArray>()
            val vTicks = (this.dy / 10).coerceIn(-127, 127)
            val hTicks = if (vTicks != 0) 0 else (this.dx / 10).coerceIn(-127, 127)
            repeat(abs(vTicks)) {
                packets.add(byteArrayOf(RawSocketEvents.SCROLL, if (vTicks > 0) 10 else -10))
            }
            repeat(abs(hTicks)) {
                packets.add(byteArrayOf(RawSocketEvents.SCROLL_H, if (hTicks > 0) 10 else -10))
            }
            packets.toTypedArray()
        }

        is InputEvent.MouseClick -> {
            val code =
                if (this.button == InputEvent.MouseButton.LEFT) RawSocketEvents.LCLICK else RawSocketEvents.RCLICK
            socketReport(code)
        }

        is InputEvent.MouseDragState -> {
            val code = if (this.isDown) RawSocketEvents.DOWN else RawSocketEvents.UP
            socketReport(code)
        }

        is InputEvent.Zoom -> {
            socketReport(RawSocketEvents.ZOOM, this.scale.toByte())
        }

        is InputEvent.KeyPress -> {
            keyList.flatMap {
                listOf(
                    byteArrayOf(RawSocketEvents.KEYPRESS, it.code, it.modifier)
                )
            }.toTypedArray()
        }

        is InputEvent.NumpadKeyPress -> {
            socketReport(RawSocketEvents.KEYPRESS, this.key, 0x00)
        }

        is InputEvent.MediaEvent -> {
            val bitmask = getMediaActionHIDBitmask(this.action)
            socketReport(RawSocketEvents.MEDIA, (bitmask.toInt() and 0xFF).toByte())
        }

        is InputEvent.BatteryEvent -> { socketReport() }
    }
}
