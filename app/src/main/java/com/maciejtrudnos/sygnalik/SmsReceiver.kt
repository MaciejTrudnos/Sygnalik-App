package com.maciejtrudnos.sygnalik

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    companion object {
        lateinit var bleManager: BLEManager
    }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action.equals("android.provider.Telephony.SMS_RECEIVED")) {
            val bundle: Bundle? = intent.extras
            try {
                if (bundle != null) {
                    val pdus = bundle["pdus"] as Array<*>
                    for (pdu in pdus) {
                        val format = bundle.getString("format")
                        val sms = SmsMessage.createFromPdu(pdu as ByteArray, format)
                        //val messageBody = sms.messageBody
                        val sender = sms.displayOriginatingAddress

                        Log.i("SMS", "Wiadomosc od $sender")

                        bleManager.sendText("Wiadomosc od $sender")
                    }
                }
            } catch (e: Exception) {
                Log.e("SMS", "Błąd odczytu SMS: ${e.message}")
            }
        }
    }
}
