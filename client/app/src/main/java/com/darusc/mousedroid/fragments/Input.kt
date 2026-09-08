package com.darusc.mousedroid.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.darusc.mousedroid.R
import com.darusc.mousedroid.databinding.FragmentInputBinding
import com.darusc.mousedroid.mkinput.KeyboardInputWatcher
import com.darusc.mousedroid.viewmodels.ConnectionViewModel
import com.darusc.mousedroid.viewmodels.KeyboardViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Input host fragment. Keyboard, Mouse and Combo are separated screens,
 * Numpad is the secondary screen. Both orientations are allowed so the
 * Combo layout itself adapts (keyboard on top in portrait,
 * keyboard on the left in landscape).
 */
class Input: Fragment() {

    private lateinit var binding: FragmentInputBinding

    private val connectionViewModel: ConnectionViewModel by activityViewModels()
    private val keyboardViewModel: KeyboardViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_input, container, false)
        binding.lifecycleOwner = viewLifecycleOwner

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            replaceChildFragment(Keyboard())
        }

        binding.hiddenInput.apply {
            addTextChangedListener(KeyboardInputWatcher(this) { bytes ->
                keyboardViewModel.handleKeypress(bytes)
            })
        }

        // Remove the focus from the hidden text input and clear its text
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if(!isKeyboardVisible && binding.hiddenInput.hasFocus()) {
                binding.hiddenInput.clearFocus()
                binding.hiddenInput.text.clear()
            }
            insets
        }

        // Set navigation listener for the side drawer
        binding.navigation.setCheckedItem(R.id.mode_keyboard)
        binding.btnOpenDrawer.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.navigation.setNavigationItemSelectedListener { item ->
            when(item.itemId) {
                R.id.mode_keyboard -> {
                    item.isChecked = true
                    closeSoftKeyboard()
                    replaceChildFragment(Keyboard())
                }
                R.id.mode_mouse -> {
                    item.isChecked = true
                    closeSoftKeyboard()
                    replaceChildFragment(Mouse())
                }
                R.id.mode_combo -> {
                    item.isChecked = true
                    closeSoftKeyboard()
                    replaceChildFragment(Combo())
                }
                R.id.mode_numpad -> {
                    item.isChecked = true
                    closeSoftKeyboard()
                    replaceChildFragment(Numpad())
                }
                R.id.mode_disconnect -> {
                    closeSoftKeyboard()
                    connectionViewModel.disconnect()
                    findNavController().navigateUp()
                }
                R.id.mode_change_layout -> {
                    showLayoutSelectorDialog(item)
                    return@setNavigationItemSelectedListener true
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    connectionViewModel.state.collect {
                        when (it) {
                            is ConnectionViewModel.State.Connected -> {
                                binding.navigation
                                    .getHeaderView(0)
                                    .findViewById<TextView>(R.id.connectionStatus)
                                    .text = "Connected to ${it.hostName}"
                            }
                            else -> {}
                        }
                    }
                }

                launch {
                    connectionViewModel.events.collect {
                        when(it) {
                            is ConnectionViewModel.Event.NavigateToInput -> { }
                            is ConnectionViewModel.Event.NavigateToMain -> findNavController().popBackStack(R.id.mainFragment, false)
                            is ConnectionViewModel.Event.ConnectionDisconnected -> {
                                val pview = showPopupDialog(R.layout.connection_disconnected_fragment)
                                pview?.apply {
                                    findViewById<TextView>(R.id.subtitle).text = "${it.connectionMode.name} Connection to Mousedroid server was interrupted."
                                    findViewById<TextView>(R.id.description).text = "Make sure the server is still ON and WIFI is active."
                                }
                            }
                            is ConnectionViewModel.Event.ConnectionFailed -> {
                                val pview = showPopupDialog(R.layout.connection_failed_fragment)
                                pview?.apply {
                                    findViewById<TextView>(R.id.subtitle).text = "Connection to Mousedroid server failed"
                                    findViewById<TextView>(R.id.description).text = "To connect over WIFI make sure you are on the same network as the computer.\nTo connect over USB make sure ADB is on and debugging is enabled.\nMake sure Mousedroid server is allowed through the firewall. "
                                }
                            }
                            else -> { }
                        }
                    }
                }
            }
        }
    }

    private fun replaceChildFragment(fragment: Fragment) {
        childFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun showLayoutSelectorDialog(menuItem: MenuItem) {
        val layouts = keyboardViewModel.layouts.toTypedArray()

        val currentLayoutIndex = layouts.indexOf(keyboardViewModel.activeKeyboardLayout.name)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Keyboard Layout")
            .setSingleChoiceItems(layouts, currentLayoutIndex) { dialog, which ->

                val selectedLayoutName = layouts[which]
                if (keyboardViewModel.setKeyboardLayout(selectedLayoutName)) {
                    menuItem.title = "Layout: $selectedLayoutName"
                } else {
                    menuItem.title = "Layout"
                }


                dialog.dismiss()
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun closeSoftKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
        view?.clearFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionViewModel.disconnect()
    }
}
