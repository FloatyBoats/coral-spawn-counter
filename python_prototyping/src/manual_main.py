import cv2 as cv
import numpy as np

from video_source import VideoSource
from contours import detect_contours, circle_contour, filter_contours
from counters import ThresholdTracker

vid_src = VideoSource(
    vid_path=f"/home/alistair/Videos/Coral Spawn/Lizard Island/20221213_153310_raw.mp4",
    skip_frames=0,
    rotate_degrees=0,
    #       y -> y      x -> x
    roi=((400, 540), (250, 1470)),
)

counter = ThresholdTracker(thresholds=[300, 700])

bg_subtractor = cv.createBackgroundSubtractorMOG2(detectShadows=False)
erode_kernel = np.ones((3, 3),np.uint8)
dialate_kernel = np.ones((7, 7),np.uint8)

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

    grey_frame = cv.cvtColor(frame, cv.COLOR_BGR2GRAY)

    # filter out the "background" - anything thats not moving (tube, etc)
    # leaving only particles in the water
    fg_mask = bg_subtractor.apply(frame)

    # erode the detection mask to remove noise
    fg_mask = cv.erode(fg_mask, erode_kernel, iterations=1)

    # detect contours, filtering by area
    contours = detect_contours(fg_mask)
    contours = filter_contours(
        contours,
        frame,
        min_area=20,
        min_convexity=0,
        min_aspect_ratio=0,
    )
    
    for contour in contours:
        pass
    