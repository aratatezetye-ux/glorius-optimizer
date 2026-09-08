package com.glorius.optimizer

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TouchTweakActivity : AppCompatActivity() {

    private lateinit var valueLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        root.addView(TextView(this).apply { text = "Menu Mouse/Touch"; textSize = 20f })

        valueLabel = TextView(this).apply { text = "Pointer speed: ..." }
        root.addView(valueLabel)

        val seekBar = SeekBar(this).apply {
            max = 14 // rentang -7..7 dipetakan ke 0..14
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val speed = progress - 7
                    lifecycleScope.launch(Dispatchers.IO) {
                        TouchTweaker.setPointerSpeed(speed)
                        withContext(Dispatchers.Main) { valueLabel.text = "Pointer speed: $speed" }
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        root.addView(seekBar)

        root.addView(AppCompatButton(this).apply {
            text = "Aktifkan Touch Boost (Game Mode)"
            setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) { TouchTweaker.enableTouchBoost() }
            }
        })
        root.addView(AppCompatButton(this).apply {
            text = "Nonaktifkan Touch Boost"
            setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) { TouchTweaker.disableTouchBoost() }
            }
        })
        root.addView(AppCompatButton(this).apply {
            text = "Reset Pointer Speed"
            setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    TouchTweaker.resetPointerSpeed()
                    withContext(Dispatchers.Main) {
                        valueLabel.text = "Pointer speed: 0"
                        seekBar.progress = 7
                    }
                }
            }
        })

        setContentView(root)

        lifecycleScope.launch(Dispatchers.IO) {
            val current = TouchTweaker.getPointerSpeed()
            withContext(Dispatchers.Main) {
                valueLabel.text = "Pointer speed: $current"
                seekBar.progress = current + 7
            }
        }
    }
}
