package com.example.coralspawncounter

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector


@SuppressLint("UnsafeOptInUsageError")
fun getHighestSupportedQuality(cameraProvider: ProcessCameraProvider) : QualitySelector {
    val cameraInfo = cameraProvider.availableCameraInfos.filter {
        Camera2CameraInfo
        .from(it)
        .getCameraCharacteristic(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK
    }

    val supportedQualities = QualitySelector.getSupportedQualities(cameraInfo[0])
    val filteredQualities = arrayListOf (Quality.FHD, Quality.HD, Quality.SD).filter { supportedQualities.contains(it) }
    return QualitySelector.from(filteredQualities.first());
}
