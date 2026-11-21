"""Generates block/item models, blockstate, recipes, loot table, tags and lang files.

Run from the project root:  python tools/gen_assets.py
"""
import copy
import json
import os

RES = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
NS = "nexusgate"
GLOW = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}


def write(rel, data):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def box(frm, to, tex, faces=("north", "south", "east", "west", "up", "down"), uv=None, glow=False, up=None, down=None):
    el = {"from": frm, "to": to, "faces": {}}
    for face in faces:
        t = tex
        if face == "up" and up:
            t = up
        if face == "down" and down:
            t = down
        f = {"texture": t}
        if uv:
            f["uv"] = uv
        el["faces"][face] = f
    if glow:
        el["neoforge_data"] = GLOW
    return el


TEXTURES = {
    "base": f"{NS}:block/station_base",
    "side": f"{NS}:block/station_side",
    "core": f"{NS}:block/station_core",
    "top": f"{NS}:block/station_top",
    "particle": f"{NS}:block/station_side",
}

LOWER = [
    box([1, 0, 1], [15, 3, 15], "#base", up="#top"),
    box([3, 3, 3], [13, 16, 13], "#side", faces=("north", "south", "east", "west")),
    box([6, 4, 2.9], [10, 16, 13.1], "#core", faces=("north", "south"), uv=[6, 0, 10, 12], glow=True),
    box([2.9, 4, 6], [13.1, 16, 10], "#core", faces=("east", "west"), uv=[6, 0, 10, 12], glow=True),
]

UPPER = [
    box([4, 0, 4], [12, 10, 12], "#side", faces=("north", "south", "east", "west")),
    box([6, 0, 3.9], [10, 9, 12.1], "#core", faces=("north", "south"), uv=[6, 4, 10, 13], glow=True),
    box([3.9, 0, 6], [12.1, 9, 10], "#core", faces=("east", "west"), uv=[6, 4, 10, 13], glow=True),
    box([2, 10, 2], [14, 12, 14], "#base", up="#top", down="#base"),
    box([7, 12, 7], [9, 15, 9], "#side", faces=("north", "south", "east", "west")),
    box([6.5, 15, 6.5], [9.5, 16.5, 9.5], "#core", uv=[6, 0, 9, 3], glow=True),
]


def shifted(elements, dy):
    out = copy.deepcopy(elements)
    for el in out:
        el["from"][1] += dy
        el["to"][1] += dy
    return out


def models():
    write(f"assets/{NS}/models/block/teleport_station_lower.json", {"textures": TEXTURES, "elements": LOWER})
    write(f"assets/{NS}/models/block/teleport_station_upper.json", {"textures": TEXTURES, "elements": UPPER})
    write(f"assets/{NS}/models/item/teleport_station.json", {
        "textures": TEXTURES,
        "elements": copy.deepcopy(LOWER) + shifted(UPPER, 16),
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -3, 0], "scale": [0.4, 0.4, 0.4]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.25, 0.25, 0.25]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, -3, 0], "scale": [0.4, 0.4, 0.4]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 1], "scale": [0.3, 0.3, 0.3]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
        },
    })
    write(f"assets/{NS}/models/item/locator.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"{NS}:item/locator"},
    })


def blockstate():
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for facing, y in rot.items():
        for half in ("lower", "upper"):
            v: dict = {"model": f"{NS}:block/teleport_station_{half}"}
            if y:
                v["y"] = y
            variants[f"facing={facing},half={half}"] = v
    write(f"assets/{NS}/blockstates/teleport_station.json", {"variants": variants})


def data():
    write(f"data/{NS}/loot_table/blocks/teleport_station.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{
                "type": "minecraft:item",
                "name": f"{NS}:teleport_station",
                "conditions": [{
                    "condition": "minecraft:block_state_property",
                    "block": f"{NS}:teleport_station",
                    "properties": {"half": "lower"},
                }],
            }],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })
    write(f"data/{NS}/recipe/teleport_station.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["IGI", "QEQ", "IRI"],
        "key": {
            "I": {"item": "minecraft:iron_ingot"},
            "G": {"item": "minecraft:glass"},
            "Q": {"item": "minecraft:quartz"},
            "E": {"item": "minecraft:ender_pearl"},
            "R": {"item": "minecraft:redstone_block"},
        },
        "result": {"id": f"{NS}:teleport_station", "count": 1},
    })
    write(f"data/{NS}/recipe/locator.json", {
        "type": "minecraft:crafting_shaped",
        "category": "tools",
        "pattern": [" I ", "ICI", "RIQ"],
        "key": {
            "I": {"item": "minecraft:iron_ingot"},
            "C": {"item": "minecraft:compass"},
            "R": {"item": "minecraft:redstone"},
            "Q": {"item": "minecraft:quartz"},
        },
        "result": {"id": f"{NS}:locator", "count": 1},
    })
    tag = {"replace": False, "values": [f"{NS}:teleport_station"]}
    write("data/minecraft/tags/block/mineable/pickaxe.json", tag)
    write("data/minecraft/tags/block/needs_stone_tool.json", tag)


LANG = {
    "block.nexusgate.teleport_station": ("Estação de Teleporte", "Teleport Station"),
    "item.nexusgate.locator": ("Localizador", "Locator"),
    "itemGroup.nexusgate.main": ("Nexus Gate", "Nexus Gate"),
    "station.nexusgate.default_name": ("Estação %s, %s", "Station %s, %s"),
    # validation results (RequestResult.translationKey)
    "msg.nexusgate.ok": ("Sincronização iniciada", "Synchronization started"),
    "msg.nexusgate.not_at_station": ("Você precisa estar junto à estação", "You must be next to the station"),
    "msg.nexusgate.target_offline": ("Estação offline", "Station offline"),
    "msg.nexusgate.target_unknown": ("Estação não sincronizada", "Station not synchronized"),
    "msg.nexusgate.same_station": ("Você já está nesta estação", "You are already at this station"),
    "msg.nexusgate.cross_dimension_disabled": ("Teleporte entre dimensões desativado", "Cross-dimension teleport is disabled"),
    "msg.nexusgate.in_combat": ("Interferência detectada: aguarde %ss", "Interference detected: wait %ss"),
    "msg.nexusgate.not_enough_xp": ("XP insuficiente: precisa de %s níveis", "Not enough XP: needs %s levels"),
    # lifecycle / charging
    "msg.nexusgate.synced": ("Estação %s sincronizada", "Station %s synchronized"),
    "msg.nexusgate.interrupted": ("Sincronização interrompida", "Synchronization interrupted"),
    "msg.nexusgate.arrived": ("Chegada: %s", "Arrived: %s"),
    "msg.nexusgate.renamed": ("Estação renomeada para %s", "Station renamed to %s"),
    "msg.nexusgate.no_permission": ("Sem permissão", "No permission"),
    "msg.nexusgate.invalid_name": ("Nome inválido (1 a 32 caracteres)", "Invalid name (1 to 32 characters)"),
    # public stations
    "msg.nexusgate.now_public": ("%s agora é pública", "%s is now public"),
    "msg.nexusgate.now_private": ("%s agora é privada", "%s is now private"),
    "msg.nexusgate.no_station_in_sight": ("Olhe para uma Estação de Teleporte", "Look at a Teleport Station"),
    "msg.nexusgate.protected": ("Estação protegida pela administração", "Station protected by the administration"),
    # locator
    "msg.nexusgate.locator_linked": ("Localizador vinculado a %s", "Locator linked to %s"),
    "msg.nexusgate.signal_lost": ("Sinal perdido", "Signal lost"),
    "msg.nexusgate.locator_other_dimension": ("%s — outra dimensão", "%s — other dimension"),
    "msg.nexusgate.locator_direction": ("→ %s  %s m  %s", "→ %s  %s m  %s"),
    "tooltip.nexusgate.locator_linked": ("Vinculado", "Linked"),
    "tooltip.nexusgate.locator_unlinked": ("Agache + clique numa estação para vincular", "Sneak + use on a station to link"),
    "tooltip.nexusgate.locator_info": ("Mostra direção e distância. Não teleporta.", "Shows direction and distance. Does not teleport."),
    # screens
    "screen.nexusgate.rename_title": ("Nomear estação", "Name station"),
    "screen.nexusgate.confirm": ("Confirmar", "Confirm"),
    "screen.nexusgate.rename": ("Renomear", "Rename"),
    "screen.nexusgate.make_public": ("Tornar pública", "Make public"),
    "screen.nexusgate.make_private": ("Tornar privada", "Make private"),
    "screen.nexusgate.public_tag": ("[PÚBLICA]", "[PUBLIC]"),
    "screen.nexusgate.interference": ("INTERFERÊNCIA DETECTADA — %ss", "INTERFERENCE DETECTED — %ss"),
    "screen.nexusgate.empty": ("Nenhuma outra estação sincronizada", "No other synchronized stations"),
    "screen.nexusgate.other_dimension": ("—", "—"),
    "screen.nexusgate.distance": ("%s m", "%s m"),
    "screen.nexusgate.xp": ("%s XP", "%s XP"),
    "screen.nexusgate.free": ("grátis", "free"),
    # commands
    "commands.nexusgate.public.usage": ("/nexusgate public <true|false>", "/nexusgate public <true|false>"),
}


def lang():
    write(f"assets/{NS}/lang/pt_br.json", {k: v[0] for k, v in LANG.items()})
    write(f"assets/{NS}/lang/en_us.json", {k: v[1] for k, v in LANG.items()})


if __name__ == "__main__":
    models()
    blockstate()
    data()
    lang()
    print("assets written")
