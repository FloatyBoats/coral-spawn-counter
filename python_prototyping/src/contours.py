import cv2 as cv
import numpy as np

from colours import BLUE

from typing import Optional

Contour = list
Point = tuple[int, int]

erode_kernel = np.ones((5, 5),np.uint8)

def detect_contours(binary_img: np.ndarray) -> list[Contour]:
    contours, _ =  cv.findContours(binary_img, cv.RETR_EXTERNAL, cv.CHAIN_APPROX_SIMPLE)
    return contours

def filter_contours(
    contours: list[Contour],
    min_area: int,
    min_convexity: float,
    min_aspect_ratio: float,
    debug_img: Optional[np.ndarray] = None,
    draw_values: bool = True,
) -> list[Contour]:
    filtered_contours = []

    for contour in contours:
        # filter area - increase threshold?
        area = cv.contourArea(contour)
        if area < min_area:
            continue

        # filter convexity
        convexity = contour_convexity(contour)
        if convexity < min_convexity:
            continue

        # filter by aspect ratio
        aspect_ratio = contour_aspect_ratio(contour)
        if aspect_ratio < min_aspect_ratio:
            continue
        
        # filter circularity?

        if debug_img is not None:
            x, y = contour_center(contour)

            if draw_values:
                cv.putText(
                    debug_img,
                    f"Ar: {round(area, 2)}",
                    (x-40 , y-35),
                    cv.FONT_HERSHEY_SIMPLEX,
                    fontScale=0.6,
                    color=BLUE,
                    thickness=1,
                    lineType=cv.LINE_AA,
                )

                cv.putText(
                    debug_img,
                    f"Cv: {round(convexity, 2)}",
                    (x-40 , y-50),
                    cv.FONT_HERSHEY_SIMPLEX,
                    fontScale=0.6,
                    color=BLUE,
                    thickness=1,
                    lineType=cv.LINE_AA,
                )

                cv.putText(
                    debug_img,
                    f"As: {round(aspect_ratio, 2)}",
                    (x-40 , y-65),
                    cv.FONT_HERSHEY_SIMPLEX,
                    fontScale=0.6,
                    color=BLUE,
                    thickness=1,
                    lineType=cv.LINE_AA,
                )

            cv.circle(
                debug_img,
                (x, y),
                radius=30,
                color=BLUE,
                thickness=2,
            )

        filtered_contours.append(contour)

    return filtered_contours   


def contour_convexity(contour: Contour) -> float:
    return cv.contourArea(contour) / cv.contourArea(cv.convexHull(contour))


def contour_aspect_ratio(contour: Contour) -> float:
    _, dimensions, _ = cv.minAreaRect(contour)
    long = max(dimensions)
    short = min(dimensions)

    return short / long

def circle_contour(contour: Contour, img: np.ndarray):
    x, y = contour_center(contour)
    cv.circle(
        img,
        (x, y),
        radius=30,
        color=BLUE,
        thickness=2,
    )
    

def contour_center(contour: Contour) -> Point:
    M = cv.moments(contour)
    c_x = int(M["m10"] / M["m00"])
    c_y = int(M["m01"] / M["m00"])

    return c_x, c_y
