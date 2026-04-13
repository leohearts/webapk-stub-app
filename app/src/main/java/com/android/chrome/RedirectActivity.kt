package com.android.chrome

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

class RedirectActivity : Activity() {

    private var hasRedirected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasRedirected = false
        handleIntent(intent, isReEntry = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // If we already redirected and the browser is on top of us,
        // this is a re-entry from the launcher icon or recents.
        // Just do nothing — the browser activity is still on top of our back stack.
        if (hasRedirected) {
            Log.d("Redirector", "Re-entry detected, skipping redirect (browser is already on top)")
            return
        }
        
        handleIntent(intent, isReEntry = true)
    }

    private fun handleIntent(incomingIntent: Intent, isReEntry: Boolean) {
        val action = incomingIntent.action
        var data = incomingIntent.data

        if (BuildConfig.DEBUG) {
            Log.d("Redirector", "--- INCOMING INTENT ---")
            Log.d("Redirector", "Action: $action")
            Log.d("Redirector", "Data: $data")
            incomingIntent.extras?.let { bundle ->
                for (key in bundle.keySet()) {
                    Log.d("Redirector", "Extra[$key]: ${bundle.get(key)}")
                }
            }
            Log.d("Redirector", "-----------------------")
        }

        // WebAPK specific extras might contain the URL
        if (data == null) {
            val webappUrl = incomingIntent.getStringExtra("org.chromium.chrome.browser.webapp_url")
            if (webappUrl != null) {
                data = Uri.parse(webappUrl)
            }
        }

        if (data != null) {
            Log.d("Redirector", "Redirecting URL: $data")
            
            val prefs = getSharedPreferences("RedirectorPrefs", Context.MODE_PRIVATE)
            val targetPackage = prefs.getString("target_package", null)

            if (targetPackage == null) {
                val selectIntent = Intent(this, SettingsActivity::class.java)
                startActivityForResult(selectIntent, 101)
                return
            }

            // CRITICAL: We intentionally hardcode Intent.ACTION_VIEW here instead of passing incomingIntent.action.
            // Why? Because Chrome WebAPKs often launch with a Chrome-specific action:
            // "com.google.android.apps.chrome.webapps.WebappManager.ACTION_START_WEBAPP"
            // If we forward that exact action string to a non-Chromium browser (like Firefox), it won't have an
            // intent filter for it and will crash with an ActivityNotFoundException.
            // By normalizing to ACTION_VIEW, we guarantee ANY browser can safely accept the intent.
            // Smart Chromium browsers like Edge will still spot the WebAPK metadata bundled inside the extras
            // and seamlessly promote the session to a PWA!
            val targetIntent = Intent(Intent.ACTION_VIEW, data)
            targetIntent.setPackage(targetPackage)
            // NO FLAG_ACTIVITY_NEW_TASK — launch browser within our task

            
            val extras = incomingIntent.extras
            if (extras != null) {
                targetIntent.putExtras(extras)
            }

            try {
                startActivityForResult(targetIntent, 100)
                hasRedirected = true
                // NO finish() — keep our activity in the back stack
                // so the task stays alive in Recents. It will finish in onActivityResult.
            } catch (e: Exception) {
                Log.e("Redirector", "Failed to start target activity", e)
                // Browser might require NEW_TASK (singleTask browsers), try with it
                targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    startActivityForResult(targetIntent, 100)
                    hasRedirected = true
                } catch (e2: Exception) {
                    Log.e("Redirector", "Fallback also failed", e2)
                    val selectIntent = Intent(this, SettingsActivity::class.java)
                    startActivityForResult(selectIntent, 101)
                }
            }
        } else if (action == Intent.ACTION_MAIN) {
            val selectIntent = Intent(this, SettingsActivity::class.java)
            startActivityForResult(selectIntent, 101)
        } else {
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("Redirector", "Child activity closed (req: $requestCode). Exiting transparent bridge.")
        finish()
    }
}
