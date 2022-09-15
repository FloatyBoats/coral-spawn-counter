package com.example.coralspawncounter

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.example.coralspawncounter.databinding.ActivityVideoBinding
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File

class VideoActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityVideoBinding
    private var counter: SpawnCounter

    init {
        // OpenCV initialization
        OpenCVLoader.initDebug()
        counter = SpawnCounter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityVideoBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        if (allPermissionsGranted()) {
            viewBinding.surfaceView.post { runVideo() }
        }
        else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun runVideo() {
        val retriever = MediaMetadataRetriever()
        val path = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val file = File(path, "PXL_20220915_042002563.mp4")
        retriever.setDataSource(file.absolutePath);
        val numFrames = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)!!)
        while (true) {
            for(i in 0 until numFrames) {
                val bmp = retriever.getFrameAtIndex(i)
                if (bmp != null) {
                    val mat = Mat()
                    Utils.bitmapToMat(bmp, mat)
                    counter.nextImage(mat)
                    Utils.matToBitmap(mat, bmp)
                    drawImage(bmp)
                }
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all { ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED}

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ).toTypedArray()
    }

    private fun drawImage(bitmap: Bitmap) {
        val canvas = viewBinding.surfaceView.holder.lockCanvas()
        val dest = Rect(0, 0, canvas.width, canvas.height)
        canvas.drawBitmap(bitmap, null, dest, null)
        viewBinding.surfaceView.holder.unlockCanvasAndPost(canvas)
    }
}
