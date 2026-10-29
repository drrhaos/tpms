# Car render for the main screen

Renders the main-screen and widget scene into `app/src/main/res/drawable-nodpi/`: the x-ray car
`car_3d_{dark,light}.webp` (3/4 view, nose to the bottom-left), per-wheel tint masks, the running
gear `car_3d_axles.webp` and the road `car_3d_env_*.webp`, and prints the constants for
`ui/main/CarStage.kt` (`CAR_IMAGE_ASPECT`, `ENV_RECT`, wheel hubs). Run the commands from this folder.
Needs Blender (Cycles) and ImageMagick. Model and HDRI are not in git: keep them in `model/`
(ignored; see `model/LICENSE.md` there for sources and licences).

## Current asset: x-ray look

Model: "Generic Sedan Car" by MMC Works — https://sketchfab.com/3d-models/generic-sedan-car-58c33766470d46e7b2aed542650494e5
(CC BY 4.0, credited in Settings → About). HDRI: `studio_small_09` (Poly Haven, CC0).

```bash
OUT=/tmp/car_out; mkdir -p $OUT
STUDIO=model/studio_small_09.hdr
# 1. Bake the .blend (rig, geometry nodes) into a GLB tagged with x-ray roles. Never run the
#    downloaded file's own scripts: --disable-autoexec. (Or skip and use model/generic_sedan_xray.glb.)
blender -b --factory-startup --disable-autoexec model/source/01-passat-generic-sedan-full-v1.blend \
  -P prepare_generic_sedan.py -- $OUT/sedan.glb
# 2. Render both themes.
for t in dark light; do
  blender -b --factory-startup -P xray_render.py -- $OUT/sedan.glb $STUDIO $OUT $t 34 38 96 1400
done
# 3. Per-wheel tint masks (tyre + rim, same camera; the app multiplies them by the state colour).
blender -b --factory-startup -P xray_render.py -- $OUT/sedan.glb $STUDIO $OUT dark 34 38 32 1400 "" masks
# 4. Surroundings: road, lane lines, floor grid, light pool (same camera, wider frame).
for t in dark light; do
  blender -b --factory-startup -P xray_render.py -- $OUT/sedan.glb $STUDIO $OUT $t 34 38 16 1400 "" env
done
# 5. Trim, clean the shadow haze, encode WebP (car + masks + env), print the Kotlin constants
#    (CAR_IMAGE_ASPECT, ENV_RECT, wheel hubs).
python3 -I post.py $OUT xray direct
cp $OUT/car_3d_*.webp ../../app/src/main/res/drawable-nodpi/
```

`xray_render.py` reads each part's role from its `xray` custom property (`shell` painted panel →
translucent glowing shell with a wire of its own topology, `glass` → dark tinted pane, `cabin` →
engine, console: denser ghost (`seats` a touch denser, `dash` lighter), `drive` → transmission
and running gear: nearly solid, `hide`, `keep` → realistic material, e.g. the wheels): densities
as if the car were x-rayed. Without the property it falls back to material-name heuristics. `post.py ... silhouette` puts the hidden right-hand wheels' anchors on
the car's far outline at their axle; `direct` uses every projected hub.

## Other scripts

- `render_car.py` — plain studio render (painted car) with the same camera and anchors.
- `model_sedan.py` — our own procedural unbranded sedan (no licence needed); fallback model.
