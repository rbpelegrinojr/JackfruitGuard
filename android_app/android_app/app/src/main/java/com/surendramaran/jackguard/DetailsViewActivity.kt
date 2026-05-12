package com.surendramaran.jackguard

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class DetailsViewActivity : AppCompatActivity() {

    private lateinit var pestNameTextView: TextView
    private lateinit var scientificNameTextView: TextView
    private lateinit var additionalDetailsTextView: TextView
    private lateinit var image1: ImageView
    private lateinit var image2: ImageView
    private lateinit var image3: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_details_view)

        pestNameTextView = findViewById(R.id.pest_name)
        scientificNameTextView = findViewById(R.id.scientific_name)
        additionalDetailsTextView = findViewById(R.id.additional_details)
        image1 = findViewById(R.id.image1)
        image2 = findViewById(R.id.image2)
        image3 = findViewById(R.id.image3)

        val pestName = intent.getStringExtra("TYPE")

        when (pestName) {
            "Healthy" -> setPestDetails(
                pestName = "HEALTHY JACKFRUIT",
                scientificName = "",
                additionalDetails = """
                    Identifying marks:
                    A healthy jackfruit has a vibrant green to yellow-brown outer rind, with firm, well-formed spiky projections. When ripe, it emits a sweet, fruity aroma, and the inner flesh is golden-yellow, moist, and has a pleasant sweet taste. The seeds are fully developed, and there are no dark spots, shriveling, or mold growth. The texture of the flesh is soft and slightly fibrous, with no signs of decay or unusual discoloration.

                    Where to find:
                    Healthy jackfruits are typically found in tropical climates on trees that grow in well-drained soil. They thrive in regions with abundant sunlight and consistent rainfall.
                """.trimIndent(),
                imageResId1 = R.raw.h1,
                imageResId2 = R.raw.h2,
                imageResId3 = R.raw.h3
            )
            "Diseased" -> setPestDetails(
                pestName = "RHIZOPUS-AFFECTED JACKFRUIT",
                scientificName = "",
                additionalDetails = """
                    Identifying marks:
                    Jackfruit affected by Rhizopus rot shows visible signs of fungal infection. The outer rind may have soft, dark, or mushy spots that appear water-soaked. Infected areas may become discolored, turning brown or black, and there may be a white, cotton-like mold that darkens over time. The inner flesh may appear slimy, with an unpleasant sour odor. The affected areas have softened textures, and the natural sweet aroma is often overpowered by the smell of decay.

                    Where to find:
                    Rhizopus-affected jackfruit is often found in warm, humid conditions, which foster fungal growth. Poorly ventilated storage areas or environments with excessive moisture are common places for Rhizopus infection.
                """.trimIndent(),
                imageResId1 = R.raw.r1,
                imageResId2 = R.raw.r2,
                imageResId3 = R.raw.r3
            )
        }

    }
    private fun setPestDetails(
        pestName: String,
        scientificName: String,
        additionalDetails: String,
        imageResId1: Int,
        imageResId2: Int,
        imageResId3: Int
    ) {
        pestNameTextView.text = pestName
        scientificNameTextView.text = scientificName
        additionalDetailsTextView.text = additionalDetails

        image1.setImageResource(imageResId1)
        image2.setImageResource(imageResId2)
        image3.setImageResource(imageResId3)
    }
}