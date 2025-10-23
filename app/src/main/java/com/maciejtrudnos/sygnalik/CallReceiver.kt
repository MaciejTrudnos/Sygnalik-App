package com.maciejtrudnos.sygnalik

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class CallReceiver : BroadcastReceiver() {

    companion object {
        lateinit var bleManager: BLEManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)

            if (stateStr == TelephonyManager.EXTRA_STATE_RINGING) {
                Log.i("CALL", "Polaczenie przychodzace")
                bleManager.sendText("call")
            }
        }
    }
}
