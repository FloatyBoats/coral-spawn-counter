import cv2 as cv
import numpy as np

from video_source import VideoSource
from contours import detect_contours, contour_center
# from trackers import ContourTracker
from counters import ThresholdTracker

# vid_src = VideoSource(
#     vid_path="../real_data/PXL_20220817_055451873.mp4",
#     skip_frames=1450,
#     rotate_degrees=2,
#     #       y -> y      x -> x
#     roi=((1025, 1280), (20, 1078)),
# )

# vid_src = VideoSource(
#     vid_path="../real_data/20220818_164256.mp4",
#     skip_frames=1200,
#     rotate_degrees=0,
#     #       y -> y      x -> x
#     roi=((405, 510), (300, 1570)),
# )

vid_src = VideoSource(
    vid_path="../real_data/20220831_103240.mp4",
    # skip_frames=0,
    rotate_degrees=1,
    #       y -> y      x -> x
    roi=((400, 520), (500, 1500)),
)

# tracker = ContourTracker(end_of_tube=950)
counter = ThresholdTracker(thresholds=[200, 400, 600, 800])

bg_subtractor = cv.createBackgroundSubtractorMOG2(detectShadows=False)
erode_kernel = np.ones((5, 5),np.uint8)

# warm up the background detector
for _ in range(2):
    frame = vid_src.read()
    bg_subtractor.apply(frame)

while vid_src.opened():
    try:
        frame = vid_src.read()
    except StopIteration:
        break

    # filter out the "background" - anything thats not moving (tube, etc)
    # leaving only particles in the water
    fg_mask = bg_subtractor.apply(frame)

    # erode the detection mask, removing noise and hopefully separating 
    # particles that are close together
    fg_mask = cv.erode(fg_mask, erode_kernel, iterations=2)

    # detect contours, filtering by area
    contours = detect_contours(fg_mask, min_area_thresh=60, debug_img=frame)

    # # update the contour tracker
    # tracker.update(contours)
    # tracker.visualise(frame)

    counter.update([contour_center(c) for c in contours], debug_img=frame)
    counter.visualise(frame)
    
    cv.imshow("fg", fg_mask)
    cv.imshow("frame", frame)

    if cv.waitKey(0) & 0xFF == ord('q'):
        # q to exit
        break

# display final image
cv.imshow("frame", frame)
input()
