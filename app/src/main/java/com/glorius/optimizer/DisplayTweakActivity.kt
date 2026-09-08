package com.glorius.optimizer

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DisplayTweakActivity : AppCompatActivity() {

    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        root.addView(TextView(this).apply { text = "Menu Display"; textSize = 20f })

        for (hz in listOf(60, 90, 120, 144)) {
            root.addView(AppCompatButton(this).apply {
                text = "Set Refresh Rate ${hz}Hz"
                setOnClickListener { applyRefresh(hz) }
            })
        }

        root.addView(AppCompatButton(this).apply {
            text = "Override Thermal Profile: Game"
            setOnClickListener { applyThermal("game") }
        })
        root.addView(AppCompatButton(this).apply {
            text = "Override Thermal Profile: Benchmark"
            setOnClickListener { applyThermal("benchmark") }
        })

        logView = TextView(this).apply { textSize = 12f; setPadding(0, 24, 0, 0) }
        root.addView(logView)

        setContentView(root)
    }

    private fun applyRefresh(hz: Int) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { OptimizerEngine.setRefreshRate(hz) }
            logView.text = "[${if (result.success) "OK" else "SKIP"}] ${result.label}"
        }
    }

    private fun applyThermal(profile: String) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { OptimizerEngine.overrideThermalProfile(profile) }
            logView.text = "[${if (result.success) "OK" else "SKIP"}] ${result.label}"
        }
    }
}
