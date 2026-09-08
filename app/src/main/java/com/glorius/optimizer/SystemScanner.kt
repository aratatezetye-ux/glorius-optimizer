package com.glorius.optimizer

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import java.io.File
import java.io.RandomAccessFile

/**
 * Modul Pembacaan Sistem Real-Time.
 * Semua nilai dibaca langsung dari /proc, /sys, dan Android system service saat
 * fungsi scanNow() dipanggil -- tidak ada data yang di-cache/hardcode, sehingga
 * hasil selalu sesuai kondisi HP yang sedang menjalankan aplikasi (ROG 8, dsb).
 */
class SystemScanner(private val context: Context) {

    fun scanNow(): SystemSnapshot {
        val ram = readRam()
        val storage = readStorage()

        return SystemSnapshot(
            totalRamMb = ram.first,
            usedRamMb = ram.second,
            availableRamMb = ram.third,

            cpuGovernor = readCpuGovernor(),
            cpuCoreCount = Runtime.getRuntime().availableProcessors(),
            cpuFreqPerCoreMhz = readCpuFreqPerCore(),
            cpuTempCelsius = readCpuTemp(),

            batteryPercent = readBatteryPercent(),
            batteryTempCelsius = readBatteryTemp(),
            batteryHealth = readBatteryHealth(),
            isCharging = readIsCharging(),

            totalStorageMb = storage.first,
            usedStorageMb = storage.second,
            freeStorageMb = storage.third,

            entropyAvailBits = readEntropyAvail(),

            socModel = readSocModel(),
            hardwarePlatform = Build.HARDWARE ?: "unknown",
            gpuRenderer = readGpuRenderer(),

            hasRootAccess = PrivilegedExecutor.currentMode(forceRecheck = true) == PrivilegedMode.ROOT,
            hasShizukuAccess = PrivilegedExecutor.currentMode() == PrivilegedMode.SHIZUKU,
            hasAdbShellHint = RootUtils.hasAdbHint()
        )
    }

    // ---------------- RAM ----------------

    /** Return Triple(totalMb, usedMb, availableMb) dibaca via ActivityManager + /proc/meminfo */
    private fun readRam(): Triple<Long, Long, Long> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)
        val usedMb = totalMb - availMb

        // Cross-check lebih akurat via /proc/meminfo bila bisa dibaca
        val procMemInfo = RootUtils.readFile("/proc/meminfo")
        if (procMemInfo != null) {
            val totalKb = extractMeminfoValue(procMemInfo, "MemTotal")
            val availKb = extractMeminfoValue(procMemInfo, "MemAvailable")
            if (totalKb > 0 && availKb > 0) {
                val t = totalKb / 1024
                val a = availKb / 1024
                return Triple(t, t - a, a)
            }
        }
        return Triple(totalMb, usedMb, availMb)
    }

    private fun extractMeminfoValue(content: String, key: String): Long {
        val line = content.lines().firstOrNull { it.startsWith(key) } ?: return -1
        val digits = line.filter { it.isDigit() }
        return digits.toLongOrNull() ?: -1
    }

    // ---------------- CPU ----------------

    private fun readCpuGovernor(): String {
        val path = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
        val value = PrivilegedExecutor.readFile(path)
        return if (value.isNotBlank()) value else "unknown"
    }

    private fun readCpuFreqPerCore(): List<Int> {
        val cores = Runtime.getRuntime().availableProcessors()
        val freqs = mutableListOf<Int>()
        for (i in 0 until cores) {
            val path = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq"
            val value = PrivilegedExecutor.readFile(path)
            val khz = value.trim().toIntOrNull() ?: 0
            freqs.add(khz / 1000) // convert kHz -> MHz
        }
        return freqs
    }

    /**
     * Temperatur CPU dibaca dari /sys/class/thermal/thermal_zoneX/temp.
     * Karena mapping zona berbeda tiap SoC (Snapdragon, Dimensity, dll), kita cari
     * zona yang labelnya paling mendekati "cpu" secara otomatis.
     */
    private fun readCpuTemp(): Float? {
        val thermalDir = File("/sys/class/thermal")
        val zones = thermalDir.listFiles { f -> f.name.startsWith("thermal_zone") } ?: return null

        var bestMatch: Float? = null
        for (zone in zones) {
            val type = PrivilegedExecutor.readFile("${zone.path}/type")
            if (type.contains("cpu", ignoreCase = true) ||
                type.contains("cpuss", ignoreCase = true) ||
                type.contains("tsens", ignoreCase = true)
            ) {
                val tempRaw = PrivilegedExecutor.readFile("${zone.path}/temp")
                val tempVal = tempRaw.trim().toFloatOrNull() ?: continue
                // Beberapa vendor melaporkan dalam milliCelsius, sebagian langsung Celsius
                val celsius = if (tempVal > 1000) tempVal / 1000f else tempVal
                bestMatch = celsius
                break
            }
        }
        return bestMatch
    }

    // ---------------- Baterai ----------------

    private fun batteryIntent(): Intent? {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        return context.registerReceiver(null, filter)
    }

    private fun readBatteryPercent(): Int {
        val intent = batteryIntent() ?: return -1
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return -1
        return (level * 100 / scale.toFloat()).toInt()
    }

    private fun readBatteryTemp(): Float? {
        val intent = batteryIntent() ?: return null
        val tenthsOfCelsius = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (tenthsOfCelsius == Int.MIN_VALUE) return null
        return tenthsOfCelsius / 10f
    }

    private fun readBatteryHealth(): String {
        val intent = batteryIntent() ?: return "unknown"
        return when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
            else -> "unknown"
        }
    }

    private fun readIsCharging(): Boolean {
        val intent = batteryIntent() ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }

    // ---------------- Storage ----------------

    private fun readStorage(): Triple<Long, Long, Long> {
        val stat = StatFs(android.os.Environment.getDataDirectory().path)
        val blockSize = stat.blockSizeLong
        val totalMb = (stat.blockCountLong * blockSize) / (1024 * 1024)
        val freeMb = (stat.availableBlocksLong * blockSize) / (1024 * 1024)
        val usedMb = totalMb - freeMb
        return Triple(totalMb, usedMb, freeMb)
    }

    // ---------------- Entropy ----------------

    private fun readEntropyAvail(): Int {
        val value = PrivilegedExecutor.readFile("/proc/sys/kernel/random/entropy_avail")
        return value.trim().toIntOrNull() ?: -1
    }

    // ---------------- SoC & GPU ----------------

    private fun readSocModel(): String {
        // Android 12+ punya API resmi untuk model SoC
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val model = Build.SOC_MODEL
            if (!model.isNullOrBlank() && model != "unknown") return model
        }
        // Fallback: baca dari getprop vendor
        val prop = RootUtils.executeShell("getprop ro.board.platform")
        if (prop.isNotBlank()) return prop
        val prop2 = RootUtils.executeShell("getprop ro.hardware")
        return if (prop2.isNotBlank()) prop2 else "unknown"
    }

    private fun readGpuRenderer(): String {
        // Rendering info paling akurat didapat dari EGL/GLES context (butuh GLSurfaceView aktif).
        // Sebagai fallback ringan tanpa membuka context OpenGL, kita baca properti vendor GPU.
        val vendor = RootUtils.executeShell("getprop ro.hardware.vulkan")
        if (vendor.isNotBlank()) return vendor
        val gpuProp = RootUtils.executeShell("getprop ro.board.platform")
        return if (gpuProp.isNotBlank()) "GPU on $gpuProp" else "unknown"
    }
}
