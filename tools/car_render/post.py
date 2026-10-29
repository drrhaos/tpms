"""Clean, trim and anchor the renders for the app.

python3 -I post.py OUT_DIR [PREFIX] [ANCHORS]  ->  OUT_DIR/car_3d_{dark,light}.webp + Kotlin constants.

PREFIX: render file prefix, "car" (render_car.py, default) or "xray" (xray_render.py).
ANCHORS: "silhouette" (default: hidden right wheels moved to the opaque outline) or "direct"
(every hub as projected: in the x-ray look all four wheels show through the body).

Both themes share one crop (same camera), so one CAR_IMAGE_ASPECT and one WHEEL_ANCHORS fit both.
"""
import json, math, os, subprocess, sys

out = sys.argv[1]
PREFIX = sys.argv[2] if len(sys.argv) > 2 else "car"
DIRECT = (sys.argv[3] if len(sys.argv) > 3 else "silhouette") == "direct"
ANCHOR_FILE = "anchors" if PREFIX == "car" else f"{PREFIX}_anchors"
THEMES = ("dark", "light")
HAZE = 8      # % alpha below which the shadow catcher's haze is dropped

def run(*a):
    return subprocess.run(a, capture_output=True, check=True).stdout

# 1. Drop the haze, keep the contact shadow soft: alpha' = (alpha - 8%) / 92%.
for t in THEMES:
    run("magick", f"{out}/{PREFIX}_{t}.png", "-channel", "A", "-level", f"{HAZE}%,100%", "+channel", f"{out}/clean_{t}.png")

W, H = map(int, run("magick", "identify", "-format", "%w %h", f"{out}/clean_dark.png").split())

# 2. One crop for both: union of the content boxes plus a small margin.
boxes = []
for t in THEMES:
    geo = run("magick", f"{out}/clean_{t}.png", "-alpha", "extract", "-threshold", "1%", "-format", "%@", "info:").decode()
    wh, x, y = geo.split("+"); w, h = map(int, wh.split("x")); boxes.append((int(x), int(y), int(x) + w, int(y) + h))
x0 = min(b[0] for b in boxes); y0 = min(b[1] for b in boxes)
x1 = max(b[2] for b in boxes); y1 = max(b[3] for b in boxes)
m = int(0.015 * max(x1 - x0, y1 - y0))
x0, y0, x1, y1 = max(0, x0 - m), max(0, y0 - m), min(W, x1 + m), min(H, y1 + m)
cw, ch = x1 - x0, y1 - y0

# 3. Anchors: visible (left) hubs as projected; hidden (right) hubs moved across the car to the
#    opaque silhouette edge (the translucent shadow does not count).
anch = json.load(open(f"{out}/{ANCHOR_FILE}_dark.json"))["wheels"]
px = {k: (v[0] * W, v[1] * H) for k, v in anch.items()}
alpha = run("magick", f"{out}/{PREFIX}_dark.png", "-alpha", "extract", "-depth", "8", "gray:-")
A = lambda x, y: alpha[int(y) * W + int(x)] if 0 <= x < W and 0 <= y < H else 0
dx = px["WheelFrontR"][0] - px["WheelFrontL"][0]; dy = px["WheelFrontR"][1] - px["WheelFrontL"][1]
l = math.hypot(dx, dy); dx, dy = dx / l, dy / l

# Edge of the car: the opaque body for the studio look; for the translucent x-ray body, where
# coverage drops to almost nothing (the outer outline).
EDGE_ALPHA = 250 if PREFIX == "car" else 110


def to_silhouette(x, y):
    steps = 0
    while A(x, y) > EDGE_ALPHA and steps < 4000:
        x += dx; y += dy; steps += 1
    return x - dx * 6, y - dy * 6

pts = {
    "FL": px["WheelFrontL"], "RL": px["WheelRearL"],
    "FR": px["WheelFrontR"] if DIRECT else to_silhouette(*px["WheelFrontR"]),
    "RR": px["WheelRearR"] if DIRECT else to_silhouette(*px["WheelRearR"]),
}
norm = {k: ((x - x0) / cw, (y - y0) / ch) for k, (x, y) in pts.items()}

# 4. Crop and encode.
for t in THEMES:
    run("magick", f"{out}/clean_{t}.png", "-crop", f"{cw}x{ch}+{x0}+{y0}", "+repage",
        "-resize", "1200x1200>", "-quality", "90", "-define", "webp:alpha-quality=100", f"{out}/car_3d_{t}.webp")
    run("magick", f"{out}/clean_{t}.png", "-crop", f"{cw}x{ch}+{x0}+{y0}", "+repage", f"{out}/car_3d_{t}.png")

# 5. Wheel masks (xray_render.py ... masks): same crop, white silhouettes the app tints by state.
for k, name in (("FL", "wheel_fl"), ("FR", "wheel_fr"), ("RL", "wheel_rl"), ("RR", "wheel_rr"), ("AXLES", "axles")):
    src = f"{out}/mask_{k}.png"
    if os.path.exists(src):
        run("magick", src, "-crop", f"{cw}x{ch}+{x0}+{y0}", "+repage", "-resize", "1200x1200>",
            "-quality", "90", "-define", "webp:alpha-quality=100", f"{out}/car_3d_{name}.webp")

# 6. Surroundings (xray_render.py ... env): road, grid, light pool rendered in a wider frame with the
#    same camera. Downscaled by ENV_SCALE; its rect is printed in car-image fractions.
ENV_SCALE = 0.5
env_rect = None
if os.path.exists(f"{out}/env_meta.json"):
    meta = json.load(open(f"{out}/env_meta.json"))
    ew, eh = meta["size"]; ox, oy = meta["origin"]
    for t in THEMES:
        if os.path.exists(f"{out}/env_{t}.png"):
            run("magick", f"{out}/env_{t}.png", "-resize", f"{int(ew * ENV_SCALE)}x{int(eh * ENV_SCALE)}!",
                "-quality", "85", "-define", "webp:alpha-quality=90", f"{out}/car_3d_env_{t}.webp")
    env_rect = (-(ox + x0) / cw, -(oy + y0) / ch, ew / cw, eh / ch)

print(f"private const val CAR_IMAGE_ASPECT = {cw}f / {ch}f")
if env_rect:
    print("private val ENV_RECT = Rect(Offset({:.4f}f, {:.4f}f), Size({:.4f}f, {:.4f}f))".format(*env_rect))
for k in ("FL", "FR", "RL", "RR"):
    print(f'    "{k}" to Offset({norm[k][0]:.3f}f, {norm[k][1]:.3f}f),')
json.dump({"size": [cw, ch], "anchors": norm}, open(f"{out}/app_anchors.json", "w"), indent=1)
