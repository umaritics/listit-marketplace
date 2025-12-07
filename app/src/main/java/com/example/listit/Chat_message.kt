package com.example.listit

import ListItDbHelper
import android.content.ContentValues
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.bumptech.glide.Glide // Added Import
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.io.File // Added Import
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Chat_message : AppCompatActivity() {

    private lateinit var dbHelper: ListItDbHelper
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: MessageAdapter
    private val messageList = ArrayList<Message>()

    private var currentUserId = -1
    private var chatId = -1

    // Auto-refresh handler
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            syncMessagesFromServer()
            handler.postDelayed(this, 3000) // Check for new messages every 3 seconds
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat_message)

        dbHelper = ListItDbHelper(this)
        auth = FirebaseAuth.getInstance()
        chatId = intent.getIntExtra("CHAT_ID", -1)

        markMessagesAsRead()

        val otherName = intent.getStringExtra("OTHER_NAME") ?: "User"

        // --- FIX STARTS HERE: Get Image string ---
        val otherImage = intent.getStringExtra("OTHER_IMAGE") ?: ""

        findViewById<TextView>(R.id.username).text = otherName
        findViewById<ImageView>(R.id.back_btn).setOnClickListener { finish() }

        // --- FIX CONTINUES: Load Profile Pic into Header ---
        val pfpView = findViewById<ImageView>(R.id.user_pfp)

        if (otherImage.isNotEmpty()) {
            if (otherImage.startsWith("/")) {
                // Local File
                val imgFile = File(otherImage)
                if (imgFile.exists()) {
                    Glide.with(this)
                        .load(imgFile)
                        .circleCrop()
                        .into(pfpView)
                }
            } else {
                // URL
                val fullUrl = if (otherImage.startsWith("http")) otherImage else Constants.BASE_URL + otherImage
                Glide.with(this)
                    .load(fullUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .into(pfpView)
            }
        }
        // --- FIX ENDS ---

        val email = auth.currentUser?.email
        if (email != null) currentUserId = getUserIdByEmail(email)

        setupRecyclerView()
        loadMessages()
        handler.post(refreshRunnable)

        // --- NORMAL SEND LOGIC ---
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
        val offerBtn = findViewById<ImageView>(R.id.offer_btn) // The Icon
        val closeOffer = findViewById<ImageView>(R.id.close_offer)
        val sendOfferBtn = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_send_offer)
        val offerInput = findViewById<EditText>(R.id.offer_input)

        // 1. Open Panel
        offerBtn.setOnClickListener {
            offerPanel.visibility = View.VISIBLE
        }

        // 2. Close Panel
        closeOffer.setOnClickListener {
            offerPanel.visibility = View.GONE
        }

        // 3. Send Offer
        sendOfferBtn.setOnClickListener {
            val price = offerInput.text.toString().trim()
            if (price.isNotEmpty()) {
                // Send as special formatted message
                sendMessage("OFFER:$price")
                offerPanel.visibility = View.GONE
                offerInput.setText("")
                Toast.makeText(this, "Offer Sent!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Enter a price", Toast.LENGTH_SHORT).show()
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
        handler.removeCallbacks(refreshRunnable) // Stop timer when closing activity
    }

    // --- Sync from Server ---
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
                            val serverText = obj.getString("message_text")
                            val sender = obj.getInt("sender_id")
                            val date = obj.getString("created_at")

                            // Check if we have this message locally
                            val check = db.rawQuery("SELECT message_id FROM messages WHERE message_id = ?", arrayOf(serverMsgId.toString()))
                            if (!check.moveToFirst()) {
                                // NEW MESSAGE: Insert it using the SERVER ID
                                val values = ContentValues().apply {
                                    put("message_id", serverMsgId)
                                    put("chat_id", chatId)
                                    put("sender_id", sender)
                                    put("message_text", serverText)
                                    put("created_at", date)
                                    put("is_read", 1)
                                }
                                db.insert(ListItDbHelper.TABLE_MESSAGES, null, values)
                                hasNewData = true
                            } else {
                                // UPDATE EXISTING
                                val values = ContentValues().apply {
                                    put("message_text", serverText)
                                }
                                db.update(ListItDbHelper.TABLE_MESSAGES, values, "message_id = ?", arrayOf(serverMsgId.toString()))
                                hasNewData = true
                            }
                            check.close()
                        }

                        if (hasNewData) {
                            loadMessages()
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            },
            { error -> }
        )
        queue.add(request)
    }

    private fun setupRecyclerView() {
        val rv = findViewById<RecyclerView>(R.id.rv_messages)
        rv.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }

        adapter = MessageAdapter(currentUserId, messageList) { message, position ->
            // Long Click Logic
            if (message.senderId == currentUserId) {
                showEditDeleteDialog(message, position)
            }
        }
        rv.adapter = adapter
    }

    private fun loadMessages() {
        messageList.clear()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT message_id, message_text, sender_id, created_at FROM messages WHERE chat_id = ? ORDER BY created_at ASC", arrayOf(chatId.toString()))

        if (cursor.moveToFirst()) {
            do {
                val msg = Message(
                    cursor.getInt(0),
                    cursor.getString(1),
                    cursor.getInt(2),
                    cursor.getString(3)
                )
                messageList.add(msg)
            } while (cursor.moveToNext())
        }
        cursor.close()
        adapter.notifyDataSetChanged()
        findViewById<RecyclerView>(R.id.rv_messages).scrollToPosition(messageList.size - 1)
    }

    private fun sendMessage(text: String) {
        // 1. SAVE LOCALLY
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

        // 2. UPLOAD TO SERVER
        val queue = Volley.newRequestQueue(this)
        val url = Constants.BASE_URL + "send_messages.php"

        val request = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getString("status") == "success") {
                        val serverId = json.getInt("message_id")
                        val updateValues = ContentValues().apply { put("message_id", serverId) }
                        db.update(ListItDbHelper.TABLE_MESSAGES, updateValues, "rowid = ?", arrayOf(localRowId.toString()))
                        loadMessages()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            },
            { error -> }
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

    private fun showEditDeleteDialog(message: Message, position: Int) {
        val options = arrayOf("Edit", "Delete")
        AlertDialog.Builder(this)
            .setTitle("Message Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditDialog(message)
                    1 -> deleteMessage(message)
                }
            }
            .show()
    }

    private fun deleteMessage(message: Message) {
        val db = dbHelper.writableDatabase
        db.delete(ListItDbHelper.TABLE_MESSAGES, "message_id = ?", arrayOf(message.id.toString()))
        loadMessages()
        Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show()

        val queue = Volley.newRequestQueue(this)
        val url = Constants.BASE_URL + "delete_message.php"

        val request = object : StringRequest(Request.Method.POST, url,
            { },
            { error -> Toast.makeText(this, "Failed to delete on server", Toast.LENGTH_SHORT).show() }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["message_id"] = message.id.toString()
                return params
            }
        }
        queue.add(request)
    }

    private fun showEditDialog(message: Message) {
        val input = EditText(this)
        input.setText(message.messageText)

        AlertDialog.Builder(this)
            .setTitle("Edit Message")
            .setView(input)
            .setPositiveButton("Update") { _, _ ->
                val newText = input.text.toString()
                updateMessage(message.id, newText)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateMessage(msgId: Int, newText: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply { put("message_text", newText) }
        db.update(ListItDbHelper.TABLE_MESSAGES, values, "message_id = ?", arrayOf(msgId.toString()))
        loadMessages()

        val queue = Volley.newRequestQueue(this)
        val url = Constants.BASE_URL + "edit_message.php"

        val request = object : StringRequest(Request.Method.POST, url,
            { },
            { error -> Toast.makeText(this, "Failed to edit on server", Toast.LENGTH_SHORT).show() }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["message_id"] = msgId.toString()
                params["message_text"] = newText
                return params
            }
        }
        queue.add(request)
    }

    private fun markMessagesAsRead() {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("is_read", 1)
        }
        // Update all messages in THIS chat to be "read"
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
}