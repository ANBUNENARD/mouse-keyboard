package com.darusc.mousedroid.fragments

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.darusc.mousedroid.R
import com.darusc.mousedroid.databinding.FragmentKeyboardBinding
import com.darusc.mousedroid.layouts.Keycode
import com.darusc.mousedroid.viewmodels.KeyboardViewModel
import android.widget.Button

/**
 * Full PC keyboard screen matching the desktop on-screen keyboard:
 * number row, Tab/QWERTY rows, Caps/Shift rows, Fn/Ctrl/Win/Alt
 * bottom row, plus the navigation column.
 *
 * Every key fires on finger-down (ACTION_DOWN) instead of on click-release,
 * so typing feels instant. Each button owns its own touch listener, so
 * multi-touch (holding Shift while tapping a letter) works.
 * Shift/Ctrl/Alt/Win are sticky: tap one to arm it, tap a key for the combo.
 * Fn is a separate toggle: while armed, 1..= send F1..F12.
 */
class Keyboard : Fragment() {

    private lateinit var binding: FragmentKeyboardBinding

    private val viewModel: KeyboardViewModel by activityViewModels()

    private var fnArmed = false
    private var previousOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    private val modifierMods = mapOf(
        R.id.k_shift to Keycode.MOD_LEFT_SHIFT,
        R.id.k_shift_r to Keycode.MOD_LEFT_SHIFT,
        R.id.k_ctrl to Keycode.MOD_LEFT_CTRL,
        R.id.k_ctrl_r to Keycode.MOD_LEFT_CTRL,
        R.id.k_alt to Keycode.MOD_LEFT_ALT,
        R.id.k_alt_r to Keycode.MOD_LEFT_ALT,
        R.id.k_win to Keycode.MOD_LEFT_GUI
    )

    private val charKeys = mapOf(
        R.id.k_grave to '`',
        R.id.k_1 to '1', R.id.k_2 to '2', R.id.k_3 to '3',
        R.id.k_4 to '4', R.id.k_5 to '5', R.id.k_6 to '6',
        R.id.k_7 to '7', R.id.k_8 to '8', R.id.k_9 to '9',
        R.id.k_0 to '0', R.id.k_minus to '-', R.id.k_equal to '=',
        R.id.k_backslash to '\\',
        R.id.k_q to 'q', R.id.k_w to 'w', R.id.k_e to 'e',
        R.id.k_r to 'r', R.id.k_t to 't', R.id.k_y to 'y',
        R.id.k_u to 'u', R.id.k_i to 'i', R.id.k_o to 'o',
        R.id.k_p to 'p', R.id.k_lbracket to '[', R.id.k_rbracket to ']',
        R.id.k_a to 'a', R.id.k_s to 's', R.id.k_d to 'd',
        R.id.k_f to 'f', R.id.k_g to 'g', R.id.k_h to 'h',
        R.id.k_j to 'j', R.id.k_k to 'k', R.id.k_l to 'l',
        R.id.k_semi to ';', R.id.k_quote to '\'',
        R.id.k_z to 'z', R.id.k_x to 'x', R.id.k_c to 'c',
        R.id.k_v to 'v', R.id.k_b to 'b', R.id.k_n to 'n',
        R.id.k_m to 'm', R.id.k_comma to ',', R.id.k_dot to '.',
        R.id.k_slash to '/'
    )

    private val codeKeys = mapOf(
        R.id.k_esc to Keycode.KEY_ESC,
        R.id.k_caps to Keycode.KEY_CAPS_LOCK,
        R.id.k_home to Keycode.KEY_HOME,
        R.id.k_end to Keycode.KEY_END,
        R.id.k_pgup to Keycode.KEY_PAGE_UP,
        R.id.k_pgdn to Keycode.KEY_PAGE_DOWN,
        R.id.k_ins to Keycode.KEY_INSERT,
        R.id.k_prtsc to Keycode.KEY_PRByte_SCREEN,
        R.id.k_pause to Keycode.KEY_PAUSE,
        R.id.k_scrlk to Keycode.KEY_SCROLL_LOCK,
        R.id.k_del to Keycode.KEY_DELETE,
        R.id.k_backspace to Keycode.KEY_BACKSPACE,
        R.id.k_tab to Keycode.KEY_TAB,
        R.id.k_enter to Keycode.KEY_ENTER,
        R.id.k_space to Keycode.KEY_SPACE,
        R.id.k_left to Keycode.KEY_LEFT,
        R.id.k_up to Keycode.KEY_UP,
        R.id.k_down to Keycode.KEY_DOWN,
        R.id.k_right to Keycode.KEY_RIGHT,
        R.id.k_mvup to Keycode.KEY_UP,
        R.id.k_mvdn to Keycode.KEY_DOWN,
        R.id.k_menu to Keycode.KEY_APPLICATION,
        R.id.k_options to Keycode.KEY_MENU,
        R.id.k_help to Keycode.KEY_F1
    )

    /** Fn layer: number row (plus - =) becomes F1..F12 while Fn is armed. */
    private val fnKeys = mapOf(
        R.id.k_1 to Keycode.KEY_F1, R.id.k_2 to Keycode.KEY_F2,
        R.id.k_3 to Keycode.KEY_F3, R.id.k_4 to Keycode.KEY_F4,
        R.id.k_5 to Keycode.KEY_F5, R.id.k_6 to Keycode.KEY_F6,
        R.id.k_7 to Keycode.KEY_F7, R.id.k_8 to Keycode.KEY_F8,
        R.id.k_9 to Keycode.KEY_F9, R.id.k_0 to Keycode.KEY_F10,
        R.id.k_minus to Keycode.KEY_F11, R.id.k_equal to Keycode.KEY_F12
    )

    private val repeatCodes = mapOf(
        R.id.k_backspace to Keycode.KEY_BACKSPACE,
        R.id.k_left to Keycode.KEY_LEFT,
        R.id.k_up to Keycode.KEY_UP,
        R.id.k_down to Keycode.KEY_DOWN,
        R.id.k_right to Keycode.KEY_RIGHT,
        R.id.k_mvup to Keycode.KEY_UP,
        R.id.k_mvdn to Keycode.KEY_DOWN,
        R.id.k_del to Keycode.KEY_DELETE,
        R.id.k_space to Keycode.KEY_SPACE
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_keyboard, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Typing needs width: rotate to landscape on entry, restore on exit.
        previousOrientation = requireActivity().requestedOrientation
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val allIds = modifierMods.keys + charKeys.keys + codeKeys.keys + setOf(R.id.k_fn)
        for (id in allIds) {
            val button = view.findViewById<View>(id) ?: continue
            if (!button.isEnabled) continue
            // Fire on finger-down for zero perceived lag; every button
            // handles its own touch stream so chords (Shift + letter)
            // and fast typing never block each other.
            button.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onKeyDown(v.id)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        onKeyUp(v.id)
                        v.performClick()
                        true
                    }
                    else -> true
                }
            }
            // Accessibility click hook; actual sending happens on ACTION_DOWN.
            button.setOnClickListener { }
        }
        refreshStickyUI()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopKeyRepeat()
    }

    override fun onDestroyView() {
        requireActivity().requestedOrientation = previousOrientation
        super.onDestroyView()
    }

    private fun onKeyDown(id: Int) {
        if (id == R.id.k_fn) {
            fnArmed = !fnArmed
            refreshStickyUI()
            return
        }
        val mod = modifierMods[id]
        if (mod != null) {
            viewModel.toggleSticky(mod)
            refreshStickyUI()
            return
        }
        if (fnArmed) {
            val fnCode = fnKeys[id]
            if (fnCode != null) {
                val repeatCode = repeatCodes[id]
                if (repeatCode != null && repeatCode == fnCode) {
                    viewModel.startKeyRepeat(fnCode)
                } else {
                    viewModel.tapKey(fnCode)
                }
                fnArmed = false
                refreshStickyUI()
                return
            }
        }
        val repeatCode = repeatCodes[id]
        if (repeatCode != null) {
            // Long-press and hold repeats like a physical keyboard;
            // releasing stops the repeat.
            viewModel.startKeyRepeat(repeatCode)
            refreshStickyUI()
            return
        }
        val char = charKeys[id]
        if (char != null) {
            viewModel.tapChar(char)
        } else {
            codeKeys[id]?.let { viewModel.tapKey(it) }
        }
        refreshStickyUI()
    }

    private fun onKeyUp(id: Int) {
        // Only repeat keys own a repeat job. Anything else (notably the
        // Shift/Ctrl/Alt/Win modifier keys) must leave the sticky state
        // alone here, or arming a shortcut would instantly disarm it.
        if (repeatCodes.containsKey(id)) {
            viewModel.stopKeyRepeat()
        }
        refreshStickyUI()
    }

    private fun refreshStickyUI() {
        val root = view ?: return
        val activeColor = ContextCompat.getColor(requireContext(), R.color.stickyActive)
        val idleColor = ContextCompat.getColor(requireContext(), R.color.white)
        for ((id, mod) in modifierMods) {
            val button = root.findViewById<Button>(id) ?: continue
            val active = (viewModel.stickyModifier.toInt() and mod.toInt()) != 0
            button.setTextColor(if (active) activeColor else idleColor)
        }
        root.findViewById<Button>(R.id.k_fn)?.let {
            it.setTextColor(if (fnArmed) activeColor else idleColor)
        }
    }
}
