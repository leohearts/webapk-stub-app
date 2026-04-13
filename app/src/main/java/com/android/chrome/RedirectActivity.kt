package com.android.chrome

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

class RedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val incomingIntent = intent
        var data = incomingIntent.data

        // WebAPK specific extras might contain the URL
        if (data == null) {
            val webappUrl = incomingIntent.getStringExtra("org.chromium.chrome.browser.webapp_url")
            if (webappUrl != null) {
                data = Uri.parse(webappUrl)
            }
        }

        if (data != null) {
            Log.d("Redirector", "Redirecting URL: $data")
            
            // Get target package from preferences
            val prefs = getSharedPreferences("RedirectorPrefs", Context.MODE_PRIVATE)
            val targetPackage = prefs.getString("target_package", null)

            if (targetPackage == null) {
                // If not set, go to selection screen
                val selectIntent = Intent(this, SettingsActivity::class.java)
                selectIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(selectIntent)
                finish()
                return
            }

            // Create a new intent to the target browser
            val targetIntent = Intent(Intent.ACTION_VIEW, data)
            targetIntent.setPackage(targetPackage)
            targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            // Copy all extras from the original intent
            val extras = incomingIntent.extras
            if (extras != null) {
                targetIntent.putExtras(extras)
            }

            try {
                startActivity(targetIntent)
            } catch (e: Exception) {
                Log.e("Redirector", "Failed to start target activity", e)
                val selectIntent = Intent(this, SettingsActivity::class.java)
                startActivity(selectIntent)
            }
        } else {
            Log.w("Redirector", "No data or webapp_url found in intent")
            // If opened directly without data, show selection screen
            val selectIntent = Intent(this, SettingsActivity::class.java)
            startActivity(selectIntent)
        }
        
        finish()
    }
}
