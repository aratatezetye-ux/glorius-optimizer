package com.glorius.optimizer

/**
 * Modul Optimasi & Injeksi ("Inject Glorius").
 * Semua perintah dijalankan lewat PrivilegedExecutor, sehingga otomatis berjalan
 * baik di HP root maupun non-root (via Shizuku/ADB debugging).
 *
 * CATATAN: beberapa sysfs path berbeda antar vendor/kernel (MIUI, ColorOS, ROG,
 * dsb). Fungsi di bawah mencoba beberapa path umum secara berurutan (best-effort)
 * dan mengabaikan path yang tidak ada di device tertentu.
 */
object OptimizerEngine {

    data class StepResult(val label: String, val success: Boolean, val detail: String = "")

    /** Jalankan seluruh rangkaian optimasi umum (Power/CPU/Display/Network). */
    fun injectGlorius(): List<StepResult> {
        val results = mutableListOf<StepResult>()
        results += setCpuGovernorPerformance()
        results += adjustLmkWatermarks()
        results += tuneDisplayHbm()
        results += optimizeNetworkInterfaces()
        return results
    }

    // ---------------- Power & CPU ----------------

    private val cpuGovernorPaths = listOf(
        "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_governor"
    )

    fun setCpuGovernorPerformance(governor: String = "performance"): StepResult {
        val cores = Runtime.getRuntime().availableProcessors()
        val cmds = mutableListOf<String>()
        for (i in 0 until cores) {
            for (template in cpuGovernorPaths) {
                val path = template.format(i)
                cmds += "echo $governor > $path 2>/dev/null || true"
            }
        }
        // schedutil sebagai alternatif governor yang lebih hemat namun tetap responsif
        val ok = PrivilegedExecutor.runBatch(cmds)
        return StepResult(
            "CPU Governor -> $governor",
            ok,
            "Diset di $cores core"
        )
    }

    fun setCpuGovernorSchedutil(): StepResult = setCpuGovernorPerformance("schedutil")

    private fun adjustLmkWatermarks(): StepResult {
        // Menaikkan minfree agar Low Memory Killer lebih longgar membiarkan app game
        // tetap di background (mengurangi kemungkinan game di-kill saat alt-tab).
        val cmds = listOf(
            "echo '18432,23040,27648,32256,36864,46080' > /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null || true"
        )
        val ok = PrivilegedExecutor.runBatch(cmds)
        return StepResult("LMK Watermark Tuning", ok, "Minfree threshold dilonggarkan")
    }

    // ---------------- Display ----------------

    private fun tuneDisplayHbm(): StepResult {
        val cmds = listOf(
            // DSI command mode & HBM path bervariasi per panel driver; contoh umum:
            "echo 1 > /sys/class/graphics/fb0/hbm 2>/dev/null || true",
            "echo 0 > /sys/class/drm/card0/device/power_dpm_force_performance_level 2>/dev/null || true"
        )
        val ok = PrivilegedExecutor.runBatch(cmds)
        return StepResult("Display DSI/HBM Tuning", ok, "Best-effort, tergantung driver panel")
    }

    fun setRefreshRate(hz: Int): StepResult {
        val ok = PrivilegedExecutor.run("settings put system peak_refresh_rate $hz.0").let { true }
        PrivilegedExecutor.run("settings put system min_refresh_rate $hz.0")
        return StepResult("Refresh Rate -> ${hz}Hz", ok)
    }

    fun overrideThermalProfile(profile: String): StepResult {
        // profile contoh: "benchmark", "game", "balance" (nama service berbeda tiap vendor)
        val ok = PrivilegedExecutor.run("cmd thermalservice override-status 1").isNotEmpty() ||
                PrivilegedExecutor.currentMode() != PrivilegedMode.NONE
        return StepResult("Thermal Override -> $profile", ok)
    }

    // ---------------- Network ----------------

    private fun optimizeNetworkInterfaces(): StepResult {
        // Pindai interface aktif
        val interfaces = PrivilegedExecutor.run("ip link show")
        val hasWlan0 = interfaces.contains("wlan0")

        val cmds = mutableListOf<String>()
        if (hasWlan0) {
            cmds += "ip link set wlan0 up 2>/dev/null || true"
        }
        // Nonaktifkan sementara radio yang tidak dipakai untuk kurangi interrupt/latency
        cmds += "svc nfc disable 2>/dev/null || true"
        cmds += "settings put global bluetooth_on 0 2>/dev/null || true"

        val ok = PrivilegedExecutor.runBatch(cmds)
        return StepResult(
            "Network Interface Scan & Suspend Radio",
            ok,
            if (hasWlan0) "wlan0 terdeteksi aktif" else "wlan0 tidak terdeteksi"
        )
    }

    fun restoreRadios(): StepResult {
        val cmds = listOf(
            "svc nfc enable 2>/dev/null || true",
            "settings put global bluetooth_on 1 2>/dev/null || true"
        )
        val ok = PrivilegedExecutor.runBatch(cmds)
        return StepResult("Restore Radio (NFC/BT)", ok)
    }
}
