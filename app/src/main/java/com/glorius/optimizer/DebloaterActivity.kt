package com.glorius.optimizer

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DebloaterActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "Menu Bloat (Debloater)"
            textSize = 20f
        }
        root.addView(title)

        val note = TextView(this).apply {
            text = "Aplikasi ditandai \"Kemungkinan aman\" berdasarkan heuristik nama package. " +
                    "Selalu periksa sebelum menonaktifkan aplikasi sistem apa pun."
            textSize = 12f
            setPadding(0, 8, 0, 16)
        }
        root.addView(note)

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer)

        setContentView(ScrollView(this).apply { addView(root) })

        loadApps()
    }

    private fun loadApps() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { Debloater.scanInstalledApps(applicationContext) }
            listContainer.removeAllViews()
            for (app in apps) {
                val row = LinearLayout(this@DebloaterActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 8, 0, 8)
                }
                val label = TextView(this@DebloaterActivity).apply {
                    text = "${app.label}\n${app.packageName}" +
                            if (Debloater.isLikelySafeToDisable(app.packageName)) " [Kemungkinan aman]" else ""
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(label)

                val btn = AppCompatButton(this@DebloaterActivity).apply {
                    text = if (app.isEnabled) "Disable" else "Enable"
                    setOnClickListener {
                        lifecycleScope.launch(Dispatchers.IO) {
                            if (app.isEnabled) Debloater.disablePackage(app.packageName)
                            else Debloater.enablePackage(app.packageName)
                            withContext(Dispatchers.Main) { loadApps() }
                        }
                    }
                }
                row.addView(btn)
                listContainer.addView(row)
            }
        }
    }
}
