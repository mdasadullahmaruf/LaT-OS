
// com/lat/os/ui/HistoryActivity.kt
package com.lat.os.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.lat.os.data.SystemDatabase

class HistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(0xFF121212.toInt())
        }

        val db = SystemDatabase(this)
        val cursor = db.readableDatabase.rawQuery("SELECT * FROM history ORDER BY timestamp DESC LIMIT 200", null)

        if (cursor.moveToFirst()) {
            do {
                val time = cursor.getString(cursor.getColumnIndexOrThrow("timestamp"))
                val cmd = cursor.getString(cursor.getColumnIndexOrThrow("command"))
                val resp = cursor.getString(cursor.getColumnIndexOrThrow("response"))
                val entry = TextView(this).apply {
                    text = "[$time] CMD: $cmd\nRESP: $resp\n"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                }
                root.addView(entry)
            } while (cursor.moveToNext())
        } else {
            root.addView(TextView(this).apply {
                text = "No history yet."
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
            })
        }
        cursor.close()
        scroll.addView(root)
        setContentView(scroll)
    }
}
