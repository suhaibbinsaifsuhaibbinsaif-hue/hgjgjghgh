package com.example.bot

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class CloudStorageRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Using a public anonymous upload API (like https://uguu.se/ or similar)
    // for demonstration. In production, replace with Firebase/S3 SDKs.
    suspend fun uploadFile(file: File): String? = withContext(Dispatchers.IO) {
        try {
            Log.d("CloudStorage", "Uploading file: ${file.absolutePath}")
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "files[]",
                    file.name,
                    file.asRequestBody("image/png".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("https://uguu.se/upload.php") // Public ephemeral file host for demonstration
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                // Parse Uguu response format
                val json = JSONObject(responseBody)
                if (json.getBoolean("success")) {
                    val files = json.getJSONArray("files")
                    if (files.length() > 0) {
                        return@withContext files.getJSONObject(0).getString("url")
                    }
                }
            } else {
                Log.e("CloudStorage", "Upload failed: ${response.code} $responseBody")
            }
        } catch (e: Exception) {
            Log.e("CloudStorage", "Upload exception", e)
        }
        return@withContext null
    }
}
