from kivy.uix.boxlayout import BoxLayout
from kivy.properties import StringProperty
from kivy.uix.slider import Slider

# SEE coralspawncounter.kv for widget definitions

class AppLayout(BoxLayout):
    pass

class ControlsSidebar(BoxLayout):
    pass

class LabelledSlider(Slider):
    text = StringProperty()
