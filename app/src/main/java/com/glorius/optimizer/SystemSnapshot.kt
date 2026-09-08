package com.glorius.optimizer

/**
 * Snapshot lengkap kondisi hardware & software HP pada satu titik waktu.
 * Semua field diisi secara dinamis oleh SystemScanner, tidak ada nilai hardcoded.
 */
data class SystemSnapshot(
    // RAM
    val totalRamMb: Long = 0L,
    val usedRamMb: Long = 0L,
    val availableRamMb: Long = 0L,

    // CPU
    val cpuGovernor: String = "unknown",
    val cpuCoreCount: Int = 0,
    val cpuFreqPerCoreMhz: List<Int> = emptyList(),
    val cpuTempCelsius: Float? = null,

    // Baterai
    val batteryPercent: Int = -1,
    val batteryTempCelsius: Float? = null,
    val batteryHealth: String = "unknown",
    val isCharging: Boolean = false,

    // Storage
    val totalStorageMb: Long = 0L,
    val usedStorageMb: Long = 0L,
    val freeStorageMb: Long = 0L,

    // Entropy
    val entropyAvailBits: Int = -1,

    // SoC & GPU
    val socModel: String = "unknown",
    val hardwarePlatform: String = "unknown",
    val gpuRenderer: String = "unknown",

    // Akses service
    val hasRootAccess: Boolean = false,
    val hasShizukuAccess: Boolean = false,
    val hasAdbShellHint: Boolean = false
)
