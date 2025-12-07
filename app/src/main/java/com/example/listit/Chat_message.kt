package com.example.listit

import ListItDbHelper
import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.lifecycle.lifecycleScope
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class Chat_message : AppCompatActivity() {

    private lateinit var dbHelper: ListItDbHelper
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: MessageAdapter
    private val messageList = ArrayList<Message>()

    private var currentUserId = -1
    private var chatId = -1

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            syncMessagesFromServer()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat_message)

        dbHelper = ListItDbHelper(this)
        auth = FirebaseAuth.getInstance()
        chatId = intent.getIntExtra("CHAT_ID", -1)

        val email = auth.currentUser?.email
        if (email != null) currentUserId = getUserIdByEmail(email)

        markMessagesAsRead()

        // Sync other user data (specifically for FCM Token)
        val otherUserId = getOtherUserIdFromChat(chatId, currentUserId)
        if (otherUserId != -1) {
            syncOtherUserDetails(otherUserId)
        }

        val otherName = intent.getStringExtra("OTHER_NAME") ?: "User"
        val otherImage = intent.getStringExtra("OTHER_IMAGE") ?: ""

        findViewById<TextView>(R.id.username).text = otherName
        findViewById<ImageView>(R.id.back_btn).setOnClickListener { finish() }

        val pfpView = findViewById<ImageView>(R.id.user_pfp)
        if (otherImage.isNotEmpty()) {
            if (otherImage.startsWith("/")) {
                val imgFile = File(otherImage)
                if (imgFile.exists()) Glide.with(this).load(imgFile).circleCrop().into(pfpView)
            } else {
                val fullUrl = if (otherImage.startsWith("http")) otherImage else Constants.BASE_URL + otherImage
                Glide.with(this).load(fullUrl).circleCrop().placeholder(R.drawable.ic_profile_placeholder).into(pfpView)
            }
        }

        setupRecyclerView()
        loadMessages()
        handler.post(refreshRunnable)

        findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.messageInputLayout).setEndIconOnClickListener {
            val input = findViewById<EditText>(R.id.messageEditText)
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                input.setText("")
            }
        }



        // --- OFFER LOGIC ---
        val offerPanel = findViewById<android.widget.RelativeLayout>(R.id.offer_panel)
        val offerBtn = findViewById<ImageView>(R.id.offer_btn)
        val closeOffer = findViewById<ImageView>(R.id.close_offer)
        val sendOfferBtn = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_send_offer)
        val offerInput = findViewById<EditText>(R.id.offer_input)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                return // Don't call yet, wait for permission
            }
        }


        offerBtn.setOnClickListener { offerPanel.visibility = View.VISIBLE }
        closeOffer.setOnClickListener { offerPanel.visibility = View.GONE }
        sendOfferBtn.setOnClickListener {
            val price = offerInput.text.toString().trim()
            if (price.isNotEmpty()) {
                sendMessage("OFFER:$price")
                offerPanel.visibility = View.GONE
                offerInput.setText("")
                Toast.makeText(this, "Offer Sent!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Enter a price", Toast.LENGTH_SHORT).show()
            }
        }

        // --- VIDEO CALL BUTTON ---
        val videoCallBtn = findViewById<ImageView>(R.id.btn_video_call)
        // Inside Chat_message.kt -> onCreate -> videoCallBtn listener

        videoCallBtn.setOnClickListener {
            val targetId = getOtherUserIdFromChat(chatId, currentUserId)
            Log.d("VIDEO_CALL", "Target ID: $targetId") // Debug 1

            if (targetId != -1) {
                val token = getUserToken(targetId)
                Log.d("VIDEO_CALL", "Target Token: $token") // Debug 2

                if (token.isNotEmpty()) {
                    startVideoCall(targetId, token)
                } else {
                    Toast.makeText(this, "Fetching user details... try again", Toast.LENGTH_SHORT).show()
                    syncOtherUserDetails(targetId)
                }
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun syncOtherUserDetails(userId: Int) {
        val queue = Volley.newRequestQueue(this)
        val url = Constants.BASE_URL + "get_users.php"

        val request = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val jsonStartIndex = response.indexOf("{")
                    if (jsonStartIndex != -1) {
                        val cleanResponse = response.substring(jsonStartIndex)
                        val json = JSONObject(cleanResponse)
                        if (json.getString("status") == "success") {
                            val usersArray = json.getJSONArray("data")
                            val db = dbHelper.writableDatabase

                            for (i in 0 until usersArray.length()) {
                                val obj = usersArray.getJSONObject(i)
                                val serverUserId = obj.getInt("user_id")
                                if (serverUserId == userId) {
                                    val token = if(obj.has("fcm_token")) obj.getString("fcm_token") else ""
                                    val values = ContentValues().apply {
                                        put("user_id", serverUserId)
                                        put("full_name", obj.getString("full_name"))
                                        put("email", obj.getString("email"))
                                        put("phone_number", obj.getString("phone_number"))
                                        put("profile_image_url", obj.getString("profile_image_url"))
                                        put("fcm_token", token)
                                    }
                                    db.insertWithOnConflict(ListItDbHelper.TABLE_USERS, null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
                                }
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }, {}
        )
        queue.add(request)
    }

    private fun syncMessagesFromServer() {
        val queue = Volley.newRequestQueue(this)
        val url = Constants.BASE_URL + "get_chat_history.php?chat_id=$chatId"

        val request = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getString("status") == "success") {
                        val msgs = json.getJSONArray("data")
                        val db = dbHelper.writableDatabase
                        var hasNewData = false
                        for (i in 0 until msgs.length()) {
                            val obj = msgs.getJSONObject(i)
                            val serverMsgId = obj.getInt("message_id")
                            val check = db.rawQuery("SELECT message_id FROM messages WHERE message_id = ?", arrayOf(serverMsgId.toString()))
                            if (!check.moveToFirst()) {
                                val values = ContentValues().apply {
                                    put("message_id", serverMsgId)
                                    put("chat_id", chatId)
                                    put("sender_id", obj.getInt("sender_id"))
                                    put("message_text", obj.getString("message_text"))
                                    put("created_at", obj.getString("created_at"))
                                    put("is_read", 1)
                                }
                                db.insert(ListItDbHelper.TABLE_MESSAGES, null, values)
                                hasNewData = true
                            }
                            check.close()
                        }
                        if (hasNewData) loadMessages()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }, {}
        )
        queue.add(request)
    }

    private fun setupRecyclerView() {
        val rv = findViewById<RecyclerView>(R.id.rv_messages)
        rv.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        adapter = MessageAdapter(currentUserId, messageList) { message, position ->
            if (message.senderId == currentUserId) showEditDeleteDialog(message, position)
        }
        rv.adapter = adapter
    }

    private fun loadMessages() {
        messageList.clear()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT message_id, message_text, sender_id, created_at FROM messages WHERE chat_id = ? ORDER BY created_at ASC", arrayOf(chatId.toString()))
        if (cursor.moveToFirst()) {
            do {
                messageList.add(Message(cursor.getInt(0), cursor.getString(1), cursor.getInt(2), cursor.getString(3)))
            } while (cursor.moveToNext())
        }
        cursor.close()
        adapter.notifyDataSetChanged()
        findViewById<RecyclerView>(R.id.rv_messages).scrollToPosition(messageList.size - 1)
    }

    private fun sendMessage(text: String) {
        val db = dbHelper.writableDatabase
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val values = ContentValues().apply {
            put("chat_id", chatId)
            put("sender_id", currentUserId)
            put("message_text", text)
            put("created_at", timestamp)
            put("is_read", 0)
        }
        val localRowId = db.insert(ListItDbHelper.TABLE_MESSAGES, null, values)
        loadMessages()

        val queue = Volley.newRequestQueue(this)
        val url = Constants.BASE_URL + "send_messages.php"
        val request = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getString("status") == "success") {
                        val updateValues = ContentValues().apply { put("message_id", json.getInt("message_id")) }
                        db.update(ListItDbHelper.TABLE_MESSAGES, updateValues, "rowid = ?", arrayOf(localRowId.toString()))
                        loadMessages()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }, {}
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["chat_id"] = chatId.toString()
                params["sender_id"] = currentUserId.toString()
                params["message_text"] = text
                return params
            }
        }
        queue.add(request)
    }

    // --- VIDEO CALL LOGIC ---
    private fun startVideoCall(targetUserId: Int, targetToken: String) {
        val id1 = min(currentUserId, targetUserId)
        val id2 = max(currentUserId, targetUserId)
        val channelName = "call_${id1}_${id2}"
        val myName = getCurrentUserName()

        // 1. SIGNALING: Write to Firebase Realtime DB using explicit URL
        val callData = mapOf(
            "callerId" to currentUserId.toString(),
            "callerName" to myName,
            "channelName" to channelName,
            "type" to "video",
            "status" to "ringing",
            "timestamp" to System.currentTimeMillis()
        )

        // EXPLICIT URL to ensure it finds your DB
        val db = FirebaseDatabase.getInstance("https://listit-749b1-default-rtdb.firebaseio.com/")
        val dbRef = db.getReference("calls").child(targetUserId.toString())

        dbRef.setValue(callData)
            .addOnSuccessListener {
                // 2. NOTIFICATION: Send FCM to wake up other phone
                lifecycleScope.launch {
                    PushNotificationSender.sendCallNotification(
                        targetToken = targetToken,
                        callerId = currentUserId,
                        callerName = myName,
                        callerImage = "",
                        channelName = channelName
                    )
                }
                // 3. UI: Open Video Activity
                val intent = Intent(this, VideoCallActivity::class.java)
                intent.putExtra("CHANNEL_NAME", channelName)
                startActivity(intent)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun getOtherUserIdFromChat(chatId: Int, myId: Int): Int {
        val db = dbHelper.readableDatabase
        var otherId = -1
        val cursor = db.rawQuery("SELECT buyer_id, seller_id FROM chat_rooms WHERE chat_id = ?", arrayOf(chatId.toString()))
        if (cursor.moveToFirst()) {
            val buyer = cursor.getInt(0)
            val seller = cursor.getInt(1)
            otherId = if (buyer == myId) seller else buyer
        }
        cursor.close()
        return otherId
    }

    private fun getUserToken(userId: Int): String {
        val db = dbHelper.readableDatabase
        var token = ""
        val cursor = db.rawQuery("SELECT fcm_token FROM users WHERE user_id = ?", arrayOf(userId.toString()))
        if (cursor.moveToFirst()) token = cursor.getString(0) ?: ""
        cursor.close()
        return token
    }

    private fun getCurrentUserName(): String {
        val db = dbHelper.readableDatabase
        var name = "ListIt User"
        val cursor = db.rawQuery("SELECT full_name FROM users WHERE user_id = ?", arrayOf(currentUserId.toString()))
        if (cursor.moveToFirst()) name = cursor.getString(0)
        cursor.close()
        return name
    }

    private fun markMessagesAsRead() {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply { put("is_read", 1) }
        db.update(ListItDbHelper.TABLE_MESSAGES, values, "chat_id = ?", arrayOf(chatId.toString()))
    }

    private fun getUserIdByEmail(email: String): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT user_id FROM users WHERE email = ?", arrayOf(email))
        var id = -1
        if (cursor.moveToFirst()) id = cursor.getInt(0)
        cursor.close()
        return id
    }

    // Helpers for Dialogs
    private fun showEditDeleteDialog(message: Message, position: Int) {
        val options = arrayOf("Edit", "Delete")
        AlertDialog.Builder(this).setItems(options) { _, which ->
            if(which == 0) showEditDialog(message) else deleteMessage(message)
        }.show()
    }
    private fun deleteMessage(message: Message) {
        dbHelper.writableDatabase.delete(ListItDbHelper.TABLE_MESSAGES, "message_id = ?", arrayOf(message.id.toString()))
        loadMessages()
        val queue = Volley.newRequestQueue(this)
        val request = object : StringRequest(Request.Method.POST, Constants.BASE_URL + "delete_message.php", {}, {}) {
            override fun getParams() = mutableMapOf("message_id" to message.id.toString())
        }
        queue.add(request)
    }
    private fun showEditDialog(message: Message) {
        val input = EditText(this); input.setText(message.messageText)
        AlertDialog.Builder(this).setView(input).setPositiveButton("Update") { _, _ ->
            updateMessage(message.id, input.text.toString())
        }.show()
    }
    private fun updateMessage(msgId: Int, newText: String) {
        dbHelper.writableDatabase.update(ListItDbHelper.TABLE_MESSAGES, ContentValues().apply { put("message_text", newText) }, "message_id = ?", arrayOf(msgId.toString()))
        loadMessages()
        val queue = Volley.newRequestQueue(this)
        val request = object : StringRequest(Request.Method.POST, Constants.BASE_URL + "edit_message.php", {}, {}) {
            override fun getParams() = mutableMapOf("message_id" to msgId.toString(), "message_text" to newText)
        }
        queue.add(request)
    }
}