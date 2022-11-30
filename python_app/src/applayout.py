from kivy.lang import Builder
from kivy.properties import ObjectProperty
from kivy.uix.floatlayout import FloatLayout
from spawn_counter import SpawnCounter

class AppLayout(FloatLayout):
    edge_detect = ObjectProperty()

Builder.load_string("""
<AppLayout>:
    edge_detect: self.ids.preview
    SpawnCounter:
        aspect_ratio: '16:9'
        id:preview
""")
