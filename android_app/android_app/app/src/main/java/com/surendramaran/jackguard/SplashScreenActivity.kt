package com.surendramaran.jackguard

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SplashScreenActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private val handler = Handler()
    private var progressStatus = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splas_screen)

        // Initialize progress bar
        progressBar = findViewById(R.id.progressBar)

        // Set up window insets for edge-to-edge support
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Start the progress in a new thread
        Thread {
            while (progressStatus < 100) {
                progressStatus += 1 // Increment progress
                handler.post {
                    progressBar.progress = progressStatus
                }
                try {
                    Thread.sleep(30) // Control speed
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }

            // After reaching 100%, start the new activity
            runOnUiThread {
                val intent = Intent(this@SplashScreenActivity, MainActivity::class.java)
                startActivity(intent)
                finish() // Optional: Finish the current activity
            }
        }.start()
    }
}
