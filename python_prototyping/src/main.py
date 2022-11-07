from dataclasses import dataclass
from math import sqrt

import cv2 as cv
import numpy as np

from video_source import VideoSource
from contours import detect_contours, contour_center, Point
from counters import ThresholdTracker
from colours import Colour, rand_colour, BLUE

from typing import Any

def dist(a: Point, b: Point) -> float:
    ax, ay = a
    bx, by = b
    return sqrt((ax-bx)**2 + (ay-by)**2)

# vid_src = VideoSource(
#     vid_path="../../real_data/PXL_20220817_055451873.mp4",
#     skip_frames=1450,
#     rotate_degrees=2,
#     #       y -> y      x -> x
#     roi=((1025, 1280), (20, 1078)),
# )

# vid_src = VideoSource(
#     vid_path="../../real_data/20220818_164256.mp4",
#     skip_frames=1200,
#     rotate_degrees=0,
#     #       y -> y      x -> x
#     roi=((405, 510), (300, 1570)),
# )

vid_src = VideoSource(
    vid_path="../../real_data/20220831_103240.mp4",
    # skip_frames=0,
    rotate_degrees=1,
    #       y -> y      x -> x
    roi=((400, 520), (500, 1500)),
)

# live camera feed
# vid_src = VideoSource(
#     capture_device=0,
#     # skip_frames=0,
#     rotate_degrees=1,
#     #       y -> y      x -> x
#     roi=((400, 520), (500, 1500)),
# )

class SpawnTrack:    
    KF: Any
    
    colour: Colour

    def __init__(self, bbox: tuple[int, int, int, int]) -> None:
        x, y, width, height = bbox
        center_x, center_y = int(round(x + width/2)), int(round(y + height/2))
        self.KF = cv.KalmanFilter(4, 4)
        self.KF.transitionMatrix = np.array([[1, 0, 1, 0], [0, 1, 0, 1], [0, 0, 1, 0], [0, 0, 0, 1]], np.float32)
        self.KF.statePost = np.array(
            [
                [center_x],  # x
                [center_y],  # y
                [0],  # dx
                [0]  # dy
            ],
            np.float32
        )
        self.KF.measurementMatrix = np.identity(4)
        self.KF.processNoiseCov = np.identity(4)*1e-5
        self.KF.measurementNoiseCov = np.identity(4)*1e-1;
        self.KF.errorCovPost = np.identity(4)*1e-1;

        self.colour = rand_colour()

    def update(self, frame: np.ndarray) -> bool:
        prev_x = self.x
        prev_y = self.y
        ok, (self.x, self.y, self.width, self.height) = self._tracker.update(frame)
        self.x_vel = self.x - prev_x
        self.y_vel = self.y - prev_y
        return ok

    @property
    def p1(self) -> tuple[int, int]:
        return self.x, self.y

    @property
    def p2(self) -> tuple[int, int]:
        return self.x + self.width, self.y + self.height

    @property
    def center(self) -> tuple[int, int]:
        return int(round(self.x + self.width/2)), int(round(self.y + self.height/2))


counter = ThresholdTracker(thresholds=[200, 400, 600, 800])
spawn_tracks: list[SpawnTrack] = []

bg_subtractor = cv.createBackgroundSubtractorMOG2(detectShadows=False)
erode_kernel = np.ones((3, 3),np.uint8)

# warm up the background detector
for _ in range(2):
    frame = vid_src.read()
    bg_subtractor.apply(frame)

while True:
    if not vid_src.opened():
        break

    try:
        frame = vid_src.read()
    except StopIteration:
        break

    out_frame = frame.copy()

    to_remove = []
    for spawn_track in spawn_tracks:
        ok = spawn_track.update(frame)
        # print(spawn_track.x_vel)
        if spawn_track.x > 900 or spawn_track.x_vel < 5:
            to_remove.append(spawn_track)
        elif ok:
            cv.rectangle(out_frame, spawn_track.p1, spawn_track.p2, spawn_track.colour, 2, 1)
    # print("===================================================================")

    for spawn_track in to_remove:
        spawn_tracks.remove(spawn_track)

    # filter out the "background" - anything thats not moving (tube, etc)
    # leaving only particles in the water
    fg_mask = bg_subtractor.apply(frame)

    # erode the detection mask, removing noise and hopefully separating 
    # particles that are close together
    fg_mask = cv.erode(fg_mask, erode_kernel, iterations=1)

    # detect contours, filtering by area
    contours = detect_contours(fg_mask, min_area_thresh=60, debug_img=None)

    for contour in contours:
        x, y = contour_center(contour)
        closest_dist = dist(min(spawn_tracks, key=lambda t: dist(t.center, (x, y))).center, (x, y)) if spawn_tracks else 1000
        if closest_dist > 50:
            cv.drawMarker(
                out_frame,
                (x, y),
                BLUE,
                cv.MARKER_TILTED_CROSS,
                markerSize=20,
                thickness=3,
            )
            bbox = cv.boundingRect(contour)
            new_track = SpawnTrack(frame, bbox)
            spawn_tracks.append(new_track)

    # counter.update([contour_center(c) for c in contours], debug_img=out_frame)
    # counter.visualise(frame)

    cv.imshow("fg", fg_mask)
    cv.imshow("frame", out_frame)    

    key = cv.waitKey(0)
    if key == ord('q'):
        # q to exit
        break
    

# # display final image
# cv.imshow("frame", frame)
# input()
