package com.example.bot

import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.text.DecimalFormat

class CommandProcessor(private val context: Context) {

    fun getBatteryLevel(): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return "Battery: $level%"
    }

    fun getStorageInfo(): String {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val totalSize = totalBlocks * blockSize
        val availableSize = availableBlocks * blockSize
        val usedSize = totalSize - availableSize

        return """
            Storage:
            Total: ${formatSize(totalSize)}
            Used: ${formatSize(usedSize)}
            Free: ${formatSize(availableSize)}
        """.trimIndent()
    }

    fun getDeviceInfo(): String {
        val actManager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)

        return """
            Device Info:
            Model: ${Build.MODEL}
            Manufacturer: ${Build.MANUFACTURER}
            Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            CPU ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}
            Total RAM: ${formatSize(memInfo.totalMem)}
            Available RAM: ${formatSize(memInfo.availMem)}
        """.trimIndent()
    }

    fun executeShellCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
            process.waitFor()
            if (error.isNotEmpty() && output.isEmpty()) {
                "Error:\n$error"
            } else if (output.isNotEmpty()) {
                output.take(3500) + if (output.length > 3500) "\n...[truncated]" else ""
            } else {
                "Command executed with no output."
            }
        } catch (e: Exception) {
            "Failed to execute command: ${e.message}"
        }
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }
}
