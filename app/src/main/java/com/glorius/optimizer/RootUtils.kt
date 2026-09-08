package com.glorius.optimizer

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

/**
 * Utility untuk eksekusi perintah shell, baik sebagai user biasa maupun via root (su).
 * Semua modul optimasi bergantung pada kelas ini untuk berinteraksi dengan sysfs/procfs.
 */
object RootUtils {

    /**
     * Cek apakah biner "su" tersedia dan device sudah di-root.
     * Mengembalikan true hanya jika perintah "id" berhasil dijalankan sebagai root (uid=0).
     */
    fun isRootAvailable(): Boolean {
        return try {
            val paths = listOf(
                "/system/bin/su", "/system/xbin/su", "/sbin/su",
                "/system/sd/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
                "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su"
            )
            val suBinaryExists = paths.any { File(it).exists() }
            if (!suBinaryExists) return false

            val result = executeRootCommand("id")
            result.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Jalankan perintah dengan hak akses root melalui su -c.
     * Mengembalikan output gabungan stdout, string kosong bila gagal.
     */
    fun executeRootCommand(command: String): String {
        var process: Process? = null
        return try {
            process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            ""
        } finally {
            process?.destroy()
        }
    }

    /**
     * Jalankan beberapa perintah root sekaligus dalam satu sesi su,
     * berguna untuk batch write ke sysfs agar lebih cepat & atomik.
     */
    fun executeRootCommands(commands: List<String>): Boolean {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            val out = DataOutputStream(process.outputStream)
            for (cmd in commands) {
                out.writeBytes("$cmd\n")
            }
            out.writeBytes("exit\n")
            out.flush()
            out.close()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /** Jalankan perintah non-root biasa (untuk data yang readable tanpa privilege). */
    fun executeShell(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            ""
        }
    }

    /** Baca isi file sysfs/procfs langsung tanpa root, jika readable oleh app. */
    fun readFile(path: String): String? {
        return try {
            val f = File(path)
            if (f.exists() && f.canRead()) f.readText().trim() else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cek apakah Shizuku terpasang & servicenya aktif, dideteksi lewat keberadaan
     * package Shizuku Manager. Untuk integrasi penuh, tambahkan dependency
     * dev.rikka.shizuku:api dan panggil Shizuku.pingBinder().
     */
    fun isShizukuPackageInstalled(context: android.content.Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Heuristik sederhana untuk mendeteksi apakah proses berjalan lewat sesi ADB shell aktif. */
    fun hasAdbHint(): Boolean {
        val output = executeShell("getprop init.svc.adbd")
        return output.contains("running")
    }
}
