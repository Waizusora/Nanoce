package com.waizusora.nanoce

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val nativeBridge by lazy { NativeBridge() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = TextView(this).apply {
            text = "Nanoce"
            textSize = 28f
            setTextColor(0xFFE6EAF0.toInt())
        }

        val subtitle = TextView(this).apply {
            text = "Nano Code Editor"
            textSize = 14f
            setTextColor(0xFF9AA4B2.toInt())
        }

        val version = TextView(this).apply {
            text = "Native core: ${nativeBridge.nativeVersion()}"
            textSize = 12f
            setTextColor(0xFF7F8A99.toInt())
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(0xFF151922.toInt())
            addView(title)
            addView(subtitle)
            addView(version)
        }

        setContentView(content)
    }
}
