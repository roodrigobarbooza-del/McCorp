"""Dibuja la textura de ejemplo del bidon de gasolina (16x16) sin librerias.

Solo hace falta correrlo si se quiere regenerar el PNG; el resultado ya esta
en resourcepack/assets/mccorp/textures/item/resources/gasolina.png.
"""
import struct
import sys
import zlib
from pathlib import Path

PALETA = {
    ".": (0, 0, 0, 0),
    "k": (40, 14, 12, 255),     # contorno
    "r": (196, 36, 30, 255),    # rojo
    "R": (232, 72, 52, 255),    # brillo
    "d": (140, 22, 20, 255),    # sombra
    "y": (242, 200, 60, 255),   # tapa
    "g": (90, 90, 96, 255),     # pico metalico
    "w": (236, 228, 214, 255),  # etiqueta
}

DIBUJO = [
    "................",
    "...kkkkk...kk...",
    "..kgggggk.kyyk..",
    "..k.kkk.kkkyyk..",
    "..kkrrrrrrrkkk..",
    ".kRRrrrrrrrrdk..",
    ".kRrkrrrrrkrdk..",
    ".kRrrkrrrkrrdk..",
    ".kRrrrkwkrrrdk..",
    ".kRrrrwkwrrrdk..",
    ".kRrrrkwkrrrdk..",
    ".kRrrkrrrkrrdk..",
    ".kRrkrrrrrkrdk..",
    ".kRrrrrrrrrrdk..",
    "..kddddddddddk..",
    "...kkkkkkkkkk...",
]


def png(rows, path):
    raw = b"".join(b"\x00" + b"".join(bytes(PALETA[c]) for c in row) for row in rows)
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data))
    w, h = len(rows[0]), len(rows)
    data = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b""))
    Path(path).write_bytes(data)


if __name__ == "__main__":
    raiz = Path(__file__).resolve().parent.parent
    png(DIBUJO, sys.argv[1] if len(sys.argv) > 1 else
        raiz / "resourcepack/assets/mccorp/textures/item/resources/gasolina.png")
