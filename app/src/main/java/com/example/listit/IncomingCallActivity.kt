package com.example.listit

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class IncomingCallActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var channelName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- FIX: Wake up screen and show over lock screen ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        // -----------------------------------------------------

        setContentView(R.layout.activity_incoming_call)

        // cancel the notification ID used in MyFirebaseService
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(111)

        val callerName = intent.getStringExtra("CALLER_NAME") ?: "Unknown"
        channelName = intent.getStringExtra("CHANNEL_NAME") ?: ""
        // No need for callerId unless you use it for logs

        findViewById<TextView>(R.id.tv_caller_name).text = callerName

        // Play Ringtone (Note: Notification might also play sound, resulting in double sound.
        // It is safer to rely on the Notification sound, but this is fine for now.)
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer.create(this, ringtoneUri)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        findViewById<ImageView>(R.id.btn_accept).setOnClickListener {
            stopRingtone()
            val intent = Intent(this, VideoCallActivity::class.java)
            intent.putExtra("CHANNEL_NAME", channelName)
            startActivity(intent)
            finish()
        }

        findViewById<ImageView>(R.id.btn_decline).setOnClickListener {
            stopRingtone()
            finish()
        }
    }

    private fun stopRingtone() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroy() {
        stopRingtone()
        super.onDestroy()
    }
}