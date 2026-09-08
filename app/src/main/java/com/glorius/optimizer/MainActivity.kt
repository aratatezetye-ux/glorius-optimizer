package com.glorius.optimizer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var scanner: SystemScanner

    private lateinit var tvSystemInfo: TextView
    private lateinit var tvPrivilegeMode: TextView
    private lateinit var tvLog: TextView

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
        runOnUiThread {
            Toast.makeText(
                this,
                if (granted) "Izin Shizuku diberikan" else "Izin Shizuku ditolak",
                Toast.LENGTH_SHORT
            ).show()
            refreshPrivilegeMode()
            runScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scanner = SystemScanner(applicationContext)

        tvSystemInfo = findViewById(R.id.tvSystemInfo)
        tvPrivilegeMode = findViewById(R.id.tvPrivilegeMode)
        tvLog = findViewById(R.id.tvLog)

        findViewById<Button>(R.id.btnRequestShizuku).setOnClickListener {
            requestShizukuPermission()
        }
        findViewById<Button>(R.id.btnRescan).setOnClickListener { runScan() }
        findViewById<Button>(R.id.btnInject).setOnClickListener { runInject() }
        findViewById<Button>(R.id.btnGameProfile).setOnClickListener {
            startActivity(Intent(this, GameProfilerActivity::class.java))
        }
        findViewById<Button>(R.id.btnRestore).setOnClickListener { runRestore() }
        findViewById<Button>(R.id.btnDebloat).setOnClickListener {
            startActivity(Intent(this, DebloaterActivity::class.java))
        }
        findViewById<Button>(R.id.btnDisplay).setOnClickListener {
            startActivity(Intent(this, DisplayTweakActivity::class.java))
        }
        findViewById<Button>(R.id.btnTouch).setOnClickListener {
            startActivity(Intent(this, TouchTweakActivity::class.java))
        }

        Shizuku.addRequestPermissionResultListener(permissionListener)

        refreshPrivilegeMode()
        runScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    private fun requestShizukuPermission() {
        if (!ShizukuUtils.isShizukuRunning()) {
            Toast.makeText(
                this,
                "Shizuku belum aktif. Aktifkan lewat aplikasi Shizuku Manager " +
                        "(via USB debugging / wireless debugging / root).",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (ShizukuUtils.hasPermission()) {
            Toast.makeText(this, "Izin Shizuku sudah aktif", Toast.LENGTH_SHORT).show()
            refreshPrivilegeMode()
            return
        }
        ShizukuUtils.requestPermission(this)
    }

    private fun refreshPrivilegeMode() {
        val mode = PrivilegedExecutor.currentMode(forceRecheck = true)
        tvPrivilegeMode.text = "Mode akses: ${PrivilegedExecutor.modeLabel()} " +
                if (mode == PrivilegedMode.NONE)
                    "(fitur privileged nonaktif, aktifkan root atau Shizuku)"
                else ""
    }

    private fun runScan() {
        tvSystemInfo.text = "Memindai..."
        lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) { scanner.scanNow() }
            tvSystemInfo.text = formatSnapshot(snapshot)
        }
    }

    private fun runInject() {
        appendLog("Menjalankan Inject Glorius...")
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) { OptimizerEngine.injectGlorius() }
            results.forEach {
                appendLog("[${if (it.success) "OK" else "SKIP"}] ${it.label} ${it.detail}")
            }
            runScan()
        }
    }

    private fun runRestore() {
        appendLog("Menjalankan Revert Defaults...")
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) { RestoreEngine.revertAll() }
            results.forEach {
                appendLog("[${if (it.success) "OK" else "SKIP"}] ${it.label} ${it.detail}")
            }
            runScan()
        }
    }

    private fun appendLog(line: String) {
        tvLog.text = "${tvLog.text}\n$line".trim()
    }

    private fun formatSnapshot(s: SystemSnapshot): String {
        return buildString {
            appendLine("RAM        : ${s.usedRamMb}MB / ${s.totalRamMb}MB (tersedia ${s.availableRamMb}MB)")
            appendLine("CPU Gov.   : ${s.cpuGovernor}")
            appendLine("CPU Cores  : ${s.cpuCoreCount} | Freq: ${s.cpuFreqPerCoreMhz.joinToString(",")} MHz")
            appendLine("CPU Temp   : ${s.cpuTempCelsius?.let { "%.1f°C".format(it) } ?: "N/A"}")
            appendLine("Baterai    : ${s.batteryPercent}% | ${s.batteryTempCelsius?.let { "%.1f°C".format(it) } ?: "N/A"} | ${s.batteryHealth} | Charging=${s.isCharging}")
            appendLine("Storage    : ${s.usedStorageMb}MB / ${s.totalStorageMb}MB (free ${s.freeStorageMb}MB)")
            appendLine("Entropy    : ${if (s.entropyAvailBits >= 0) "${s.entropyAvailBits} bits" else "N/A"}")
            appendLine("SoC        : ${s.socModel} (${s.hardwarePlatform})")
            appendLine("GPU        : ${s.gpuRenderer}")
            appendLine("Root       : ${s.hasRootAccess}")
            appendLine("Shizuku    : ${s.hasShizukuAccess}")
            appendLine("ADB Hint   : ${s.hasAdbShellHint}")
        }
    }
}
