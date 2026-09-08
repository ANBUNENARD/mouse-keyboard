package com.darusc.mousedroid.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.darusc.mousedroid.R
import com.darusc.mousedroid.databinding.FragmentMouseBinding
import com.darusc.mousedroid.mkinput.GestureHandler
import com.darusc.mousedroid.mkinput.InputEvent
import com.darusc.mousedroid.viewmodels.TouchpadViewModel
import kotlin.math.abs

/**
 * Mouse screen: a mouse graphic is dragged around to hover the PC cursor.
 * Left / Right buttons click, the wheel in the middle scrolls.
 * Reuses TouchpadViewModel so it sends through the same connection.
 */
class Mouse : Fragment() {

    private lateinit var binding: FragmentMouseBinding
    private val touchpadViewModel: TouchpadViewModel by activityViewModels()

    private var wheelLastY = 0f
    private var wheelAccumulator = 0f
    private var wheelMoved = false

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
        binding.mouseWheel.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    wheelLastY = event.y
                    wheelAccumulator = 0f
                    wheelMoved = false
                    parentRequestDisallow(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = wheelLastY - event.y
                    wheelLastY = event.y
                    if (abs(dy) > 0.5f) {
                        wheelMoved = true
                    }
                    wheelAccumulator += dy
                    while (abs(wheelAccumulator) >= 24f) {
                        val tick = if (wheelAccumulator > 0) 10 else -10
                        touchpadViewModel.sendMouseEvent(InputEvent.MouseScroll(0, tick))
                        wheelAccumulator -= if (wheelAccumulator > 0) 24f else -24f
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parentRequestDisallow(false)
                    true
                }
                else -> true
            }
        }
    }

    private fun parentRequestDisallow(disallow: Boolean) {
        binding.mouseWheel.parent?.requestDisallowInterceptTouchEvent(disallow)
    }
}
