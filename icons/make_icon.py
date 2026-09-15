"""Generates FastFinder's app icon: a rounded-square badge in the app's own accent
purple with a white magnifying glass, matching the in-app color palette
(LightAppColors.accent = #5B4FE0 in AppTheme.kt). Drawn at high resolution and
downsampled for clean anti-aliased edges at small sizes (16/32px taskbar/shortcut).
"""
from PIL import Image, ImageDraw

SUPERSAMPLE = 8
SIZE = 256 * SUPERSAMPLE

ACCENT = (0x5B, 0x4F, 0xE0, 255)
ACCENT_DARK = (0x46, 0x3C, 0xB8, 255)
WHITE = (0xFF, 0xFF, 0xFF, 255)

img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)

# Rounded-square background with a subtle top-to-bottom gradient (accent -> darker accent),
# matching Windows 11's "squircle" app-icon convention.
corner_radius = int(SIZE * 0.225)
mask = Image.new("L", (SIZE, SIZE), 0)
mask_draw = ImageDraw.Draw(mask)
mask_draw.rounded_rectangle([0, 0, SIZE - 1, SIZE - 1], radius=corner_radius, fill=255)

gradient = Image.new("RGBA", (SIZE, SIZE), 0)
for y in range(SIZE):
    t = y / SIZE
    r = int(ACCENT[0] + (ACCENT_DARK[0] - ACCENT[0]) * t)
    g = int(ACCENT[1] + (ACCENT_DARK[1] - ACCENT[1]) * t)
    b = int(ACCENT[2] + (ACCENT_DARK[2] - ACCENT[2]) * t)
    ImageDraw.Draw(gradient).line([(0, y), (SIZE, y)], fill=(r, g, b, 255))

img.paste(gradient, (0, 0), mask)
draw = ImageDraw.Draw(img)

# Magnifying glass: thick white ring + handle, centered with headroom above/below
# matching the badge's rounded corners.
cx, cy = SIZE * 0.44, SIZE * 0.44
lens_radius = SIZE * 0.195
ring_width = int(SIZE * 0.075)

draw.ellipse(
    [cx - lens_radius, cy - lens_radius, cx + lens_radius, cy + lens_radius],
    outline=WHITE,
    width=ring_width,
)

# Handle: a rounded-cap thick line from the lens's bottom-right edge out to the badge corner.
import math
angle = math.radians(45)
start_r = lens_radius + ring_width * 0.15
end_r = lens_radius + SIZE * 0.235
x1 = cx + start_r * math.cos(angle)
y1 = cy + start_r * math.sin(angle)
x2 = cx + end_r * math.cos(angle)
y2 = cy + end_r * math.sin(angle)
handle_width = int(SIZE * 0.085)
draw.line([(x1, y1), (x2, y2)], fill=WHITE, width=handle_width)
cap_r = handle_width / 2
for (cxp, cyp) in [(x1, y1), (x2, y2)]:
    draw.ellipse([cxp - cap_r, cyp - cap_r, cxp + cap_r, cyp + cap_r], fill=WHITE)

master = img.resize((1024, 1024), Image.LANCZOS)
master.save("icons/app_master.png")

ico_sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
master.save("icons/app.ico", sizes=ico_sizes)

# A single mid-size PNG for the in-app runtime window/taskbar icon.
runtime_png = img.resize((256, 256), Image.LANCZOS)
runtime_png.save("src/main/resources/icon.png")

print("done")
