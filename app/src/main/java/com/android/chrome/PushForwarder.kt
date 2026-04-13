package com.android.chrome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PushForwarder : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (BuildConfig.DEBUG) {
            Log.d("Redirector", "--- INCOMING BROADCAST ---")
            Log.d("Redirector", "Action: ${intent.action}")
            intent.extras?.let { bundle ->
                for (key in bundle.keySet()) {
                    Log.d("Redirector", "Extra[$key]: ${bundle.get(key)}")
                }
            }
            Log.d("Redirector", "--------------------------")
        }

        val prefs = context.getSharedPreferences("RedirectorPrefs", Context.MODE_PRIVATE)
        val targetPackage = prefs.getString("target_package", null)

        if (targetPackage != null) {
            Log.d("Redirector", "Forwarding FCM/Broadcast to: $targetPackage")
            
            // Clone the incoming intent so we can forward it
            val forwardIntent = Intent(intent.action)
            forwardIntent.setPackage(targetPackage)
            
            val extras = intent.extras
            if (extras != null) {
                forwardIntent.putExtras(extras)
            }

            try {
                // FCM intents are typically broadcasts. We forward it as a broadcast to the target app.
                // Note: The target app must have a receiver exported to catch it, or we rely on explicit package targeting.
                context.sendBroadcast(forwardIntent)
            } catch (e: Exception) {
                Log.e("Redirector", "Failed to forward broadcast", e)
            }
        } else {
            Log.d("Redirector", "No target browser selected, dropping broadcast")
        }
    }
}
