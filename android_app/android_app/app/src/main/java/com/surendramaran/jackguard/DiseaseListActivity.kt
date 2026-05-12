package com.surendramaran.jackguard

import android.content.Intent
import android.os.Bundle
import android.widget.RelativeLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class DiseaseListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_disease_list)


        val pest1: RelativeLayout = findViewById(R.id.pest1)
        val pest2: RelativeLayout = findViewById(R.id.pest2)

        pest1.setOnClickListener {
            openDetailViewActivity("Healthy")
        }

        pest2.setOnClickListener {
            openDetailViewActivity("Diseased")
        }

    }

    private fun openDetailViewActivity(pestName: String) {
        val intent = Intent(this, DetailsViewActivity::class.java)
        intent.putExtra("TYPE", pestName) // Pass pest name to the detail activity
        startActivity(intent)
    }

}