// com/lat/os/data/SystemDatabase.kt
package com.lat.os.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.*

class SystemDatabase(context: Context) : SQLiteOpenHelper(context, "latos.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT,
                command TEXT,
                response TEXT
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun logCommand(command: String, response: String) {
        val db = writableDatabase
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val ts = sdf.format(Date())
        db.execSQL("INSERT INTO history (timestamp, command, response) VALUES (?, ?, ?)",
            arrayOf(ts, command, response))
    }
}
