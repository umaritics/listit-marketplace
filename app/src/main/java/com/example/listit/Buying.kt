package com.example.listit

import ListItDbHelper
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
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

class Buying : AppCompatActivity() {

    private lateinit var dbHelper: ListItDbHelper
    private lateinit var auth: FirebaseAuth

    // We keep two separate lists in memory
    private val buyingList = ArrayList<ChatRoomUI>()
    private val sellingList = ArrayList<ChatRoomUI>()

    // To track which tab is currently active (Default to Buying)
    private var isBuyingTabActive = true

    data class ChatRoomUI(
        val chatId: Int,
        val otherUserName: String,
        val otherUserImage: String,
        val adTitle: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_buying)

        dbHelper = ListItDbHelper(this)
        auth = FirebaseAuth.getInstance()

        setupNavigation()
        setupTabSwitching()


        findViewById<View>(R.id.btn_start_selling).setOnClickListener {
            startActivity(Intent(this, PostAd::class.java))
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        // This runs every time you come back to this screen
        // It ensures the counters go to 0 immediately after you read a chat
        loadChatsFromDB()
        syncChatsFromServer()
    }

    private fun setupTabSwitching() {
        val tabBuying = findViewById<RelativeLayout>(R.id.tab_buying)
        val tabSelling = findViewById<RelativeLayout>(R.id.tab_selling)
        val lineBuying = findViewById<View>(R.id.underline_buying)
        val lineSelling = findViewById<View>(R.id.underline_selling)

        // Note: We use one RecyclerView for both, so we don't need to hide/show
        // specific 'view_buying_content' containers unless you have specific text instructions inside them.
        // For this logic, we just swap the adapter data.

        tabBuying.setOnClickListener {
            isBuyingTabActive = true
            lineBuying.setBackgroundColor(Color.parseColor("#FF913C"))
            lineSelling.setBackgroundColor(Color.parseColor("#E0E0E0"))

            // Show Buying List
            updateRecyclerView(buyingList)
        }

        tabSelling.setOnClickListener {
            isBuyingTabActive = false
            lineSelling.setBackgroundColor(Color.parseColor("#FF913C"))
            lineBuying.setBackgroundColor(Color.parseColor("#E0E0E0"))

            // Show Selling List
            updateRecyclerView(sellingList)
        }
    }

    // In Buying.kt

    private fun loadChatsFromDB() {
        val currentUserEmail = auth.currentUser?.email ?: return
        val currentUserId = getUserIdByEmail(currentUserEmail)

        buyingList.clear()
        sellingList.clear()

        // Variables to track total unread counts
        var totalBuyingUnread = 0
        var totalSellingUnread = 0

        val db = dbHelper.readableDatabase

        val query = "SELECT chat_id, ad_id, buyer_id, seller_id FROM chat_rooms WHERE buyer_id = ? OR seller_id = ? ORDER BY created_at DESC"
        val cursor = db.rawQuery(query, arrayOf(currentUserId.toString(), currentUserId.toString()))

        if (cursor.moveToFirst()) {
            do {
                val chatId = cursor.getInt(0)
                val adId = cursor.getInt(1)
                val buyerId = cursor.getInt(2)
                val sellerId = cursor.getInt(3)

                // NEW: Get unread count for this specific chat
                val unreadCount = getUnreadCountForChat(chatId, currentUserId)
                val adTitle = getAdTitle(adId)

                if (currentUserId == buyerId) {
                    // Buying Chat
                    val otherDetails = getUserDetails(sellerId)
                    buyingList.add(ChatRoomUI(chatId, otherDetails.first, otherDetails.second, adTitle))

                    // Add to total buying unread
                    totalBuyingUnread += unreadCount

                } else if (currentUserId == sellerId) {
                    // Selling Chat
                    val otherDetails = getUserDetails(buyerId)
                    sellingList.add(ChatRoomUI(chatId, otherDetails.first, otherDetails.second, adTitle))

                    // Add to total selling unread
                    totalSellingUnread += unreadCount
                }

            } while (cursor.moveToNext())
        }
        cursor.close()

        // --- UPDATE BADGES UI ---
        updateBadges(totalBuyingUnread, totalSellingUnread)

        if (isBuyingTabActive) {
            updateRecyclerView(buyingList)
        } else {
            updateRecyclerView(sellingList)
        }
    }

// --- NEW HELPER FUNCTIONS ---

    // 1. Calculate unread messages in a specific chat
    private fun getUnreadCountForChat(chatId: Int, currentUserId: Int): Int {
        val db = dbHelper.readableDatabase
        // Count messages in this chat where is_read is 0 AND I am NOT the sender
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE chat_id = ? AND is_read = 0 AND sender_id != ?",
            arrayOf(chatId.toString(), currentUserId.toString())
        )
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        return count
    }

    // 2. Update the UI Textviews
    private fun updateBadges(buyingCount: Int, sellingCount: Int) {
        val badgeBuying = findViewById<TextView>(R.id.badge_buying)
        val badgeSelling = findViewById<TextView>(R.id.badge_selling)

        if (buyingCount > 0) {
            badgeBuying.text = buyingCount.toString()
            badgeBuying.visibility = View.VISIBLE
        } else {
            badgeBuying.visibility = View.GONE
        }

        if (sellingCount > 0) {
            badgeSelling.text = sellingCount.toString()
            badgeSelling.visibility = View.VISIBLE
        } else {
            badgeSelling.visibility = View.GONE
        }
    }
    private fun updateRecyclerView(data: ArrayList<ChatRoomUI>) {
        val rv = findViewById<RecyclerView>(R.id.rv_buying_chats)
        val emptyView = findViewById<TextView>(R.id.empty_buying) // Ensure this ID exists in XML

        rv.layoutManager = LinearLayoutManager(this)

        if (data.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            emptyView.text = if(isBuyingTabActive) "No active buying chats" else "No active selling chats"
            rv.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            rv.visibility = View.VISIBLE
            rv.adapter = ChatListAdapter(data)
        }
    }

    private fun syncChatsFromServer() {
        val email = auth.currentUser?.email ?: return
        val userId = getUserIdByEmail(email)

        val queue = Volley.newRequestQueue(this)
        val url = Constants.BASE_URL + "get_my_chats.php?user_id=$userId"

        val request = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getString("status") == "success") {
                        val chats = json.getJSONArray("data")
                        val db = dbHelper.writableDatabase
                        var hasNewData = false

                        for (i in 0 until chats.length()) {
                            val obj = chats.getJSONObject(i)
                            val serverChatId = obj.getInt("chat_id")

                            val check = db.rawQuery("SELECT chat_id FROM chat_rooms WHERE chat_id = ?", arrayOf(serverChatId.toString()))
                            if (!check.moveToFirst()) {
                                val values = ContentValues().apply {
                                    put("chat_id", serverChatId)
                                    put("ad_id", obj.getInt("ad_id"))
                                    put("buyer_id", obj.getInt("buyer_id"))
                                    put("seller_id", obj.getInt("seller_id"))
                                    put("created_at", obj.getString("created_at"))
                                }
                                db.insert(ListItDbHelper.TABLE_CHAT_ROOMS, null, values)
                                hasNewData = true
                            }
                            check.close()
                        }
                        // Refresh user data then refresh lists
                        syncAllUsers(hasNewData)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            },
            { error -> Log.e("Buying", "Sync Error: ${error.message}") }
        )
        queue.add(request)
    }

    private fun syncAllUsers(shouldReload: Boolean) {
        val queue = Volley.newRequestQueue(this)
        val url = Constants.BASE_URL + "get_users.php"
        val request = StringRequest(Request.Method.GET, url, { response ->
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
                            val values = ContentValues().apply {
                                put("user_id", obj.getInt("user_id"))
                                put("full_name", obj.getString("full_name"))
                                put("email", obj.getString("email"))
                                put("phone_number", obj.getString("phone_number"))
                                put("profile_image_url", obj.getString("profile_image_url"))
                            }
                            db.insertWithOnConflict(ListItDbHelper.TABLE_USERS, null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
                        }
                        // Reload the lists from DB
                        loadChatsFromDB()
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }, {})
        queue.add(request)
    }

    // --- ADAPTER ---
    inner class ChatListAdapter(private val chats: ArrayList<ChatRoomUI>) : RecyclerView.Adapter<ChatListAdapter.ChatHolder>() {

        inner class ChatHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val name: TextView = itemView.findViewById(R.id.chat_row_username)
            val msg: TextView = itemView.findViewById(R.id.chat_row_last_msg)
            val img: ImageView = itemView.findViewById(R.id.chat_row_pfp)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_row, parent, false)
            return ChatHolder(view)
        }

        override fun onBindViewHolder(holder: ChatHolder, position: Int) {
            val chat = chats[position]
            holder.name.text = chat.otherUserName
            holder.msg.text = "Regarding: ${chat.adTitle}"

            val path = chat.otherUserImage
            if (path.isNotEmpty()) {
                if (path.startsWith("/")) {
                    val imgFile = File(path)
                    if (imgFile.exists()) {
                        Glide.with(holder.itemView.context).load(imgFile).circleCrop().into(holder.img)
                    }
                } else {
                    val fullUrl = if (path.startsWith("http")) path else Constants.BASE_URL + path
                    Glide.with(holder.itemView.context).load(fullUrl).circleCrop().placeholder(R.drawable.ic_profile_placeholder).into(holder.img)
                }
            } else {
                holder.img.setImageResource(R.drawable.ic_profile_placeholder)
            }

            holder.itemView.setOnClickListener {
                val intent = Intent(this@Buying, Chat_message::class.java)
                intent.putExtra("CHAT_ID", chat.chatId)
                intent.putExtra("OTHER_NAME", chat.otherUserName)
                intent.putExtra("OTHER_IMAGE", chat.otherUserImage) // Passing the image
                startActivity(intent)
            }
        }
        override fun getItemCount() = chats.size
    }

    private fun getUserIdByEmail(email: String): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT user_id FROM users WHERE email = ?", arrayOf(email))
        var id = -1
        if (cursor.moveToFirst()) id = cursor.getInt(0)
        cursor.close()
        return id
    }

    private fun getUserDetails(userId: Int): Pair<String, String> {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT full_name, profile_image_url FROM users WHERE user_id = ?", arrayOf(userId.toString()))
        var name = "Unknown User"
        var image = ""
        if (cursor.moveToFirst()) {
            name = cursor.getString(0)
            image = cursor.getString(1) ?: ""
        }
        cursor.close()
        return Pair(name, image)
    }

    private fun getAdTitle(adId: Int): String {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT title FROM ads WHERE ad_id = ?", arrayOf(adId.toString()))
        var title = "Product"
        if (cursor.moveToFirst()) {
            title = cursor.getString(0)
        }
        cursor.close()
        return title
    }

    private fun setupNavigation() {
        findViewById<ImageView>(R.id.home_ic).setOnClickListener { startActivity(Intent(this, Home::class.java)); overridePendingTransition(0,0) }
        findViewById<ImageView>(R.id.add_ic).setOnClickListener { startActivity(Intent(this, PostAd::class.java)); overridePendingTransition(0,0) }
        findViewById<ImageView>(R.id.fav_ic).setOnClickListener { startActivity(Intent(this, MyAds::class.java)); overridePendingTransition(0,0) }
        findViewById<ImageView>(R.id.prof_ic).setOnClickListener { startActivity(Intent(this, Profile::class.java)); overridePendingTransition(0,0) }
    }
}