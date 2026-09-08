package com.glorius.optimizer

/**
 * Lapisan abstraksi tunggal untuk semua modul lain (Scanner, OptimizerEngine, RestoreEngine,
 * GameProfiler). Otomatis memilih jalur privileged yang tersedia:
 *   1. Root (su) jika device di-root
 *   2. Shizuku (ADB/USB debugging atau wireless debugging) jika root tidak tersedia
 *   3. Fallback baca file langsung jika readable tanpa privilege
 *
 * Semua fitur Inject/Restore di app ini memanggil lewat sini, BUKAN langsung ke RootUtils
 * atau ShizukuUtils, supaya app tetap berfungsi baik di HP root maupun non-root (Shizuku).
 */
enum class PrivilegedMode { ROOT, SHIZUKU, NONE }

object PrivilegedExecutor {

    /** Cache mode supaya tidak mengecek ulang root/Shizuku setiap kali command dijalankan. */
    private var cachedMode: PrivilegedMode? = null

    fun currentMode(forceRecheck: Boolean = false): PrivilegedMode {
        if (!forceRecheck) {
            cachedMode?.let { return it }
        }
        val mode = when {
            RootUtils.isRootAvailable() -> PrivilegedMode.ROOT
            ShizukuUtils.isShizukuRunning() && ShizukuUtils.hasPermission() -> PrivilegedMode.SHIZUKU
            else -> PrivilegedMode.NONE
        }
        cachedMode = mode
        return mode
    }

    fun isAvailable(): Boolean = currentMode() != PrivilegedMode.NONE

    /** Jalankan satu perintah shell privileged, otomatis lewat root atau Shizuku. */
    fun run(command: String): String {
        return when (currentMode()) {
            PrivilegedMode.ROOT -> RootUtils.executeRootCommand(command)
            PrivilegedMode.SHIZUKU -> ShizukuUtils.execute(command)
            PrivilegedMode.NONE -> ""
        }
    }

    /** Jalankan banyak perintah sekaligus, kembalikan true jika sukses dijalankan. */
    fun runBatch(commands: List<String>): Boolean {
        return when (currentMode()) {
            PrivilegedMode.ROOT -> RootUtils.executeRootCommands(commands)
            PrivilegedMode.SHIZUKU -> ShizukuUtils.executeMultiple(commands)
            PrivilegedMode.NONE -> false
        }
    }

    /** Baca file: coba non-privileged dulu, baru fallback ke privileged bila perlu. */
    fun readFile(path: String): String {
        RootUtils.readFile(path)?.let { return it }
        val viaPrivileged = run("cat $path")
        return viaPrivileged
    }

    fun modeLabel(): String = when (currentMode()) {
        PrivilegedMode.ROOT -> "Root"
        PrivilegedMode.SHIZUKU -> "Shizuku (ADB)"
        PrivilegedMode.NONE -> "Tidak tersedia"
    }
}
