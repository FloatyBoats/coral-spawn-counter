import cv2 as cv
import numpy as np

from colours import BLUE

from typing import Optional

Contour = list
Point = tuple[int, int]

erode_kernel = np.ones((5, 5),np.uint8)

def detect_contours(
    binary_img: np.ndarray,
    grey_img: np.ndarray,
    min_area_thresh: Optional[int] = None,
    debug_img: Optional[np.ndarray] = None,
) -> list[Contour]:
    contours, _ = cv.findContours(binary_img, cv.RETR_EXTERNAL, cv.CHAIN_APPROX_SIMPLE)

    if min_area_thresh is not None:
        contours = [c for c in contours if cv.contourArea(c) > min_area_thresh]

    final_contours = []

    for contour in contours:
        temp = np.zeros_like(grey_img)
        cv.drawContours(temp, [contour], 0, (255,), -1)
        temp = cv.erode(temp, erode_kernel, iterations=1)

        val = np.median(grey_img[temp==255])
        x, y = contour_center(contour)

        OVERRIDE = True
        if OVERRIDE or val < 180:
            cv.circle(
                grey_img,
                (x, y),
                30,
                0,
                thickness=2,
            )
            cv.putText(
                grey_img,
                f"{round(val, 2)}",
                (x-40 , y-40),
                cv.FONT_HERSHEY_SIMPLEX,
                0.6,
                0,
                1,
                cv.LINE_AA,
            )
            cv.putText(
                grey_img,
                f"{round(cv.contourArea(contour), 2)} px",
                (x-40 , y-60),
                cv.FONT_HERSHEY_SIMPLEX,
                0.6,
                0,
                1,
                cv.LINE_AA,
            )
            final_contours.append(contour)

    return final_contours

def contour_center(contour: Contour) -> Point:
    M = cv.moments(contour)
    c_x = int(M["m10"] / M["m00"])
    c_y = int(M["m01"] / M["m00"])

    return c_x, c_y
