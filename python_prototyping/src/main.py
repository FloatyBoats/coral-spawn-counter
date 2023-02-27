import cv2 as cv
import numpy as np

from video_source import VideoSource
from contours import detect_contours, contour_center, filter_contours
from counters import ThresholdTracker

video_name = "20221213_153310_raw"

vid_src = VideoSource(
    vid_path=f"/home/alistair/Videos/Coral Spawn/Lizard Island/{video_name}.mp4",
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
    # dialte to expand the mask around the spawn
    fg_mask = cv.dilate(fg_mask, dialate_kernel, iterations=1)

    # apply the mask
    # grey_frame_masked = cv.bitwise_and(grey_frame, grey_frame, mask=fg_mask)

    # threshold the masked grey to try and find the edge of the spawn
    # _, fg_mask = cv.threshold(grey_frame_masked, 0, 255, cv.THRESH_BINARY + cv.THRESH_OTSU)
    fg_mask = cv.adaptiveThreshold(grey_frame, 255, cv.ADAPTIVE_THRESH_GAUSSIAN_C, cv.THRESH_BINARY_INV, 5, 2)

    # detect contours, filtering by area
    contours = detect_contours(fg_mask)
    contours = filter_contours(
        contours,
        frame,
        min_area=50,
        min_convexity=0.6,
        min_aspect_ratio=0.4,
    )

    counter.update(
        [contour_center(c) for c in contours],
        debug_img=frame,
        binary_img=fg_mask,
        video_name=video_name
    )
    counter.visualise(frame)
    
    if len(contours) < 1:
        continue

    cv.imshow("fg", fg_mask)
    cv.imshow("frame", frame)

    if cv.waitKey(0) & 0xFF == ord('q'):
        # q to exit
        break


# display final image
# cv.imshow("frame", frame)

print(f"Counts: {counter.threshold_counts}")
# input()
