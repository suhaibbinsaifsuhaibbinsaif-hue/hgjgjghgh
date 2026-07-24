package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.bot.BotService
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startBotService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedPrefs = getSharedPreferences("BotPrefs", Context.MODE_PRIVATE)

        setContent {
            var token by remember { mutableStateOf(sharedPrefs.getString("TELEGRAM_BOT_TOKEN", "8629620673:AAGh-E5q2paQUvWjExgH4Jx0Rw4ShVWaFM8") ?: "") }
            var deviceId by remember { mutableStateOf(sharedPrefs.getString("DEVICE_ID", Build.MODEL) ?: Build.MODEL) }

            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BotStatusScreen(
                        modifier = Modifier.padding(innerPadding),
                        token = token,
                        onTokenChange = { 
                            token = it
                            sharedPrefs.edit().putString("TELEGRAM_BOT_TOKEN", it).apply()
                        },
                        deviceId = deviceId,
                        onDeviceIdChange = {
                            deviceId = it
                            sharedPrefs.edit().putString("DEVICE_ID", it).apply()
                        },
                        onStartService = { checkPermissionsAndStart() },
                        onOpenAccessibility = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onOpenBatteryOpt = {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    )
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val requiredPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            requiredPermissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            requiredPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            startBotService()
        }
    }

    private fun startBotService() {
        val serviceIntent = Intent(this, BotService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}

@Composable
fun BotStatusScreen(
    modifier: Modifier = Modifier,
    token: String,
    onTokenChange: (String) -> Unit,
    deviceId: String,
    onDeviceIdChange: (String) -> Unit,
    onStartService: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenBatteryOpt: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Cloud Screenshot Bot",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            label = { Text("Telegram Bot Token") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = deviceId,
            onValueChange = onDeviceIdChange,
            label = { Text("Device ID (Optional)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        if (token.isEmpty() || token == "YOUR_TELEGRAM_BOT_TOKEN") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = "Bot Token is missing!\nPlease add TELEGRAM_BOT_TOKEN in Secrets panel.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    text = "Bot Token is configured.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onStartService, modifier = Modifier.fillMaxWidth()) {
            Text("Start Background Service")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) {
            Text("Enable Accessibility Service (For Screenshots)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = onOpenBatteryOpt, modifier = Modifier.fillMaxWidth()) {
            Text("Disable Battery Optimizations (Keep Alive)")
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Instructions:\n" +
                   "1. Enter Bot Token in AI Studio Secrets\n" +
                   "2. Start Background Service\n" +
                   "3. Enable Accessibility Service if not rooted\n" +
                   "4. Disable Battery Optimizations for persistent background run\n" +
                   "5. Chat with your Bot on Telegram using /ping, /screenshot, /upload",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
