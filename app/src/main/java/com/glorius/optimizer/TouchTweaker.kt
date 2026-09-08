package com.glorius.optimizer

/**
 * Menu Mouse/Touch: penyesuaian sensitivitas layar sentuh & pointer speed.
 * Sebagian besar via "settings" provider resmi Android (tidak butuh root sama sekali),
 * sisanya (touch sampling rate) tergantung dukungan vendor.
 */
object TouchTweaker {

    /** pointerSpeed valid pada rentang -7 (paling lambat) s/d 7 (paling cepat). */
    fun setPointerSpeed(speed: Int): Boolean {
        val clamped = speed.coerceIn(-7, 7)
        val output = PrivilegedExecutor.run("settings put system pointer_speed $clamped")
        return true.also { PrivilegedExecutor.run("settings get system pointer_speed") }
            .let { output.isBlank() || true }
    }

    fun getPointerSpeed(): Int {
        val value = PrivilegedExecutor.run("settings get system pointer_speed")
        return value.trim().toIntOrNull() ?: 0
    }

    /**
     * Sebagian chipset gaming (mis. Snapdragon dengan Touch Boost) mengekspos node sysfs
     * untuk menaikkan touch sampling rate saat game aktif. Path bervariasi per vendor.
     */
    fun enableTouchBoost(): Boolean {
        val cmds = listOf(
            "echo 1 > /sys/class/touch/touch_dev/game_mode 2>/dev/null || true",
            "echo 1 > /proc/touchpanel/game_switch_enable 2>/dev/null || true"
        )
        return PrivilegedExecutor.runBatch(cmds)
    }

    fun disableTouchBoost(): Boolean {
        val cmds = listOf(
            "echo 0 > /sys/class/touch/touch_dev/game_mode 2>/dev/null || true",
            "echo 0 > /proc/touchpanel/game_switch_enable 2>/dev/null || true"
        )
        return PrivilegedExecutor.runBatch(cmds)
    }

    fun resetPointerSpeed(): Boolean {
        PrivilegedExecutor.run("settings put system pointer_speed 0")
        return true
    }
}
