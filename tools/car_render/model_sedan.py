"""
Procedural unbranded sedan for the main-screen render (our own model: no brand, no licence).

blender -b --factory-startup -P model_sedan.py -- OUT.glb [PREVIEW_DIR]

Builds the body by lofting cross-sections along the car (front = -Y, left side = +X, z up),
splits glass from paint, adds wheels, a simple cabin, lights and glowing "internals", names
everything the way render_car.py / xray_render.py expect, and exports a GLB. With PREVIEW_DIR it
also writes quick Workbench previews to check the shape.
"""
import bpy, bmesh, math, sys
from mathutils import Vector, Matrix

argv = sys.argv[sys.argv.index("--") + 1:]
OUT = argv[0]
PREVIEW = argv[1] if len(argv) > 1 else None
bpy.ops.wm.read_factory_settings(use_empty=True)   # before any datablock is created

# ---------------------------------------------------------------- dimensions (metres)
# Classic B-segment three-box sedan proportions (no particular car): short bonnet, upright
# glasshouse, high flat roof, separate boot, R15 wheels.
L = 4.40
Y_FRONT, Y_REAR = -L / 2, L / 2
AXLE_F, AXLE_R = -1.33, 1.27            # 2.60 m wheelbase
WHEEL_R, WHEEL_W, TRACK = 0.31, 0.195, 0.745
RIM_R = 0.195                            # 15"
ARCH_R = 0.36
WS_BASE, ROOF_F, ROOF_R, RG_BASE = -0.97, -0.22, 0.92, 1.45   # windshield base .. rear glass base


def pchip(y, pts):
    """Monotone cubic interpolation (Fritsch-Carlson) through (y, value) keys: smooth, no
    overshoot, and no flat steps at the keys (smoothstep put a zero slope at every key)."""
    xs = [p[0] for p in pts]; vs = [p[1] for p in pts]
    if y <= xs[0]:
        return vs[0]
    if y >= xs[-1]:
        return vs[-1]
    n = len(xs)
    d = [(vs[k + 1] - vs[k]) / (xs[k + 1] - xs[k]) for k in range(n - 1)]
    m = [d[0]] + [0.0 if d[k - 1] * d[k] <= 0 else
                  3 * (xs[k + 1] - xs[k - 1]) / ((2 * xs[k + 1] - xs[k] - xs[k - 1]) / d[k - 1] + (xs[k + 1] + xs[k] - 2 * xs[k - 1]) / d[k])
                  for k in range(1, n - 1)] + [d[-1]]
    k = max(i for i in range(n - 1) if xs[i] <= y)
    h = xs[k + 1] - xs[k]; t = (y - xs[k]) / h
    h00 = 2 * t ** 3 - 3 * t ** 2 + 1; h10 = t ** 3 - 2 * t ** 2 + t
    h01 = -2 * t ** 3 + 3 * t ** 2; h11 = t ** 3 - t ** 2
    return h00 * vs[k] + h10 * h * m[k] + h01 * vs[k + 1] + h11 * h * m[k + 1]


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def half_width(y):
    return pchip(y, [(-2.20, 0.60), (-2.15, 0.75), (-2.05, 0.815), (-1.8, 0.848), (-1.3, 0.862), (0.0, 0.866),
                     (1.3, 0.865), (1.9, 0.852), (2.10, 0.815), (2.17, 0.745), (2.20, 0.64)])


def z_belt(y):   # bonnet / beltline / boot lid
    return pchip(y, [(-2.20, 0.66), (-2.12, 0.745), (-1.8, 0.83), (-1.3, 0.895), (WS_BASE, 0.94),
                     (0.0, 0.972), (1.0, 0.995), (RG_BASE, 1.03), (2.02, 1.045), (2.20, 0.985)])


def z_roof(y):
    if y <= WS_BASE or y >= RG_BASE:
        return z_belt(y)
    return pchip(y, [(WS_BASE, z_belt(WS_BASE)), (ROOF_F - 0.25, 1.32), (ROOF_F, 1.452), (0.2, 1.48),
                     (0.7, 1.476), (ROOF_R, 1.448), (ROOF_R + 0.25, 1.30), (RG_BASE, z_belt(RG_BASE))])


def z_side_bottom(y):   # sill line, lifted over the wheels into arches
    z = 0.30 + 0.07 * smooth((abs(y) - 1.95) / 0.25)
    for ya in (AXLE_F, AXLE_R):
        dy = y - ya
        if abs(dy) < ARCH_R:
            z = max(z, WHEEL_R + math.sqrt(ARCH_R ** 2 - dy * dy) * 0.98)
    return z


def greenhouse(y):
    """Half widths of the glasshouse at the belt and at the roof rail."""
    w = half_width(y)
    return w * 0.885, min(0.635, w * 0.735)


def section(y):
    """Half cross-section control points (x >= 0), underbody centre to roof centre."""
    w = half_width(y)
    zb, zt = z_side_bottom(y), z_belt(y)
    zr = z_roof(y)
    g = zr - zt
    s = smooth(g / 0.05)                  # 0 on bonnet / boot lid, 1 under the glasshouse
    pts = [
        (0.0, 0.27),
        (w * 0.94, zb),
        (w * 0.997, zb + 0.05),
        (w * 1.0, zb + (zt - zb) * 0.42),
        (w * 0.998, zb + (zt - zb) * 0.80),  # character line
        (w * 0.972, zt - 0.016),             # shoulder
        (w * 0.905, zt),
    ]
    gw_b, gw_r = greenhouse(y)
    glass = [(gw_b, zt + 0.012), ((gw_b + gw_r) / 2 + 0.014, zt + g * 0.52), (gw_r, zr - 0.035),
             (gw_r * 0.55, zr - 0.004), (0.0, zr + 0.006)]
    lid = [(w * 0.72, zt + 0.018), (w * 0.56, zt + 0.028), (w * 0.40, zt + 0.034), (w * 0.20, zt + 0.038),
           (0.0, zt + 0.04)]
    pts += [(lx + (gx - lx) * s, lz + (gz - lz) * s) for (lx, lz), (gx, gz) in zip(lid, glass)]
    return pts


def dense_section(y, per_segment=3):
    """Catmull-Rom through the control points; mirrored neighbours keep the centreline smooth."""
    p = [Vector(c) for c in section(y)]
    ext = [Vector((-p[1].x, p[1].y))] + p + [Vector((-p[-2].x, p[-2].y))]
    out = []
    for i in range(1, len(ext) - 2):
        p0, p1, p2, p3 = ext[i - 1], ext[i], ext[i + 1], ext[i + 2]
        for k in range(per_segment):
            t = k / per_segment
            out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t
                              + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t))
    out.append(p[-1])
    out[0].x = 0.0; out[-1].x = 0.0
    return out


def ring(y):
    half = dense_section(y)
    full = [(v.x, v.y) for v in half] + [(-v.x, v.y) for v in reversed(half[1:-1])]
    return [Vector((x, y, z)) for x, z in full]


def build_body():
    """One continuous painted shell; the windows are separate panels laid onto it later."""
    n_rings = 100
    ys = [Y_FRONT + (Y_REAR - Y_FRONT) * (0.5 - 0.5 * math.cos(math.pi * i / n_rings)) for i in range(n_rings + 1)]
    rings = [ring(y) for y in ys]
    # Close the ends: two shrinking rings and a centre point, so the bumpers round off.
    def shrunk(r, k, dy):
        cz = sum(p.z for p in r) / len(r)
        return [Vector((p.x * k, p.y + dy, cz + (p.z - cz) * (0.55 + 0.45 * k))) for p in r]
    front = [shrunk(rings[0], 0.86, -0.035), shrunk(rings[0], 0.45, -0.06)]
    rear = [shrunk(rings[-1], 0.86, 0.035), shrunk(rings[-1], 0.45, 0.06)]
    all_rings = front[::-1] + rings + rear
    n = len(all_rings[0])
    bm = bmesh.new()
    verts = [[bm.verts.new(p) for p in r] for r in all_rings]
    for i in range(len(all_rings) - 1):
        for j in range(n):
            j2 = (j + 1) % n
            bm.faces.new((verts[i][j], verts[i][j2], verts[i + 1][j2], verts[i + 1][j]))
    for r, sign in ((verts[0], -1), (verts[-1], 1)):
        c = bm.verts.new(sum((v.co for v in r), Vector()) / len(r) + Vector((0, 0.012 * sign, 0)))
        for j in range(n):
            bm.faces.new((r[j], r[(j + 1) % n], c) if sign > 0 else (r[(j + 1) % n], r[j], c))
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new("Body")
    bm.to_mesh(me); bm.free()
    return me


# ---------------------------------------------------------------- materials
def principled(name, color, metallic=0.0, rough=0.4, emission=None, strength=0.0, transmission=0.0, alpha=1.0, coat=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*color, 1)
    b.inputs["Metallic"].default_value = metallic
    b.inputs["Roughness"].default_value = rough
    if transmission:
        b.inputs["Transmission Weight"].default_value = transmission
    if coat:
        b.inputs["Coat Weight"].default_value = coat
    if emission:
        b.inputs["Emission Color"].default_value = (*emission, 1)
        b.inputs["Emission Strength"].default_value = strength
    b.inputs["Alpha"].default_value = alpha
    m.diffuse_color = (*(emission or color), 1)   # Workbench preview colour
    return m


MAT = {
    "paint": principled("Paint Body", (0.06, 0.065, 0.07), metallic=0.9, rough=0.3, coat=1.0),
    "glass": principled("Glass", (0.02, 0.025, 0.03), rough=0.03, transmission=1.0),
    "tire": principled("Tiretread", (0.018, 0.018, 0.02), rough=0.75),
    "rim": principled("Rim1", (0.72, 0.73, 0.75), metallic=1.0, rough=0.22),
    "rim_dark": principled("Rim2", (0.08, 0.085, 0.09), metallic=0.8, rough=0.35),
    "disc": principled("Disc", (0.35, 0.35, 0.36), metallic=1.0, rough=0.45),
    "caliper": principled("Brake", (0.12, 0.12, 0.13), metallic=0.3, rough=0.4),
    "interior": principled("Interior 1", (0.035, 0.036, 0.04), rough=0.8),
    "trim": principled("Hardware", (0.02, 0.02, 0.022), rough=0.5),
    "head": principled("Headlight", (0.9, 0.95, 1.0), emission=(0.85, 0.92, 1.0), strength=6.0),
    "tail": principled("Brakelight", (0.6, 0.02, 0.03), emission=(1.0, 0.05, 0.06), strength=4.0),
    "mirror": principled("Paint Mirror", (0.06, 0.065, 0.07), metallic=0.9, rough=0.3, coat=1.0),
    "core": principled("Mechanical", (0.1, 0.4, 0.9), emission=(0.1, 0.5, 1.0), strength=2.0),
    "panel": principled("PanelLine", (0.2, 0.55, 1.0), emission=(0.25, 0.62, 1.0), strength=1.6),
}


def obj(name, me, mat=None, parent=None):
    o = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(o)
    if mat is not None:
        me.materials.clear(); me.materials.append(mat)
    if parent:
        o.parent = parent
    return o


def subdivide(o, levels):
    mod = o.modifiers.new("Subd", 'SUBSURF'); mod.levels = levels; mod.render_levels = levels
    for p in o.data.polygons:
        p.use_smooth = True


def rounded_box(name, size, loc, mat, levels=2, bevel=None):
    """Box with soft edges: a bevel keeps it boxy (subdivision alone turns it into a pebble)."""
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    bmesh.ops.scale(bm, vec=Vector(size), verts=bm.verts)
    bmesh.ops.translate(bm, vec=Vector(loc), verts=bm.verts)
    me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free()
    o = obj(name, me, mat)
    mod = o.modifiers.new("Bevel", 'BEVEL')
    mod.width = bevel if bevel is not None else min(size) * 0.3
    mod.segments = 3 * levels
    for p in o.data.polygons:
        p.use_smooth = True
    return o


# ---------------------------------------------------------------- wheels
def revolve(profile, segments, axis_x, centre):
    """Revolve (radius, x_offset) profile around the X axis at centre."""
    bm = bmesh.new()
    rings = []
    for s in range(segments):
        a = 2 * math.pi * s / segments
        rings.append([bm.verts.new((centre.x + dx, centre.y + r * math.cos(a), centre.z + r * math.sin(a))) for r, dx in profile])
    for s in range(segments):
        r0, r1 = rings[s], rings[(s + 1) % segments]
        for k in range(len(profile) - 1):
            bm.faces.new((r0[k], r0[k + 1], r1[k + 1], r1[k]))
    return bm


def wheel(name, cx, cy, side):
    centre = Vector((cx, cy, WHEEL_R))
    empty = bpy.data.objects.new(name, None)
    bpy.context.scene.collection.objects.link(empty)
    empty.location = centre
    hw = WHEEL_W / 2
    o = side  # +1 outer face towards +X
    # Tyre: rounded section, sidewalls down to the rim.
    prof = [(RIM_R + 0.008, -hw * 0.92), (WHEEL_R - 0.045, -hw), (WHEEL_R - 0.012, -hw * 0.96), (WHEEL_R, -hw * 0.75),
            (WHEEL_R, hw * 0.75), (WHEEL_R - 0.012, hw * 0.96), (WHEEL_R - 0.045, hw), (RIM_R + 0.008, hw * 0.92)]
    prof = [(r, dx * o) for r, dx in prof]
    bm = revolve(prof, 64, True, centre)
    me = bpy.data.meshes.new(name + "Tire"); bm.to_mesh(me); bm.free()
    t = obj(name + "Tire", me, MAT["tire"]); t.parent = None
    for p in t.data.polygons:
        p.use_smooth = True
    # Rim: barrel + outer lip.
    face = cx + o * (hw * 0.80)
    prof = [(RIM_R - 0.012, -hw * 0.85 * o), (RIM_R, -hw * 0.9 * o), (RIM_R, hw * 0.78 * o),
            (RIM_R + 0.008, hw * 0.86 * o), (RIM_R - 0.015, hw * 0.86 * o)]
    bm = revolve(prof, 64, True, centre)
    me = bpy.data.meshes.new(name + "Rim"); bm.to_mesh(me); bm.free()
    rim = obj(name + "Rim", me, MAT["rim"])
    for p in rim.data.polygons:
        p.use_smooth = True
    # Spokes: five double spokes from the hub to the lip, slightly concave.
    bm = bmesh.new()
    for k in range(10):
        a = 2 * math.pi * (k // 2) / 5 + (0.12 if k % 2 else -0.12)
        r0, r1 = 0.06, RIM_R - 0.012
        w0, w1 = 0.022, 0.016
        da = math.atan2(w0, r0), math.atan2(w1, r1)
        quad = []
        for r, half_a, depth in ((r0, da[0], -0.035), (r1, da[1], 0.0)):
            for sgn in (-1, 1):
                ang = a + sgn * half_a
                quad.append((face + o * depth, cy + r * math.cos(ang), WHEEL_R + r * math.sin(ang)))
        v = [bm.verts.new(p) for p in (quad[0], quad[1], quad[3], quad[2])]
        f = bm.faces.new(v)
        ext = bmesh.ops.extrude_face_region(bm, geom=[f])
        moved = [e for e in ext["geom"] if isinstance(e, bmesh.types.BMVert)]
        bmesh.ops.translate(bm, vec=Vector((-o * 0.03, 0, 0)), verts=moved)
    # Hub cap.
    hub = bmesh.ops.create_cone(bm, cap_ends=True, segments=24, radius1=0.065, radius2=0.05, depth=0.04)
    bmesh.ops.rotate(bm, cent=Vector(), matrix=Matrix.Rotation(math.radians(90), 3, 'Y'), verts=hub["verts"])
    bmesh.ops.translate(bm, vec=Vector((face - o * 0.04, cy, WHEEL_R)), verts=hub["verts"])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name + "Spokes"); bm.to_mesh(me); bm.free()
    obj(name + "Spokes", me, MAT["rim"])
    # Brake disc and caliper behind the spokes.
    bm = bmesh.new()
    d = bmesh.ops.create_cone(bm, cap_ends=True, segments=48, radius1=RIM_R - 0.05, radius2=RIM_R - 0.05, depth=0.026)
    bmesh.ops.rotate(bm, cent=Vector(), matrix=Matrix.Rotation(math.radians(90), 3, 'Y'), verts=d["verts"])
    bmesh.ops.translate(bm, vec=Vector((cx - o * 0.02, cy, WHEEL_R)), verts=d["verts"])
    me = bpy.data.meshes.new(name + "BrakeDisc"); bm.to_mesh(me); bm.free()
    obj(name + "BrakeDisc", me, MAT["disc"])
    rounded_box(name + "BrakePad", (0.06, 0.10, 0.07), (cx + o * 0.005, cy + 0.08, WHEEL_R + RIM_R * 0.55), MAT["caliper"], levels=1)
    return empty


# ---------------------------------------------------------------- assemble
scene = bpy.context.scene

shell = obj("BodyShell", build_body(), MAT["paint"])
subdivide(shell, 1)

for name, y, side in (("WheelFrontL", AXLE_F, 1), ("WheelFrontR", AXLE_F, -1), ("WheelRearL", AXLE_R, 1), ("WheelRearR", AXLE_R, -1)):
    wheel(name, side * TRACK, y, side)

# Lights: slim LED strips under the bonnet edge, one light bar across the boot.
def strip(name, pts, thickness, height, mat):
    bm = bmesh.new()
    vs = []
    for x, y, z in pts:
        vs.append((bm.verts.new((x, y, z)), bm.verts.new((x, y, z + height)),
                   bm.verts.new((x, y + thickness, z)), bm.verts.new((x, y + thickness, z + height))))
    for a, b in zip(vs, vs[1:]):
        for i0, i1 in ((0, 1), (1, 3), (3, 2), (2, 0)):
            bm.faces.new((a[i0], b[i0], b[i1], a[i1]))
    me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free()
    return obj(name, me, mat)

for side in (1, -1):   # headlamps wrapping the front corners, under the bonnet edge
    pts = []
    for k in range(10):
        u = k / 9
        x = side * (0.40 + 0.42 * u)
        y = Y_FRONT - 0.01 + 0.20 * u ** 2.2
        pts.append((x, y, z_belt(y) - 0.115 + 0.012 * u))
    strip("BodyHeadlights" + ("L" if side > 0 else "R"), pts, 0.03, 0.075, MAT["head"])
y_g = Y_FRONT + 0.035
rounded_box("BodyGrille", (0.70, 0.04, 0.09), (0, y_g, z_belt(y_g) - 0.08), MAT["trim"], levels=2)
rounded_box("BodyGrilleLower", (0.92, 0.04, 0.11), (0, Y_FRONT + 0.05, 0.40), MAT["trim"], levels=2)
for side in (1, -1):   # tail lamps on the rear corners, wrapping onto the wings
    pts = []
    for k in range(10):
        u = k / 9
        x = side * (0.46 + 0.37 * u)
        y = Y_REAR - 0.005 - 0.17 * u ** 2.6
        pts.append((x, y, z_belt(y) - 0.16))
    strip("BodyTaillights" + ("L" if side > 0 else "R"), pts, 0.03, 0.09, MAT["tail"])

# ---------------------------------------------------------------- windows and shut lines
# Both are laid onto the finished shell by ray casting, so they follow its real surface.
from mathutils.bvhtree import BVHTree
bpy.context.view_layer.update()
BVH = BVHTree.FromObject(shell, bpy.context.evaluated_depsgraph_get())


def _hit(origin, direction, outward, outset):
    loc, normal, _, _ = BVH.ray_cast(origin, direction)
    if loc is None:
        return None
    if normal.dot(outward) < 0:
        normal = -normal
    return loc + normal * outset


def on_side(y, z, side, outset=0.005):
    """Body side point at (y, z), seen from the car's left (+1) or right (-1)."""
    return _hit(Vector((side * 3.0, y, z)), Vector((-side, 0, 0)), Vector((side, 0, 0)), outset)


def on_top(x, y, outset=0.005):
    """Body point under (x, y), seen from above."""
    return _hit(Vector((x, y, 3.0)), Vector((0, 0, -1)), Vector((0, 0, 1)), outset)


def tube(bm, pts, r=0.0045, sides=6):
    pts = [p for p in pts if p is not None]
    if len(pts) < 2:
        return
    rings = []
    for i, p in enumerate(pts):
        t = (pts[min(i + 1, len(pts) - 1)] - pts[max(i - 1, 0)]).normalized()
        a = t.orthogonal().normalized(); b = t.cross(a)
        rings.append([bm.verts.new(p + (a * math.cos(2 * math.pi * k / sides) + b * math.sin(2 * math.pi * k / sides)) * r)
                      for k in range(sides)])
    for r0, r1 in zip(rings, rings[1:]):
        for k in range(sides):
            bm.faces.new((r0[k], r0[(k + 1) % sides], r1[(k + 1) % sides], r1[k]))


def rounded(corners, radius, arc_steps=6, step=0.025):
    """Closed 2D outline through corners with filleted corners, resampled every `step`."""
    c = [Vector(p) for p in corners]
    pts = []
    for i in range(len(c)):
        p0, p1, p2 = c[i - 1], c[i], c[(i + 1) % len(c)]
        a, b = p0 - p1, p2 - p1
        r = min(radius, a.length * 0.45, b.length * 0.45)
        t0, t1 = p1 + a.normalized() * r, p1 + b.normalized() * r
        pts += [(1 - t) ** 2 * t0 + 2 * (1 - t) * t * p1 + t * t * t1 for t in (k / arc_steps for k in range(arc_steps + 1))]
    out = []
    for i in range(len(pts)):
        q0, q1 = pts[i], pts[(i + 1) % len(pts)]
        n = max(1, int((q1 - q0).length / step))
        out += [q0 + (q1 - q0) * k / n for k in range(n)]
    return out


def panel(name, outline, to3d, mat, trim):
    """Glass panel filling a 2D outline, projected onto the body, with a seal along its edge."""
    bm = bmesh.new()
    face = bm.faces.new([bm.verts.new((p.x, p.y, 0.0)) for p in outline])
    bmesh.ops.triangulate(bm, faces=[face], ngon_method='BEAUTY')
    bmesh.ops.subdivide_edges(bm, edges=bm.edges[:], cuts=2, use_grid_fill=True)
    for v in list(bm.verts):
        q = to3d(v.co.x, v.co.y, 0.007)
        if q is None:
            bm.verts.remove(v)
        else:
            v.co = q
    me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free()
    o = obj(name, me, mat)
    for poly in o.data.polygons:
        poly.use_smooth = True
    tube(trim, [to3d(p.x, p.y, 0.009) for p in outline] + [to3d(outline[0].x, outline[0].y, 0.009)], r=0.006)
    return o


ROOF_INSET, BELT_INSET = 0.065, 0.035
Y_A0, Y_A1 = WS_BASE + 0.10, ROOF_F + 0.08          # A-pillar foot / head
Y_B0, Y_B1 = 0.27, 0.40                              # B-pillar
Y_C1, Y_C0 = ROOF_R - 0.06, 1.22                     # C-pillar head / foot


def side_outline(y_front_top, y_rear_top, y_front_bottom, y_rear_bottom):
    top = [(y, z_roof(y) - ROOF_INSET) for y in (y_front_top + (y_rear_top - y_front_top) * k / 6 for k in range(7))]
    bottom = [(y, z_belt(y) + BELT_INSET) for y in (y_rear_bottom + (y_front_bottom - y_rear_bottom) * k / 6 for k in range(7))]
    return top + bottom


trim = bmesh.new()
for side in (1, -1):
    tag = "L" if side > 0 else "R"
    def side3d(u, v, outset, side=side):
        return on_side(u, v, side, outset)
    panel("BodyWindowFront" + tag, rounded(side_outline(Y_A1, Y_B0, Y_A0, Y_B0), 0.05), side3d, MAT["glass"], trim)
    panel("BodyWindowRear" + tag, rounded(side_outline(Y_B1, Y_C1, Y_B1, Y_C0), 0.05), side3d, MAT["glass"], trim)


def top3d(u, v, outset):
    return on_top(u, v, outset)


y0, y1 = WS_BASE + 0.05, ROOF_F - 0.03
hb, ht = greenhouse(y0)[0] - 0.08, greenhouse(y1)[1] - 0.07
panel("BodyWindshield", rounded([(-hb, y0), (hb, y0), (ht, y1), (-ht, y1)], 0.07), top3d, MAT["glass"], trim)
y0, y1 = ROOF_R + 0.03, RG_BASE - 0.05
ht, hb = greenhouse(y0)[1] - 0.08, greenhouse(y1)[0] - 0.10
panel("BodyRearWindow", rounded([(-ht, y0), (ht, y0), (hb, y1), (-hb, y1)], 0.07), top3d, MAT["glass"], trim)
me = bpy.data.meshes.new("BodyWindowTrim"); trim.to_mesh(me); trim.free()
obj("BodyWindowTrim", me, MAT["panel"])

# Shut lines: doors, sills, wheel-arch lips, handles, bonnet and boot lid.
lines = bmesh.new()
for side in (1, -1):
    def line_side(pts, r=0.0045, side=side):
        tube(lines, [on_side(y, z, side) for y, z in pts], r)
    y_fd = Y_A0 - 0.02
    line_side([(y_fd, z_side_bottom(y_fd) + 0.04 + (z_belt(y_fd) - 0.01 - z_side_bottom(y_fd) - 0.04) * k / 10) for k in range(11)])
    y_b = (Y_B0 + Y_B1) / 2
    line_side([(y_b, z_side_bottom(y_b) + 0.04 + (z_belt(y_b) - 0.01 - z_side_bottom(y_b) - 0.04) * k / 10) for k in range(11)])
    y_r0 = AXLE_R - ARCH_R * 0.55
    z_r0 = WHEEL_R + math.sqrt(ARCH_R ** 2 - (ARCH_R * 0.55) ** 2) + 0.03
    line_side([(Y_C0 + (y_r0 - Y_C0) * k / 10, z_belt(Y_C0) - 0.01 + (z_r0 - z_belt(Y_C0) + 0.01) * k / 10) for k in range(11)])
    y_s1 = AXLE_R - ARCH_R - 0.01
    line_side([(y, z_side_bottom(y) + 0.04) for y in (y_fd + (y_s1 - y_fd) * k / 30 for k in range(31))])
    for ya in (AXLE_F, AXLE_R):
        line_side([(ya + ARCH_R * 1.03 * math.cos(math.pi * k / 30), WHEEL_R + ARCH_R * 1.03 * math.sin(math.pi * k / 30)) for k in range(31)])
    for y in (Y_B0 - 0.10, Y_C0 - 0.20):
        z = z_belt(y) - 0.10
        line_side([(y - 0.06 + 0.12 * k / 4, z) for k in range(5)], r=0.009)
    for ya, yb in ((Y_FRONT + 0.12, WS_BASE - 0.02), (RG_BASE + 0.03, Y_REAR - 0.10)):
        tube(lines, [on_top(side * half_width(y) * 0.86, y) for y in (ya + (yb - ya) * k / 20 for k in range(21))])
for y in (Y_FRONT + 0.12, RG_BASE + 0.03, Y_REAR - 0.10):
    tube(lines, [on_top(half_width(y) * 0.86 * (-1 + 2 * k / 30), y) for k in range(31)])
me = bpy.data.meshes.new("BodyPanelLines"); lines.to_mesh(me); lines.free()
obj("BodyPanelLines", me, MAT["panel"])

# Cabin (left-hand drive: driver on the +X side).
for side in (1, -1):
    rounded_box("InteriorSeatCushion" + str(side), (0.48, 0.50, 0.12), (side * 0.36, -0.12, 0.47), MAT["interior"])
    rounded_box("InteriorSeatBack" + str(side), (0.46, 0.12, 0.64), (side * 0.36, 0.16, 0.83), MAT["interior"])
rounded_box("InteriorRearBench", (1.24, 0.48, 0.14), (0, 0.80, 0.49), MAT["interior"])
rounded_box("InteriorRearBack", (1.24, 0.14, 0.56), (0, 1.08, 0.83), MAT["interior"])
rounded_box("InteriorDashMid", (1.50, 0.34, 0.18), (0, -0.70, 0.86), MAT["interior"])
bpy.ops.mesh.primitive_torus_add(major_radius=0.18, minor_radius=0.018, location=(0.36, -0.42, 0.90),
                                 rotation=(math.radians(62), 0, 0))
bpy.context.active_object.name = "InteriorSteeringWheel"
bpy.context.active_object.data.materials.append(MAT["interior"])

# Glowing internals (as on OEM x-ray renders): engine under the bonnet and the axles.
rounded_box("EngineBlock", (0.64, 0.50, 0.40), (0, -1.62, 0.58), MAT["core"], levels=2)
for name, y in (("AxleFront", AXLE_F), ("AxleRear", AXLE_R)):
    bpy.ops.mesh.primitive_cylinder_add(radius=0.03, depth=1.35, location=(0, y, WHEEL_R), rotation=(0, math.radians(90), 0))
    bpy.context.active_object.name = name
    bpy.context.active_object.data.materials.append(MAT["core"])

bpy.ops.object.select_all(action='SELECT')
bpy.ops.export_scene.gltf(filepath=OUT, export_format='GLB', export_apply=True, use_selection=False)
print("EXPORTED", OUT)

if PREVIEW:
    scene.render.engine = 'BLENDER_WORKBENCH'
    scene.display.shading.light = 'STUDIO'
    scene.display.shading.color_type = 'MATERIAL'
    scene.display.shading.show_cavity = True
    scene.render.resolution_x, scene.render.resolution_y = 900, 600
    cam = bpy.data.objects.new("Cam", bpy.data.cameras.new("Cam")); scene.collection.objects.link(cam); scene.camera = cam
    cam.data.lens = 70
    for tag, loc in (("q", (6.5, -7.5, 5.5)), ("side", (11.0, 0.0, 0.9)), ("front", (0.0, -12.0, 1.5)), ("top", (0.0, 0.001, 14.0))):
        cam.location = loc
        cam.rotation_euler = (Vector((0, 0, 0.6)) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
        scene.render.filepath = f"{PREVIEW}/sedan_{tag}.png"
        bpy.ops.render.render(write_still=True)
    print("PREVIEWS", PREVIEW)
