package com.example.listit

import ListItDbHelper
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas

class VideoCallActivity : AppCompatActivity() {

    // Your Agora App ID
    private val APP_ID = "aeaac8205bdd4d0198f3913358395694"

    private var channelName: String = "testChannel"
    private var rtcEngine: RtcEngine? = null

    private lateinit var localContainer: FrameLayout
    private lateinit var remoteContainer: FrameLayout
    private lateinit var btnEnd: ImageView

    // --- ADDED FOR CLEANUP ---
    private lateinit var dbHelper: ListItDbHelper
    private var currentUserId = -1
    // -------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)

        // --- 1. GET USER ID (Needed to know which node to delete) ---
        dbHelper = ListItDbHelper(this)
        val email = FirebaseAuth.getInstance().currentUser?.email
        if (email != null) {
            currentUserId = getUserIdByEmail(email)
        }
        // ------------------------------------------------------------

        localContainer = findViewById(R.id.local_video_view_container)
        remoteContainer = findViewById(R.id.remote_video_view_container)
        btnEnd = findViewById(R.id.btn_end_call)

        val passedChannel = intent.getStringExtra("CHANNEL_NAME")
        if (!passedChannel.isNullOrEmpty()) {
            channelName = passedChannel
        }

        if (checkPermissions()) {
            initAgora()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 101)
        }

        btnEnd.setOnClickListener { finish() }
    }

    private fun initAgora() {
        try {
            rtcEngine = RtcEngine.create(baseContext, APP_ID, object : IRtcEngineEventHandler() {
                override fun onUserJoined(uid: Int, elapsed: Int) {
                    runOnUiThread { setupRemoteVideo(uid) }
                }
                override fun onUserOffline(uid: Int, reason: Int) {
                    runOnUiThread {
                        Toast.makeText(this@VideoCallActivity, "Call Ended", Toast.LENGTH_SHORT).show()
                        finish() // This triggers onDestroy
                    }
                }
            })
        } catch (e: Exception) { e.printStackTrace(); return }

        rtcEngine?.enableVideo()

        val surfaceView = SurfaceView(baseContext)
        surfaceView.setZOrderMediaOverlay(true)
        localContainer.addView(surfaceView)
        rtcEngine?.setupLocalVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0))

        val options = ChannelMediaOptions().apply {
            channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
        }
        rtcEngine?.joinChannel(null, channelName, 0, options)
    }

    private fun setupRemoteVideo(uid: Int) {
        val surfaceView = SurfaceView(baseContext)
        remoteContainer.removeAllViews()
        remoteContainer.addView(surfaceView)
        rtcEngine?.setupRemoteVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, uid))
    }

    override fun onDestroy() {
        super.onDestroy()

        // --- 2. CLEANUP FIREBASE NODE ---
        deleteCallNode()
        // --------------------------------

        rtcEngine?.leaveChannel()
        RtcEngine.destroy()
    }

    private fun deleteCallNode() {
        if (currentUserId != -1) {
            // Remove the call node for THIS user so Home.kt stops finding it
            val db = FirebaseDatabase.getInstance("https://listit-749b1-default-rtdb.firebaseio.com/")
            val ref = db.getReference("calls").child(currentUserId.toString())
            ref.removeValue()
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

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
}