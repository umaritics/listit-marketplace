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

    // --- ADDED: Flag to distinguish between 'Answered' and 'Swiped Away' ---
    private var isCallActionTaken = false
    private lateinit var dbHelper: ListItDbHelper
    private var currentUserId = -1
    // ----------------------------------------------------------------------

    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- 1. Init Helper & ID for cleanup ---
        dbHelper = ListItDbHelper(this)
        val email = auth.currentUser?.email
        if (email != null) {
            currentUserId = getUserIdByEmail(email)
        }
        // ---------------------------------------

        // Screen Wake Logic
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

        // Clear Notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(111)

        callerName = intent.getStringExtra("CALLER_NAME") ?: "Unknown"
        channelName = intent.getStringExtra("CHANNEL_NAME") ?: ""

        findViewById<TextView>(R.id.tv_caller_name).text = callerName

        // Play Ringtone
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
            isCallActionTaken = true // Mark as handled so onDestroy doesn't delete it

            // A. Tell Database we Accepted
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
            isCallActionTaken = true // Mark as handled

            // A. Tell Database we Rejected (Deletes node)
            updateCallStatus("rejected")

            finish()
        }
    }

    override fun onDestroy() {
        stopRingtone()
        super.onDestroy()

        // --- CLEANUP IF SWIPED AWAY ---
        // If the user didn't click Accept or Decline, but the activity is destroyed
        // (e.g., Back button, Swipe away, App closed), we must delete the node
        // so Home.kt doesn't keep ringing.
        if (!isCallActionTaken) {
            deleteCallNode()
        }
    }

    private fun deleteCallNode() {
        if (currentUserId != -1) {
            val db = FirebaseDatabase.getInstance("https://listit-749b1-default-rtdb.firebaseio.com/")
            val ref = db.getReference("calls").child(currentUserId.toString())
            ref.removeValue()
        }
    }

    private fun updateCallStatus(status: String) {
        if (currentUserId != -1) {
            val db = FirebaseDatabase.getInstance("https://listit-749b1-default-rtdb.firebaseio.com/")
            val ref = db.getReference("calls").child(currentUserId.toString())

            if (status == "rejected") {
                ref.removeValue()
            } else {
                ref.child("status").setValue(status)
            }
        }
    }

    private fun getUserIdByEmail(email: String): Int {
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
}