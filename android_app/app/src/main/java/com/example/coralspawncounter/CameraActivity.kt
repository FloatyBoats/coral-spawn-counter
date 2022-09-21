package com.example.coralspawncounter

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.coralspawncounter.databinding.ActivityCameraBinding
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


fun convertRGBAtoMat(img: Image?): Mat? {
    if (img == null) {
        return null;
    }

    if (img.planes[0].buffer.remaining() < 1) {
        return null;
    }

    val rgba = Mat(img.height, img.width, CvType.CV_8UC4)
    val data = ByteArray(img.planes[0].buffer.remaining())
    img.planes[0].buffer.get(data)
    rgba.put(0, 0, data)
    return rgba
}


class CameraActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityCameraBinding
    private lateinit var cameraExecutor: ExecutorService
    private var counter: SpawnCounter


    init {
        // OpenCV initialization
        OpenCVLoader.initDebug()
        counter = SpawnCounter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, SpawnCountAnalyzer())
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED}

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
            ).toTypedArray()
    }

    private inner class SpawnCountAnalyzer() : ImageAnalysis.Analyzer {
        @ExperimentalGetImage
        override fun analyze(image: ImageProxy) {
            convertRGBAtoMat(image.image)?.let {
                val width = it.cols()
                val height = it.rows()

                val bitmapFiltered =
                    Bitmap.createBitmap(
                        width, height,
                        Bitmap.Config.ARGB_8888
                    )
                counter.nextImage(it)
                Utils.matToBitmap(it, bitmapFiltered)
                drawImage(bitmapFiltered)
            }
            image.close()
        }
    }

    private fun drawImage(bitmap: Bitmap) {
        val canvas = viewBinding.surfaceView.holder.lockCanvas()
        val width = (bitmap.width.toDouble()/bitmap.height.toDouble()) * canvas.height
        val dest = Rect(0, 0, width.toInt(), canvas.height)
        canvas.drawBitmap(bitmap, null, dest, null)
        viewBinding.surfaceView.holder.unlockCanvasAndPost(canvas)
    }
}
