package com.darusc.mousedroid

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.darusc.mousedroid.networking.Connection.Mode


fun getDeviceDetails(context: Context, connectionMode: Mode): String {
    val manufacturer = Build.MANUFACTURER
    val model = Build.MODEL

    val deviceName = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        ?: Settings.Secure.getString(context.contentResolver, "bluetooth_name")
        ?: model

    val deviceDetails = "$manufacturer/$deviceName/$model/${connectionMode.ordinal}"
    return deviceDetails
}
