import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ListItDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "ListItLocal.db"
        // Incremented version to apply schema changes
        private const val DATABASE_VERSION = 3

        const val TABLE_USERS = "users"
        const val TABLE_ADS = "ads"
        const val TABLE_AD_IMAGES = "ad_images"
        const val TABLE_SAVED_ADS = "saved_ads" // Using this now
        const val TABLE_CHAT_ROOMS = "chat_rooms"
        const val TABLE_MESSAGES = "messages"
    }

    override fun onCreate(db: SQLiteDatabase) {

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

        val createAds = """
            CREATE TABLE $TABLE_ADS (
                ad_id INTEGER PRIMARY KEY,
                user_id INTEGER NOT NULL,
                category TEXT NOT NULL, 
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
                FOREIGN KEY(user_id) REFERENCES $TABLE_USERS(user_id)
            )
        """.trimIndent()

        val createAdImages = """
            CREATE TABLE $TABLE_AD_IMAGES (
                image_id INTEGER PRIMARY KEY,
                ad_id INTEGER NOT NULL,
                image_url TEXT NOT NULL,
                is_primary INTEGER DEFAULT 0,
                FOREIGN KEY(ad_id) REFERENCES $TABLE_ADS(ad_id)
            )
        """.trimIndent()

        // UPDATED: Added is_synced to handle offline saves
        val createSavedAds = """
            CREATE TABLE $TABLE_SAVED_ADS (
                save_id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                ad_id INTEGER NOT NULL,
                is_deleted INTEGER DEFAULT 0, 
                is_synced INTEGER DEFAULT 1,
                created_at TEXT,
                FOREIGN KEY(user_id) REFERENCES $TABLE_USERS(user_id),
                FOREIGN KEY(ad_id) REFERENCES $TABLE_ADS(ad_id)
            )
        """.trimIndent()

        // (Chat tables omitted for brevity, assume they exist as before)
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

        db.execSQL(createUsers)
        db.execSQL(createAds)
        db.execSQL(createAdImages)
        db.execSQL(createSavedAds)
        db.execSQL(createChatRooms)
        db.execSQL(createMessages)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CHAT_ROOMS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SAVED_ADS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_AD_IMAGES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ADS")
        db.execSQL("DROP TABLE IF EXISTS categories")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
    }
}