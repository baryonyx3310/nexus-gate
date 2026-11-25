"""Generates block/item models, blockstate, recipes, loot table, tags and lang files.

Run from the project root:  python tools/gen_assets.py
"""
import copy
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

RES = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
NS = "nexusgate"
GLOW = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}


def write(rel, data):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


from station_model import ITEM_DISPLAY, LOWER, PARTICLES, TEXTURES, UPPER  # noqa: E402


def models():
    write(f"assets/{NS}/models/block/teleport_station_lower.json", {"textures": TEXTURES, "elements": LOWER})
    write(f"assets/{NS}/models/block/teleport_station_upper.json", {"textures": TEXTURES, "elements": UPPER})
    # The item is drawn by StationItemRenderer (both halves + the animated drum).
    write(f"assets/{NS}/models/item/teleport_station.json", {
        "parent": "minecraft:builtin/entity",
        "textures": {"particle": f"{NS}:block/frame_graphite"},
        "gui_light": "side",
        "display": ITEM_DISPLAY,
    })
    write(f"assets/{NS}/models/item/locator.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"{NS}:item/locator"},
    })
    for name in PARTICLES:
        write(f"assets/{NS}/particles/{name}.json", {"textures": [f"{NS}:{name}"]})


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
    "msg.nexusgate.no_safe_spot": ("Destino obstruído: nenhum local seguro para chegar", "Destination blocked: no safe place to arrive"),
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
    "screen.nexusgate.select_destination": ("SELECIONE UM DESTINO", "SELECT A DESTINATION"),
    "screen.nexusgate.select_hint": ("Clique numa câmara do tambor. Depois, ative o núcleo.", "Click a drum chamber, then engage the core."),
    "nexusgate.configuration.showSelectHint": ("Mostrar dica de destino", "Show destination hint"),
    "screen.nexusgate.logo": ("Logo", "Logo"),
    "icon.nexusgate.pin": ("Marcador", "Pin"),
    "icon.nexusgate.house": ("Casa", "House"),
    "icon.nexusgate.base": ("Base", "Base"),
    "icon.nexusgate.skull": ("Perigo", "Danger"),
    "icon.nexusgate.mine": ("Mina", "Mine"),
    "icon.nexusgate.farm": ("Fazenda", "Farm"),
    "icon.nexusgate.portal": ("Portal", "Portal"),
    "icon.nexusgate.tree": ("Floresta", "Forest"),
    "icon.nexusgate.chest": ("Loja", "Shop"),
    "icon.nexusgate.sword": ("Arena", "Arena"),
    "icon.nexusgate.star": ("Favorito", "Favorite"),
    "icon.nexusgate.anchor": ("Porto", "Harbor"),
    "screen.nexusgate.engage": ("[ ATIVAR NÚCLEO ]", "[ ENGAGE CORE ]"),
    "screen.nexusgate.engage_now": ("▶ ATIVAR NÚCLEO ◀", "▶ ENGAGE CORE ◀"),
    "screen.nexusgate.status.charging": ("SINCRONIZANDO %s%%", "SYNCHRONIZING %s%%"),
    "screen.nexusgate.status.awaiting": ("AGUARDANDO A ESTAÇÃO…", "AWAITING STATION…"),
    "screen.nexusgate.status.interference": ("INTERFERÊNCIA", "INTERFERENCE"),
    "screen.nexusgate.status.no_xp": ("XP INSUFICIENTE", "NOT ENOUGH XP"),
    "screen.nexusgate.dimension": ("DIM", "DIM"),
    "screen.nexusgate.distance_label": ("DIST", "DIST"),
    "screen.nexusgate.cost_label": ("CUSTO", "COST"),
    "screen.nexusgate.page": ("CILINDRO %s/%s", "CYLINDER %s/%s"),
    "screen.nexusgate.public_short": ("PÚBLICA", "PUBLIC"),
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
