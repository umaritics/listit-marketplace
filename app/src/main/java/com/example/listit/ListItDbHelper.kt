import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ListItDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "ListItLocal.db"
        private const val DATABASE_VERSION = 1

        // --- Table Names ---
        const val TABLE_USERS = "users"
        const val TABLE_CATEGORIES = "categories"
        const val TABLE_ADS = "ads"
        const val TABLE_AD_IMAGES = "ad_images"
        const val TABLE_SAVED_ADS = "saved_ads"
        const val TABLE_CHAT_ROOMS = "chat_rooms"
        const val TABLE_MESSAGES = "messages"
    }

    override fun onCreate(db: SQLiteDatabase) {

        // 1. Users Table
        val createUsers = """
            CREATE TABLE $TABLE_USERS (
                user_id INTEGER PRIMARY KEY,
                full_name TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                phone_number TEXT,
                profile_image_url TEXT,
                fcm_token TEXT,
                created_at TEXT,
                updated_at TEXT
            )
        """.trimIndent()
        // Note: user_id is NOT autoincrement here because we usually get the ID from the Server (MySQL)
        // and save it here to match. Only local-only data needs autoincrement.

        // 2. Categories Table
        val createCategories = """
            CREATE TABLE $TABLE_CATEGORIES (
                category_id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                icon_url TEXT,
                created_at TEXT
            )
        """.trimIndent()

        // 3. Ads Table
        val createAds = """
            CREATE TABLE $TABLE_ADS (
                ad_id INTEGER PRIMARY KEY,
                user_id INTEGER NOT NULL,
                category_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                description TEXT,
                price REAL NOT NULL,
                condition_type TEXT, 
                location_address TEXT,
                lat REAL,
                lng REAL,
                status TEXT,
                is_deleted INTEGER DEFAULT 0,
                is_synced INTEGER DEFAULT 1, 
                created_at TEXT,
                updated_at TEXT,
                FOREIGN KEY(user_id) REFERENCES $TABLE_USERS(user_id),
                FOREIGN KEY(category_id) REFERENCES $TABLE_CATEGORIES(category_id)
            )
        """.trimIndent()
        // Added 'is_synced' to help you with the Data Sync rubric.
        // 0 = Needs to be uploaded to MySQL. 1 = Already synced.

        // 4. Ad Images Table
        val createAdImages = """
            CREATE TABLE $TABLE_AD_IMAGES (
                image_id INTEGER PRIMARY KEY,
                ad_id INTEGER NOT NULL,
                image_url TEXT NOT NULL,
                is_primary INTEGER DEFAULT 0,
                FOREIGN KEY(ad_id) REFERENCES $TABLE_ADS(ad_id)
            )
        """.trimIndent()

        // 5. Saved Ads Table
        val createSavedAds = """
            CREATE TABLE $TABLE_SAVED_ADS (
                save_id INTEGER PRIMARY KEY,
                user_id INTEGER NOT NULL,
                ad_id INTEGER NOT NULL,
                is_deleted INTEGER DEFAULT 0,
                created_at TEXT,
                FOREIGN KEY(user_id) REFERENCES $TABLE_USERS(user_id),
                FOREIGN KEY(ad_id) REFERENCES $TABLE_ADS(ad_id)
            )
        """.trimIndent()

        // 6. Chat Rooms Table
        val createChatRooms = """
            CREATE TABLE $TABLE_CHAT_ROOMS (
                chat_id INTEGER PRIMARY KEY,
                ad_id INTEGER NOT NULL,
                buyer_id INTEGER NOT NULL,
                seller_id INTEGER NOT NULL,
                created_at TEXT,
                updated_at TEXT,
                FOREIGN KEY(ad_id) REFERENCES $TABLE_ADS(ad_id)
            )
        """.trimIndent()

        // 7. Messages Table
        val createMessages = """
            CREATE TABLE $TABLE_MESSAGES (
                message_id INTEGER PRIMARY KEY,
                chat_id INTEGER NOT NULL,
                sender_id INTEGER NOT NULL,
                message_text TEXT NOT NULL,
                is_read INTEGER DEFAULT 0,
                created_at TEXT,
                FOREIGN KEY(chat_id) REFERENCES $TABLE_CHAT_ROOMS(chat_id)
            )
        """.trimIndent()

        // Execute all creations
        db.execSQL(createUsers)
        db.execSQL(createCategories)
        db.execSQL(createAds)
        db.execSQL(createAdImages)
        db.execSQL(createSavedAds)
        db.execSQL(createChatRooms)
        db.execSQL(createMessages)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Simple strategy for student project: Drop and recreate.
        // In a real app, you would migrate data to avoid losing it.
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CHAT_ROOMS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SAVED_ADS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_AD_IMAGES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ADS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CATEGORIES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
    }
}