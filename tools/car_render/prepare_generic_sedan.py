"""
Prepare "Generic Sedan Car" by MMC Works (Sketchfab, CC BY 4.0) for xray_render.py.

blender -b --factory-startup --disable-autoexec MODEL.blend -P prepare_generic_sedan.py -- OUT.glb

Bakes every visible part into world space with its modifiers (geometry nodes, lattices, rig),
drops the rig, tags each part with its x-ray role (custom property "xray": shell / glass / cabin /
seats / dash / drive / hide / keep), adds the wheel-centre empties WheelFrontL ... WheelRearR and exports a GLB.
"""
import bpy, sys

OUT = sys.argv[sys.argv.index("--") + 1]

PAINT = "Car Paint - All Colors"
GLASS = {"Glass ext-tinted", "Glass ext"}
# Three densities, as if the car were x-rayed: the body is the most transparent, the cabin and the
# engine less so, the transmission and running gear (and the realistic wheels) nearly solid.
CABIN = ("engine-parts", "radiator", "center-console")
SEATS = ("front-seat", "rear-seat")                            # a touch denser than the cabin
DASH = ("dashboard", "steering-wheel", "column-wheel")         # lighter: a big surface right under the glass
DRIVE = ("engine-transmission", "suspension-front", "suspension-rear", "shock-absorber", "spring",
         "brake-", "escape-pipes")
HIDE = ("frame-", "engine-protector", "-interior-panel-", "roof-pillars-interior", "wheel-fender",
        "headlights-cover-r.001", "WGT-")
WHEELS = {"Name-Wheel.Ft.L": "WheelFrontL", "Name-Wheel.Ft.R": "WheelFrontR",
          "Name-Wheel.Bk.L": "WheelRearL", "Name-Wheel.Bk.R": "WheelRearR"}


def role(o):
    mats = {s.material.name for s in o.material_slots if s.material}
    if any(k in o.name for k in HIDE):
        return "hide"
    if PAINT in mats:
        return "shell"
    if mats and mats <= GLASS:
        return "glass"
    if o.name.startswith(SEATS):
        return "seats"
    if o.name.startswith(DASH):
        return "dash"
    if o.name.startswith(CABIN):
        return "cabin"
    if o.name.startswith(DRIVE):
        return "drive"
    return "keep"


scene = bpy.context.scene
# The rig is saved with the steering turned (Rigacar "Steering" bone moved sideways): centre it
# so the front wheels are straight; the rest of the pose stays as authored.
for arm in [o for o in scene.objects if o.type == 'ARMATURE']:
    steering = arm.pose.bones.get("Steering")
    if steering:
        steering.location = (0.0, 0.0, 0.0)
bpy.context.view_layer.update()
dg = bpy.context.evaluated_depsgraph_get()
baked = []
for o in list(scene.objects):
    if o.type != 'MESH' or not o.visible_get():
        continue
    r = role(o)
    if r == "hide":
        continue
    e = o.evaluated_get(dg)
    me = bpy.data.meshes.new_from_object(e, preserve_all_data_layers=True, depsgraph=dg)
    if not me.polygons:
        continue
    n = bpy.data.objects.new(o.name + "_baked", me)
    n.matrix_world = e.matrix_world.copy()
    n["xray"] = r
    baked.append(n)
wheel_pos = {WHEELS[o.name]: o.matrix_world.translation.copy() for o in scene.objects if o.name in WHEELS}

# Fresh scene with only the baked parts and the wheel centres.
out_scene = bpy.data.scenes.new("Export")
for n in baked:
    out_scene.collection.objects.link(n)
    n.name = n.name[:-len("_baked")]
for name, pos in wheel_pos.items():
    e = bpy.data.objects.new(name, None)
    e.location = pos
    out_scene.collection.objects.link(e)
bpy.context.window.scene = out_scene if bpy.context.window else out_scene
for s in [s for s in bpy.data.scenes if s != out_scene]:
    bpy.data.scenes.remove(s)

counts = {}
for n in baked:
    counts[n["xray"]] = counts.get(n["xray"], 0) + 1
print("ROLES", counts, "WHEELS", {k: tuple(round(c, 3) for c in v) for k, v in wheel_pos.items()})
bpy.ops.export_scene.gltf(filepath=OUT, export_format='GLB', export_extras=True, use_selection=False)
print("EXPORTED", OUT)
