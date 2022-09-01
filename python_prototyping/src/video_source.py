import cv2 as cv
import numpy as np

from typing import Optional

ROI = tuple[tuple[int, int], tuple[int, int]]

class VideoSource:
    roi: Optional[ROI]
    rotate_degrees: Optional[int]

    _cap: cv.VideoCapture
    _rotation_M: Optional[np.ndarray] = None
    _width: int
    _height: int

    def __init__(
        self,
        *,
        vid_path: str,
        skip_frames: Optional[int] = None,
        rotate_degrees: Optional[int] = None,
        roi: Optional[ROI] = None,
    ) -> None:
        self._cap = cv.VideoCapture(vid_path)
        self.rotate_degrees = rotate_degrees
        self.roi = roi

        if not self._cap.isOpened():
            raise ValueError("Error opeing video file.")

        _, frame = self._cap.read()
        self._height, self._width, _ = frame.shape
                
        if skip_frames is not None:
            self._cap.set(cv.CAP_PROP_POS_FRAMES, skip_frames)
        
        if self.rotate_degrees is not None:
            self._rotation_M = cv.getRotationMatrix2D(
                (round(self._width/2), round(self._height/2)),
                self.rotate_degrees,
                1.0
            )

    def read(self) -> Optional[np.ndarray]:
        ret, frame = self._cap.read()
        if not ret:
            raise StopIteration("No more frames.")

        if self.rotate_degrees is not None:
            frame = cv.warpAffine(
                frame,
                self._rotation_M,
                (self._width, self._height),
            )
        
        if self.roi is not None:
            roi_y, roi_x = self.roi
            frame = frame[roi_y[0]:roi_y[1], roi_x[0]:roi_x[1]]

        return frame

    def opened(self) -> bool:
        return self._cap.isOpened()
