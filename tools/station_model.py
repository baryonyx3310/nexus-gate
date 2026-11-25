"""Static housing of the Nexus Gate station, modelled in one 16x32x16 space and split into two block halves.

Minecraft-style construction: every element is an axis-aligned cuboid on the pixel grid (whole or half
pixels), silhouettes are built from stepped blocks instead of rotated parts, and depth comes from layered
plates. Thin emissive overlays sit a fraction of a pixel in front of their host face to avoid z-fighting.

The animated drum (stator ring, rotor, chambers, iris, plasma core) is drawn by StationRenderer around the
axis x=8, y=16, z=3..9.5, so no static element occupies that volume.
"""
import copy

NS = "nexusgate"
GLOW = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
ALL = ("north", "south", "east", "west", "up", "down")

TEXTURES = {
    "frame": f"{NS}:block/frame_graphite",
    "armor": f"{NS}:block/armor_ceramic",
    "vent": f"{NS}:block/vent_grille",
    "sink": f"{NS}:block/heatsink",
    "mach": f"{NS}:block/machinery",
    "top": f"{NS}:block/plinth_top",
    "glow": f"{NS}:block/glow_cyan",
    "amber": f"{NS}:block/glow_amber",
    "hazard": f"{NS}:block/hazard_stripe",
    "cable": f"{NS}:block/cable",
    "screen": f"{NS}:block/screen",
    "particle": f"{NS}:block/frame_graphite",
}


def part(frm, to, faces, rotation=None, glow=False, uv=None):
    """faces: dict face -> texture key. Missing faces are not drawn. uv: optional dict face -> [u0, v0, u1, v1]."""
    el = {"from": list(frm), "to": list(to), "faces": {}}
    for face, tex in faces.items():
        el["faces"][face] = {"texture": "#" + tex}
        if uv and face in uv:
            el["faces"][face]["uv"] = list(uv[face])
    if rotation:
        el["rotation"] = rotation
    if glow:
        el["neoforge_data"] = dict(GLOW)
    return el


def all_faces(tex, **override):
    d = {f: tex for f in ALL}
    d.update(override)
    return d


def mirror_x(el):
    """Mirror an element across x = 8 (left/right symmetric parts)."""
    e = copy.deepcopy(el)
    e["from"][0], e["to"][0] = 16 - el["to"][0], 16 - el["from"][0]
    f = e["faces"]
    east, west = f.pop("east", None), f.pop("west", None)
    if west is not None:
        f["east"] = west
    if east is not None:
        f["west"] = east
    if "rotation" in e:
        e["rotation"]["origin"][0] = 16 - e["rotation"]["origin"][0]
        if e["rotation"]["axis"] in ("y", "z"):
            e["rotation"]["angle"] = -e["rotation"]["angle"]
    return e


LEFT = [
    # yoke arm hugging the drum, ceramic armour on the front
    part([0, 9.5, 3], [1.5, 22.5, 10], {"north": "armor", "west": "frame", "east": "frame", "up": "frame", "down": "frame"}),
    # ceramic clamp plate layered on the arm (depth + "encaixe")
    part([0, 19.5, 2.5], [2, 21, 3], all_faces("armor", south="frame")),
    # bearing cap and amber status pin
    part([0, 14, 2], [2, 18, 3], all_faces("frame")),
    part([0.5, 15, 1.9], [1.5, 17, 2], {"north": "amber"}, glow=True),
    # hydraulic strut from the plinth to the arm (body + ceramic rod)
    part([0.5, 3, 5.5], [2, 7.5, 7], all_faces("frame")),
    part([1, 7.5, 6], [1.5, 9.5, 6.5], all_faces("armor")),
    # rear pylon
    part([0.5, 3, 10.5], [2.5, 28, 14.5], {"north": "frame", "south": "frame", "west": "mach", "east": "frame", "up": "frame"}),
    # cable conduit from the rear tower into the arm
    part([1, 19.5, 9], [3, 20.5, 10.5], all_faces("frame")),
    # stepped shoulder joining arm and arch (two blocks instead of a rotated bevel)
    part([0.5, 21.5, 3], [2.5, 23.5, 10],
         {"north": "armor", "south": "frame", "up": "armor", "down": "frame", "west": "frame", "east": "frame"}),
    part([2, 23, 3], [3.5, 25, 10],
         {"north": "armor", "south": "frame", "up": "armor", "down": "frame", "west": "frame", "east": "frame"}),
    # glow seam on the pylon side (1px wide)
    part([0.4, 6, 12], [0.5, 25, 13], {"west": "glow"}, glow=True),
]

CENTER = [
    # --- plinth
    part([0.5, 0, 0.5], [15.5, 1.5, 15.5], all_faces("frame")),
    part([1.5, 1.5, 1.5], [14.5, 3, 14.5], all_faces("armor", up="top", down="frame")),
    # hazard band on the front step: one continuous strip folding over the edge, mapped 1:1 so stripes line up
    part([1.5, 3, 1.5], [14.5, 3.05, 3], {"up": "hazard"}, uv={"up": [1.5, 1.5, 14.5, 3]}),
    part([1.5, 1.5, 1.45], [14.5, 3.05, 1.5], {"north": "hazard"}, uv={"north": [1.5, 13, 14.5, 14.5]}),
    part([4, 0.5, 0.4], [12, 1, 0.5], {"north": "glow"}, glow=True),
    # corner feet
    part([0, 0, 0], [2.5, 2, 2.5], all_faces("frame")),
    part([13.5, 0, 0], [16, 2, 2.5], all_faces("frame")),
    part([0, 0, 13.5], [2.5, 2, 16], all_faces("frame")),
    part([13.5, 0, 13.5], [16, 2, 16], all_faces("frame")),
    # --- cradle under the drum (two stepped tiers)
    part([4, 3, 3], [12, 7, 9.5], {"north": "vent", "east": "armor", "west": "armor", "up": "frame"}),
    part([5, 7, 4], [11, 8.5, 9], all_faces("frame")),
    # --- rear machinery tower (both halves) and heat sinks
    part([3, 3, 9.5], [13, 27, 13.5], {"north": "mach", "south": "mach", "east": "frame", "west": "frame"}),
    part([3.5, 5, 13.5], [12.5, 14, 15], all_faces("sink", north="frame")),
    part([3.5, 17, 13.5], [12.5, 25.5, 15], all_faces("sink", north="frame")),
    part([5.5, 14, 13.5], [10.5, 17, 15], all_faces("frame")),
    part([7, 15, 15], [9, 16, 15.1], {"south": "glow"}, glow=True),
    # --- arch over the drum, with a layered ceramic lip
    part([3, 23.5, 3], [13, 26, 10], {"north": "armor", "up": "armor", "down": "frame", "east": "frame", "west": "frame"}),
    part([3, 25, 2.5], [13, 26, 3], all_faces("armor", south="frame")),
    part([5, 23.5, 2.8], [11, 25, 3], {"north": "vent"}),
    part([6, 24, 2.7], [10, 25, 2.8], {"north": "glow"}, glow=True),
    # --- crown (stepped)
    part([3, 26, 3], [13, 28, 13], all_faces("frame")),
    part([4, 28, 4], [12, 29, 12], all_faces("armor", up="top")),
    # name screen: StationRenderer draws the station name (ticker) just in front of its face
    part([3.5, 26, 2.6], [12.5, 28, 3], all_faces("frame", north="screen"), uv={"north": [0, 0, 9, 2]}),
    part([6.5, 29, 6.5], [9.5, 30.5, 9.5], all_faces("frame")),
    part([7, 30.5, 7], [9, 32, 9], all_faces("glow"), glow=True),
    # antenna fin
    part([12, 28, 10.5], [13, 31.5, 12.5], all_faces("frame")),
    # (siren cube on the fin tip at [12,31.5,11]-[13,32,12] is drawn by StationRenderer: amber / red alarm)
]

# Rear cabling (+z side): a junction box on the plinth fed by cables hanging from the heat-sink connector and
# wiring coming down the rear pylons. Cables are 1x1 voxel runs; bends are 1px stair steps, never rotations.
REAR = [
    # junction box sitting on the rear of the plinth, with a status pin
    part([4.5, 1.5, 14.5], [11.5, 3.5, 16], all_faces("frame", up="mach")),
    part([7.5, 2, 16], [8.5, 3, 16.1], {"south": "amber"}, glow=True),
    # main cable straight down from the connector, with a collar where it plugs in
    part([7.5, 3.5, 15], [8.5, 14.5, 16], all_faces("cable", up="frame")),
    part([7.25, 13.5, 15], [8.75, 14.5, 16.25], all_faces("frame")),
]

REAR_LEFT = [
    # side cable: leaves the connector, steps 1px outward and drops into the box
    part([5.5, 8, 15], [6.5, 14.5, 16], all_faces("cable", up="frame")),
    part([4.5, 3.5, 15], [5.5, 9, 16], all_faces("cable")),
    part([5.25, 13.5, 15], [6.75, 14.5, 16.25], all_faces("frame")),
    # pylon wiring: runs down the rear face of the pylon and elbows into the side of the box
    part([1, 3, 14.5], [2, 10, 15.5], {"north": "cable", "south": "cable", "east": "cable", "west": "cable", "up": "frame"}),
    part([1, 2, 14.5], [4.5, 3, 15.5], all_faces("cable")),
]

ELEMENTS = CENTER + LEFT + [mirror_x(e) for e in LEFT] + REAR + REAR_LEFT + [mirror_x(e) for e in REAR_LEFT]


def split(elements):
    """Cuts the 32-tall model at y=16 into lower and upper halves (upper shifted down by 16)."""
    lower, upper = [], []
    for el in elements:
        y0, y1 = el["from"][1], el["to"][1]
        if y1 <= 16:
            lower.append(copy.deepcopy(el))
        elif y0 >= 16:
            e = copy.deepcopy(el)
            e["from"][1] -= 16
            e["to"][1] -= 16
            if "rotation" in e:
                e["rotation"]["origin"][1] -= 16
            upper.append(e)
        else:
            assert "rotation" not in el, "rotated elements must not cross the half boundary"
            lo = copy.deepcopy(el)
            lo["to"][1] = 16
            lo["faces"].pop("up", None)
            hi = copy.deepcopy(el)
            hi["from"][1] = 0
            hi["to"][1] = y1 - 16
            hi["faces"].pop("down", None)
            lower.append(lo)
            upper.append(hi)
    return lower, upper


LOWER, UPPER = split(ELEMENTS)

ITEM_DISPLAY = {
    "gui": {"rotation": [30, 200, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.35, 0.35, 0.35]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
    "thirdperson_lefthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
    "firstperson_righthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
}

PARTICLES = ("spark", "mote", "shard", "shock_ring")
