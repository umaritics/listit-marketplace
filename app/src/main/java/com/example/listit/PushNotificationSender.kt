package com.example.listit

import android.util.Log
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object PushNotificationSender {

    private const val FCM_URL = "https://fcm.googleapis.com/v1/projects/listit-749b1/messages:send"

    private const val SERVICE_ACCOUNT_JSON = """
Paste here
"""


    suspend fun sendAdSavedNotification(targetToken: String, saverName: String) {
        val dataMap = mapOf(
            "type" to "save",
            "title" to "Ad Saved! ❤️",
            "body" to "$saverName just saved your ad."
        )
        sendPushNotification(targetToken, null, null, dataMap)
    }
    suspend fun sendChatNotification(targetToken: String, saverName: String) {
        val dataMap = mapOf(
            "type" to "save",
            "title" to "You recived a message!",
            "body" to "$saverName"
        )
        sendPushNotification(targetToken, null, null, dataMap)
    }
    suspend fun sendCallNotification(targetToken: String, saverName: String) {
        val dataMap = mapOf(
            "type" to "save",
            "title" to "You recived a call!",
            "body" to "$saverName"
        )
        sendPushNotification(targetToken, null, null, dataMap)
    }

    // --- NEW: CHAT MESSAGE NOTIFICATION ---
    suspend fun sendMessageNotification(targetToken: String, senderName: String, messageText: String) {
        val dataMap = mapOf(
            "type" to "message",
            "title" to "New Message from $senderName",
            "body" to messageText
        )
        // Sending as data-only so MyFirebaseService handles it in background too
        sendPushNotification(targetToken, null, null, dataMap)
    }

    private suspend fun sendPushNotification(targetToken: String, title: String?, body: String?, data: Map<String, String>? = null, androidConfig: JSONObject? = null) {
        withContext(Dispatchers.IO) {
            try {
                val inputStream = ByteArrayInputStream(SERVICE_ACCOUNT_JSON.toByteArray(StandardCharsets.UTF_8))
                val googleCredentials = GoogleCredentials.fromStream(inputStream)
                    .createScoped(listOf("https://www.googleapis.com/auth/firebase.messaging"))
                googleCredentials.refreshIfExpired()
                val accessToken = googleCredentials.accessToken.tokenValue

                val message = JSONObject().apply {
                    put("token", targetToken)

                    if (title != null && body != null) {
                        put("notification", JSONObject().apply {
                            put("title", title)
                            put("body", body)
                        })
                    }

                    data?.let { d -> put("data", JSONObject(d)) }
                    androidConfig?.let { put("android", it) }
                }

                val json = JSONObject().apply { put("message", message) }

                val conn = (URL(FCM_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", "application/json; UTF-8")
                    doOutput = true
                    outputStream.use { it.write(json.toString().toByteArray(StandardCharsets.UTF_8)) }
                }

                val responseCode = conn.responseCode
                Log.d("FCM_SEND", "Response Code: $responseCode")

                if (responseCode != 200) {
                    val err = conn.errorStream?.bufferedReader()?.readText()
                    Log.e("FCM_SEND", "Error: $err")
                }
            } catch (e: Exception) {
                Log.e("FCM_SEND", "Exception: ${e.message}", e)
            }
        }
    }
}