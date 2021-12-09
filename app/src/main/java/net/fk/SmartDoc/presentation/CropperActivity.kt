package net.fk.SmartDoc.presentation

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import net.fk.SmartDoc.databinding.ActivityCropperBinding
import net.fk.SmartDoc.extensions.outputDirectory
import net.fk.SmartDoc.extensions.toByteArray
import net.fk.SmartDoc.extensions.triggerFullscreen
import java.io.File
import java.io.FileOutputStream
import java.util.*
import android.graphics.*
import android.graphics.Bitmap
import net.fk.SmartDoc.extensions.waitForLayout


class CropperActivity : AppCompatActivity() {
    private lateinit var cropModel: CropperModel
    private lateinit var bitmapUri: Uri
    private lateinit var binding: ActivityCropperBinding
    private lateinit var mFilter: CustomFilters

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        triggerFullscreen()
        binding = ActivityCropperBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val extras = intent.extras
        if (extras != null) {
            bitmapUri = intent.extras?.getString("lastUri")?.toUri() ?: error("invalid uri")
        }

        mFilter = CustomFilters
        val cropModel: CropperModel by viewModels()

        // Picture taken from User
        cropModel.original.observe(this, {
            binding.cropPreview.setImageBitmap(cropModel.original.value)
            binding.cropWrap.visibility = View.VISIBLE

            // Wait for bitmap to be loaded on view, then draw corners
            binding.cropWrap.waitForLayout {
                binding.cropHud.onCorners(
                    corners = cropModel.corners.value ?: error("invalid Corners"),
                    height = binding.cropPreview.measuredHeight,
                    width = binding.cropPreview.measuredWidth
                )
            }
        })

        cropModel.bitmapToCrop.observe(this, {
            val bit : Bitmap? = cropModel.bitmapToCrop.value
            if (bit != null) {
                val newBit: Bitmap? = mFilter.applyFilters(bit)
                binding.cropResultPreview.setImageBitmap(newBit)
//                val bd = BitmapDrawable(Resources.getSystem(),bit)
//                bd.setAntiAlias(false);
//                bd.setFilterBitmap(false);
//                binding.cropResultPreview.setImageDrawable(bd)
            }
        })

        binding.closeResultPreview.setOnClickListener {
            closeActivity()
        }

        binding.closeCropPreview.setOnClickListener {
            closeActivity()
        }

        binding.confirmCropPreview.setOnClickListener {
            binding.cropWrap.visibility = View.GONE
            binding.cropHud.visibility = View.GONE
            loadBitmapFromView(binding.cropPreview)?.let { bitmapToCrop ->
                cropModel.onCornersAccepted(
                    bitmapToCrop
                )
            }
            binding.cropResultWrap.visibility = View.VISIBLE
        }

        binding.confirmCropResult.setOnClickListener {

            val file = File(outputDirectory, "${UUID.randomUUID()}.jpg")
            val outputStream = FileOutputStream(file)
            outputStream.write(cropModel.bitmapToCrop.value?.toByteArray())
            outputStream.close()

            val resultIntent = Intent()
            resultIntent.putExtra("croppedPath", file.absolutePath)
            setResult(RESULT_OK, resultIntent)

            finish()
        }

        binding.cropPreview.setOnTouchListener { view: View, motionEvent: MotionEvent ->
            view.performClick()
            binding.cropHud.onTouch(motionEvent)
        }

        this.cropModel = cropModel
    }

    override fun onResume() {
        super.onResume()
        cropModel.onViewCreated(bitmapUri, contentResolver)
    }

    private fun closeActivity() {
        this.setResult(Activity.RESULT_CANCELED)
        finish()
    }
}

private fun loadBitmapFromView(v: View): Bitmap? {
    val b = Bitmap.createBitmap(
        v.measuredWidth,
        v.measuredHeight,
        Bitmap.Config.ARGB_8888
    )
    val c = Canvas(b)
    v.layout(v.left, v.top, v.right, v.bottom)
    v.draw(c)
    return b
}









