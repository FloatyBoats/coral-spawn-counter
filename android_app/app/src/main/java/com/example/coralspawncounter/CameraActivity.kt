package com.example.coralspawncounter

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.media.Image
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceView
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.FocusMeteringAction.FLAG_AF
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.coralspawncounter.databinding.ActivityCameraBinding
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
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
    private lateinit var analysisExecutor: ExecutorService
    private lateinit var recordingExecutor: ExecutorService
    private lateinit var camera: Camera
    private var counter: SpawnCounter
    private lateinit var mediaRecorder: MediaRecorder
    private var recorderSurface: Surface? = null
    private lateinit var videoCapture: VideoCapture<Recorder>
    private var activeRecording: Recording? = null

    init {
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

        analysisExecutor = Executors.newSingleThreadExecutor()
        recordingExecutor = Executors.newSingleThreadExecutor()

        viewBinding.SliderErodeKernelSize.addOnChangeListener { _, value, _ ->  counter.setErodeKernelSize(value.toDouble())}
        viewBinding.SliderErodeIterations.addOnChangeListener { _, value, _ ->  counter.erodeIterations = value.toInt()}
        viewBinding.SliderMinAreaThreshold.addOnChangeListener { _, value, _ ->  counter.minContourAreaThreshold = value.toInt()}

        viewBinding.SliderROIHorizontal.addOnChangeListener {
            slider, _, _ -> counter.setROIHorizontal(slider.values[0].toInt(), slider.values[1].toInt())
        }

        viewBinding.SliderROIVertical.addOnChangeListener {
            slider, _, _ -> counter.setROIVertical(slider.values[0].toInt(), slider.values[1].toInt())
        }

        counter.setErodeKernelSize(viewBinding.SliderErodeKernelSize.value.toDouble())
        counter.erodeIterations = viewBinding.SliderErodeIterations.value.toInt()
        counter.minContourAreaThreshold = viewBinding.SliderMinAreaThreshold.value.toInt()
        counter.setROIHorizontal(viewBinding.SliderROIHorizontal.values[0].toInt(), viewBinding.SliderROIHorizontal.values[1].toInt())
        counter.setROIVertical(viewBinding.SliderROIVertical.values[0].toInt(), viewBinding.SliderROIVertical.values[1].toInt())

        viewBinding.SwitchCount.setOnCheckedChangeListener { _, isChecked -> counter.doCount = isChecked; if (isChecked) {startRecording()} else {stopRecording()} }
        viewBinding.ButtonReset.setOnClickListener { counter.counter.reset() }
    }

    @SuppressLint("SimpleDateFormat")
    private fun startRecording() {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss")
        val timestamp: String = formatter.format(Date())

        val rawName = "${timestamp}_raw.mp4"
        val processedName = "${timestamp}_processed.mp4"

        val rawContentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, rawName)
        }

         val outputFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), processedName);

        mediaRecorder = MediaRecorder()
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setOutputFile(outputFile)
        mediaRecorder.setVideoEncodingBitRate(10000000)
        mediaRecorder.setVideoFrameRate(30)
        mediaRecorder.setVideoSize(viewBinding.surfaceView.width, viewBinding.surfaceView.height)
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mediaRecorder.prepare()
        mediaRecorder.start()
        recorderSurface = mediaRecorder.surface

        val mediaStoreOutput = MediaStoreOutputOptions.Builder(this.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(rawContentValues)
            .build()

        activeRecording = videoCapture.output
            .prepareRecording(baseContext, mediaStoreOutput)
            .start(ContextCompat.getMainExecutor(this)) {}
    }

    private fun stopRecording() {
        recorderSurface = null
        mediaRecorder.stop()
        mediaRecorder.release()

        activeRecording?.stop()
        activeRecording = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetResolution(Size(1920, 1080))
                .build()
                .also {
                    it.setAnalyzer(analysisExecutor, SpawnCountAnalyzer())
                }

            val recorder = Recorder.Builder()
            .setExecutor(analysisExecutor)
            .setQualitySelector(getHighestSupportedQuality(cameraProvider))
            .build()

            videoCapture = VideoCapture.withOutput(recorder)


            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, imageAnalyzer, videoCapture)

                val exposureState = camera.cameraInfo.exposureState

                viewBinding.seekBarExposure.apply {
                    isEnabled = exposureState.isExposureCompensationSupported
                    max = exposureState.exposureCompensationRange.upper
                    min = exposureState.exposureCompensationRange.lower
                    progress = exposureState.exposureCompensationIndex
                }

                viewBinding.seekBarExposure.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        camera.cameraControl.setExposureCompensationIndex(progress)
                            .addListener({viewBinding.seekBarExposure.progress = camera.cameraInfo.exposureState.exposureCompensationIndex}, mainExecutor)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        // Not needed
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        // Not needed
                    }

                })

                viewBinding.surfaceView.setOnTouchListener { v, e ->
                    val meteringPointFactory = DisplayOrientedMeteringPointFactory(
                        v.display,
                        camera.cameraInfo,
                        v.width.toFloat(),
                        v.height.toFloat(),
                    )

                    val meteringPoint = meteringPointFactory.createPoint(e.x, e.y)
                    val action = FocusMeteringAction
                        .Builder(meteringPoint, FLAG_AF)
                        .disableAutoCancel()
                        .build()
                    camera.cameraControl.startFocusAndMetering(action)
                    true
                }

            } catch(exc: Exception) {
                Log.e("Camera Failed", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED}

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
        recordingExecutor.shutdown()
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
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
            ).toTypedArray()
    }

    private inner class SpawnCountAnalyzer() : ImageAnalysis.Analyzer {
        @ExperimentalGetImage
        override fun analyze(image: ImageProxy) {
            convertRGBAtoMat(image.image)?.let {
                val binaryMat = Mat()

                counter.nextImage(it, binaryMat)

                val bitmap =
                    Bitmap.createBitmap(
                        it.cols(), it.rows(),
                        Bitmap.Config.ARGB_8888
                    )
                val binaryBitmap =
                    Bitmap.createBitmap(
                        binaryMat.cols(), binaryMat.rows(),
                        Bitmap.Config.ARGB_8888
                    )

                Utils.matToBitmap(it, bitmap)
                Utils.matToBitmap(binaryMat, binaryBitmap)

                drawBitmaps(viewBinding.surfaceView, listOf( bitmap, binaryBitmap), listOf(0.7, 0.3), listOf(0.0, 0.7))
            }
            image.close()
        }
    }

    private fun drawBitmaps(surfaceView: SurfaceView, bitmaps: List<Bitmap>, heights: List<Double>, vertOffsets: List<Double>) {
        val viewCanvas = surfaceView.holder.lockCanvas()
        val recordCanvas = recorderSurface?.lockCanvas(null)

        viewCanvas.drawColor(Color.BLACK)
        recordCanvas?.drawColor(Color.BLACK)

        for (i in bitmaps.indices) {
            val bitmap = bitmaps[i]
            val height = heights[i]
            val vertOffset = vertOffsets[i]

            val drawHeight = (viewCanvas.height * height).toInt();
            val drawOffset = (viewCanvas.height * vertOffset).toInt();

            val canvasRatio = viewCanvas.width.toDouble() / drawHeight.toDouble()
            val bitmapRatio = bitmap.width.toDouble() / bitmap.height.toDouble()

            val dest = if(canvasRatio > bitmapRatio) {
                val destWidth = (bitmap.width.toDouble()/bitmap.height.toDouble()) * drawHeight
                Rect(0, drawOffset, destWidth.toInt(), drawOffset + drawHeight)
            } else {
                val destHeight = (bitmap.height.toDouble()/bitmap.width.toDouble()) * viewCanvas.width
                Rect(0, drawOffset, viewCanvas.width, drawOffset + destHeight.toInt())
            }

            viewCanvas.drawBitmap(bitmap, null, dest, null)
            recordCanvas?.drawBitmap(bitmap, null, dest, null)
        }

        surfaceView.holder.unlockCanvasAndPost(viewCanvas)
        recorderSurface?.unlockCanvasAndPost(recordCanvas)
    }
}
