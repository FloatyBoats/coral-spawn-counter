import cv2 as cv
import numpy as np

from contours import Point
from colours import GREEN, RED

from typing import Optional

def manhattan_dist(p1: Point, p2: Point) -> float:
    return abs(p1[0] - p2[0]) + abs(p1[1] - p2[1])

class ThresholdTracker:
    prevous_points: list[Point] = []
    count_line: int
    thresholds: list[int]
    threhold_counts: list[int]

    def __init__(self, thresholds: list[int]) -> None:
        self.thresholds = thresholds
        self.threhold_counts = [0 for _ in thresholds]

    def update(self, points: list[Point], debug_img: Optional[np.ndarray] = None) -> int:
        for i, threshold in enumerate(self.thresholds):
            countable_points = [p for p in points if p[0] > threshold]
            for point in countable_points:
                # we need to find a previous point that matches
                for prev_point in self.prevous_points:
                    is_left = prev_point[0] < point[0]
                    close_enough = manhattan_dist(prev_point, point) < 200
                    crossed_threshold = prev_point[0] <= threshold

                    if is_left and close_enough and crossed_threshold:
                        # count it!
                        self.threhold_counts[i] += 1
                        cv.drawMarker(
                            debug_img,
                            point,
                            GREEN,
                            cv.MARKER_TILTED_CROSS,
                            markerSize=20,
                            thickness=3,
                        )
                        break
        
        self.prevous_points = points
    
    def visualise(self, debug_img: np.ndarray):
        height, _, _ = debug_img.shape
        for threshold, count in zip(self.thresholds, self.threhold_counts):
            cv.line(
                debug_img,
                (threshold, 0),
                (threshold, height),
                color=RED,
                thickness=2
            )
            cv.putText(debug_img, f"{count}", (threshold, 30), cv.FONT_HERSHEY_SIMPLEX, 1, GREEN, 2)
        
        cv.putText(debug_img, f"Cnt: {self.get_max_count()}", (30, 30), cv.FONT_HERSHEY_SIMPLEX, 1, GREEN, 2)

    def get_max_count(self) -> int:
        return max(self.threhold_counts)
