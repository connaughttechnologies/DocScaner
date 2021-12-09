package net.fk.SmartDoc.presentation

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Insets
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData

import net.fk.SmartDoc.data.OpenCVLoader
import net.fk.SmartDoc.databinding.ActivityScannerBinding
import net.fk.SmartDoc.extensions.outputDirectory
import net.fk.SmartDoc.extensions.triggerFullscreen
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService

private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

abstract class MyBaseActivity : AppCompatActivity() {
    /////////////////////////////////////////
    var camera: Camera? = null
    var scale = 1.0f
    var imageCapture: ImageCapture? = null
    val isBusy = MutableLiveData<Boolean>()
    var cameraProvider: ProcessCameraProvider? = null
    private var executor: ExecutorService? = null
    var topLayout: ViewGroup? = null
    var tvCameraHint: TextView? = null
    var cameraView: PreviewView? = null
    var myLastUri: MutableLiveData<Uri> = MutableLiveData()
    ///////////////////////////////////////

    private lateinit var viewModel: CameraViewModel
    private lateinit var binding: ActivityScannerBinding

    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val bitmapUri =
                    result.data?.extras?.getString("croppedPath") ?: error("invalid path")

                val image = File(bitmapUri)
                val bmOptions = BitmapFactory.Options()
                val bitmap = BitmapFactory.decodeFile(image.absolutePath, bmOptions)
                onDocumentAccepted(bitmap)

                image.delete()
            } else {
                viewModel.onViewCreated(OpenCVLoader(this), this, binding.viewFinder)
            }
            viewModel.onViewCreated(OpenCVLoader(this), this, binding.viewFinder)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        triggerFullscreen()
        initViews()
        startCamera()
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val viewModel: CameraViewModel by viewModels()
        viewModel.isBusy.observe(this, { isBusy ->
            binding.progress.visibility = if (isBusy) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
        })
//        viewModel.lastUri.observe(this, {
//            val intent = Intent(this, CropperActivity::class.java)
//            intent.putExtra("lastUri", it.toString())
//            resultLauncher.launch(intent)
//        })

        myLastUri.observe(this, {
            val intent = Intent(this, CropperActivity::class.java)
            intent.putExtra("lastUri", it.toString())
            resultLauncher.launch(intent)
        })

        viewModel.errors.observe(this, {
            onError(it)
            Log.e(ScannerActivity::class.java.simpleName, it.message, it)
        })

        viewModel.corners.observe(this, {
            it?.let { corners ->
                binding.hud.onCornersDetected(corners)
            } ?: run {
                binding.hud.onCornersNotDetected()
            }
        })

//        viewModel.flashStatus.observe(this, { status ->
//            binding.flashToggle.setImageResource(
//                when (status) {
//                    FlashStatus.ON -> R.drawable.ic_flash_on
//                    FlashStatus.OFF -> R.drawable.ic_flash_off
//                    else -> R.drawable.ic_flash_off
//                }
//            )
//        })

        binding.flashToggle.setOnClickListener {
            viewModel.onFlashToggle()
        }


        binding.shutter.setOnClickListener {
            onTakePicture(outputDirectory, this)
        }

        binding.closeScanner.setOnClickListener {
            closePreview()
        }
        this.viewModel = viewModel
    }

    override fun onResume() {
        super.onResume()
        viewModel.onViewCreated(OpenCVLoader(this), this, binding.viewFinder)
    }

    private fun closePreview() {
        binding.rootView.visibility = View.GONE
        viewModel.onClosePreview()
        finish()
    }

    abstract fun onError(throwable: Throwable)
    abstract fun onDocumentAccepted(bitmap: Bitmap)
    abstract fun onClose()

    /////////////////////////////
    private fun setFullscreen() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        topLayout?.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LOW_PROFILE
        )
    }

    private fun initViews() {
        //cameraView = findViewById(R.id.viewFinder)
        setcameraFocusAndZoom()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setcameraFocusAndZoom() {
        val scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector?): Boolean {
                    camera?.let {
                        scale = (it.cameraInfo.zoomState.value?.zoomRatio
                            ?: 1.0f) * (detector?.scaleFactor ?: 1.0f)
                        val minZoomRation = it.cameraInfo.zoomState.value?.minZoomRatio ?: 1.0f
                        val maxZoomRation = it.cameraInfo.zoomState.value?.maxZoomRatio ?: 1.0f
                        if (scale >= minZoomRation || scale <= maxZoomRation)
                            it.cameraControl.setZoomRatio(scale)
                    }
                    return true
                }
            })
        cameraView?.setOnTouchListener { _, event ->
            tvCameraHint?.visibility = View.GONE
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
                        cameraView?.width?.toFloat() ?: 0f, cameraView?.height?.toFloat() ?: 0f
                    )
                    val autoFocusPoint = factory.createPoint(event.x, event.y)
                    try {
                        camera?.cameraControl?.startFocusAndMetering(
                            FocusMeteringAction.Builder(
                                autoFocusPoint,
                                FocusMeteringAction.FLAG_AF
                            ).apply {
                                disableAutoCancel()
                            }.build()
                        )
                    } catch (e: CameraInfoUnavailableException) {
                    }
                    return@setOnTouchListener true
                }
            }
            scaleGestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }
    }

     fun onTakePicture(outputDirectory: File, context: Context) {
         isBusy.value = true
         val photoFile = File(
             outputDirectory,
             SimpleDateFormat(
                 FILENAME_FORMAT, Locale.US
             ).format(System.currentTimeMillis()) + ".jpg"
         )
         val outputFileOptions = ImageCapture
             .OutputFileOptions
             .Builder(
                 photoFile
             )
             .build()
         val takePicture = imageCapture?.takePicture(
             outputFileOptions,
             ContextCompat.getMainExecutor(context),
             object : ImageCapture.OnImageSavedCallback {
                 override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                     // myLastUri.value = Uri.fromFile(photoFile)
                     goToDistination(Uri.fromFile(photoFile))
                 }

                 override fun onError(error: ImageCaptureException) {
//                     Toast.makeText(this@MyBaseActivity, error.localizedMessage, Toast.LENGTH_LONG)
//                         .show()
                 }
             }
         )
         goToDistination(Uri.fromFile(photoFile))
    }

    private fun goToDistination(fromFile: Uri?) {
        val intent = Intent(this, CropperActivity::class.java)
        intent.putExtra("lastUri", fromFile.toString())
        resultLauncher.launch(intent)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()
            val size = Size(getScreenWidth(), cameraView?.measuredHeight ?: getScreenHeight())
            // Preview
            val preview = Preview.Builder()
                .setTargetResolution(size)
                .build()
            imageCapture = ImageCapture.Builder()
                .setTargetResolution(size)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(cameraView?.display?.rotation ?: 0)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()
            // Select back camera
            val cameraSelector = CameraSelector
                .Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
            try {
                // Unbind use cases before rebinding
                cameraProvider?.unbindAll()
                // Bind use cases to camera
                camera =
                    cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                preview.setSurfaceProvider(cameraView?.surfaceProvider)
                //auto focus by default
            } catch (exc: Exception) {
                Log.i("khota", exc.localizedMessage)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    fun Activity.getScreenHeight(): Int {
        return if (Build.VERSION.SDK_INT >= 30) {
            val windowMetrics: WindowMetrics = windowManager.currentWindowMetrics
            val insets: Insets = windowMetrics.getWindowInsets()
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            windowMetrics.getBounds().height() - insets.bottom - insets.top
        } else {
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            return displayMetrics.heightPixels
        }
    }

    fun Activity.getScreenWidth(): Int {
        return if (Build.VERSION.SDK_INT >= 30) {
            val windowMetrics: WindowMetrics = windowManager.currentWindowMetrics
            val insets: Insets = windowMetrics.getWindowInsets()
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            windowMetrics.getBounds().width() - insets.left - insets.right
        } else {
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            return displayMetrics.widthPixels
        }
    }



}
