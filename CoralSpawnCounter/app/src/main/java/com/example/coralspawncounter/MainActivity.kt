package com.example.coralspawncounter

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.material.*
import androidx.core.content.ContextCompat
import com.example.coralspawncounter.ui.ScaffoldWithNav
import com.example.coralspawncounter.ui.theme.CoralSpawnCounterTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.coralspawncounter.ui.RecordScreen
import com.example.coralspawncounter.ui.Screen

class MainActivity : ComponentActivity() {

    private var camera: Camera? = null

    private val analysisExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    // TODO view model here
    // TODO instance of analyser linked to view model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CoralSpawnCounterTheme {
                ScaffoldWithNav { innerPadding, navController ->
                    NavHost(
                        navController,
                        startDestination = Screen.Record.route,
                        Modifier.padding(innerPadding)
                    ) {
                        composable(route = Screen.Record.route) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                RecordScreen(
                                    onPreviewViewAvailable = { startCamera(it) }
                                )
                            }
                        }
                        composable(route = Screen.Review.route) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Text(text = "Review")
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun startCamera(cameraPreviewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(
            {
                // CameraX Preview UseCase
                val previewUseCase = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(cameraPreviewView.surfaceProvider)
                    }

                val analysisUseCase = ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .build()
                    //.also {
                    //    it.setAnalyzer(
                    //        analysisExecutor,
                    //        TODO: use instance of analyser above
                    //    )
                    //}

                val cameraProvider = cameraProviderFuture.get()
                try {
                    // Must unbind the use-cases before rebinding them.
                    cameraProvider.unbindAll()
                    camera = cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        previewUseCase,
                        analysisUseCase,
                    )
                } catch (ex: Exception) {
                    Log.e("startCamera", "Use case binding failed", ex)
                }

            }, ContextCompat.getMainExecutor(this)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
    }
}
