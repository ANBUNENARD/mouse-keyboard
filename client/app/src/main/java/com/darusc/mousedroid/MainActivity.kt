package com.darusc.mousedroid

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import com.darusc.mousedroid.R

class MainActivity : AppCompatActivity() {

    private val TAG = "Mousedroid"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Required on Android 15+ (API 35+, incl. Android 16): edge-to-edge is enforced.
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        //BatteryMonitor.getInstance().start(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        BatteryMonitor.getInstance().stop(applicationContext)
    }
}
