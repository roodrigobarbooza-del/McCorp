"""Dibuja una vista isometrica de cada maquina (modo resource pack) para revisar las formas."""
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
import geometria  # noqa: E402
import texturas  # noqa: E402


def color_medio(im):
    px = [p for p in im.getdata() if p[3] > 0]
    if not px:
        return (0, 0, 0, 0)
    return tuple(sum(p[i] for p in px) // len(px) for i in range(3)) + (255,)


COLORES = {k: color_medio(v) for k, v in texturas.maquinas().items()}


def rot(p, eje, o, ang):
    x, y, z = (p[i] - o[i] for i in range(3))
    c, s = math.cos(ang), math.sin(ang)
    if eje == "y":
        x, z = x * c + z * s, -x * s + z * c
    elif eje == "x":
        y, z = y * c - z * s, y * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


def caras(c):
    (x0, y0, z0), (x1, y1, z1) = c["de"], c["a"]
    t = c["t"] if isinstance(c["t"], dict) else {f: c["t"] for f in ("north", "south", "east", "west", "up", "down")}
    f = {
        "up": ([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], 1.0),
        "north": ([(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], 0.8),
        "west": ([(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)], 0.65),
        "south": ([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], 0.8),
        "east": ([(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)], 0.65),
    }
    out = []
    for cara, (pts, luz) in f.items():
        if "rot" in c:
            pts = [rot(p, c["rot"]["eje"], c["rot"]["origen"], math.radians(45)) for p in pts]
        out.append((pts, COLORES[t[cara]], luz))
    return out


def proyectar(p, escala, ox, oy):
    # Vista desde el frente-izquierda (desde donde esta el jugador), de arriba.
    x, y, z = p
    a = math.radians(35)
    u = (x * math.cos(a) + z * math.sin(a))
    d = (-x * math.sin(a) + z * math.cos(a))
    return (ox + u * escala, oy - y * escala * 0.9 - d * escala * 0.45), d - y * 0.01


def render(nombre, fn, ruta):
    datos = fn()
    polis = []
    for p in datos["partes"]:
        for c in p.cajas:
            if c.get("solo") == "bloques":
                continue
            for pts, col, luz in caras(c):
                if col[3] == 0:
                    continue
                pr = [proyectar(q, 48, 330, 520) for q in pts]
                prof = sum(d for _, d in pr) / len(pr)
                polis.append((prof, [xy for xy, _ in pr], tuple(int(v * luz) for v in col[:3])))
    im = Image.new("RGB", (720, 620), (196, 222, 240))
    dr = ImageDraw.Draw(im)
    suelo = [proyectar(q, 48, 330, 520)[0] for q in ((-4, 0, -1), (6, 0, -1), (6, 0, 10), (-4, 0, 10))]
    dr.polygon(suelo, fill=(110, 150, 80))
    for _, pts, col in sorted(polis, key=lambda t: -t[0]):
        dr.polygon(pts, fill=col, outline=tuple(max(0, v - 30) for v in col))
    dr.text((12, 10), nombre, fill=(20, 20, 20))
    im.save(ruta)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    for n, f in geometria.MAQUINAS.items():
        render(n, f, os.path.join(out, n + ".png"))
