import os
from math import sqrt

import cv2 as cv
import numpy as np

from contours import Point
from colours import GREEN, RED

from typing import Optional

def euclidian_dist(p1: Point, p2: Point) -> float:
    return sqrt((p1[0] - p2[0])**2 + (p1[1] - p2[1])**2)

class ThresholdTracker:
    prevous_points: list[Point] = []
    count_line: int
    thresholds: list[int]
    threshold_counts: list[int]

    def __init__(self, thresholds: list[int]) -> None:
        self.thresholds = thresholds
        self.threshold_counts = [0 for _ in thresholds]

    def update(self, points: list[Point], debug_img: Optional[np.ndarray] = None, binary_img: Optional[np.ndarray] = None, video_name: Optional[str] = None):
        for point in points:
            candidate_points = [p for p in self.prevous_points if p[0] < point[0]]
            if len(candidate_points) < 1:
                continue

            closest_prev_point = min(candidate_points, key=lambda p: euclidian_dist(point, p))
            self.prevous_points.remove(closest_prev_point)

            for i, threshold in enumerate(self.thresholds):
                if closest_prev_point[0] <= threshold and threshold < point[0]:
                    # count it!
                    self.threshold_counts[i] += 1
                    if video_name is not None:
                        folder_path = f"./counted/{video_name}"
                        if not os.path.exists(folder_path):
                            os.makedirs(folder_path)
                        if debug_img is not None:
                            cv.imwrite(f"{folder_path}/{i}_{self.threshold_counts[i]}_processed.jpeg", debug_img)
                        if binary_img is not None:
                            cv.imwrite(f"{folder_path}/{i}_{self.threshold_counts[i]}_binary.jpeg", binary_img)
                    break
        
        self.prevous_points = points
    
    def visualise(self, debug_img: np.ndarray):
        height, _, _ = debug_img.shape
        for threshold, count in zip(self.thresholds, self.threshold_counts):
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
        return max(self.threshold_counts)
