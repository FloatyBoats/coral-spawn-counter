from kivy.clock import mainthread
from kivy.graphics import Color, Rectangle
from kivy.graphics.texture import Texture
import numpy as np
import cv2 as cv
from camera4kivy import Preview

from contours import detect_contours, contour_center
from counters import ThresholdTracker

from typing import Optional

counter = ThresholdTracker(thresholds=[300, 700])
bg_subtractor = cv.createBackgroundSubtractorMOG2(detectShadows=False)
erode_kernel = np.ones((3, 3),np.uint8)

roi_y, roi_x = ((400, 610), (400, 1450))



class SpawnCounter(Preview):
    analyzed_texture: Optional[Texture] = None

    def analyze_pixels_callback(
        self,
        pixels: bytes,
        image_size: tuple[int, int],
        image_pos: tuple[int, int],
        scale: float,
        mirror: bool,
    ):
        """
        Analyze a Frame - NOT on UI Thread

        pixels: analyze pixels (bytes)
        image_size: analyze pixels size (w,h)
        image_pos: location of Texture in Preview (due to letterbox)
        scale: scale from Analysis resolution to Preview resolution
        mirror: true if Preview is mirrored
        """
        width, height = image_size
        channels = 4  # rgba
        rgba_frame = np.fromstring(pixels, np.uint8).reshape(height, width, channels)
        roi_frame = rgba_frame[roi_y[0]:roi_y[1], roi_x[0]:roi_x[1]]

        frame = cv.cvtColor(roi_frame, cv.COLOR_RGBA2BGR)
        grey_frame = cv.cvtColor(roi_frame, cv.COLOR_RGBA2GRAY)

        fg_mask = bg_subtractor.apply(frame)
        fg_mask = cv.erode(fg_mask, erode_kernel, iterations=1)
        contours = detect_contours(fg_mask, grey_frame, min_area_thresh=100, debug_img=frame)

        cv.rectangle(rgba_frame, (roi_x[0], roi_y[0]), (roi_x[1], roi_y[1]), (0, 255, 0, 255))
        for contour in contours:
            x, y = contour_center(contour)
            x += roi_x[0]
            y += roi_y[0]
            cv.circle(
                rgba_frame,
                (x, y),
                30,
                (0, 0, 255, 255),
                thickness=2,
            )

        pixels = rgba_frame.tostring()

        self.make_thread_safe(pixels, image_size)

    @mainthread
    def make_thread_safe(self, pixels, size):
        if not self.analyzed_texture or\
           self.analyzed_texture.size[0] != size[0] or\
           self.analyzed_texture.size[1] != size[1]:
            self.analyzed_texture = Texture.create(size=size, colorfmt='rgba')
            self.analyzed_texture.flip_vertical()
        self.analyzed_texture.blit_buffer(pixels, colorfmt='rgba') 

    ################################
    # Annotate Screen - on UI Thread
    ################################

    def canvas_instructions_callback(self, texture, tex_size, tex_pos):
        # texture : preview Texture
        # size    : preview Texture size (w,h)
        # pos     : location of Texture in Preview Widget (letterbox)
        # Add the analyzed image
        if self.analyzed_texture:
            Color(1,1,1,1)
            Rectangle(texture= self.analyzed_texture,
                      size = tex_size, pos = tex_pos)
