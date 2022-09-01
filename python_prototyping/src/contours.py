import cv2 as cv
import numpy as np

from colours import BLUE

from typing import Optional

Contour = list
Point = tuple[int, int]

def detect_contours(
    binary_img: np.ndarray,
    min_area_thresh: Optional[int] = None,
    debug_img: Optional[np.ndarray] = None,
) -> list[Contour]:
    contours, _ = cv.findContours(binary_img, cv.RETR_EXTERNAL, cv.CHAIN_APPROX_SIMPLE)

    if min_area_thresh is not None:
        contours = [c for c in contours if cv.contourArea(c) > min_area_thresh]

    if debug_img is not None:
        for contour in contours:
            cv.drawMarker(
                debug_img,
                contour_center(contour),
                BLUE,
                cv.MARKER_TILTED_CROSS,
                markerSize=20,
                thickness=3,
            )

    return contours

def contour_center(contour: Contour) -> Point:
    M = cv.moments(contour)
    c_x = int(M["m10"] / M["m00"])
    c_y = int(M["m01"] / M["m00"])

    return c_x, c_y
