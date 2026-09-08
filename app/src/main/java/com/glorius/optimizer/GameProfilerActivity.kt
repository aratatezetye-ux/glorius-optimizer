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

/**
 * UI sederhana (dibuat programatik agar file tetap ringkas) untuk memilih target game
 * dan menjalankan/merestore Game Profiler.
 */
class GameProfilerActivity : AppCompatActivity() {

    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "Game Profiler"
            textSize = 20f
        }
        root.addView(title)

        for (pkg in GameProfiler.SUPPORTED_PACKAGES) {
            val btnApply = AppCompatButton(this).apply {
                text = "Terapkan Profile: $pkg"
                setOnClickListener { applyProfile(pkg) }
            }
            root.addView(btnApply)

            val btnRestore = AppCompatButton(this).apply {
                text = "Restore Profile: $pkg"
                setOnClickListener { restoreProfile(pkg) }
            }
            root.addView(btnRestore)
        }

        logView = TextView(this).apply {
            textSize = 12f
            setPadding(0, 24, 0, 0)
        }
        root.addView(logView)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun applyProfile(packageName: String) {
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) { GameProfiler.profileGame(packageName) }
            logView.text = results.joinToString("\n") {
                "[${if (it.success) "OK" else "SKIP"}] ${it.label} - ${it.detail}"
            }
        }
    }

    private fun restoreProfile(packageName: String) {
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) { GameProfiler.restoreGame(packageName) }
            logView.text = results.joinToString("\n") {
                "[${if (it.success) "OK" else "SKIP"}] ${it.label} - ${it.detail}"
            }
        }
    }
}
