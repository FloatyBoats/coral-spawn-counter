package com.example.coralspawncounter

import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video

class SpawnCounter {
    val count = 0
    val bgSubtractor = Video.createBackgroundSubtractorMOG2()
    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))

    fun nextImage(mat: Mat, debug_out: Mat) {
        bgSubtractor.apply(mat, debug_out)
        Imgproc.erode(debug_out, debug_out, kernel, Point(0.0, 0.0), 3)
    }
}