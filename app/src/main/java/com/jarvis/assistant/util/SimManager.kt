package com.jarvis.assistant.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

object SimManager {

    @SuppressLint("MissingPermission")
    fun getAvailableSims(context: Context): List<String> {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return listOf("SIM 1 (Default)")
        }

        return try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val activeList: List<SubscriptionInfo>? = subscriptionManager?.activeSubscriptionInfoList

            if (!activeList.isNullOrEmpty()) {
                activeList.mapIndexed { index, info ->
                    val carrierName = info.carrierName?.toString()?.trim()
                    if (!carrierName.isNullOrEmpty()) {
                        "SIM ${index + 1} ($carrierName)"
                    } else {
                        "SIM ${index + 1}"
                    }
                }
            } else {
                listOf("SIM 1 (Active)")
            }
        } catch (e: Exception) {
            listOf("SIM 1", "SIM 2")
        }
    }
}
