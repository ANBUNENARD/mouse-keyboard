package com.darusc.mousedroid.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.darusc.mousedroid.R
import com.darusc.mousedroid.databinding.FragmentMouseBinding
import com.darusc.mousedroid.mkinput.GestureHandler
import com.darusc.mousedroid.viewmodels.TouchpadViewModel

/**
 * Trackpad screen: a plain pad area with the same layout in portrait
 * and landscape. Drag to hover, tap for Left, two-finger tap for Right,
 * two-finger drag to scroll. Left / Right buttons below the pad click.
 * Reuses TouchpadViewModel so it sends through the same connection.
 */
class Mouse : Fragment() {

    private lateinit var binding: FragmentMouseBinding
    private val touchpadViewModel: TouchpadViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_mouse, container, false)
        binding.viewmodel = touchpadViewModel
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.mousePadSensor.setOnTouchListener(
            GestureHandler(requireContext()) { event ->
                touchpadViewModel.sendMouseEvent(event)
            }
        )
    }
}
