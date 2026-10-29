"""
X-ray studio render of a car model for the TPMS main screen (3/4 view, nose to the bottom-left).

blender -b --factory-startup -P render_car.py -- MODEL.glb HDRI.hdr OUT_DIR THEME [YAW PITCH SAMPLES RES]

THEME: dark (graphite paint) | light (silver paint). Writes OUT_DIR/car_THEME.png (transparent,
shadow catcher) and OUT_DIR/anchors_THEME.json (wheel hubs as fractions of the image).
"""
import bpy, sys, json, math, re
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view

argv = sys.argv[sys.argv.index("--") + 1:]
MODEL, HDRI, OUT, THEME = argv[:4]
YAW = float(argv[4]) if len(argv) > 4 else 34.0
PITCH = float(argv[5]) if len(argv) > 5 else 38.0
SAMPLES = int(argv[6]) if len(argv) > 6 else 192
RES = int(argv[7]) if len(argv) > 7 else 1400
TAG = argv[8] if len(argv) > 8 else ""
# "masks": instead of the picture, one white silhouette per wheel (tyre + rim) with the same camera,
# for the app to tint by tyre state (mask_FL.png ...).
MODE = argv[9] if len(argv) > 9 else "render"

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

# --- x-ray look: translucent glowing body shell with a fine wire, glowing internals; wheels,
# lights, mirrors, glass and the cabin keep their realistic materials ---
import bmesh
XRAY = {
    "dark": dict(tint=(0.10, 0.45, 1.0), rim=1.8, face=0.07, edge=0.45, wire=1.6, wire_alpha=0.32, core=2.5, gloss=(0.10, 0.14, 0.20),
                 cabin=dict(face=0.05, edge=0.30, rim=2.4), seats=dict(face=0.13, edge=0.45, rim=2.4),
                 dash=dict(face=0.02, edge=0.18, rim=2.0), drive=dict(face=0.90, edge=0.10, rim=1.3, tint=(0.08, 0.55, 1.0)),
                 glass=(0.004, 0.012, 0.03), glass_alpha=0.55),
    "light": dict(tint=(0.04, 0.26, 0.80), rim=1.0, face=0.08, edge=0.50, wire=0.9, wire_alpha=0.32, core=1.4, gloss=(0.12, 0.15, 0.20),
                  cabin=dict(face=0.06, edge=0.30, rim=1.2), seats=dict(face=0.14, edge=0.45, rim=1.2),
                  dash=dict(face=0.025, edge=0.18, rim=1.0), drive=dict(face=0.90, edge=0.10, rim=0.6, tint=(0.01, 0.10, 0.45)),
                  glass=(0.02, 0.06, 0.16), glass_alpha=0.55),
}[THEME]

def is_shell(o):
    return any(s.material and s.material.name.startswith("Paint") for s in o.material_slots)

def is_core(o):
    n = o.name.lower()
    return any(k in n for k in ("engine", "axle"))

def shell_material(name="XrayShell", face=None, edge=None, rim=None, tint=None):
    face = XRAY["face"] if face is None else face
    edge = XRAY["edge"] if edge is None else edge
    rim = XRAY["rim"] if rim is None else rim
    m = bpy.data.materials.new(name); m.use_nodes = True
    nt = m.node_tree; nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    lw = nt.nodes.new("ShaderNodeLayerWeight"); lw.inputs["Blend"].default_value = 0.4
    pw = nt.nodes.new("ShaderNodeMath"); pw.operation = 'POWER'; pw.inputs[1].default_value = 2.4
    nt.links.new(lw.outputs["Facing"], pw.inputs[0])
    fac = nt.nodes.new("ShaderNodeMath"); fac.operation = 'MULTIPLY_ADD'
    nt.links.new(pw.outputs[0], fac.inputs[0]); fac.inputs[1].default_value = edge; fac.inputs[2].default_value = face
    clamp = nt.nodes.new("ShaderNodeClamp"); nt.links.new(fac.outputs[0], clamp.inputs["Value"])
    em = nt.nodes.new("ShaderNodeEmission"); em.inputs["Color"].default_value = (*(tint or XRAY["tint"]), 1)
    em.inputs["Strength"].default_value = rim
    gl = nt.nodes.new("ShaderNodeBsdfGlossy"); gl.inputs["Roughness"].default_value = 0.08
    gl.inputs["Color"].default_value = (*XRAY["gloss"], 1)
    add = nt.nodes.new("ShaderNodeAddShader")
    nt.links.new(em.outputs[0], add.inputs[0]); nt.links.new(gl.outputs[0], add.inputs[1])
    tr = nt.nodes.new("ShaderNodeBsdfTransparent")
    mix = nt.nodes.new("ShaderNodeMixShader")
    nt.links.new(clamp.outputs[0], mix.inputs["Fac"])
    nt.links.new(tr.outputs[0], mix.inputs[1]); nt.links.new(add.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], out.inputs["Surface"])
    return m

def glow_material(name, strength, alpha):
    m = bpy.data.materials.new(name); m.use_nodes = True
    nt = m.node_tree; nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    em = nt.nodes.new("ShaderNodeEmission"); em.inputs["Color"].default_value = (*XRAY["tint"], 1)
    em.inputs["Strength"].default_value = strength
    tr = nt.nodes.new("ShaderNodeBsdfTransparent")
    mix = nt.nodes.new("ShaderNodeMixShader"); mix.inputs["Fac"].default_value = alpha
    nt.links.new(tr.outputs[0], mix.inputs[1]); nt.links.new(em.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], out.inputs["Surface"])
    return m

SHELL = shell_material()
WIRE = glow_material("XrayWire", XRAY["wire"], XRAY["wire_alpha"])
# Untagged models: engine and axles clearly lit, the heart of the x-ray.
CORE = glow_material("XrayCore", XRAY["core"] * 1.3, 0.5)
# Tagged models, as if x-rayed: the body is the most transparent, the cabin and the engine denser
# (CABIN), the transmission and running gear nearly solid (DRIVE). The cabin is many stacked
# surfaces: keep its opacity low and its glow high, or it hides the gear and the far wheels.
CABIN = shell_material("XrayCabin", **XRAY["cabin"])
SEATS = shell_material("XraySeats", **XRAY["seats"])
DASH = shell_material("XrayDash", **XRAY["dash"])
DRIVE = shell_material("XrayDrive", **XRAY["drive"])
# Structural panels (inner bonnet, floor, cabin sides, underbody): faint glow so the far wheels and
# the cabin show through, as on OEM x-ray renders.
STRUCTURE = glow_material("XrayStructure", XRAY["core"] * 0.6, 0.16)
STRUCTURE_MATS = {"Material_2", "Panel Sides", "Mechanical", "Floormat"}
# Windows: dark tinted and nearly clear (a real glass shader reflects the studio and turns milky).
def tinted_glass():
    """Windows read as darker panes than the glowing body, so the glasshouse shows from above."""
    m = bpy.data.materials.new("XrayGlass"); m.use_nodes = True
    nt = m.node_tree; nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    lw = nt.nodes.new("ShaderNodeLayerWeight"); lw.inputs["Blend"].default_value = 0.3
    fac = nt.nodes.new("ShaderNodeMath"); fac.operation = 'MULTIPLY_ADD'
    nt.links.new(lw.outputs["Facing"], fac.inputs[0]); fac.inputs[1].default_value = 0.35
    fac.inputs[2].default_value = XRAY["glass_alpha"]
    em = nt.nodes.new("ShaderNodeEmission"); em.inputs["Color"].default_value = (*XRAY["glass"], 1)
    em.inputs["Strength"].default_value = 1.0
    # Weak, rough reflection only: a sharp one mirrors the overhead softbox as a grey slab.
    gl = nt.nodes.new("ShaderNodeBsdfGlossy"); gl.inputs["Roughness"].default_value = 0.35
    gl.inputs["Color"].default_value = tuple(c * 0.25 for c in XRAY["gloss"]) + (1,)
    add = nt.nodes.new("ShaderNodeAddShader")
    nt.links.new(em.outputs[0], add.inputs[0]); nt.links.new(gl.outputs[0], add.inputs[1])
    tr = nt.nodes.new("ShaderNodeBsdfTransparent")
    mix = nt.nodes.new("ShaderNodeMixShader")
    nt.links.new(fac.outputs[0], mix.inputs["Fac"])
    nt.links.new(tr.outputs[0], mix.inputs[1]); nt.links.new(add.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], out.inputs["Surface"])
    return m

GLASS = tinted_glass()
GLASS_WIRE = glow_material("XrayGlassWire", XRAY["wire"] * 0.7, XRAY["wire_alpha"] * 0.5)

def add_wire(o, material=None):
    data = o.data.copy(); data.transform(o.matrix_world)
    w = bpy.data.objects.new(o.name + "_wire", data); scene.collection.objects.link(w)
    bm = bmesh.new(); bm.from_mesh(data)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
    bmesh.ops.dissolve_degenerate(bm, edges=bm.edges, dist=1e-5)
    bmesh.ops.join_triangles(bm, faces=bm.faces, angle_face_threshold=math.radians(40), angle_shape_threshold=math.radians(40))
    bm.to_mesh(data); bm.free()
    data.materials.clear(); data.materials.append(material or WIRE)
    mod = w.modifiers.new("Wire", 'WIREFRAME'); mod.thickness = 0.0040; mod.use_replace = True; mod.use_even_offset = False

shells = 0
hidden = 0
for o in list(meshes):
    tagged = o.get("xray")      # role set by the model's prepare script, if any
    if tagged:
        if tagged == "shell":
            for slot in o.material_slots:
                slot.material = SHELL
            add_wire(o); shells += 1
        elif tagged == "glass":
            for slot in o.material_slots:
                slot.material = GLASS
        elif tagged in ("cabin", "seats", "dash", "drive", "core"):
            mat = {"cabin": CABIN, "seats": SEATS, "dash": DASH}.get(tagged, DRIVE)
            for slot in o.material_slots:
                slot.material = mat
        elif tagged == "hide":
            o.hide_render = True; hidden += 1
        continue
    slot_names = {s.material.name for s in o.material_slots if s.material}
    if not is_core(o) and slot_names and slot_names <= STRUCTURE_MATS and not is_shell(o):
        # Purely structural inner panels: stacked translucent layers turn milky, so leave them out
        # of the render (not deleted: they parent other parts).
        o.hide_render = True; hidden += 1
        continue
    if is_shell(o):
        for slot in o.material_slots:
            slot.material = SHELL
        add_wire(o); shells += 1
    elif is_core(o):
        for slot in o.material_slots:
            slot.material = CORE
    else:
        for slot in o.material_slots:   # red "Carmine" trim / interior -> graphite
            m = slot.material
            if m and m.name == "Glass":
                slot.material = GLASS
                # no wire on glass: panels are triangulated and the seal already outlines them
            elif m and m.name in STRUCTURE_MATS:
                slot.material = STRUCTURE
            elif m and "Carmine" in m.name:
                slot.material = bpy.data.materials.get(m.name.replace("Carmine", INTERIOR_VARIANT)) or m
print("XRAY shells", shells, "hidden", hidden)

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
# Many stacked translucent layers (shell, wire, cabin, gear): the default 8 transparent bounces cut
# the far wheels and the running gear off.
scene.cycles.transparent_max_bounces = 64
scene.cycles.max_bounces = 16
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

if MODE == "env":
    # The setting OEM configurators put the car in: a road in perspective under it (asphalt band,
    # solid edge line on the right, dashed centre line on the left, the far edge), a fine floor grid
    # and a soft pool of light under the car, all fading out into the page background. Same camera,
    # wider frame (the drawn area grows, the projection stays): the app lays it under the car.
    import numpy as np
    ENV = {
        "dark": dict(line=(0.45, 0.70, 1.0), grid=(0.30, 0.55, 1.0), pool=(0.15, 0.45, 1.0), road=(0.014, 0.017, 0.022),
                     a_line=0.50, a_grid=0.07, a_pool=0.22, a_road=0.85),
        "light": dict(line=(0.10, 0.30, 0.75), grid=(0.10, 0.30, 0.75), pool=(0.20, 0.45, 0.95), road=(0.22, 0.24, 0.28),
                      a_line=0.40, a_grid=0.06, a_pool=0.20, a_road=0.45),
    }[THEME]
    SIZE, N = 36.0, 2400                      # plane side (m), texture side (px)
    lat_axis = 1 - long_axis
    c = (np.arange(N) + 0.5) / N * SIZE - SIZE / 2
    lat, lon = np.meshgrid(c, c)              # rows: along the car, columns: across it
    lat = lat * (left[lat_axis] or 1.0)       # + = the car's left
    lon = lon * FRONT                          # + = ahead
    def band(d, half):                         # 1 inside a line of half-width `half`, soft 1 cm edge
        return np.clip((half - np.abs(d)) / 0.01 + 0.5, 0, 1)
    rad = np.hypot(lat, lon * 0.55)
    fade = np.clip(1 - (rad - 2.0) / 6.0, 0, 1) ** 1.8
    along = np.clip(1 - (np.abs(lon) - 3.0) / 9.0, 0, 1)
    LANE = 3.6
    edge_r, centre_l, edge_l = -LANE / 2, LANE / 2, LANE * 1.5
    dashes = (np.mod(lon, 6.0) < 3.0).astype(float)
    lines = np.maximum.reduce([band(lat - edge_r, 0.06), band(lat - centre_l, 0.06) * dashes, band(lat - edge_l, 0.06)])
    road = ((lat > edge_r - 0.4) & (lat < edge_l + 0.4)).astype(float)
    grid = np.maximum(band(np.mod(lat + 0.5, 1.0) - 0.5, 0.012), band(np.mod(lon + 0.5, 1.0) - 0.5, 0.012))
    pool = np.exp(-(np.hypot(lat, lon * 0.6) / 2.6) ** 2)
    layers = [  # back to front: (colour, alpha)
        (ENV["road"], road * ENV["a_road"] * fade * along),
        (ENV["grid"], grid * ENV["a_grid"] * np.clip(1 - (rad - 1.5) / 4.0, 0, 1) ** 1.5),
        (ENV["pool"], pool * ENV["a_pool"]),
        (ENV["line"], lines * ENV["a_line"] * fade * along),
    ]
    rgb = np.zeros((N, N, 3)); a = np.zeros((N, N))
    for col, al in layers:                     # "over" compositing, premultiplied
        rgb = np.array(col) * al[..., None] + rgb * (1 - al[..., None]); a = al + a * (1 - al)
    rgba = np.dstack([rgb / np.maximum(a, 1e-6)[..., None], a]).astype(np.float32)
    img = bpy.data.images.new("Road", N, N, alpha=True, float_buffer=True)
    img.pixels.foreach_set(rgba.ravel())

    bpy.ops.mesh.primitive_plane_add(size=SIZE, location=(centre.x, centre.y, ground_z + 0.002))
    floor = bpy.context.active_object
    floor.rotation_euler = (0, 0, 0)
    m = bpy.data.materials.new("Road"); m.use_nodes = True
    nt = m.node_tree; nt.nodes.clear()
    tex = nt.nodes.new("ShaderNodeTexImage"); tex.image = img; tex.interpolation = 'Cubic'
    # Plane UV: u along world X, v along world Y; the texture's columns run across the car, rows along.
    uv = nt.nodes.new("ShaderNodeTexCoord")
    if long_axis == 0:
        sep = nt.nodes.new("ShaderNodeSeparateXYZ"); comb = nt.nodes.new("ShaderNodeCombineXYZ")
        nt.links.new(uv.outputs["UV"], sep.inputs[0])
        nt.links.new(sep.outputs["Y"], comb.inputs["X"]); nt.links.new(sep.outputs["X"], comb.inputs["Y"])
        nt.links.new(comb.outputs[0], tex.inputs["Vector"])
    else:
        nt.links.new(uv.outputs["UV"], tex.inputs["Vector"])
    em = nt.nodes.new("ShaderNodeEmission"); nt.links.new(tex.outputs["Color"], em.inputs["Color"])
    tr = nt.nodes.new("ShaderNodeBsdfTransparent")
    mix = nt.nodes.new("ShaderNodeMixShader")
    nt.links.new(tex.outputs["Alpha"], mix.inputs["Fac"])
    nt.links.new(tr.outputs[0], mix.inputs[1]); nt.links.new(em.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], nt.nodes.new("ShaderNodeOutputMaterial").inputs["Surface"])
    floor.data.materials.append(m)
    for o in scene.objects:
        if o.type == 'MESH' and o != floor:
            o.hide_render = True

    # Wider frame, same pixel scale and centre: the car frame sits at (ox, oy) inside it.
    WIDE_X, WIDE_Y = 3.0, 1.6
    cam_data.sensor_fit = 'HORIZONTAL'
    cam_data.sensor_width = cam_data.sensor_width * WIDE_X
    r.resolution_x, r.resolution_y = int(RES * WIDE_X), int(RES * WIDE_Y)
    scene.view_settings.view_transform = 'Standard'
    scene.view_settings.look = 'None'
    scene.world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.0
    scene.cycles.samples = 32
    scene.cycles.use_denoising = False
    r.filepath = f"{OUT}/env_{THEME}.png"
    bpy.ops.render.render(write_still=True)
    with open(f"{OUT}/env_meta.json", "w") as f:
        json.dump({"frame": RES, "size": [r.resolution_x, r.resolution_y],
                   "origin": [(r.resolution_x - RES) // 2, (r.resolution_y - RES) // 2]}, f)
    print("DONE", r.filepath)
    raise SystemExit(0)

if MODE == "masks":
    def wheel_parts():
        """Tyre and rim meshes, each assigned to the nearest wheel-centre empty."""
        empties = {o.name: o.matrix_world.translation for o in scene.objects
                   if o.type == 'EMPTY' and o.name.lower().startswith("wheel")}
        parts = {name: [] for name in empties}
        for o in meshes:
            n = o.name.lower()
            words = set(re.split(r"[^a-z]+", n))   # whole words: "trim" is not a rim
            if not words & {"tire", "tyre", "wheel", "rim", "rims"} or words & {"steering", "column", "fender"}:
                continue
            lo_, hi_ = world_bbox([o])
            c = (lo_ + hi_) / 2
            nearest = min(empties, key=lambda e: (empties[e] - c).length)
            parts[nearest].append(o)
        return parts
    # Grey-scale shading, not a flat fill: rims and tread stay readable once the app multiplies the
    # mask by the state colour (brightness 0.35 facing the camera .. 1.0 at grazing edges).
    white = bpy.data.materials.new("MaskWhite"); white.use_nodes = True
    nt = white.node_tree; nt.nodes.clear()
    lw = nt.nodes.new("ShaderNodeLayerWeight"); lw.inputs["Blend"].default_value = 0.5
    mr = nt.nodes.new("ShaderNodeMapRange")
    mr.inputs["To Min"].default_value = 0.35; mr.inputs["To Max"].default_value = 1.0
    nt.links.new(lw.outputs["Facing"], mr.inputs["Value"])
    em = nt.nodes.new("ShaderNodeEmission")
    nt.links.new(mr.outputs["Result"], em.inputs["Strength"])
    nt.links.new(em.outputs[0], nt.nodes.new("ShaderNodeOutputMaterial").inputs["Surface"])
    # Holdout: an object that hides what is behind it but leaves no pixels of its own.
    holdout = bpy.data.materials.new("MaskHoldout"); holdout.use_nodes = True
    nt = holdout.node_tree; nt.nodes.clear()
    nt.links.new(nt.nodes.new("ShaderNodeHoldout").outputs[0], nt.nodes.new("ShaderNodeOutputMaterial").inputs["Surface"])

    def paint(objs, mat):
        for o in objs:
            if o.material_slots:
                for slot in o.material_slots:
                    slot.material = mat
            else:
                o.data.materials.append(mat)

    scene.view_settings.view_transform = 'Standard'
    scene.cycles.samples = 16
    scene.world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.0
    short = {"WheelFrontL": "FL", "WheelFrontR": "FR", "WheelRearL": "RL", "WheelRearR": "RR"}
    parts = wheel_parts()
    wheels_all = [o for objs in parts.values() for o in objs]

    # Running gear that ties the wheels together: axle shafts into the hubs, a prop shaft and the
    # model's own suspension / brake parts.
    hubs = {short.get(o.name, o.name): o.matrix_world.translation.copy()
            for o in scene.objects if o.type == 'EMPTY' and o.name.lower().startswith("wheel")}
    gear = []
    def shaft(a, b, radius, name):
        d = b - a
        bpy.ops.mesh.primitive_cylinder_add(radius=radius, depth=d.length, location=(a + b) / 2, vertices=24)
        o = bpy.context.active_object
        o.name = name
        o.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
        gear.append(o)
    if all(k in hubs for k in ("FL", "FR", "RL", "RR")):
        shaft(hubs["FL"], hubs["FR"], 0.035, "AxleFrontShaft")
        shaft(hubs["RL"], hubs["RR"], 0.035, "AxleRearShaft")
        shaft((hubs["FL"] + hubs["FR"]) / 2, (hubs["RL"] + hubs["RR"]) / 2, 0.03, "PropShaft")
    for o in meshes:
        words = set(re.split(r"[^a-z]+", o.name.lower()))
        if words & {"suspension", "shock", "absorber", "spring", "brake", "caliper", "disc"}:
            gear.append(o)

    # Each layer keeps the true depth order: the others are holdouts, so a wheel is cut where an
    # axle passes in front of it (far wheels: the shaft reaches the hub over the inner face) and
    # the gear is cut where a wheel is in front (near wheels hide the shaft's end). The layers then
    # never overlap and the app's drawing order does not matter.
    def render_layer(visible, cutters, path):
        for o in scene.objects:
            if o.type == 'MESH':
                o.hide_render = o not in visible and o not in cutters
        paint(visible, white)
        paint(cutters, holdout)
        r.filepath = path
        bpy.ops.render.render(write_still=True)

    for name, objs in parts.items():
        render_layer(objs, gear, f"{OUT}/mask_{short.get(name, name)}.png")
        print("MASK", name, [o.name for o in objs])
    render_layer(gear, wheels_all, f"{OUT}/mask_AXLES.png")
    print("AXLES", [o.name for o in gear])
    raise SystemExit(0)

r.filepath = f"{OUT}/xray_{THEME}{TAG}.png"
bpy.ops.render.render(write_still=True)

anchors = {}
for o in wheels:
    p = world_to_camera_view(scene, cam, o.matrix_world.translation)
    anchors[o.name] = (round(p.x, 4), round(1 - p.y, 4), round(p.z, 3))
with open(f"{OUT}/xray_anchors_{THEME}{TAG}.json", "w") as f:
    json.dump({"front_sign": FRONT, "long_axis": "XY"[long_axis], "wheels": anchors}, f, indent=1)
print("DONE", r.filepath)
