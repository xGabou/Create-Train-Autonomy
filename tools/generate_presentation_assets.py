"""Original, coordinate-drawn Minecraft pixel art. No resampling or antialiasing.

Run with Python and Pillow to regenerate the cabinet and GUI resources.
Create's textures are references only; this script never reads or copies them.
"""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/createtrainmining"
METAL = "#555b59"
DARK = "#303734"
LIGHT = "#828880"
BRASS = "#b29458"
BRASS_LIGHT = "#dbc083"
BRASS_DARK = "#725e3c"


def save(image, name):
    path = ASSETS / "textures" / name
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def rivet(draw, x, y):
    draw.point((x, y), LIGHT)
    draw.point((x + 1, y + 1), DARK)


def metal():
    image = Image.new("RGB", (16, 16), METAL)
    draw = ImageDraw.Draw(image)
    # Sparse, deterministic metal grain. Every mark is an individual pixel.
    for x, y in ((3, 4), (11, 8), (6, 12), (13, 3), (2, 10), (9, 6)):
        draw.point((x, y), "#5b615b")
    draw.line((0, 0, 15, 0), LIGHT)
    draw.line((0, 0, 0, 15), "#70766f")
    draw.line((0, 15, 15, 15), DARK)
    draw.line((15, 0, 15, 15), DARK)
    return image


side = metal()
d = ImageDraw.Draw(side)
d.rectangle((2, 3, 13, 12), fill="#454c48", outline="#252d2a")
for y in (5, 7, 9):
    d.line((4, y, 11, y), "#232b28")
    d.line((4, y + 1, 11, y + 1), "#666d62")
d.line((0, 1, 15, 1), BRASS)
d.line((0, 14, 15, 14), BRASS_DARK)
for p in ((1, 3), (13, 3), (1, 12), (13, 12)):
    rivet(d, *p)
save(side, "block/controller_side.png")

top = metal()
d = ImageDraw.Draw(top)
d.rectangle((2, 2, 13, 13), outline=DARK)
d.rectangle((3, 3, 12, 12), fill="#60655d")
d.line((0, 2, 15, 2), BRASS)
d.line((0, 3, 15, 3), BRASS_DARK)
for p in ((1, 1), (13, 1), (1, 13), (13, 13)):
    rivet(d, *p)
save(top, "block/controller_top.png")

back = metal()
d = ImageDraw.Draw(back)
d.rectangle((3, 3, 12, 12), outline=DARK)
d.line((0, 1, 15, 1), BRASS)
for p in ((3, 3), (11, 3), (3, 11), (11, 11)):
    rivet(d, *p)
save(back, "block/controller_back.png")

trim = Image.new("RGB", (16, 16), BRASS)
d = ImageDraw.Draw(trim)
for y in (0, 4, 8, 12):
    d.line((0, y, 15, y), BRASS_LIGHT)
    d.line((0, y + 3, 15, y + 3), BRASS_DARK)
save(trim, "block/controller_brass.png")

for state, color, shine in (
    ("inactive", "#666c65", "#91958a"),
    ("running", "#619247", "#bdd587"),
    ("waiting", "#c49439", "#f0d186"),
    ("error", "#a64e3e", "#e49a71"),
):
    front = metal()
    d = ImageDraw.Draw(front)
    d.rectangle((1, 1, 14, 14), fill=BRASS_DARK)
    d.rectangle((2, 2, 13, 13), fill=BRASS)
    d.line((2, 2, 13, 2), BRASS_LIGHT)
    d.line((2, 2, 2, 13), BRASS_LIGHT)
    d.rectangle((3, 3, 12, 12), fill="#393e38")
    d.line((3, 3, 12, 3), "#242a26")
    # Two rails, three sleepers, and a branch: a mechanical railway schematic.
    for y in (6, 8, 10):
        d.line((4, y, 9, y), "#756548")
    d.line((5, 5, 5, 10), "#b2ad90")
    d.line((8, 5, 8, 10), "#b2ad90")
    d.line((8, 7, 10, 5), "#b2ad90")
    # Status lens and two small mechanical controls, not a computer display.
    d.rectangle((10, 3, 12, 5), fill="#242a26")
    d.rectangle((11, 3, 12, 4), fill=color)
    d.point((11, 3), shine)
    d.rectangle((4, 11, 5, 12), fill="#aab0a0")
    d.rectangle((9, 11, 10, 12), fill=BRASS_LIGHT)
    for p in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(d, *p)
    save(front, f"block/controller_front_{state}.png")
    lens = Image.new("RGB", (16, 16), "#242a26")
    lens_draw = ImageDraw.Draw(lens)
    lens_draw.rectangle((2, 2, 13, 13), fill=color)
    lens_draw.rectangle((3, 3, 12, 5), fill=shine)
    save(lens, f"block/controller_lamp_{state}.png")

# Small pixel glyphs for presentation controls not represented by Create's atlas.
gear = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
gd = ImageDraw.Draw(gear)
for box in ((6, 1, 9, 14), (1, 6, 14, 9), (3, 3, 12, 12)):
    gd.rectangle(box, fill="#eeeadd")
gd.rectangle((5, 5, 10, 10), fill="#485a52")
gd.rectangle((6, 6, 9, 9), fill="#8c9386")
save(gear, "gui/gear.png")
pencil = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
pd = ImageDraw.Draw(pencil)
for i in range(8):
    pd.rectangle((4 + i, 10 - i, 6 + i, 12 - i), fill=BRASS_LIGHT)
    pd.point((4 + i, 10 - i), "#eeeadd")
pd.rectangle((2, 12, 4, 14), fill="#eeeadd")
pd.point((2, 14), DARK)
pd.rectangle((11, 1, 13, 3), fill="#afb7a2")
save(pencil, "gui/pencil.png")
controls = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
cd = ImageDraw.Draw(controls)
cd.rectangle((2, 10, 13, 13), fill="#eeeadd")
cd.rectangle((3, 13, 12, 14), fill="#afb7a2")
cd.line((7, 10, 10, 4), BRASS_LIGHT, width=2)
cd.rectangle((8, 2, 12, 5), fill="#eeeadd")
cd.point((11, 5), "#afb7a2")
save(controls, "gui/manual_controls.png")

# A restrained station-equipment frame: narrow metal bevels, brass header,
# dark information inset, and a fixed bottom instrumentation strip.
gui = Image.new("RGBA", (304, 226), (0, 0, 0, 0))
d = ImageDraw.Draw(gui)
d.rectangle((1, 1, 302, 224), fill="#b6b8b1", outline="#2c302f")
d.line((2, 2, 301, 2), "#eeeee5")
d.line((2, 2, 2, 223), "#dddcd3")
d.line((3, 223, 301, 223), "#666c67")
d.line((301, 3, 301, 223), "#666c67")
d.rectangle((6, 25, 297, 181), fill="#d2d3ca", outline="#858b83")
# Two-pixel knurled edge strips, distinct from Create's original screen assets.
for y in range(26, 181):
    for x in (7, 296):
        d.point((x, y), "#8c9188" if y % 2 else "#afb2a8")
d.rectangle((6, 5, 297, 22), fill=BRASS, outline=BRASS_DARK)
d.line((7, 6, 296, 6), BRASS_LIGHT)
d.line((7, 21, 296, 21), "#8a7045")
d.rectangle((12, 28, 113, 177), fill="#424941", outline="#242c27")
d.line((13, 176, 112, 176), "#e0dfd0")
d.line((113, 29, 113, 176), "#eeeadd")
d.line((118, 29, 118, 177), "#949b90")
d.line((119, 29, 119, 177), "#eaeadd")
d.line((125, 59, 291, 59), "#8d9489")
d.line((125, 60, 291, 60), "#ededdf")
d.rectangle((10, 207, 293, 221), fill="#545d52", outline="#29342c")
d.line((11, 220, 292, 220), "#e5e3d4")
for x, y in ((4, 4), (297, 4), (4, 218), (297, 218)):
    d.rectangle((x, y, x + 2, y + 2), fill="#555e54")
    d.point((x, y), "#e5e4d6")
save(gui, "gui/controller.png")


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def cuboid(start, end, front="#side", sides="#side", up="#top", down="#side", front_uv=None):
    faces = {name: {"texture": tex, "uv": [0, 0, 16, 16]} for name, tex in (
        ("north", front), ("south", "#back"), ("east", sides), ("west", sides), ("up", up), ("down", down))}
    if front_uv:
        faces["north"]["uv"] = front_uv
    return {"from": start, "to": end, "faces": faces}


model = {
    "parent": "minecraft:block/block",
    "textures": {"particle": "createtrainmining:block/controller_side",
                 "side": "createtrainmining:block/controller_side",
                 "top": "createtrainmining:block/controller_top",
                 "back": "createtrainmining:block/controller_back",
                 "brass": "createtrainmining:block/controller_brass",
                 "front": "createtrainmining:block/controller_front_inactive",
                 "lamp": "createtrainmining:block/controller_lamp_inactive"},
    "elements": [
        cuboid([0, 0, 0], [16, 2, 16]),
        cuboid([1, 2, 1], [15, 13, 15]),
        cuboid([.5, 13, .5], [15.5, 15.5, 15.5], sides="#side"),
        # Front trim stands half a pixel proud; the illustrated panel is inset.
        cuboid([2, 3, .5], [14, 12, 1], front="#brass", sides="#brass", up="#brass"),
        cuboid([2.5, 3.5, .75], [13.5, 11.5, 1.05], front="#front", sides="#brass", up="#brass"),
    ],
    "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [.625, .625, .625]},
        "fixed": {"rotation": [0, 180, 0], "scale": [.5, .5, .5]},
        "ground": {"translation": [0, 3, 0], "scale": [.25, .25, .25]},
    },
}
# The plate's top/bottom/back are within the body. Only its north face is needed.
# A ring leaves the front recessed rather than covering it with another cuboid.
frame = model["elements"].pop(3)
model["elements"] = model["elements"][:3] + [
    cuboid([2, 3, .5], [14, 3.5, 1.1], front="#brass", up="#brass"),
    cuboid([2, 11.5, .5], [14, 12, 1.1], front="#brass", up="#brass"),
    cuboid([2, 3.5, .5], [2.5, 11.5, 1.1], front="#brass", sides="#brass", up="#brass"),
    cuboid([13.5, 3.5, .5], [14, 11.5, 1.1], front="#brass", sides="#brass", up="#brass"),
    {"from": [2.5, 3.5, .75], "to": [13.5, 11.5, 1.05],
     "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#front"}}},
    {"from": [11, 12.05, .5], "to": [13, 12.95, 1.1], "shade": False,
     "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#lamp"}}},
]
write_json(ASSETS / "models/block/train_automation_controller.json", model)
for state in ("running", "waiting", "error"):
    write_json(ASSETS / f"models/block/train_automation_controller_{state}.json", {
        "parent": "createtrainmining:block/train_automation_controller",
        "textures": {"front": f"createtrainmining:block/controller_front_{state}",
                     "lamp": f"createtrainmining:block/controller_lamp_{state}"},
    })
variants = {}
for facing, rotation in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
    for state in ("inactive", "running", "waiting", "error"):
        suffix = "" if state == "inactive" else "_" + state
        variants[f"facing={facing},lamp={state}"] = {
            "model": "createtrainmining:block/train_automation_controller" + suffix, "y": rotation,
        }
write_json(ASSETS / "blockstates/train_automation_controller.json", {"variants": variants})
print("Wrote original 16x16 cabinet textures, GUI frame, models, and 16 facing/lamp variants.")
