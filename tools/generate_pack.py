#!/usr/bin/env python3
"""Generates the 26.2 parts of the resource pack from the original Simply Caterpillar models.

* assets/simplycaterpillar/items/<model>.json: one item definition per original block model, used by the
  plugin's display entities (item_model = simplycaterpillar:<model>).
* assets/simplycaterpillar/items/part_<id>.json: the inventory icons of the part items.
* assets/minecraft/items/<block>.json: overrides of the vanilla items the parts are made of (see config.yml
  "blocks:"). A part item carries custom_model_data "simplycaterpillar:<id>" and gets the original icon; every other
  item of that type falls back to the normal vanilla model, so players without the pack see nothing odd.

Run from the repository root: python3 tools/generate_pack.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "resourcepack")
NS = "simplycaterpillar"

# Block models used in the world, by display name -> model path.
MODELS = {}
for sub in ["drill_head", "drill_base", "collector", "decoration", "drill_seat", "incinerator",
            "reinforcement", "storage", "transporter"]:
    folder = os.path.join(ROOT, "assets", NS, "models", "block", sub)
    for name in sorted(os.listdir(folder)):
        if name.endswith(".json"):
            MODELS[name[:-5]] = f"{NS}:block/{sub}/{name[:-5]}"

# Part id -> (item model for the inventory icon, default vanilla block of the part, vanilla item model fallback)
PARTS = {
    "drill_head": ("drill_head", "gray_glazed_terracotta", "minecraft:block/gray_glazed_terracotta"),
    "drill_base": ("drill_base", "waxed_copper_block", "minecraft:block/copper_block"),
    "storage": ("storage", "dark_oak_planks", "minecraft:block/dark_oak_planks"),
    "collector": ("collector", "lapis_block", "minecraft:block/lapis_block"),
    "incinerator": ("incinerator", "red_nether_bricks", "minecraft:block/red_nether_bricks"),
    "drill_seat": ("drill_seat", "quartz_stairs", "minecraft:block/quartz_stairs"),
    "transporter": ("transporter", "cyan_concrete", "minecraft:block/cyan_concrete"),
    "reinforcement": ("reinforcement", "chiseled_stone_bricks", "minecraft:block/chiseled_stone_bricks"),
    "decoration": ("decoration", "chiseled_sandstone", "minecraft:block/chiseled_sandstone"),
}


def write(path, data):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as out:
        json.dump(data, out, indent=2)
        out.write("\n")


def model(ref):
    return {"type": "minecraft:model", "model": ref}


for name, ref in MODELS.items():
    write(f"assets/{NS}/items/{name}.json", {"model": model(ref)})

by_block = {}
for part, (icon, block, fallback) in PARTS.items():
    write(f"assets/{NS}/items/part_{part}.json", {"model": model(f"{NS}:item/{icon}")})
    by_block.setdefault(block, (fallback, []))[1].append((part, icon))

for block, (fallback, parts) in by_block.items():
    write(f"assets/minecraft/items/{block}.json", {
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "cases": [{"when": f"{NS}:{part}", "model": model(f"{NS}:item/{icon}")} for part, icon in parts],
            "fallback": model(fallback),
        }
    })

write("pack.mcmeta", {
    "pack": {
        "description": "Simply Caterpillar: original models for the Paper plugin",
        "min_format": 88,
        "max_format": 999,
    }
})
print(f"{len(MODELS)} world models, {len(PARTS)} part icons, {len(by_block)} vanilla overrides")
