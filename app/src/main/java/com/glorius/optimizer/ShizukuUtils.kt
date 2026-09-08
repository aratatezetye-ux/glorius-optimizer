package com.glorius.optimizer

import android.app.Activity
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Wrapper untuk Shizuku: menjalankan perintah shell dengan privilege ADB/root
 * TANPA memerlukan device di-root, cukup mengaktifkan Shizuku sekali lewat:
 *  - USB debugging: `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`
 *  - Wireless debugging (Android 11+): aktifkan langsung dari app Shizuku Manager
 *  - atau lewat root, jika device root tersedia
 *
 * Setelah Shizuku service berjalan, aplikasi ini cukup meminta izin sekali (runtime permission).
 */
object ShizukuUtils {

    const val REQUEST_CODE_PERMISSION = 9001

    /** Apakah Shizuku service sedang berjalan & bisa dihubungi. */
    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    /** Apakah aplikasi ini sudah diberi izin oleh Shizuku Manager. */
    fun hasPermission(): Boolean {
        if (!isShizukuRunning()) return false
        return try {
            if (Shizuku.isPreV11()) {
                false // versi lama tidak didukung
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Throwable) {
            false
        }
    }

    /** Minta izin Shizuku ke user. Hasil diterima lewat Shizuku.OnRequestPermissionResultListener. */
    fun requestPermission(activity: Activity) {
        if (!isShizukuRunning()) return
        try {
            if (!Shizuku.isPreV11()) {
                Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
            }
        } catch (e: Throwable) {
            // abaikan, akan dianggap belum granted
        }
    }

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.addRequestPermissionResultListener(listener)
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.removeRequestPermissionResultListener(listener)
    }

    /**
     * Jalankan perintah shell melalui proses privileged milik Shizuku (uid shell/ADB, atau
     * root bila Shizuku diaktifkan lewat root). Menggunakan Shizuku.newProcess via reflection
     * sesuai pola resmi dari demo shizuku-api (moe.shizuku.demo ShellDemo).
     */
    fun execute(command: String): String {
        if (!hasPermission()) return ""
        return try {
            val method = Shizuku::class.java.getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process

            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            ""
        }
    }

    /** Jalankan beberapa perintah sekaligus dalam satu sesi shell privileged. */
    fun executeMultiple(commands: List<String>): Boolean {
        if (!hasPermission()) return false
        val joined = commands.joinToString(" && ")
        return execute(joined).let { true }
    }
}
