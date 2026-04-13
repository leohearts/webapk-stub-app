package com.android.chrome

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import java.util.*

class SettingsActivity : Activity() {

    private lateinit var adapter: ArrayAdapter<String>
    private val allPackages = mutableListOf<String>()
    private val displayedPackages = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val searchBox = findViewById<EditText>(R.id.searchBox)
        val listView = findViewById<ListView>(R.id.packageList)
        val manualInput = findViewById<EditText>(R.id.manualPackageInput)
        val saveBtn = findViewById<Button>(R.id.saveManualBtn)

        refreshPackageList()
        displayedPackages.addAll(allPackages)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayedPackages)
        listView.adapter = adapter

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterList(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedPackage = adapter.getItem(position) ?: return@setOnItemClickListener
            saveAndFinish(selectedPackage)
        }

        saveBtn.setOnClickListener {
            val pkg = manualInput.text.toString().trim()
            if (pkg.isNotEmpty()) {
                saveAndFinish(pkg)
            } else {
                Toast.makeText(this, "Please enter a package name", Toast.LENGTH_SHORT).show()
            }
        }
        
        val current = getSharedPreferences("RedirectorPrefs", Context.MODE_PRIVATE).getString("target_package", "None")
        Toast.makeText(this, "Current target: $current", Toast.LENGTH_SHORT).show()
    }

    private fun filterList(query: String) {
        displayedPackages.clear()
        if (query.isEmpty()) {
            displayedPackages.addAll(allPackages)
        } else {
            val lowerQuery = query.toLowerCase(Locale.ROOT)
            for (pkg in allPackages) {
                if (pkg.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                    displayedPackages.add(pkg)
                }
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun refreshPackageList() {
        allPackages.clear()
        val pm = packageManager
        val packageSet = mutableSetOf<String>()

        // 1. Scan ALL installed packages for CURRENT user
        try {
            // Note: The '0' here represents flags, NOT the User ID. 
            // It automatically returns packages for the current user (even if not User 0).
            // We'll add MATCH_DISABLED_COMPONENTS to see even disabled apps.
            val allInstalled = pm.getInstalledPackages(PackageManager.MATCH_DISABLED_COMPONENTS)
            for (pkgInfo in allInstalled) {
                packageSet.add(pkgInfo.packageName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Scan for browsers specifically (to ensure we capture them even if getInstalledPackages was limited)
        val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://"))
        val resolveInfos = pm.queryIntentActivities(browserIntent, 0)
        for (info in resolveInfos) {
            packageSet.add(info.activityInfo.packageName)
        }

        // 3. Actively detect common browsers
        val commonBrowsers = listOf(
            "org.cromite.cromite", 
            "com.android.chrome", 
            "com.microsoft.emmx", 
            "org.mozilla.firefox", 
            "com.opera.browser",
            "com.brave.browser",
            "com.vivaldi.browser",
            "com.duckduckgo.mobile.android",
            "mark.via.gp",
            "com.sec.android.app.sbrowser"
        )
        for (pkgName in commonBrowsers) {
            try {
                pm.getPackageInfo(pkgName, 0)
                packageSet.add(pkgName)
            } catch (e: PackageManager.NameNotFoundException) {
                // Not installed
            }
        }

        // 4. Specifically identify/verify default browser
        val defaultBrowser = pm.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
        defaultBrowser?.let {
            packageSet.add(it.activityInfo.packageName)
        }

        allPackages.addAll(packageSet.sorted())
    }

    private fun saveAndFinish(packageName: String) {
        val prefs = getSharedPreferences("RedirectorPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("target_package", packageName).apply()
        Toast.makeText(this, "Target set to: $packageName", Toast.LENGTH_SHORT).show()
        finish()
    }
}
