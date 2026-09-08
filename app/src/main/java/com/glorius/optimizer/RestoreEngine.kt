package com.glorius.optimizer

/**
 * Modul Restore ("Revert Defaults").
 * Mengembalikan seluruh perubahan yang dilakukan OptimizerEngine & GameProfiler
 * ke kondisi default/hemat daya bawaan sistem.
 */
object RestoreEngine {

    data class RestoreResult(val label: String, val success: Boolean, val detail: String = "")

    fun revertAll(): List<RestoreResult> {
        val results = mutableListOf<RestoreResult>()
        results += revertCpuGovernor()
        results += revertLmkWatermarks()
        results += revertDisplaySettings()
        results += revertNetworkInterfaces()
        results += revertGameProcessesIfAny()
        return results
    }

    private fun revertCpuGovernor(default: String = "schedutil"): RestoreResult {
        val cores = Runtime.getRuntime().availableProcessors()
        val cmds = mutableListOf<String>()
        for (i in 0 until cores) {
            cmds += "echo $default > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor 2>/dev/null || true"
        }
        val ok = PrivilegedExecutor.runBatch(cmds)
        return RestoreResult("CPU Governor -> $default (default)", ok)
    }

    private fun revertLmkWatermarks(): RestoreResult {
        val cmds = listOf(
            "echo '18432,23040,27648,32256,38400,46080' > /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null || true"
        )
        val ok = PrivilegedExecutor.runBatch(cmds)
        return RestoreResult("LMK Watermark -> default", ok)
    }

    private fun revertDisplaySettings(): RestoreResult {
        val cmds = listOf(
            "echo 0 > /sys/class/graphics/fb0/hbm 2>/dev/null || true"
        )
        val ok = PrivilegedExecutor.runBatch(cmds)
        // Refresh rate dikembalikan ke auto/default oleh sistem (hapus override manual)
        PrivilegedExecutor.run("settings delete system peak_refresh_rate")
        PrivilegedExecutor.run("settings delete system min_refresh_rate")
        return RestoreResult("Display Panel & HBM -> default", ok)
    }

    private fun revertNetworkInterfaces(): RestoreResult {
        val result = OptimizerEngine.restoreRadios()
        return RestoreResult("Network Interface -> normal", result.success, result.detail)
    }

    private fun revertGameProcessesIfAny(): RestoreResult {
        var anyRestored = false
        for (pkg in GameProfiler.SUPPORTED_PACKAGES) {
            val res = GameProfiler.restoreGame(pkg)
            if (res.any { it.success }) anyRestored = true
        }
        return RestoreResult(
            "OOM Score Game -> normal",
            anyRestored,
            if (anyRestored) "Berhasil di-restore" else "Tidak ada game target yang sedang berjalan"
        )
    }
}
