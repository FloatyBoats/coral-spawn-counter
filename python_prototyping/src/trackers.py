from dataclasses import dataclass
from collections import defaultdict

import cv2 as cv
import numpy as np

from contours import Contour, contour_center, Point
from colours import rand_colour, Colour, RED


@dataclass
class IdPointDistance:
    id_: int
    detected_point: Point
    distance: float


def manhattan_dist(old_point: Point, new_point: Point) -> float:
    return abs(old_point[0] - new_point[0]) + abs(old_point[1] - new_point[1])


class ContourTracker:
    """
    Currently just a centroid tracker.

    Other things to consider:
        - tracking based on each of the contour points
        - tracking based area + movement
    """

    centers: dict[int, list[Point]]  = {}
    unmatched_count: dict[int, int] = defaultdict(int)
    debug_colours: dict[int, Colour] = defaultdict(rand_colour)
    curr_id = 0

    end_of_tube: int

    def __init__(self, end_of_tube: int) -> None:
        self.end_of_tube = end_of_tube

    def update(self, contours: list[Contour]):
        detected_centers = [contour_center(c) for c in contours]
        distances = [
            IdPointDistance(
                id_,
                detected_pt,
                manhattan_dist(pts[-1], detected_pt),
            )
            for detected_pt in detected_centers
            for id_, pts in self.centers.items()
        ]

        unmatched_ids = set(self.centers.keys())
        for d in sorted(distances, key=lambda d: d.distance):
            if (
                d.id_ in unmatched_ids
                and d.detected_point in detected_centers
                and self._validate_match(
                    self.centers[d.id_][-1], d.detected_point
                )
            ):
                self.centers[d.id_].append(d.detected_point)
                self.unmatched_count.pop(d.id_, None)
                unmatched_ids.remove(d.id_)
                detected_centers.remove(d.detected_point)

        # register the unmatched centers
        for point in detected_centers:
            self._register(point)
        
        self._process_unmatched(unmatched_ids)

    def visualise(self, debug_img: np.ndarray):
        height, _, _ = debug_img.shape
        cv.line(
            debug_img,
            (self.end_of_tube, 0),
            (self.end_of_tube, height),
            color=RED,
            thickness=2
        )

        for id_, points in self.centers.items():
            if len(points) > 1:
                colour = self.debug_colours[id_]
                for p1, p2 in zip(points, points[1:]):
                    cv.line(debug_img, p1, p2, colour, thickness=2)


    def _register(self, point: Point):
        # new point
        self.centers[self.curr_id] = [point]
        self.curr_id += 1

    def _validate_match(self, old_point: Point, new_point: Point) -> bool:
        return (
            old_point[0] < new_point[0]
            and abs(old_point[1] - new_point[1]) < 50
        )

    def _process_unmatched(self, unmatched_ids: list[int]):
        ids_to_remove = []
        for id_ in unmatched_ids:
            if self.centers[id_][-1][0] > self.end_of_tube:
                ids_to_remove.append(id_)
                continue
            
            self.unmatched_count[id_] += 1
            if self.unmatched_count[id_] > 10:
                ids_to_remove.append(id_)

        for id_ in ids_to_remove:
            self.centers.pop(id_)
            self.unmatched_count.pop(id_, None)
