#!/usr/bin/env python3
"""Render the north-up client texture from four exact Full Map Review 02 region files."""
import argparse
from hashlib import sha256
import importlib.util
from pathlib import Path
import sys
from PIL import Image, ImageColor

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "walls53"))
from audit_sites import World

REGIONS = {
    "r.-1.-1.mca": "c569887cb9e8c397e6bfd6dd918af1976cd26320c7f4f08dde3a9ecbc9cbe5ad",
    "r.-1.0.mca": "285eebbf496f9c7c79cef5efaf526a3b584d53243fa324e5a1b266caef78ec44",
    "r.0.-1.mca": "54672befb0487ad08c9465da293e66490546c24f35ede5cdf4c28e9bbafef4cb",
    "r.0.0.mca": "b384c91918a487c9900b660455387d54543ee04bf9012eea6c6caa6f06bb0707",
}
COLORS = {
    "grass_block": "#728568", "grass": "#748868", "fern": "#667f5d", "moss_block": "#688062",
    "oxeye_daisy": "#88987b", "gravel": "#bbb7ab", "andesite": "#afa99c",
    "polished_andesite": "#98958a", "cobblestone": "#99988f", "coarse_dirt": "#9f9178",
    "stone": "#92928b", "yellow_terracotta": "#c69d64", "white_concrete": "#d8d6ca",
    "sea_lantern": "#c0d8cb", "red_terracotta": "#ad745e", "light_blue_stained_glass": "#90aeb3",
}

def color(name):
    if name in COLORS:
        return COLORS[name]
    for token, tint in (
        ("leaves", "#536e54"), ("copper", "#879b89" if "weathered" in name or "oxidized" in name else "#b28369"),
        ("deepslate", "#6d6b69"), ("blackstone", "#656363"), ("stone_bricks", "#93918b"),
        ("sandstone", "#c9b797"), ("quartz", "#d2cfc2"), ("spruce", "#897c65")
    ):
        if token in name:
            return tint
    return "#aaa496"

def render(save, parser):
    for name, expected in REGIONS.items():
        if sha256((save / "region" / name).read_bytes()).hexdigest() != expected:
            raise ValueError("Full Map Review 02 region mismatch: " + name)
    spec = importlib.util.spec_from_file_location("pinned_nbt", parser)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    world = World(save, module.NBT)
    size = 160
    image = Image.new("RGB", (size, size))
    heights = {}
    for z in range(-68, -68 + size):
        for x in range(-40, -40 + size):
            for y in range(159, 62, -1):
                name = world.at((x, y, z)).split("[")[0].removeprefix("minecraft:")
                if name not in {"air", "cave_air", "void_air"}:
                    break
            heights[x, z] = y
            shading = max(.80, min(1.12, 1 + .038 * (y - heights.get((x, z-1), y))))
            rgb = ImageColor.getrgb(color(name))
            image.putpixel((x + 40, z + 68), tuple(round(c * shading) for c in rgb))
    return image

if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("extracted_world", type=Path)
    p.add_argument("r7_nbt_check_py", type=Path)
    p.add_argument("png", type=Path)
    a = p.parse_args()
    render(a.extracted_world, a.r7_nbt_check_py).save(a.png)
