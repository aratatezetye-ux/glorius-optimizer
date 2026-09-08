package com.glorius.optimizer

/**
 * Modul Game Profiling: menerapkan tweak khusus untuk game target agar prioritas CPU/GPU/memori
 * lebih tinggi selama sesi bermain. Target default: Free Fire & Free Fire MAX.
 *
 * PENTING: oom_score_adj dan nice level negatif ekstrem (-20) dapat memengaruhi stabilitas
 * sistem/app lain jika disalahgunakan; nilai di sini dipilih sesuai kebutuhan game-boost umum
 * dan dikembalikan lagi lewat RestoreEngine setelah sesi selesai.
 */
object GameProfiler {

    val SUPPORTED_PACKAGES = listOf(
        "com.dts.freefireth",   // Free Fire
        "com.dts.freefiremax"   // Free Fire MAX
    )

    data class ProfileResult(val label: String, val success: Boolean, val detail: String = "")

    fun profileGame(packageName: String = "com.dts.freefiremax"): List<ProfileResult> {
        val results = mutableListOf<ProfileResult>()
        results += lockOomScore(packageName)
        results += setNiceLevel(packageName)
        results += runDexCompileSpeedProfile(packageName)
        results += lockGpuGovernorPerformance()
        return results
    }

    /** Cari PID proses game yang sedang berjalan. Null jika game belum dibuka. */
    private fun findPid(packageName: String): String? {
        val output = PrivilegedExecutor.run("pidof $packageName")
        return output.trim().split(" ").firstOrNull { it.isNotBlank() }
    }

    private fun lockOomScore(packageName: String): ProfileResult {
        val pid = findPid(packageName)
            ?: return ProfileResult("Lock OOM Score", false, "$packageName belum berjalan")
        val cmd = "echo -1000 > /proc/$pid/oom_score_adj"
        val out = PrivilegedExecutor.run(cmd)
        val verify = PrivilegedExecutor.run("cat /proc/$pid/oom_score_adj").trim()
        val ok = verify == "-1000"
        return ProfileResult("Lock OOM Score (-1000)", ok, "PID=$pid, verify=$verify")
    }

    private fun setNiceLevel(packageName: String): ProfileResult {
        val pid = findPid(packageName)
            ?: return ProfileResult("Set Nice Level", false, "$packageName belum berjalan")
        val ok = PrivilegedExecutor.run("renice -n -20 -p $pid").let { true }
        return ProfileResult("CPU Nice Level -> -20", ok, "PID=$pid")
    }

    private fun runDexCompileSpeedProfile(packageName: String): ProfileResult {
        val output = PrivilegedExecutor.run("cmd package compile -m speed-profile -f $packageName")
        val ok = output.contains("Success", ignoreCase = true)
        return ProfileResult("ART DEX Compile (speed-profile)", ok, output.take(120))
    }

    private fun lockGpuGovernorPerformance(): ProfileResult {
        val cmds = listOf(
            // Path GPU governor bervariasi tergantung SoC (Adreno/Mali):
            "echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null || true",
            "echo performance > /sys/kernel/gpu/gpu_governor 2>/dev/null || true"
        )
        val ok = PrivilegedExecutor.runBatch(cmds)
        return ProfileResult("GPU Governor -> performance", ok)
    }

    /** Restore khusus untuk game target: kembalikan oom_score & nice ke default. */
    fun restoreGame(packageName: String = "com.dts.freefiremax"): List<ProfileResult> {
        val results = mutableListOf<ProfileResult>()
        val pid = findPid(packageName)
        if (pid == null) {
            results += ProfileResult("Restore Game Profile", false, "$packageName tidak berjalan")
            return results
        }
        val ok1 = PrivilegedExecutor.run("echo 0 > /proc/$pid/oom_score_adj").let { true }
        results += ProfileResult("Restore OOM Score -> 0", ok1, "PID=$pid")

        val ok2 = PrivilegedExecutor.run("renice -n 0 -p $pid").let { true }
        results += ProfileResult("Restore Nice Level -> 0", ok2, "PID=$pid")

        val cmds = listOf(
            "echo simple_ondemand > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null || true",
            "echo default > /sys/kernel/gpu/gpu_governor 2>/dev/null || true"
        )
        val ok3 = PrivilegedExecutor.runBatch(cmds)
        results += ProfileResult("Restore GPU Governor", ok3)

        return results
    }
}
