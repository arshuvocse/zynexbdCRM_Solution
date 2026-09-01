package com.zynexbd.crmsolution.activities

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ActivityPunchAttendanceBinding
import com.zynexbd.crmsolution.utils.SessionManager
import com.zynexbd.crmsolution.viewmodel.PunchAttendanceViewModel
import com.zynexbd.crmsolution.viewmodel.PunchUiState
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Selfie-based attendance: captures a front-camera photo, allows interactive cropping/rotation,
 * tags it with the current GPS fix, and submits it as Punch In or Punch Out.
 */
class PunchAttendanceActivity : BaseActivity() {

    private lateinit var binding: ActivityPunchAttendanceBinding
    private lateinit var viewModel: PunchAttendanceViewModel

    private var imageCapture: ImageCapture? = null
    private var capturedFile: File? = null
    private var currentBitmap: Bitmap? = null
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    private var isCropModeActive = false

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) startCamera() else {
            Toast.makeText(this, "ক্যামেরা এবং লোকেশন পারমিশন নেওয়া আবশ্যক।", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPunchAttendanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[PunchAttendanceViewModel::class.java]

        if (hasPermissions()) startCamera() else requestPermissions()
        fetchLocation()

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonCapture.setOnClickListener { captureSelfie() }
        binding.buttonRetake.setOnClickListener { showCameraMode() }
        binding.buttonPunchIn.setOnClickListener { submit(isPunchIn = true) }
        binding.buttonPunchOut.setOnClickListener { submit(isPunchIn = false) }

        // Crop & Rotate Toolbar Handlers
        binding.buttonToggleCrop.setOnClickListener { toggleCropMode() }
        binding.buttonRotate.setOnClickListener { rotateImage90() }
        binding.buttonApplyCrop.setOnClickListener { applyCrop() }

        viewModel.uiState.observe(this) { state ->
            when (state) {
                is PunchUiState.Idle -> {}
                is PunchUiState.Loading -> setControlsEnabled(false)
                is PunchUiState.Error -> {
                    setControlsEnabled(true)
                    val msg = when {
                        state.message.contains("Outside allowed office geofence", ignoreCase = true) -> "অফিস লোকেশন সীমার বাইরে আছেন। নির্দিষ্ট সীমার মধ্যে এসে ট্রাই করুন।"
                        else -> state.message
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
                is PunchUiState.Success -> {
                    setControlsEnabled(true)
                    val isPunchInRequested = intent.getBooleanExtra("EXTRA_IS_PUNCH_IN", true)
                    val msg = if (isPunchInRequested) {
                        getString(R.string.punch_success_in)
                    } else {
                        getString(R.string.punch_success_out)
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun hasPermissions(): Boolean {
        val camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val location = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return camera && location
    }

    private fun requestPermissions() {
        permissionRequest.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION))
    }

    private fun fetchLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val client = LocationServices.getFusedLocationProviderClient(this)
        val request = CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_HIGH_ACCURACY).build()
        client.getCurrentLocation(request, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    currentLatitude = location.latitude
                    currentLongitude = location.longitude
                }
            }
            .addOnFailureListener {
                // Log or handle in background
            }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "ক্যামেরা চালু করতে ব্যর্থ হয়েছে।", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureSelfie() {
        val capture = imageCapture ?: return
        val file = File(cacheDir, "selfie_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturedFile = file
                    val rawBmp = BitmapFactory.decodeFile(file.absolutePath)
                    val watermarked = applyEmployeeWatermark(rawBmp)
                    currentBitmap = watermarked
                    saveBitmapToFile(watermarked)
                    showPreviewMode()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@PunchAttendanceActivity, "ছবি তুলতে ব্যর্থ হয়েছে।", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun applyEmployeeWatermark(srcBitmap: Bitmap): Bitmap {
        val session = SessionManager(this)
        val empName = session.getFullName()?.ifBlank { null } ?: session.getUsername() ?: "Employee"
        val timeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val dateStr = timeFormat.format(Date())

        val result = srcBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val w = result.width.toFloat()
        val h = result.height.toFloat()

        val baseScale = max(1f, min(w, h) / 900f)
        val nameTextSize = 26f * baseScale
        val dateTextSize = 18f * baseScale
        val padding = 18f * baseScale
        val cornerRadius = 12f * baseScale
        val margin = 20f * baseScale

        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = nameTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f * baseScale, 1f, 1f, Color.BLACK)
        }

        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = dateTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            setShadowLayer(3f * baseScale, 1f, 1f, Color.BLACK)
        }

        val nameText = "👤 $empName"
        val dateText = "🕒 $dateStr"

        val nameWidth = namePaint.measureText(nameText)
        val dateWidth = datePaint.measureText(dateText)
        val boxWidth = max(nameWidth, dateWidth) + (padding * 2)
        val boxHeight = nameTextSize + dateTextSize + (padding * 2f)

        val right = w - margin
        val bottom = h - margin
        val left = max(margin, right - boxWidth)
        val top = bottom - boxHeight

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B30F172A") // Modern dark glass card overlay
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#40FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 2f * baseScale
        }

        val rect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        val textX = left + padding
        val nameY = top + padding + (nameTextSize * 0.85f)
        val dateY = nameY + dateTextSize + (8f * baseScale)

        canvas.drawText(nameText, textX, nameY, namePaint)
        canvas.drawText(dateText, textX, dateY, datePaint)

        return result
    }

    private fun showPreviewMode() {
        binding.previewView.visibility = View.GONE
        binding.imageContainer.visibility = View.VISIBLE
        binding.imageCaptured.setImageBitmap(currentBitmap)
        binding.buttonCapture.visibility = View.GONE
        binding.cropToolbarRow.visibility = View.VISIBLE
        binding.confirmRow.visibility = View.VISIBLE
        binding.cropOverlay.visibility = View.GONE
        isCropModeActive = false
        binding.buttonApplyCrop.visibility = View.GONE
        binding.buttonToggleCrop.text = "✂️ Crop"

        val isPunchInRequested = intent.getBooleanExtra("EXTRA_IS_PUNCH_IN", true)
        val confirmText = getString(R.string.action_confirm)
        binding.buttonPunchIn.text = confirmText
        binding.buttonPunchOut.text = confirmText
        binding.buttonPunchIn.visibility = if (isPunchInRequested) View.VISIBLE else View.GONE
        binding.buttonPunchOut.visibility = if (!isPunchInRequested) View.VISIBLE else View.GONE
    }

    private fun showCameraMode() {
        capturedFile?.delete()
        capturedFile = null
        currentBitmap = null
        isCropModeActive = false

        binding.previewView.visibility = View.VISIBLE
        binding.imageContainer.visibility = View.GONE
        binding.cropOverlay.visibility = View.GONE
        binding.buttonCapture.visibility = View.VISIBLE
        binding.cropToolbarRow.visibility = View.GONE
        binding.confirmRow.visibility = View.GONE
    }

    private fun toggleCropMode() {
        val bmp = currentBitmap ?: return
        isCropModeActive = !isCropModeActive

        if (isCropModeActive) {
            binding.cropOverlay.visibility = View.VISIBLE
            binding.buttonApplyCrop.visibility = View.VISIBLE
            binding.buttonToggleCrop.text = "✖ Cancel"

            // Compute image display bounds inside container
            binding.imageContainer.post {
                val containerW = binding.imageContainer.width.toFloat()
                val containerH = binding.imageContainer.height.toFloat()
                if (containerW <= 0 || containerH <= 0) return@post

                val bmpW = bmp.width.toFloat()
                val bmpH = bmp.height.toFloat()

                val scale = min(containerW / bmpW, containerH / bmpH)
                val displayedW = bmpW * scale
                val displayedH = bmpH * scale
                val left = (containerW - displayedW) / 2f
                val top = (containerH - displayedH) / 2f

                binding.cropOverlay.initCropBox(RectF(left, top, left + displayedW, top + displayedH))
            }
        } else {
            binding.cropOverlay.visibility = View.GONE
            binding.buttonApplyCrop.visibility = View.GONE
            binding.buttonToggleCrop.text = "✂️ Crop"
        }
    }

    private fun rotateImage90() {
        val bmp = currentBitmap ?: return
        val matrix = Matrix().apply { postRotate(90f) }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        currentBitmap = rotated
        binding.imageCaptured.setImageBitmap(rotated)

        saveBitmapToFile(rotated)

        if (isCropModeActive) {
            toggleCropMode() // re-init bounds
            toggleCropMode()
        }
    }

    private fun applyCrop() {
        val bmp = currentBitmap ?: return
        val containerW = binding.imageContainer.width.toFloat()
        val containerH = binding.imageContainer.height.toFloat()
        if (containerW <= 0 || containerH <= 0) return

        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()

        val scale = min(containerW / bmpW, containerH / bmpH)
        val displayedW = bmpW * scale
        val displayedH = bmpH * scale
        val dispLeft = (containerW - displayedW) / 2f
        val dispTop = (containerH - displayedH) / 2f

        val cRect = binding.cropOverlay.cropRect

        // Map displayed crop rect back to bitmap coordinates
        val cropX = max(0f, (cRect.left - dispLeft) / scale)
        val cropY = max(0f, (cRect.top - dispTop) / scale)
        val cropW = min(bmpW - cropX, cRect.width() / scale)
        val cropH = min(bmpH - cropY, cRect.height() / scale)

        if (cropW > 10 && cropH > 10) {
            try {
                val cropped = Bitmap.createBitmap(
                    bmp,
                    cropX.toInt(),
                    cropY.toInt(),
                    cropW.toInt(),
                    cropH.toInt()
                )
                val watermarked = applyEmployeeWatermark(cropped)
                currentBitmap = watermarked
                binding.imageCaptured.setImageBitmap(watermarked)
                saveBitmapToFile(watermarked)

                isCropModeActive = false
                binding.cropOverlay.visibility = View.GONE
                binding.buttonApplyCrop.visibility = View.GONE
                binding.buttonToggleCrop.text = "✂️ Crop"
                Toast.makeText(this, "ছবি ক্রপ করা হয়েছে!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "ক্রপ করতে সমস্যা হয়েছে: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap) {
        val file = capturedFile ?: File(cacheDir, "selfie_${System.currentTimeMillis()}.jpg").also { capturedFile = it }
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
    }

    private fun submit(isPunchIn: Boolean) {
        val file = capturedFile
        val lat = currentLatitude
        val lng = currentLongitude

        if (file == null) {
            Toast.makeText(this, "অনুগ্রহ করে প্রথমে একটি সেলফি তুলুন।", Toast.LENGTH_SHORT).show()
            return
        }
        if (lat == null || lng == null) {
            Toast.makeText(this, "লোকেশন এখনো প্রস্তুত নয়। একটু অপেক্ষা করুন।", Toast.LENGTH_SHORT).show()
            return
        }

        if (isPunchIn) viewModel.punchIn(file, lat, lng) else viewModel.punchOut(file, lat, lng)
    }

    private fun setControlsEnabled(enabled: Boolean) {
        binding.buttonPunchIn.isEnabled = enabled
        binding.buttonPunchOut.isEnabled = enabled
        binding.buttonRetake.isEnabled = enabled
        binding.buttonToggleCrop.isEnabled = enabled
        binding.buttonRotate.isEnabled = enabled
        binding.buttonApplyCrop.isEnabled = enabled
        val confirmText = getString(R.string.action_confirm)
        binding.buttonPunchIn.text = if (enabled) confirmText else "প্রসেসিং হচ্ছে..."
        binding.buttonPunchOut.text = if (enabled) confirmText else "প্রসেসিং হচ্ছে..."
    }
}
