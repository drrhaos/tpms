"""
Studio render of a car model for the TPMS main screen (3/4 view, nose to the bottom-left).

blender -b --factory-startup -P render_car.py -- MODEL.glb HDRI.hdr OUT_DIR THEME [YAW PITCH SAMPLES RES]

THEME: dark (graphite paint) | light (silver paint). Writes OUT_DIR/car_THEME.png (transparent,
shadow catcher) and OUT_DIR/anchors_THEME.json (wheel hubs as fractions of the image).
"""
import bpy, sys, json, math
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view

argv = sys.argv[sys.argv.index("--") + 1:]
MODEL, HDRI, OUT, THEME = argv[:4]
YAW = float(argv[4]) if len(argv) > 4 else 34.0
PITCH = float(argv[5]) if len(argv) > 5 else 38.0
SAMPLES = int(argv[6]) if len(argv) > 6 else 192
RES = int(argv[7]) if len(argv) > 7 else 1400
TAG = argv[8] if len(argv) > 8 else ""

# The model ships colour variants (KHR_materials_variants): pick one per theme by material name.
VARIANT = "Graphite"
# (base colour, roughness) of the main paint / the darker accent panels, per theme. Clear coat on
# top, no thin film (it shows as rainbow blotches at this size).
PAINT_PARAMS = {
    "dark": {"Paint 1": (0.06, 0.32), "Paint 2": (0.025, 0.25)},
    "light": {"Paint 1": (0.62, 0.30), "Paint 2": (0.30, 0.25)},
}[THEME]
INTERIOR_VARIANT = "Graphite"
HIDE = ("License Plate",)   # may carry a logo

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=MODEL)
scene = bpy.context.scene

for o in list(scene.objects):
    if o.name in HIDE:
        bpy.data.objects.remove(o, do_unlink=True)

meshes = [o for o in scene.objects if o.type == 'MESH']

def world_bbox(objs):
    pts = [o.matrix_world @ Vector(c) for o in objs for c in o.bound_box]
    return (Vector([min(p[i] for p in pts) for i in range(3)]),
            Vector([max(p[i] for p in pts) for i in range(3)]))

lo, hi = world_bbox(meshes)
size = hi - lo
centre = (lo + hi) / 2

# --- orientation: the long horizontal axis is the car's length; wheels tell front from rear ---
def is_wheel(o):
    n = o.name.lower()
    return any(k in n for k in ("wheel", "tire", "tyre", "rim"))

wheels = [o for o in scene.objects if o.type == 'EMPTY' and o.name.lower().startswith("wheel")] \
    or [o for o in meshes if is_wheel(o)]
long_axis = 0 if size.x > size.y else 1
print("BBOX", tuple(round(v, 3) for v in lo), tuple(round(v, 3) for v in hi), "long axis", "XY"[long_axis])

# --- materials: swap the "Carmine" variant for the theme's variant (paint) and graphite (interior) ---
def variant_of(name, variant):
    return bpy.data.materials.get(name.replace("Carmine", variant)) if "Carmine" in name else None

for o in meshes:
    for slot in o.material_slots:
        m = slot.material
        if not m or "Carmine" not in m.name:
            continue
        target = variant_of(m.name, VARIANT if m.name.startswith("Paint") else INTERIOR_VARIANT)
        if target:
            slot.material = target
for prefix, (base, rough) in PAINT_PARAMS.items():
    m = bpy.data.materials.get(f"{prefix} {VARIANT}")
    for n in (m.node_tree.nodes if m else []):
        if n.type != 'BSDF_PRINCIPLED':
            continue
        n.inputs["Base Color"].default_value = (base, base, base * 1.03, 1.0)
        n.inputs["Roughness"].default_value = rough
        n.inputs["Coat Weight"].default_value = 1.0
        n.inputs["Coat Roughness"].default_value = 0.03
        if "Thin Film Thickness" in n.inputs:
            for link in list(n.inputs["Thin Film Thickness"].links):
                m.node_tree.links.remove(link)
            n.inputs["Thin Film Thickness"].default_value = 0.0
print("VARIANT", VARIANT, THEME)

# --- world: studio HDRI for reflections, kept out of the picture (transparent film) ---
world = bpy.data.worlds.new("Studio")
scene.world = world
world.use_nodes = True
nt = world.node_tree
nt.nodes.clear()
env = nt.nodes.new("ShaderNodeTexEnvironment")
env.image = bpy.data.images.load(HDRI)
bg = nt.nodes.new("ShaderNodeBackground")
bg.inputs["Strength"].default_value = 1.0
out = nt.nodes.new("ShaderNodeOutputWorld")
nt.links.new(env.outputs["Color"], bg.inputs["Color"])
nt.links.new(bg.outputs["Background"], out.inputs["Surface"])

# Soft key light from above for the long highlights a car render lives on.
key_data = bpy.data.lights.new("Key", type='AREA')
key_data.energy = 900.0 * max(size) ** 2 / 20
key_data.size = max(size) * 1.6
key = bpy.data.objects.new("Key", key_data)
scene.collection.objects.link(key)
key.location = centre + Vector((0, 0, size.z * 4))
key.rotation_euler = (0, 0, 0)

# --- ground: shadow catcher at the lowest point ---
def lowest_vertex_z(objs):
    zs = [(o.matrix_world @ v.co).z for o in objs for v in o.data.vertices]
    return min(zs) if zs else lo.z
tyres = [o for o in meshes if any(s.material and "tire" in s.material.name.lower() for s in o.material_slots)]
ground_z = lowest_vertex_z(tyres or meshes)
print("GROUND", round(ground_z, 3))
bpy.ops.mesh.primitive_plane_add(size=max(size) * 2.0, location=(centre.x, centre.y, ground_z))
ground = bpy.context.active_object
ground.is_shadow_catcher = True

# --- camera: orbit so the nose points to the bottom-left of the frame ---
cam_data = bpy.data.cameras.new("Cam")
cam_data.lens = 85
cam = bpy.data.objects.new("Cam", cam_data)
scene.collection.objects.link(cam)
scene.camera = cam

# Front direction: the wheel pair furthest along the long axis that is nearest the bonnet is
# unknown in general; FRONT_SIGN is set from the model's naming when possible.
def front_sign():
    front = [o for o in meshes if "headlight" in o.name.lower()] or [o for o in wheels if "front" in o.name.lower()]
    rear = [o for o in meshes if "taillight" in o.name.lower()] or [o for o in wheels if "rear" in o.name.lower()]
    if front and rear:
        f = sum((world_bbox([o])[0][long_axis] + world_bbox([o])[1][long_axis]) / 2 for o in front) / len(front)
        r = sum((world_bbox([o])[0][long_axis] + world_bbox([o])[1][long_axis]) / 2 for o in rear) / len(rear)
        return 1.0 if f > r else -1.0
    return 1.0

FRONT = front_sign()
fwd = Vector((0, 0, 0)); fwd[long_axis] = FRONT
up = Vector((0, 0, 1))
left = up.cross(fwd)   # the car's left side (right-handed, z up)
yaw = math.radians(YAW); pitch = math.radians(PITCH)
# Camera ahead-left of the car and above it: we look at the nose and the left flank.
horiz = (fwd * math.cos(yaw) + left * math.sin(yaw)).normalized()
dist = max(size) * 3.2
direction = (horiz * math.cos(pitch) + up * math.sin(pitch)).normalized()
cam.location = centre + direction * dist
look = (centre - cam.location).normalized()
cam.rotation_euler = look.to_track_quat('-Z', 'Y').to_euler()

# Frame the car: fit the projected bbox with a margin.
r = scene.render
r.engine = 'CYCLES'
scene.cycles.samples = SAMPLES
scene.cycles.use_denoising = True
r.film_transparent = True
r.resolution_x = RES
r.resolution_y = RES
r.resolution_percentage = 100
scene.view_settings.view_transform = 'AgX'
scene.view_settings.look = 'AgX - Medium High Contrast'
bpy.context.view_layer.update()
corners = [Vector((x, y, z)) for x in (lo.x, hi.x) for y in (lo.y, hi.y) for z in (lo.z, hi.z)]
for _ in range(25):
    pr = [world_to_camera_view(scene, cam, c) for c in corners]
    span = max(max(p.x for p in pr) - min(p.x for p in pr), max(p.y for p in pr) - min(p.y for p in pr))
    if abs(span - 0.86) < 0.01:
        break
    cam.location = centre + (cam.location - centre) * (span / 0.86)
    bpy.context.view_layer.update()

try:
    scene.cycles.device = 'CPU'
except Exception:
    pass

r.filepath = f"{OUT}/car_{THEME}{TAG}.png"
bpy.ops.render.render(write_still=True)

anchors = {}
for o in wheels:
    p = world_to_camera_view(scene, cam, o.matrix_world.translation)
    anchors[o.name] = (round(p.x, 4), round(1 - p.y, 4), round(p.z, 3))
with open(f"{OUT}/anchors_{THEME}{TAG}.json", "w") as f:
    json.dump({"front_sign": FRONT, "long_axis": "XY"[long_axis], "wheels": anchors}, f, indent=1)
print("DONE", r.filepath)
