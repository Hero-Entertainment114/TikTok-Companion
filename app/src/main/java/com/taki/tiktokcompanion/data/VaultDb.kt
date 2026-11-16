package com.taki.tiktokcompanion.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

class VaultDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    private val crypto = CryptoBox()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                package_name TEXT NOT NULL,
                title_cipher TEXT NOT NULL,
                body_cipher TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                edited_cipher TEXT,
                note_cipher TEXT,
                fingerprint TEXT NOT NULL UNIQUE
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE drafts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title_cipher TEXT NOT NULL,
                body_cipher TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX idx_messages_created ON messages(created_at DESC)")
        db.execSQL("CREATE INDEX idx_drafts_updated ON drafts(updated_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v0.1.0 is the first schema. Future upgrades must preserve encrypted records.
    }

    @Synchronized
    fun saveNotification(packageName: String, title: String, body: String, timestamp: Long, notificationKey: String) {
        if (body.isBlank()) return
        val fingerprint = sha256("$packageName|$notificationKey|$title|$body|$timestamp")
        val values = ContentValues().apply {
            put("package_name", packageName)
            put("title_cipher", crypto.encrypt(title))
            put("body_cipher", crypto.encrypt(body))
            put("created_at", timestamp)
            put("fingerprint", fingerprint)
        }
        writableDatabase.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    @Synchronized
    fun getMessages(): List<MessageRecord> {
        val result = mutableListOf<MessageRecord>()
        readableDatabase.query(
            "messages", null, null, null, null, null, "created_at DESC", "500"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += MessageRecord(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    packageName = cursor.getString(cursor.getColumnIndexOrThrow("package_name")),
                    title = crypto.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("title_cipher"))),
                    body = crypto.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("body_cipher"))),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    editedDisplay = cursor.getString(cursor.getColumnIndexOrThrow("edited_cipher"))?.let(crypto::decrypt),
                    note = cursor.getString(cursor.getColumnIndexOrThrow("note_cipher"))?.let(crypto::decrypt)
                )
            }
        }
        return result
    }

    @Synchronized
    fun updateMessagePresentation(id: Long, editedDisplay: String?, note: String?) {
        val values = ContentValues().apply {
            put("edited_cipher", editedDisplay?.takeIf { it.isNotBlank() && it != getOriginalBody(id) }?.let(crypto::encrypt))
            put("note_cipher", note?.takeIf { it.isNotBlank() }?.let(crypto::encrypt))
        }
        writableDatabase.update("messages", values, "id=?", arrayOf(id.toString()))
    }

    private fun getOriginalBody(id: Long): String? {
        readableDatabase.query(
            "messages", arrayOf("body_cipher"), "id=?", arrayOf(id.toString()), null, null, null
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return crypto.decrypt(cursor.getString(0))
        }
    }

    @Synchronized
    fun getNotes(): List<MessageRecord> = getMessages().filter { !it.note.isNullOrBlank() }

    @Synchronized
    fun saveDraft(id: Long?, title: String, body: String) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("title_cipher", crypto.encrypt(title))
            put("body_cipher", crypto.encrypt(body))
            put("updated_at", now)
        }
        if (id == null) {
            values.put("created_at", now)
            writableDatabase.insert("drafts", null, values)
        } else {
            writableDatabase.update("drafts", values, "id=?", arrayOf(id.toString()))
        }
    }

    @Synchronized
    fun getDrafts(): List<DraftRecord> {
        val result = mutableListOf<DraftRecord>()
        readableDatabase.query("drafts", null, null, null, null, null, "updated_at DESC").use { cursor ->
            while (cursor.moveToNext()) {
                result += DraftRecord(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    title = crypto.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("title_cipher"))),
                    body = crypto.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("body_cipher"))),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
                )
            }
        }
        return result
    }

    @Synchronized
    fun deleteDraft(id: Long) {
        writableDatabase.delete("drafts", "id=?", arrayOf(id.toString()))
    }

    @Synchronized
    fun clearMessages() {
        writableDatabase.delete("messages", null, null)
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val DB_NAME = "private_vault.db"
        private const val DB_VERSION = 1
    }
}
