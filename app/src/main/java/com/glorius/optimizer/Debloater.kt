package com.glorius.optimizer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * Menu Bloat (Debloater): daftar aplikasi sistem non-esensial yang aman dinonaktifkan
 * (bukan uninstall permanen, agar bisa dikembalikan lewat "Enable" kapan saja).
 */
object Debloater {

    data class BloatApp(
        val packageName: String,
        val label: String,
        val isSystemApp: Boolean,
        val isEnabled: Boolean
    )

    /**
     * Daftar package yang UMUMNYA aman dinonaktifkan (bukan core system).
     * Selalu tampilkan ke user sebelum menonaktifkan -- jangan auto-disable tanpa konfirmasi.
     */
    private val SAFE_TO_DISABLE_HINTS = listOf(
        "facebook", "netflix", "linkedin", "musicfx", "miservice",
        "wpsoffice", "amazonshopping", "opera", "booking", "spotify.lite"
    )

    fun scanInstalledApps(context: Context): List<BloatApp> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            .map { info ->
                BloatApp(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    isSystemApp = true,
                    isEnabled = info.enabled
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun isLikelySafeToDisable(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return SAFE_TO_DISABLE_HINTS.any { lower.contains(it) }
    }

    /** Nonaktifkan package untuk user saat ini (bisa di-enable lagi kapan saja). */
    fun disablePackage(packageName: String): Boolean {
        val output = PrivilegedExecutor.run("pm disable-user --user 0 $packageName")
        return output.contains("disabled", ignoreCase = true) || output.isBlank().not()
    }

    fun enablePackage(packageName: String): Boolean {
        val output = PrivilegedExecutor.run("pm enable $packageName")
        return output.contains("enabled", ignoreCase = true) || output.isBlank().not()
    }
}
