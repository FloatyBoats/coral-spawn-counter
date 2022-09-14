package com.example.coralspawncounter

import android.util.Log
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.round

fun contourCenter(contour: MatOfPoint): Point {
    val moments = Imgproc.moments(contour, false)
    return Point(
        round(moments.m10/moments.m00),
        round(moments.m01/moments.m00),
    )
}

class SpawnCounter {
    val count = 0
    val bgSubtractor = Video.createBackgroundSubtractorMOG2()
    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))

    fun nextImage(mat: Mat, debug_out: Mat) {
        mat.copyTo(debug_out)
        val binaryMat = Mat()
        bgSubtractor.apply(mat, binaryMat)
        Imgproc.erode(binaryMat, binaryMat, kernel, Point(0.0, 0.0), 3)
        var hierarchy = Mat()
        var contours = mutableListOf<MatOfPoint>()
        val colour = Scalar(255.0, 0.0, 0.0, 255.0)
        Imgproc.findContours(binaryMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        for (contour in contours) {
            val center = contourCenter(contour)
            if(center.x.isNaN() || center.y.isNaN()) {
                continue
            }
            Imgproc.drawMarker(debug_out, contourCenter(contour), colour, Imgproc.MARKER_TILTED_CROSS, 20, 3)
        }
    }
}