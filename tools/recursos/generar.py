"""
Genera, a partir de geometria.py y texturas.py:
  - minercorp-resources/src/main/resources/modelos/maquinas.json (lo lee el plugin)
  - resourcepack/assets/mccorp/{items,models/item,textures/item}/resources/... (modelos y texturas)

Uso, desde la raiz del repo:  python3 tools/recursos/generar.py
Requiere Pillow (pip install pillow).
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import geometria  # noqa: E402
import texturas  # noqa: E402

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
PACK = os.path.join(RAIZ, "resourcepack", "assets", "mccorp")
NS = "mccorp"
CARPETA = "resources"
MEDIA_MAX_PX = 23.5  # los elementos de un modelo tienen que caber en [-16, 32]

CARAS = ("north", "south", "east", "west", "up", "down")


def escribir(ruta, contenido):
    os.makedirs(os.path.dirname(ruta), exist_ok=True)
    if isinstance(contenido, (dict, list)):
        with open(ruta, "w", encoding="utf-8") as f:
            json.dump(contenido, f, indent=1, ensure_ascii=False)
            f.write("\n")
    else:
        contenido.save(ruta)


def caja_extendida(c):
    """Bounding box de una caja, contando su rotacion de 45 grados."""
    lo, hi = list(c["de"]), list(c["a"])
    if "rot" in c:
        eje = "xyz".index(c["rot"]["eje"])
        o = c["rot"]["origen"]
        for i in range(3):
            if i == eje:
                continue
            r = max(abs(hi[j] - o[j]) for j in range(3) if j != eje) * math.sqrt(2)
            lo[i] = min(lo[i], o[i] - r)
            hi[i] = max(hi[i], o[i] + r)
    return lo, hi


def trozos(a, b):
    """Corta [a, b] en tramos de 1 bloque como maximo (16 px de textura por bloque)."""
    out = []
    x = a
    while x < b - 1e-6:
        y = min(b, x + 1.0)
        out.append((x, y))
        x = y
    return out


def r4(v):
    return round(v, 4)


def modelo_parte(maquina, parte, cajas):
    lo = [min(caja_extendida(c)[0][i] for c in cajas) for i in range(3)]
    hi = [max(caja_extendida(c)[1][i] for c in cajas) for i in range(3)]
    centro = [(lo[i] + hi[i]) / 2 for i in range(3)]
    media = max((hi[i] - lo[i]) / 2 for i in range(3))
    k = min(1.0, MEDIA_MAX_PX / (media * 16))

    def m(w, i):
        return r4(8 + (w - centro[i]) * 16 * k)

    texs = {}
    elementos = []
    for c in cajas:
        tex = c["t"] if isinstance(c["t"], dict) else {f: c["t"] for f in CARAS}
        for nombre in tex.values():
            texs.setdefault(nombre, "t%d" % len(texs))
        de, a = c["de"], c["a"]
        for x0, x1 in trozos(de[0], a[0]):
            for y0, y1 in trozos(de[1], a[1]):
                for z0, z1 in trozos(de[2], a[2]):
                    caras = {}
                    dx, dy, dz = (x1 - x0) * 16, (y1 - y0) * 16, (z1 - z0) * 16
                    lim = {
                        "north": (abs(z0 - de[2]) < 1e-6, dx, dy), "south": (abs(z1 - a[2]) < 1e-6, dx, dy),
                        "west": (abs(x0 - de[0]) < 1e-6, dz, dy), "east": (abs(x1 - a[0]) < 1e-6, dz, dy),
                        "down": (abs(y0 - de[1]) < 1e-6, dx, dz), "up": (abs(y1 - a[1]) < 1e-6, dx, dz),
                    }
                    for cara, (borde, u, v) in lim.items():
                        if borde:
                            caras[cara] = {"uv": [0, 0, r4(min(16, u)), r4(min(16, v))], "texture": "#" + texs[tex[cara]]}
                    el = {"from": [m(x0, 0), m(y0, 1), m(z0, 2)], "to": [m(x1, 0), m(y1, 1), m(z1, 2)], "faces": caras}
                    if "rot" in c:
                        o = c["rot"]["origen"]
                        el["rotation"] = {"angle": 45, "axis": c["rot"]["eje"],
                                          "origin": [m(o[0], 0), m(o[1], 1), m(o[2], 2)], "rescale": False}
                    elementos.append(el)
    modelo = {
        "textures": dict({v: "%s:item/%s/maquinas/%s" % (NS, CARPETA, k_) for k_, v in texs.items()},
                         particle="%s:item/%s/maquinas/acero" % (NS, CARPETA)),
        "elements": elementos,
    }
    nombre = "%s_%s" % (maquina, parte.id)
    escribir(os.path.join(PACK, "models", "item", CARPETA, "maquinas", nombre + ".json"), modelo)
    escribir(os.path.join(PACK, "items", CARPETA, "maquinas", nombre + ".json"),
             {"model": {"type": "minecraft:model", "model": "%s:item/%s/maquinas/%s" % (NS, CARPETA, nombre)}})
    return "%s:%s/maquinas/%s" % (NS, CARPETA, nombre), [r4(v) for v in centro], r4(k)


def main():
    for nombre, im in texturas.maquinas().items():
        escribir(os.path.join(PACK, "textures", "item", CARPETA, "maquinas", nombre + ".png"), im)
    for nombre, im in texturas.items().items():
        escribir(os.path.join(PACK, "textures", "item", CARPETA, nombre + ".png"), im)
        escribir(os.path.join(PACK, "models", "item", CARPETA, nombre + ".json"),
                 {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s/%s" % (NS, CARPETA, nombre)}})
        escribir(os.path.join(PACK, "items", CARPETA, nombre + ".json"),
                 {"model": {"type": "minecraft:model", "model": "%s:item/%s/%s" % (NS, CARPETA, nombre)}})

    salida = {}
    for maquina, fn in geometria.MAQUINAS.items():
        datos = fn()
        partes = []
        for p in datos["partes"]:
            de_pack = [c for c in p.cajas if c.get("solo") != "bloques"]
            de_bloques = [c for c in p.cajas if c.get("solo") != "pack" and c["b"] != "AIR"]
            entrada = {"id": p.id, "anim": p.anim, "pivote": p.pivote, "args": p.anim_args,
                       "cajas": [{k: v for k, v in c.items() if k in ("de", "a", "b", "rot")} for c in de_bloques]}
            if de_pack:
                entrada["modelo"], entrada["centro"], entrada["k"] = modelo_parte(maquina, p, de_pack)
            partes.append(entrada)
        datos["partes"] = partes
        salida[maquina] = datos
    escribir(os.path.join(RAIZ, "minercorp-resources", "src", "main", "resources", "modelos", "maquinas.json"), salida)
    print("ok:", ", ".join("%s (%d partes)" % (m, len(d["partes"])) for m, d in salida.items()))


if __name__ == "__main__":
    main()
