package com.surendramaran.jackguard

import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.surendramaran.jackguard.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), Detector.DetectorListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var detector: Detector
    private lateinit var cameraExecutor: ExecutorService

    private enum class Side { FRONT, BACK }
    private var currentSide: Side = Side.FRONT
    private var pendingSide: Side? = null

    private var frontPercent: Int? = null
    private var backPercent: Int? = null

    private var bestBox: BoundingBox? = null

    // ✅ Track if user already captured at least 1 photo
    private var hasAnyCapture: Boolean = false

    private val labelDisplay = mapOf(
        "Healthy_Jackfruit" to "Healthy Jackfruit",
        "R10" to "Rhizopus Damage (R10)",
        "R25" to "Rhizopus Damage (R25)",
        "R50" to "Rhizopus Damage (R50)",
        "R100" to "Rhizopus Damage (R100)"
    )

    private fun damagePercentFromLabel(label: String): Int = when (label) {
        "Healthy_Jackfruit" -> 0
        "R10" -> 10
        "R25" -> 25
        "R50" -> 50
        "R100" -> 100
        else -> 0
    }

    private fun bucketFromTotal(total: Int): String = when {
        total >= 100 -> "R100"
        total >= 50 -> "R50"
        total >= 25 -> "R25"
        total >= 10 -> "R10"
        else -> "Healthy_Jackfruit"
    }

    /** Camera capture (returns null if user cancels) */
    private val takePreviewLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
            if (bmp == null) {
                // ✅ User canceled camera
                pendingSide = null
                Toast.makeText(this, "Capture canceled.", Toast.LENGTH_SHORT).show()

                // If no capture happened yet, hide panels again
                if (!hasAnyCapture) {
                    hidePanelsToStartupState()
                } else {
                    // If we already had a capture earlier, keep UI but restore message
                    binding.noDetectionMessage.text =
                        if (currentSide == Side.FRONT) "Ready.\nCapture FRONT side."
                        else "Ready.\nCapture BACK side."
                    binding.capturePlaceholder.visibility =
                        if (binding.capturedImageView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }
                return@registerForActivityResult
            }

            // ✅ A real image was captured -> show panels (commit)
            showPanels()
            hasAnyCapture = true
            processCapturedImage(bmp)
        }

    /** Permission request */
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchCameraNow()
            } else {
                showPermissionDeniedDialog()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        detector = Detector(baseContext, Constants.MODEL_PATH, Constants.LABELS_PATH, this)
        detector.setup()

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Start hidden
        hidePanelsToStartupState()

        binding.captureButton.setOnClickListener {
            // Do NOT show panels yet; only show after a real capture.
            // We just attempt to launch camera.
            ensureCameraPermissionThenLaunch()
        }

        // Hold to reset and hide again
        binding.captureButton.setOnLongClickListener {
            resetAllAndHide()
            true
        }

        binding.buttonShowInfo.setOnClickListener {
            val total = computeTotalOrNull()
            if (total == null) {
                Toast.makeText(this, "Capture FRONT and BACK first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val bucket = bucketFromTotal(total.coerceAtMost(100))
            when (bucket) {
                "R10", "R25", "R50" -> showRhizopusInfoDialog(bucket)
                "R100" -> Toast.makeText(this, "No recommender needed (R100).", Toast.LENGTH_SHORT).show()
                else -> Toast.makeText(this, "No recommender needed (Healthy).", Toast.LENGTH_SHORT).show()
            }
        }

        binding.questionButton.setOnClickListener {
            startActivity(Intent(this, DiseaseListActivity::class.java))
        }
    }

    private fun ensureCameraPermissionThenLaunch() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            launchCameraNow()
        } else {
            requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun launchCameraNow() {
        // Set which side we are capturing
        pendingSide = currentSide

        // Optional: show a quick toast (no UI panels yet)
        Toast.makeText(
            this,
            if (currentSide == Side.FRONT) "Capturing FRONT..." else "Capturing BACK...",
            Toast.LENGTH_SHORT
        ).show()

        takePreviewLauncher.launch(null)
    }

    private fun showPanels() {
        binding.captureCard.visibility = View.VISIBLE
        binding.resultPanel.visibility = View.VISIBLE

        // If no image yet, show placeholder (but after first capture, image will appear)
        if (binding.capturedImageView.visibility != View.VISIBLE) {
            binding.capturePlaceholder.visibility = View.VISIBLE
        }
    }

    private fun hidePanelsToStartupState() {
        binding.captureCard.visibility = View.GONE
        binding.resultPanel.visibility = View.GONE
        binding.buttonShowInfo.visibility = View.GONE

        binding.captureButton.text = "CAPTURE FRONT"
        currentSide = Side.FRONT
        pendingSide = null

        // reset content safely (even if hidden)
        binding.tvFront.text = "Front: -"
        binding.tvBack.text = "Back: -"
        binding.tvTotal.text = "Total Damage: -"
        binding.noDetectionMessage.text = "Ready.\nTap CAPTURE FRONT to start."

        binding.overlay.clear()
        binding.overlay.visibility = View.GONE
        binding.capturedImageView.visibility = View.GONE
        binding.capturePlaceholder.visibility = View.GONE

        hasAnyCapture = false
        frontPercent = null
        backPercent = null
        bestBox = null
    }

    private fun resetAllAndHide() {
        hidePanelsToStartupState()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Camera Permission Needed")
            .setMessage("Camera permission is denied/revoked. Please allow it in Settings to capture images.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun processCapturedImage(bitmap: Bitmap) {
        val loadingDialog = AlertDialog.Builder(this)
            .setView(R.layout.dialog_loading)
            .setCancelable(false)
            .create()

        loadingDialog.show()

        // show image
        binding.capturePlaceholder.visibility = View.GONE
        binding.capturedImageView.visibility = View.VISIBLE
        binding.capturedImageView.setImageBitmap(bitmap)

        binding.overlay.clear()
        binding.overlay.visibility = View.GONE

        binding.noDetectionMessage.text = "Analyzing ${pendingSide ?: currentSide}..."

        Thread {
            detector.detect(bitmap)
            runOnUiThread { loadingDialog.dismiss() }
        }.start()
    }

    override fun onEmptyDetect() {
        runOnUiThread {
            bestBox = null
            binding.overlay.clear()
            binding.overlay.visibility = View.GONE
            binding.noDetectionMessage.text = "No detections.\nPlease retake ${pendingSide ?: currentSide}."
            binding.buttonShowInfo.visibility = View.GONE
        }
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            binding.overlay.clear()
            binding.overlay.visibility = View.GONE
            binding.buttonShowInfo.visibility = View.GONE

            if (boundingBoxes.isEmpty()) {
                onEmptyDetect()
                return@runOnUiThread
            }

            bestBox = boundingBoxes.maxByOrNull { it.cnf }
            val rawLabel = bestBox?.clsName
            if (rawLabel.isNullOrBlank()) {
                onEmptyDetect()
                return@runOnUiThread
            }

            val side = pendingSide ?: currentSide
            val pct = damagePercentFromLabel(rawLabel)
            val display = labelDisplay[rawLabel] ?: rawLabel
            val confPct = String.format("%.1f", (bestBox?.cnf ?: 0f) * 100)

            when (side) {
                Side.FRONT -> {
                    frontPercent = pct
                    binding.tvFront.text = "Front: $display ($pct%) • conf $confPct%"
                    binding.noDetectionMessage.text = "Front saved.\nNow capture BACK side."

                    currentSide = Side.BACK
                    binding.captureButton.text = "CAPTURE BACK"

                    val bucketNow = bucketFromTotal((frontPercent ?: 0).coerceAtMost(100))
                    binding.buttonShowInfo.visibility =
                        if (bucketNow == "R10" || bucketNow == "R25" || bucketNow == "R50") View.VISIBLE else View.GONE
                }

                Side.BACK -> {
                    backPercent = pct
                    binding.tvBack.text = "Back: $display ($pct%) • conf $confPct%"

                    val total = computeTotalOrNull() ?: 0
                    val clamped = total.coerceAtMost(100)

                    binding.tvTotal.text =
                        "Total Damage: $clamped% (Front ${frontPercent ?: 0}% + Back ${backPercent ?: 0}%)"

                    binding.noDetectionMessage.text = "Done.\nHold button to RESET."
                    binding.captureButton.text = "DONE (HOLD TO RESET)"

                    val bucket = bucketFromTotal(clamped)
                    binding.buttonShowInfo.visibility =
                        if (bucket == "R10" || bucket == "R25" || bucket == "R50") View.VISIBLE else View.GONE
                }
            }

            pendingSide = null
        }
    }

    private fun computeTotalOrNull(): Int? {
        val f = frontPercent
        val b = backPercent
        if (f == null || b == null) return null
        return f + b
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.clear()
        cameraExecutor.shutdown()
    }

    private fun showRhizopusInfoDialog(diseaseLevel: String) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_rhizopus_info)
        dialog.window?.setLayout(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(true)

        val infoTextView = dialog.findViewById<TextView>(R.id.infoTextView)
        val diseaseDescriptionText = dialog.findViewById<TextView>(R.id.diseaseDescription)

        when (diseaseLevel) {
            "R10" -> {
                infoTextView.text = "10% Rhizopus Damage (Total)"
                diseaseDescriptionText.text = """
- Maintain cleanliness around trees (sanitation, remove weeds)
- Provide adequate spacing for air circulation
- Ensure proper drainage
                """.trimIndent()
            }
            "R25" -> {
                infoTextView.text = "25% Rhizopus Damage (Total)"
                diseaseDescriptionText.text = """
- Use chicken dung as fertilizer
- Apply compost or manures
- Cover the fruit with plastic or sack
                """.trimIndent()
            }
            "R50" -> {
                infoTextView.text = "50% Rhizopus Damage (Total)"
                diseaseDescriptionText.text = """
- Utilize extracts from Kamantigue (violet/dark-red varieties)
  via bark spraying or injection
                """.trimIndent()
            }
            else -> {
                infoTextView.text = "Unknown Level"
                diseaseDescriptionText.text = "No specific recommendations available."
            }
        }

        dialog.findViewById<Button>(R.id.closeButton).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}