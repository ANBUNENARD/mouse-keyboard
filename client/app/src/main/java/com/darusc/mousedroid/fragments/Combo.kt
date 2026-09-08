package com.darusc.mousedroid.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.darusc.mousedroid.R
import com.darusc.mousedroid.databinding.FragmentComboBinding
import com.darusc.mousedroid.mkinput.GestureHandler
import com.darusc.mousedroid.viewmodels.TouchpadViewModel

/**
 * Combo mode: full keyboard plus mouse pad in one screen.
 * Adapts to orientation (keyboard on top in portrait,
 * keyboard on the left in landscape). Reuses the Keyboard fragment
 * and TouchpadViewModel so both panes send through the same connection.
 */
class Combo : Fragment() {

    private lateinit var binding: FragmentComboBinding
    private val touchpadViewModel: TouchpadViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_combo, container, false)
        binding.viewmodel = touchpadViewModel
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.comboKeyboardHost, Keyboard())
                .commit()
        }
        binding.comboTouchpadSensor.setOnTouchListener(
            GestureHandler(requireContext()) { event ->
                touchpadViewModel.sendMouseEvent(event)
            }
        )
    }
}
