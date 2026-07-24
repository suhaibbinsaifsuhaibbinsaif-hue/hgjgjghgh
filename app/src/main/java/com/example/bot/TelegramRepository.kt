package com.example.bot

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class TelegramRepository(private val botToken: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://api.telegram.org/bot$botToken"

    suspend fun getUpdates(offset: Long): JSONArray = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/getUpdates?offset=$offset&timeout=50"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            if (response.isSuccessful && body != null) {
                val json = JSONObject(body)
                if (json.getBoolean("ok")) {
                    return@withContext json.getJSONArray("result")
                }
            }
        } catch (e: Exception) {
            Log.e("TelegramRepo", "getUpdates failed", e)
        }
        return@withContext JSONArray()
    }

    suspend fun sendMessage(chatId: Long, text: String) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/sendMessage")
                .post(body)
                .build()
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.e("TelegramRepo", "sendMessage failed", e)
        }
    }

    suspend fun sendPhoto(chatId: Long, photo: File, caption: String = "") = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId.toString())
                .addFormDataPart("caption", caption)
                .addFormDataPart(
                    "photo",
                    photo.name,
                    photo.asRequestBody("image/png".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("$baseUrl/sendPhoto")
                .post(requestBody)
                .build()
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.e("TelegramRepo", "sendPhoto failed", e)
        }
    }
}
