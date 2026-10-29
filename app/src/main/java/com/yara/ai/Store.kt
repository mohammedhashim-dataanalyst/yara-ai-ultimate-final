package com.yara.ai

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Msg(val id: Long, val chat: String, val role: String, val text: String, val mood: String, val ts: Long)
data class Mem(val id: Long, val q: String, val a: String, val m: String)
data class Rem(val id: Long, val text: String, val at: Long)

class Store(c: Context) : SQLiteOpenHelper(c, "yara.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE msgs(id INTEGER PRIMARY KEY AUTOINCREMENT, chat TEXT, role TEXT, text TEXT, mood TEXT, ts INTEGER)")
        db.execSQL("CREATE INDEX i_msgs ON msgs(chat, id)")
        db.execSQL("CREATE TABLE mem(id INTEGER PRIMARY KEY AUTOINCREMENT, q TEXT, a TEXT, m TEXT, ts INTEGER, uses INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE facts(k TEXT PRIMARY KEY, v TEXT)")
        db.execSQL("CREATE TABLE rem(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT, at INTEGER, done INTEGER DEFAULT 0)")
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    @Synchronized
    fun addMsg(chat: String, role: String, text: String, mood: String): Long {
        val cv = ContentValues()
        cv.put("chat", chat); cv.put("role", role); cv.put("text", text)
        cv.put("mood", mood); cv.put("ts", System.currentTimeMillis())
        return writableDatabase.insert("msgs", null, cv)
    }

    @Synchronized
    fun msgs(chat: String, limit: Int): List<Msg> {
        val out = ArrayList<Msg>()
        readableDatabase.rawQuery(
            "SELECT id,chat,role,text,mood,ts FROM msgs WHERE chat=? ORDER BY id DESC LIMIT ?",
            arrayOf(chat, limit.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(Msg(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5)))
        }
        out.reverse()
        return out
    }

    fun last(chat: String): Msg? = msgs(chat, 1).firstOrNull()

    @Synchronized
    fun clearChat(chat: String) { writableDatabase.delete("msgs", "chat=?", arrayOf(chat)) }

    @Synchronized
    fun addMemory(q: String, a: String, m: String) {
        val db = writableDatabase
        db.delete("mem", "q=?", arrayOf(q))
        val cv = ContentValues()
        cv.put("q", q); cv.put("a", a); cv.put("m", m); cv.put("ts", System.currentTimeMillis())
        db.insert("mem", null, cv)
        if (memCount() > 6000) {
            db.execSQL("DELETE FROM mem WHERE id IN (SELECT id FROM mem ORDER BY uses ASC, ts ASC LIMIT 500)")
        }
    }

    @Synchronized
    fun memories(): List<Mem> {
        val out = ArrayList<Mem>()
        readableDatabase.rawQuery("SELECT id,q,a,m FROM mem ORDER BY ts DESC LIMIT 4000", null).use { c ->
            while (c.moveToNext()) out.add(Mem(c.getLong(0), c.getString(1), c.getString(2), c.getString(3)))
        }
        return out
    }

    @Synchronized
    fun memCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM mem", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    @Synchronized
    fun useMem(id: Long) { writableDatabase.execSQL("UPDATE mem SET uses=uses+1 WHERE id=?", arrayOf<Any>(id)) }

    @Synchronized
    fun clearMemory() { writableDatabase.delete("mem", null, null) }

    @Synchronized
    fun setFact(k: String, v: String) {
        val cv = ContentValues(); cv.put("k", k); cv.put("v", v)
        writableDatabase.insertWithOnConflict("facts", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun facts(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        readableDatabase.rawQuery("SELECT k,v FROM facts", null).use { c -> while (c.moveToNext()) out[c.getString(0)] = c.getString(1) }
        return out
    }

    @Synchronized
    fun addRem(text: String, at: Long): Long {
        val cv = ContentValues(); cv.put("text", text); cv.put("at", at)
        return writableDatabase.insert("rem", null, cv)
    }

    @Synchronized
    fun pendingRems(): List<Rem> {
        val out = ArrayList<Rem>()
        readableDatabase.rawQuery("SELECT id,text,at FROM rem WHERE done=0 AND at>? ORDER BY at", arrayOf(System.currentTimeMillis().toString())).use { c ->
            while (c.moveToNext()) out.add(Rem(c.getLong(0), c.getString(1), c.getLong(2)))
        }
        return out
    }

    @Synchronized
    fun doneRem(id: Long) { writableDatabase.execSQL("UPDATE rem SET done=1 WHERE id=?", arrayOf<Any>(id)) }
}
