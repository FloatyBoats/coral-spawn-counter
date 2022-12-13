package com.example.coralspawncounter

import android.R.attr.src
import android.util.Log
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.video.BackgroundSubtractorMOG2
import org.opencv.video.Video
import java.io.File
import kotlin.concurrent.thread
import kotlin.coroutines.coroutineContext
import kotlin.math.*


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

fun euclideanDist(p1: Point, p2: Point): Double {
    return kotlin.math.sqrt((p1.x - p2.x).pow(2.0) + (p1.y - p2.y).pow(2.0))
}

data class CenterAndWidth(val center: Point, val width: Double)

class SpawnCounter {
    private val bgSubtractor: BackgroundSubtractorMOG2 = Video.createBackgroundSubtractorMOG2(500, 16.0, false)
    private val roi = Rect(0, 0, 0, 0)
    val counter = Counter(2, roi.width, 100, 100, 12)
    private var erodeKernel: Mat = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(1.0, 1.0))
    private var minContourAreaPxThreshold = 0
    var minDiameterThresholdUM = 10
    var notes = ""
    var detectionDirectory: File? = null
    var detectionIncrement = 0

    var doCount = false
    var erodeIterations = 1
    var fiveMMpx = 100

    fun setErodeKernelSize(size: Double) {
        erodeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(size, size))
    }

    fun setROIHorizontal(start: Int, end: Int) {
        roi.x = start
        roi.width = end - start
        counter.updateROI(roi.width)
    }

    fun setROIVertical(start: Int, end: Int) {
        roi.y = start
        roi.height = end - start
    }

    private fun convertUMtoPx(valueUM: Double): Double {
        return valueUM * (fiveMMpx/5000.0)
    }

    private fun convertPxtoUM(valuePx: Double): Double {
        return valuePx / (fiveMMpx/5000.0)
    }

    private fun centerAndWidth(contour: MatOfPoint): CenterAndWidth {
        val dst = MatOfPoint2f()
        contour.convertTo(dst, CvType.CV_32F)
        return CenterAndWidth(contourCenter(contour), convertPxtoUM(Imgproc.minAreaRect(dst).size.width))
    }

    fun nextImage(mat: Mat, binaryMat: Mat) {
        val roiMat = Mat(mat, roi)

        bgSubtractor.apply(roiMat, binaryMat)
        Imgproc.erode(binaryMat, binaryMat, erodeKernel, Point(0.0, 0.0), erodeIterations)

        val red = Scalar(255.0, 0.0, 0.0, 255.0)
        val green = Scalar(0.0, 255.0, 0.0, 255.0)
        val blue = Scalar(0.0, 0.0, 255.0, 255.0)

        val pxMinRadius = convertUMtoPx(minDiameterThresholdUM/2.0)
        minContourAreaPxThreshold = round((pxMinRadius.pow(2) * PI)).toInt()
        val contours = detectContours(binaryMat, minContourAreaPxThreshold)
        val contourCenters = contours.map {centerAndWidth(it) }.filter { !it.center.x.isNaN() && !it.center.y.isNaN() }

        contourCenters.forEach {
            Imgproc.circle(mat, Point(roi.x + it.center.x, it.center.y + roi.y),30, red, 2)
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

        var countedCenters: List<Point>? = null
        if(doCount) {
            countedCenters = counter.update(contourCenters)
            countedCenters.forEach {
                center -> Imgproc.circle(mat, Point(roi.x + center.x, center.y + roi.y),30, green, 2)
            }
        }

        // draw counts above threshold vals
        counter.thresholds.zip(counter.thresholdHistCounts).forEach {
            (thresh, hist) ->
            run {
                for(i in 0 until hist.binNum-1) {
                    Imgproc.putText(
                        mat,
                        "[${hist.binUMLowerEdges[i]},${hist.binUMLowerEdges[i] + hist.binUMWidth}): ${hist.binCounts[i]}",
                        Point(roi.x + thresh.toDouble(), roi.y - (30.0*i) - 15),
                        Imgproc.FONT_HERSHEY_SIMPLEX,
                        1.0,
                        green,
                        3,
                    )
                }
                Imgproc.putText(
                    mat,
                    "[${hist.binUMLowerEdges[hist.binNum-1]},inf): ${hist.binCounts[hist.binNum-1]}",
                    Point(roi.x + thresh.toDouble(), roi.y - (30.0*(hist.binNum-1)) - 15),
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    1.0,
                    green,
                    3,
                )
            }
        }

        Imgproc.putText(
            mat,
            "Kernel: ${erodeKernel.size().width.toInt()}x${erodeKernel.size().height.toInt()} px",
            Point(10.0, 30.0),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            1.0,
            green,
            3,
        )
        Imgproc.putText(
            mat,
            "Iterations: $erodeIterations",
            Point(10.0, 80.0),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            1.0,
            green,
            3,
        )
        Imgproc.putText(
            mat,
            "Min Area Threshold: ${minContourAreaPxThreshold}px",
            Point(10.0, 130.0),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            1.0,
            green,
            3,
        )
        Imgproc.putText(
            mat,
            "Min Diameter: ${minDiameterThresholdUM}um",
            Point(10.0, 180.0),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            1.0,
            green,
            3,
        )
        Imgproc.putText(
            mat,
            "Notes:",
            Point(10.0, 230.0),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            1.0,
            green,
            3,
        )
        Imgproc.putText(
            mat,
            notes,
            Point(10.0, 280.0),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            1.0,
            green,
            3,
        )

        Imgproc.circle(mat, Point(roi.x + pxMinRadius, roi.y - pxMinRadius - 20), round(pxMinRadius).toInt(), green, 2)

        Imgproc.putText(
            mat,
            "5mm=${fiveMMpx}px",
            Point(roi.x.toDouble(), roi.y - pxMinRadius*2 - 60),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            1.0,
            green,
            3,
        )
        Imgproc.line(mat, Point(roi.x.toDouble(), roi.y - pxMinRadius*2 - 40), Point(roi.x.toDouble() + fiveMMpx, roi.y - pxMinRadius*2 - 40), green, 2)

        Imgproc.putText(
            binaryMat,
            "Kernel:",
            Point(1.0, 5.0),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            0.3,
            Scalar(255.0),
            1,
        )
        Imgproc.rectangle(binaryMat, Rect(40, 1, erodeKernel.width(), erodeKernel.height()), Scalar(255.0), -1)

        if (doCount && countedCenters != null && countedCenters.isNotEmpty()) {
            thread {
                val outputFile = File(detectionDirectory, "${detectionIncrement}.jpg")
                detectionIncrement++
                Imgcodecs.imwrite(outputFile.absolutePath, mat)
            }
        } else if (!doCount){
            detectionIncrement = 0
        }
    }



    class Counter(private val numThresholds: Int, roiWidth: Int, binUMStart: Int, binUMWidth: Int, binNum: Int) {
        val thresholds: MutableList<Int> = MutableList(numThresholds) {0}

        init {
            updateROI(roiWidth)
        }

        val thresholdHistCounts = MutableList(numThresholds) {Histogram(binUMStart, binUMWidth, binNum)}
        private var previousPoints = mutableListOf<Point>()

        fun updateROI(roiWidth: Int) {
            val interval = roiWidth / (numThresholds + 1)
            for (i in 1..numThresholds) {
                thresholds[i-1] = (interval*i)
            }
        }

        fun reset() {
            thresholdHistCounts.forEach {it.reset()}
        }

        fun update(contourCenters: List<CenterAndWidth>): List<Point> {
            val countedPoints = mutableListOf<Point>()
            for (contour in contourCenters) {
//                val candidatePoints = previousPoints.filter { it.x < point.x && euclideanDist(point, it) < 200}
                val candidatePoints = previousPoints.filter { it.x < contour.center.x}
                val closestPrevPoint = candidatePoints.minByOrNull { euclideanDist(contour.center, it) } ?: continue
                previousPoints.remove(closestPrevPoint)
                for ((i, threshold) in thresholds.withIndex()) {
                    if(closestPrevPoint.x <= threshold && threshold < contour.center.x) {
                        // count it!
                        thresholdHistCounts[i].count(contour.width)
                        countedPoints.add(contour.center)
                        break
                    }
                }
            }
            previousPoints = contourCenters.map { it.center } as MutableList<Point>
            return countedPoints
        }
    }

    class Histogram(val binUMStart: Int, val binUMWidth: Int, val binNum: Int) {
        val binUMLowerEdges: List<Int> = (binUMStart until binUMStart+binUMWidth*binNum step binUMWidth).toList()
        val binCounts: MutableList<Int> = MutableList(binNum) {0}

        fun count(valueUM: Double) {
            val idx = floor((valueUM - binUMStart) / binUMWidth).toInt()
            if(idx < 1) {
                return
            }
            binCounts[min(idx, binNum-1)]++
        }

        fun reset() {
            binCounts.fill(0)
        }
    }
}

