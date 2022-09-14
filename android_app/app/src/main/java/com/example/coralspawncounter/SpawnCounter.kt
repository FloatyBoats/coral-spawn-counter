package com.example.coralspawncounter

import android.util.Log
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.BackgroundSubtractorMOG2
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
    private val bgSubtractor: BackgroundSubtractorMOG2 = Video.createBackgroundSubtractorMOG2()
    private val kernel: Mat = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
    private val roi = Rect(250, 400, 1500, 150)

    fun nextImage(mat: Mat) {
        val roiMat = Mat(mat, roi)
        val binaryMat = Mat()
        bgSubtractor.apply(roiMat, binaryMat)
        Imgproc.erode(binaryMat, binaryMat, kernel, Point(0.0, 0.0), 3)
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()
        val colour = Scalar(255.0, 0.0, 0.0, 255.0)
        Imgproc.findContours(binaryMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        for (contour in contours) {
            val center = contourCenter(contour)
            if(center.x.isNaN() || center.y.isNaN()) {
                continue
            }

            Imgproc.drawMarker(
                mat,
                Point(center.x + roi.x, center.y + roi.y),
                colour,
                Imgproc.MARKER_TILTED_CROSS,
                20,
                3,
            )
        }
        Imgproc.rectangle(mat, roi, Scalar(0.0, 255.0, 0.0, 255.0), 10)
    }
}