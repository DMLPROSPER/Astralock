package com.example.astralock

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var constellationView: ConstellationLockView
    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvStatus: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

    private val timeRunnable = object : Runnable {
        override fun run() {
            val now = Date()
            tvTime.text = timeFormat.format(now)
            tvDate.text = dateFormat.format(now)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        tvTime = findViewById(R.id.tvTime)
        tvDate = findViewById(R.id.tvDate)
        tvStatus = findViewById(R.id.tvStatus)
        constellationView = findViewById(R.id.constellationView)

        constellationView.onUnlockListener = {
            tvStatus.text = "✦  welcome home  ✦"
            tvStatus.setTextColor(0xFFa0e0ff.toInt())
        }

        constellationView.onWrongListener = {
            tvStatus.text = "✦  wrong pattern  ✦"
            tvStatus.setTextColor(0xFFff6666.toInt())
            handler.postDelayed({
                if (constellationView.getStatus() != ConstellationLockView.Status.UNLOCKED) {
                    tvStatus.text = "connect the constellation"
                    tvStatus.setTextColor(0x66FFFFFF)
                }
            }, 900)
        }

        constellationView.onResetListener = {
            tvStatus.text = "connect the constellation"
            tvStatus.setTextColor(0x66FFFFFF)
        }

        handler.post(timeRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeRunnable)
    }
}
