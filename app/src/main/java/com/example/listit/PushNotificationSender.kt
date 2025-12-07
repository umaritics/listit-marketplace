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

    // YOUR KEY
    private const val SERVICE_ACCOUNT_JSON = """
{
  "type": "service_account",
  "project_id": "listit-749b1",
  "private_key_id": "d57345b4c1cdef7198c8a554b377e9b27e88c28f",
  "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCmsULpPdcZaSI0\nHJYRkPLoJSBMzipwVE3wLGmFUxkHU6yGjRFuiuV6qO0qSzEU2d6yeYSsZJ7ZYr0+\nagpEcXkSx3UxvPNF5Bxzi8rcJlTKaQYCQQiduuKAKeXAk0XQqLF0aYyrF17K/ME8\ncaGfQ2WMVDu/3XDy+OUqkaoCPh0q++zhWHdMhakCnbhVaaSs4kGBOG581X47uKLO\nefJ206fQAVhMeQ+kCcgA7a7QHJdZF8YQbA/xD7iRq2NsAW/3pWILcWRF7+48YXpB\nROuIBJhnscmgcn9AUaDrKvEsbJ1Y+1+kuB2i/RcvESR3kRjKZSli59CRKN1wOtKZ\nChCGa15ZAgMBAAECggEAA04lgQF4Z+kVRApDEYMZxe+ihdNatRJ+3yHKT9n3d5U5\nqQtYCqSXa0i5Nyr5hKvTRh+xuUdffPj/vqKUpInJeE1bLgrMmhitVb8yXQ4nhUHy\n3A2VEaeYgSkThK+G5V3K8v96yhOzDfxYCo5IQaOw+kjbTHEkIbU8ugzcXaIEqwr+\nlKQlPqf+Ftm0p9PmHyilBnDJRvowbJax4ibkq2G/wjGsgBIcTuqwh09/bhCqVYlz\nLCm15s74IlW8K6YmuhaHdGkaW0AmyyI+rHX3OQxeWTteAYafydBL19psC4eFxYMK\nEpQp7Zahw3IM7k/x+v+MlYZSWbOXTCgj2PupgpRf9wKBgQDQeh3zmmVE5qw4c11a\nEGZYYVU4xI487hb2bakRWj98ve0im/yZJTuzHiOWnvVboBejj7G+5aVGiHXmTMB4\nbUZMs3bLwe/g18z8iVFFtnLszUk74zLU1y7O+ugLJaVgGKC8nTjbJs+B/VVRWiDZ\n6ZZWHYUaaReewhq947Ag7c8p7wKBgQDMsMNaREC/vps2fo2IW+oYYzMmAarCSGM6\n7+GPmTJUpgnUOayN7sydZ/SZT/b9CYRg1KwetSffA9E3HKEgeBk9JjmuaU4nVy00\nAB/WeX4W2WfRO3/TTNmapqFH8cjavsQc8sQCzgTuhJu8+sqDDQc/zYfFnckkudvr\nHg3lTfhkNwKBgQC5aHGszdpUrcXqqocSa1VqMp4lT4GkpKadYSekfBvMZ+k3B31e\nAiQXB63k7dgONdHwMAKHYRtdIE2ilQ3zzFNiMZVsXz1kPOhcjA9QrZOGEIiaD1SM\nwBcsEy89gqySSzTgqf7/wIN5+wDeygY/ZyPB0J0owOA13DEGQjHJB1Zf1wKBgDrW\nHHEX6Vy0Vz0kx14IvZNhAFTOad0KnatVRIrYSEVYrL6aDWWG3L3qIb7n42D8mVaU\nCx2QiPNrz3l9+zqwCuEu2amuj05zmoS1/HDT31CGEXdtGOMN1gbEGtvpPgjSiOCh\nT4JW4cgFyhZaKFffKNRIKdy97BFoczR0IR5meR2lAoGBAMHdH1jX8+sR/h2WpeRr\n8UK1FtAn0dvslpn7wt+TwrrTFSBOC036hBNOjJiZB4eRVvbw+TG5LZPyBiZlM/D+\naydce2/cisJXCE34E69bPPbgHkU2qQInpeylSWdt5jJ9OnMcAY+7Z0CWuBiEXcXk\nC7bR6rvZn+fLNj8kPrDZef/s\n-----END PRIVATE KEY-----\n",
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
        val dataMap = mapOf(
            "type" to "call",
            "callerId" to callerId.toString(),
            "callerName" to callerName,
            "callerImage" to callerImage,
            "channelName" to channelName
        )
        // High priority for calls
        val androidConfig = JSONObject().apply { put("priority", "high") }

        sendPushNotification(targetToken, null, null, dataMap, androidConfig)
    }

    suspend fun sendAdSavedNotification(targetToken: String, saverName: String) {
        // Putting title/body in DATA map ensures onMessageReceived is always called
        val dataMap = mapOf(
            "type" to "save",
            "title" to "Ad Saved! ❤️",
            "body" to "$saverName just saved your ad."
        )
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

                    // Only add 'notification' block if explicit arguments are passed (standard background behavior)
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