import cv2 as cv
import numpy as np

from video_source import VideoSource
from contours import detect_contours, contour_center
from counters import ThresholdTracker

vid_src = VideoSource(
    vid_path="/home/alistair/Videos/Coral Spawn/Whitsundays/20221117_154038_raw.mp4",
    skip_frames=0,
    rotate_degrees=0,
    #       y -> y      x -> x
    roi=((400, 610), (400, 1450)),
)

counter = ThresholdTracker(thresholds=[300, 700])

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

    # filter out the "background" - anything thats not moving (tube, etc)
    # leaving only particles in the water
    fg_mask = bg_subtractor.apply(frame)

    # erode the detection mask, removing noise and hopefully separating 
    # particles that are close together
    fg_mask = cv.erode(fg_mask, erode_kernel, iterations=1)

    # detect contours, filtering by area
    contours = detect_contours(fg_mask, min_area_thresh=100, debug_img=frame)

    counter.update([contour_center(c) for c in contours], debug_img=frame)
    counter.visualise(frame)
    

    # cv.imshow("fg", fg_mask)
    # cv.imshow("frame", frame)  

    # if cv.waitKey(20) & 0xFF == ord('q'):
    #     # q to exit
    #     break


# display final image
# cv.imshow("frame", frame)

print(f"Counts: {counter.threhold_counts}")
# input()
