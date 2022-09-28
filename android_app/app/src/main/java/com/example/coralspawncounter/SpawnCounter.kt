package com.example.coralspawncounter

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.BackgroundSubtractorMOG2
import org.opencv.video.Video
import kotlin.math.abs
import kotlin.math.round

fun contourCenter(contour: MatOfPoint): Point {
    val moments = Imgproc.moments(contour, false)
    return Point(
        round(moments.m10/moments.m00),
        round(moments.m01/moments.m00),
    )
}

fun detectContours(binaryMat: Mat, minAreaThreshold: Int? = null): List<MatOfPoint> {
    val hierarchy = Mat()
    val contours = mutableListOf<MatOfPoint>()
    Imgproc.findContours(binaryMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
    if (minAreaThreshold != null) {
        contours.removeAll {contour -> Imgproc.contourArea(contour) < minAreaThreshold}
    }

    return contours
}

fun manhattanDist(p1: Point, p2: Point): Double {
    return abs(p1.x - p2.x) + abs(p1.y - p2.y)
}

class SpawnCounter {
    private val bgSubtractor: BackgroundSubtractorMOG2 = Video.createBackgroundSubtractorMOG2(500, 16.0, false)
    private val kernel: Mat = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
    private val roi = Rect(0, 0, 0, 0)
    private val minContourAreaThreshold = 10
    val counter = Counter(3, roi.width)
    var doCount = false

    fun setROIHorizontal(start: Int, end: Int) {
        roi.x = start
        roi.width = end - start
        counter.updateROI(roi.width)
    }

    fun setROIVertical(start: Int, end: Int) {
        roi.y = start
        roi.height = end - start
    }

    fun nextImage(mat: Mat, binaryMat: Mat) {
        val roiMat = Mat(mat, roi)

        bgSubtractor.apply(roiMat, binaryMat)
        Imgproc.erode(binaryMat, binaryMat, kernel, Point(0.0, 0.0), 3)

        val red = Scalar(255.0, 0.0, 0.0, 255.0)
        val green = Scalar(0.0, 255.0, 0.0, 255.0)
        val blue = Scalar(0.0, 0.0, 255.0, 255.0)

        val contours = detectContours(binaryMat, minContourAreaThreshold)
        val contourCenters = contours.map { contour -> contourCenter(contour) }.filter { point -> !point.x.isNaN() && !point.y.isNaN() }
        contourCenters.forEach {
            center -> Imgproc.drawMarker(
                mat,
                Point(roi.x + center.x, roi.y + center.y),
                red,
                Imgproc.MARKER_TILTED_CROSS,
                20,
                3,
            )
        }

        // draw ROI
        Imgproc.rectangle(mat, roi, green, 5)

        // draw threshold lines
        counter.thresholds.forEach {
            thresh -> Imgproc.line(
                mat,
                Point(roi.x + thresh.toDouble(), roi.y.toDouble()),
                Point(roi.x + thresh.toDouble(), roi.y.toDouble() + roi.height.toDouble()),
                blue,
                3,
            )
        }

        if(doCount) {
            val countedCenters = counter.update(contourCenters)

            countedCenters.forEach {
                center -> Imgproc.drawMarker(
                    mat,
                    Point(roi.x + center.x, center.y + roi.y),
                    green,
                    Imgproc.MARKER_TILTED_CROSS,
                    20,
                    3,
                )
            }
        }

        // draw counts above threshold vals
        counter.thresholds.zip(counter.thresholdCounts).forEach {
            (thresh, count) -> Imgproc.putText(
                mat,
                count.toString(),
                Point(roi.x + thresh.toDouble(), roi.y - 15.0),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                1.0,
                green,
                3,
            )
        }
    }

    class Counter(private val numThresholds: Int, roiWidth: Int) {
        val thresholds: MutableList<Int> = MutableList(numThresholds) {0}

        init {
            updateROI(roiWidth)
        }

        val thresholdCounts = MutableList(numThresholds) {0}
        private var previousPoints = listOf<Point>()

        fun updateROI(roiWidth: Int) {
            val interval = roiWidth / (numThresholds + 1)
            for (i in 1..numThresholds) {
                thresholds[i-1] = (interval*i)
            }
        }

        fun reset() {
            thresholdCounts.fill(0)
        }

        fun update(points: List<Point>): List<Point> {
            val countedPoints = mutableListOf<Point>()
            for ((i, threshold) in thresholds.withIndex()) {
                val countablePoints = points.filter { point -> point.x > threshold }
                for (point in countablePoints) {
                    for (prevPoint in previousPoints) {
                        val movedRight = prevPoint.x < point.x
                        val closeEnough = manhattanDist(point, prevPoint) < 200
                        val crossedThreshold = prevPoint.x <= threshold
                        if (movedRight && closeEnough && crossedThreshold) {
                            // count it!
                            thresholdCounts[i]++
                            countedPoints.add(point)
                            break
                        }
                    }
                }
            }
            previousPoints = points
            return countedPoints
        }
    }
}

