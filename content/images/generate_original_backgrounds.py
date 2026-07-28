from __future__ import annotations

import random
from collections.abc import Callable
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

WIDTH = 1200
HEIGHT = 1600
OUTPUT_DIR = Path(__file__).parent
PAPER = (232, 231, 226)
INK = (58, 59, 58)
MID_INK = (91, 91, 88)
RED = (145, 42, 46)


def paper_texture(seed: int) -> Image.Image:
    random_source = random.Random(seed)
    image = Image.new("RGB", (WIDTH, HEIGHT), PAPER)
    pixels = image.load()
    for y in range(HEIGHT):
        for x in range(WIDTH):
            grain = random_source.randint(-6, 6)
            pixels[x, y] = (
                PAPER[0] + grain,
                PAPER[1] + grain,
                PAPER[2] + grain,
            )
    return image


def save_background(
    name: str,
    seed: int,
    painter: Callable[[ImageDraw.ImageDraw], None],
) -> None:
    image = paper_texture(seed)
    layer = Image.new("RGBA", image.size, (0, 0, 0, 0))
    painter(ImageDraw.Draw(layer))
    layer = layer.filter(ImageFilter.GaussianBlur(radius=3.2))
    composed = Image.alpha_composite(image.convert("RGBA"), layer).convert("RGB")
    composed.save(
        OUTPUT_DIR / f"{name}.jpg",
        quality=91,
        optimize=True,
        progressive=True,
    )


def mountains(draw: ImageDraw.ImageDraw) -> None:
    draw.ellipse((820, 160, 1030, 370), fill=(*RED, 108))
    draw.polygon(
        [(0, 1120), (180, 900), (330, 1040), (520, 780), (760, 1060), (940, 850), (1200, 1100), (1200, 1600), (0, 1600)],
        fill=(*MID_INK, 70),
    )
    draw.polygon(
        [(0, 1280), (250, 1070), (430, 1200), (690, 980), (900, 1190), (1200, 1010), (1200, 1600), (0, 1600)],
        fill=(*INK, 88),
    )
    draw.line((170, 1370, 1030, 1370), fill=(*RED, 78), width=5)


def field_paths(draw: ImageDraw.ImageDraw) -> None:
    draw.ellipse((905, 220, 1035, 350), fill=(*RED, 78))
    for offset, alpha in [(0, 42), (90, 50), (180, 58), (280, 68), (390, 78)]:
        draw.arc(
            (-260 + offset, 740 + offset, 1460 - offset // 2, 1700 + offset // 3),
            start=194,
            end=346,
            fill=(*MID_INK, alpha),
            width=18,
        )
    draw.line((520, 1600, 570, 1320, 665, 1090, 805, 910), fill=(*INK, 72), width=54)
    draw.line((540, 1600, 590, 1330, 680, 1110, 820, 930), fill=(*PAPER, 160), width=22)


def reading_lamp(draw: ImageDraw.ImageDraw) -> None:
    draw.polygon([(735, 660), (1015, 1085), (570, 1085)], fill=(*RED, 28))
    draw.polygon([(660, 590), (800, 530), (895, 640), (720, 710)], fill=(*MID_INK, 92))
    draw.line((815, 565, 900, 290), fill=(*INK, 88), width=24)
    draw.line((890, 290, 1015, 290), fill=(*INK, 88), width=22)
    draw.line((760, 680, 760, 1080), fill=(*INK, 74), width=18)
    draw.ellipse((650, 1060, 870, 1110), fill=(*INK, 60))
    draw.polygon([(240, 1110), (580, 1025), (600, 1325), (250, 1390)], fill=(*MID_INK, 72))
    draw.polygon([(600, 1025), (950, 1110), (930, 1390), (600, 1325)], fill=(*INK, 62))
    draw.line((600, 1030, 600, 1320), fill=(*RED, 82), width=6)
    for y in range(1135, 1285, 46):
        draw.line((315, y, 520, y - 42), fill=(*PAPER, 145), width=8)
        draw.line((675, y - 42, 870, y), fill=(*PAPER, 135), width=8)


def river_bridge(draw: ImageDraw.ImageDraw) -> None:
    draw.polygon(
        [(0, 1110), (240, 1000), (460, 1050), (710, 970), (960, 1030), (1200, 940), (1200, 1600), (0, 1600)],
        fill=(*MID_INK, 48),
    )
    draw.line((0, 1260, 300, 1170, 610, 1210, 900, 1115, 1200, 1160), fill=(*PAPER, 170), width=90)
    draw.line((95, 1080, 1105, 820), fill=(*INK, 78), width=34)
    draw.line((95, 1105, 1105, 845), fill=(*RED, 54), width=7)
    for x, y in [(260, 1038), (500, 976), (740, 914), (980, 850)]:
        draw.line((x, y, x + 20, 1270), fill=(*INK, 56), width=20)
    draw.ellipse((130, 230, 285, 385), outline=(*RED, 65), width=12)


def printing_press(draw: ImageDraw.ImageDraw) -> None:
    draw.rectangle((180, 710, 965, 1365), fill=(*MID_INK, 38), outline=(*INK, 76), width=15)
    draw.rectangle((265, 800, 875, 1275), fill=(*PAPER, 150), outline=(*INK, 56), width=10)
    for row in range(7):
        y = 875 + row * 54
        draw.line((350, y, 790 - (row % 3) * 55, y), fill=(*INK, 70), width=12)
    for x in (235, 330, 425, 520, 615, 710, 805, 900):
        draw.rectangle((x, 590, x + 55, 665), fill=(*INK, 48))
    draw.ellipse((820, 690, 1040, 910), outline=(*RED, 76), width=18)
    draw.line((170, 1435, 1010, 1435), fill=(*RED, 58), width=6)


def workshop(draw: ImageDraw.ImageDraw) -> None:
    draw.polygon(
        [(130, 990), (310, 825), (480, 990), (650, 825), (820, 990), (1035, 900), (1110, 1010), (1110, 1440), (130, 1440)],
        fill=(*MID_INK, 72),
    )
    draw.rectangle((770, 560, 885, 990), fill=(*INK, 66))
    draw.rectangle((930, 690, 1015, 955), fill=(*INK, 58))
    for x in (225, 410, 595, 780, 965):
        draw.rectangle((x, 1080, x + 90, 1200), fill=(*PAPER, 130))
    draw.ellipse((475, 1050, 705, 1280), outline=(*RED, 70), width=24)
    draw.ellipse((545, 1120, 635, 1210), fill=(*RED, 45))
    draw.line((160, 1500, 1080, 1500), fill=(*INK, 54), width=12)


def memorial_pine(draw: ImageDraw.ImageDraw) -> None:
    draw.ellipse((805, 215, 995, 405), fill=(*RED, 62))
    draw.line((405, 1510, 475, 470), fill=(*INK, 88), width=42)
    crowns = [
        [(470, 460), (245, 805), (650, 720)],
        [(455, 650), (125, 1050), (770, 930)],
        [(440, 860), (70, 1320), (830, 1160)],
        [(425, 1080), (105, 1480), (770, 1370)],
    ]
    for index, points in enumerate(crowns):
        draw.polygon(points, fill=(*MID_INK, 42 + index * 8))
        draw.line(points + [points[0]], fill=(*INK, 46), width=12)
    for y, reach in [(710, 250), (900, 330), (1110, 370), (1300, 315)]:
        draw.line((450, y, 450 - reach, y + 155), fill=(*INK, 62), width=20)
        draw.line((450, y + 20, 450 + reach, y + 135), fill=(*INK, 58), width=20)
    draw.line((230, 1440, 980, 1440), fill=(*RED, 62), width=6)


def dawn_city(draw: ImageDraw.ImageDraw) -> None:
    draw.ellipse((470, 510, 730, 770), fill=(*RED, 70))
    draw.line((150, 780, 1050, 780), fill=(*RED, 56), width=7)
    buildings = [(80, 1100, 250), (265, 940, 430), (445, 1040, 590), (605, 860, 790), (805, 1010, 990), (1005, 930, 1160)]
    for left, top, right in buildings:
        draw.rectangle((left, top, right, 1480), fill=(*MID_INK, 66))
        for y in range(top + 90, 1400, 105):
            draw.line((left + 45, y, right - 35, y), fill=(*PAPER, 105), width=12)
    draw.line((100, 1480, 1110, 1480), fill=(*INK, 70), width=18)


def main() -> None:
    backgrounds = [
        ("distant-mountains", 1937, mountains),
        ("field-paths", 1927, field_paths),
        ("reading-lamp", 1941, reading_lamp),
        ("river-bridge", 1943, river_bridge),
        ("printing-press", 1942, printing_press),
        ("workshop", 1948, workshop),
        ("memorial-pine", 1939, memorial_pine),
        ("dawn-city", 1949, dawn_city),
    ]
    for name, seed, painter in backgrounds:
        save_background(name, seed, painter)


if __name__ == "__main__":
    main()
