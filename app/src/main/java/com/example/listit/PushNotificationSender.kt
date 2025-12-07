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

    // KEEP YOUR SERVICE ACCOUNT JSON STRING EXACTLY AS IT WAS
    private const val SERVICE_ACCOUNT_JSON = """
{
  "type": "service_account",
  "project_id": "listit-749b1",
  "private_key_id": "aaf3baa432ac2a9cf4f9d537f39e7f1d734687be",
  "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQD1JZe/xXKxc/QP\n5RT7uF7I7jYBCflM8zy/LFpU9r2p81K//i9nJTLGZ2iUyYPbwPiu3iG8GJRhLfHG\nkMxmJivCQ6H7VwTbUczclbsciMvcPmaNUfR/QevZzaQC4ha+obHRPBGJvL//afOO\ns9TMvUTOm7Gdqza9Exsc2w8Q/BUVfj1wUq5tVWJLyu8HJZ1LoRhpQePJID3vWTaK\nwEZ3+e+gaLlrbBW6gCC0ixjYbaQIEbgkkbl8uqEzOH+ei/G8B/2XtPApP/NiMGUN\nW2iMUJ6J6ZYndMDLovKxlTXMmFyW4+HW94daFz0TpGTaDhx5iABiO8tG1G3pn16S\nryAHmhfnAgMBAAECggEAVDoPUldPmcKiJ1fpBreI8XZO3bqEijjENVbSzoBcF+k/\nFDIMLV8te9eJqh02jalWiBZP9uVnDaCQgk27vJj+zecY31c9lvEa9usG973UdR6H\nc6Sl4ZdPpmyuHkN51xONGbuOtwk/2kyX3v7QbWvWGTqLIwXxb7MBzL9DBO3nfJNP\n+ZsBSWPz0bDoQ9Iw9fzI/04Dsf3vJRNHKwJ94MY8daOhd9pQwGROHgaBDUxA+J1E\nHgMPh+Z5dypM4ZPHuNWxivpCPVuhj1tzjSltWM/50U19qxgJ97sMEda8CrV6zGa1\nDag4wHLaoHBldjPMERtpYRyGS+Iytbq166b/AKGTuQKBgQD6mMbGAEiaUsrvD/GN\ntxsaQs5F8rbYjRJnQJEI12UtEMQ+jwRJh23wdgzRYyQrCR5ISc4n5hGljxXFkea7\n7glu7q2lxAu4T9aU61eofwfTXbcPNzCEwCNxNAIyTu89hRVTujMca1oVlR8GFV+o\n/GMVQUFRSgv+nw8IJQ6+e367YwKBgQD6brv0kFNndrNq4nMMc5zMro1SgVcc4enl\n4IjXsxDrlSH+mgMJe5VE92UJ5XDIw3zEtgRGpK2g+dnCP3sYZZ7Y3uXfxKCI2TKi\nxjKEavOQJAIPgou8E3HpGWcPzHRc0SUAUrX/Npi8554TmYs3B1HjsuREkgJgcaV0\nRKoMfL2SrQKBgDphhU1zm3Z4e1aefEPruKCxl6SsGvTwSK1NWXyZ0bRiB5Ybc4A9\n0NsIZYwScMal5SwqJaEd9FaBsyzIBN0bY484g7PurFxQHUmsWkui7IvNdWxSCzei\nG5+v4iMeSJYofwN2iZnBWMdWalfceuC/i8XT4geyHIFBRRs8puaxlqDJAoGBAJR8\nV8E4Wdt8zADR57k4S34o+O40djxPzulX6otKRvwH3rIhCy/yMJ1FuojVm7vN/Qp9\niaeBONm7itvb29apWjfoYY/9+9loPte4gHd3GpcaYoZjtwp61Q2K3ErHxS7Law73\n+6Uo8AMBqf6hCaRFGM7TYPkvQW2BtMJtfA4PYC+5AoGBANKtPtgbYuwh/M5ndICx\nsyhEGYRAfNWuHmrlpvmaeZS87k0qufRjQG5lg//111xYkPwZH+GJrdaX5Vyp70PU\nl4afwY3dK862IUG2Bss/alXX99xCdWMNzfQ2PGNpGTmXYb39A25JxX+zHzTx1UdK\n+thiIDQ9AayzPwzg5MbQG7E1\n-----END PRIVATE KEY-----\n",
  "client_email": "firebase-adminsdk-fbsvc@listit-749b1.iam.gserviceaccount.com",
  "client_id": "104112192833817863631",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token",
  "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
  "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/firebase-adminsdk-fbsvc%40listit-749b1.iam.gserviceaccount.com",
  "universe_domain": "googleapis.com"
}
"""

    suspend fun sendCallNotification(targetToken: String, callerId: Int, callerName: String, callerImage: String, channelName: String) {
        withContext(Dispatchers.IO) {
            try {
                val dataMap = mapOf(
                    "type" to "call",
                    "callerId" to callerId.toString(),
                    "callerName" to callerName,
                    "callerImage" to callerImage,
                    "channelName" to channelName
                )

                // IMPORTANT: High priority for Android
                val androidConfig = JSONObject().apply { put("priority", "high") }

                // Pass NULL for title/body so it becomes a DATA ONLY message
                sendPushNotification(targetToken, null, null, dataMap, androidConfig)
            } catch (e: Exception) {
                Log.e("FCM_SEND", "Error sending call: ${e.message}")
            }
        }
    }

    suspend fun sendAdSavedNotification(targetToken: String, saverName: String) {
        // Normal notification still needs title/body
        sendPushNotification(
            targetToken = targetToken,
            title = "Ad Saved! ❤️",
            body = "$saverName just saved your ad.",
            data = mapOf("type" to "save")
        )
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

                    // FIXED: Only add notification block if title/body exist
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
                Log.d("FCM_SEND", "Response Code: $responseCode") // Check Logcat for "200"

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