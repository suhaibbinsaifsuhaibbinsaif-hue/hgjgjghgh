package com.example.bot

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.Executors

object ScreenshotUtil {

    private val executor = Executors.newSingleThreadExecutor()

    suspend fun takeScreenshot(context: Context): File? = withContext(Dispatchers.IO) {
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (storageDir?.exists() == false) storageDir.mkdirs()
        
        val file = File(storageDir, "screenshot_${Date().time}.png")

        // 1. Try Root Method First
        if (takeRootScreenshot(file)) {
            Log.d("ScreenshotUtil", "Captured via Root")
            return@withContext file
        }

        // 2. Try AccessibilityService Method (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val service = ScreenshotAccessibilityService.instance
            if (service != null) {
                val deferred = CompletableDeferred<Boolean>()
                service.takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    executor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            try {
                                val bitmap = Bitmap.wrapHardwareBuffer(
                                    screenshot.hardwareBuffer,
                                    screenshot.colorSpace
                                )
                                val fos = FileOutputStream(file)
                                bitmap?.compress(Bitmap.CompressFormat.PNG, 100, fos)
                                fos.close()
                                screenshot.hardwareBuffer.close()
                                deferred.complete(true)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                deferred.complete(false)
                            }
                        }
                        override fun onFailure(errorCode: Int) {
                            Log.e("ScreenshotUtil", "Accessibility Screenshot Failed: $errorCode")
                            deferred.complete(false)
                        }
                    }
                )
                if (deferred.await()) {
                    Log.d("ScreenshotUtil", "Captured via AccessibilityService")
                    return@withContext file
                }
            } else {
                Log.e("ScreenshotUtil", "AccessibilityService not running")
            }
        }

        return@withContext null
    }

    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo root"))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun takeRootScreenshot(file: File): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "screencap -p ${file.absolutePath}"))
            process.waitFor() == 0 && file.exists() && file.length() > 0
        } catch (e: Exception) {
            false
        }
    }
}
