from kivy.uix.boxlayout import BoxLayout
from kivy.properties import StringProperty, NumericProperty

# SEE coralspawncounter.kv for widget definitions

class AppLayout(BoxLayout):
    pass

class ControlsSidebar(BoxLayout):
    pass

class LabelledSlider(BoxLayout):
    text = StringProperty()
    value = NumericProperty(0.)
    min = NumericProperty(0.)
    max = NumericProperty(0.)
