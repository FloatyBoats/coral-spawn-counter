from random import randint

Colour = tuple[int, int, int]

BLUE: Colour = (255, 0, 0)
GREEN: Colour = (0, 255, 0)
RED: Colour = (0, 0, 255)

def rand_colour() -> Colour:
    return (
        randint(0, 255),
        randint(0, 255),
        randint(0, 255),
    )
