from kivy.app import App
from kivy.clock import Clock
from android_permissions import AndroidPermissions

from widgets import AppLayout

class CoralSpawnCounterApp(App):
    def build(self):
        self.layout = AppLayout()
        return self.layout

    def on_start(self):
        # assigned to self.dont_gc so the class isnt garbage collected
        # this is very sus way to do it and ill change it when i get a chance
        self.dont_gc = AndroidPermissions(self.start_app)

    def start_app(self):
        self.dont_gc = None
        # Can't connect camera till after on_start()
        Clock.schedule_once(self.connect_camera)

    def connect_camera(self, dt):
        self.layout.preview.connect_camera(
            analyze_pixels_resolution=3840,
            enable_analyze_pixels=True,
        )

    def on_stop(self):
        self.layout.preview.disconnect_camera()

CoralSpawnCounterApp().run()
