package com.example.listit

import ListItDbHelper
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class IncomingCallActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var channelName = ""
    private var callerName = ""

    // Auth to find our own DB node
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Screen Wake Logic (Keep this)
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

        setContentView(R.layout.activity_incoming_call)

        // 2. Clear Notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(111)

        callerName = intent.getStringExtra("CALLER_NAME") ?: "Unknown"
        channelName = intent.getStringExtra("CHANNEL_NAME") ?: ""

        findViewById<TextView>(R.id.tv_caller_name).text = callerName

        // 3. Play Ringtone
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer.create(this, ringtoneUri)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // --- ACCEPT BUTTON ---
        findViewById<ImageView>(R.id.btn_accept).setOnClickListener {
            stopRingtone()

            // A. Tell Database we Accepted (So caller knows)
            updateCallStatus("accepted")

            // B. Join Video
            val intent = Intent(this, VideoCallActivity::class.java)
            intent.putExtra("CHANNEL_NAME", channelName)
            startActivity(intent)
            finish()
        }

        // --- DECLINE BUTTON ---
        findViewById<ImageView>(R.id.btn_decline).setOnClickListener {
            stopRingtone()

            // A. Tell Database we Rejected (So caller stops waiting)
            updateCallStatus("rejected") // Or just removeValue()

            finish()
        }
    }

    private fun updateCallStatus(status: String) {
        val email = auth.currentUser?.email ?: return
        // We need our OWN user ID because the call is stored under 'calls/{myUserId}'
        // Since this is an async lookup, for speed we usually recommend passing userId in intent.
        // But for now, we can query it or assume you have a helper.
        // Ideally:
        val dbHelper = ListItDbHelper(this)
        val myUserId = getUserIdByEmail(email, dbHelper)

        if (myUserId != -1) {
            val db = FirebaseDatabase.getInstance("https://listit-749b1-default-rtdb.firebaseio.com/")
            val ref = db.getReference("calls").child(myUserId.toString())

            if (status == "rejected") {
                ref.removeValue() // Delete the call node entirely
            } else {
                ref.child("status").setValue(status)
            }
        }
    }

    private fun getUserIdByEmail(email: String, dbHelper: ListItDbHelper): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT user_id FROM users WHERE email = ?", arrayOf(email))
        var id = -1
        if (cursor.moveToFirst()) {
            id = cursor.getInt(0)
        }
        cursor.close()
        return id
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