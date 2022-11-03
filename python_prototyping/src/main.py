from dataclasses import dataclass

import cv2 as cv
import numpy as np

from video_source import VideoSource
from contours import detect_contours, contour_center
from counters import ThresholdTracker
from colours import Colour, rand_colour

from typing import Any

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

@dataclass
class SpawnTrack:
    tracker: cv.TrackerCSRT
    colour: Colour


counter = ThresholdTracker(thresholds=[200, 400, 600, 800])
# tracker = cv.TrackerCSRT_create()
spawn_tracks: list[SpawnTrack] = []

bg_subtractor = cv.createBackgroundSubtractorMOG2(detectShadows=False)
erode_kernel = np.ones((5, 5),np.uint8)

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
        ok, (x, y, w, h) = spawn_track.tracker.update(frame)
        if x > 900:
            to_remove.append(spawn_track)
        elif ok:
            p1 = (x, y)  
            p2 = (x+w, y+h)
            cv.rectangle(out_frame, p1, p2, spawn_track.colour, 2, 1)

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
        if x < 100:
            bbox = cv.boundingRect(contour)
            new_track = SpawnTrack(cv.TrackerCSRT_create(), rand_colour())
            new_track.tracker.init(frame, bbox)
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
