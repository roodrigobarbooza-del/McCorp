"""Revisa el resource pack antes de empaquetarlo (lo corre el CI).

- todos los .json y pack.mcmeta se pueden leer
- las rutas usan solo minusculas, numeros, _ - . y /
- cada definicion en assets/<ns>/items apunta a modelos que existen
- cada modelo apunta a texturas y padres que existen
Solo controla referencias al namespace mccorp; las de minecraft: son del juego.
"""
import json
import re
import sys
from pathlib import Path

PACK = Path(__file__).resolve().parent.parent / "resourcepack"
ASSETS = PACK / "assets"
RUTA_OK = re.compile(r"^[a-z0-9_.\-/]+$")
errores = []


def error(path, msg):
    errores.append(f"{path.relative_to(PACK)}: {msg}")


def leer(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, UnicodeDecodeError) as e:
        error(path, f"JSON invalido ({e})")
        return None


def separar(ref):
    ns, _, ruta = ref.partition(":") if ":" in ref else ("minecraft", ":", ref)
    return ns, ruta


def modelos_de(nodo):
    """Todos los "model" de tipo minecraft:model dentro de una definicion de item."""
    if isinstance(nodo, dict):
        if nodo.get("type") in ("minecraft:model", "model") and isinstance(nodo.get("model"), str):
            yield nodo["model"]
        for v in nodo.values():
            yield from modelos_de(v)
    elif isinstance(nodo, list):
        for v in nodo:
            yield from modelos_de(v)


def existe(ns, carpeta, ruta, ext):
    return (ASSETS / ns / carpeta / (ruta + ext)).is_file()


def main():
    if leer(PACK / "pack.mcmeta") is None:
        pass
    for path in sorted(ASSETS.rglob("*")):
        if not path.is_file() or path.name == ".gitkeep":
            continue
        rel = path.relative_to(ASSETS).as_posix()
        if not RUTA_OK.match(rel):
            error(path, "la ruta solo puede tener minusculas, numeros, _ - . y / (Minecraft no lo carga)")
        if path.suffix == ".json":
            leer(path)
    for ns_dir in ASSETS.iterdir():
        if not ns_dir.is_dir():
            continue
        for path in sorted((ns_dir / "items").rglob("*.json")):
            data = leer(path)
            if data is None:
                continue
            if "model" not in data:
                error(path, 'falta "model"')
            for ref in modelos_de(data.get("model")):
                ns, ruta = separar(ref)
                if ns == "mccorp" and not existe(ns, "models", ruta, ".json"):
                    error(path, f"el modelo {ref} no existe (assets/{ns}/models/{ruta}.json)")
        for path in sorted((ns_dir / "models").rglob("*.json")):
            data = leer(path)
            if data is None:
                continue
            parent = data.get("parent")
            if isinstance(parent, str):
                ns, ruta = separar(parent)
                if ns == "mccorp" and not existe(ns, "models", ruta, ".json"):
                    error(path, f"el padre {parent} no existe")
            for clave, ref in (data.get("textures") or {}).items():
                if not isinstance(ref, str) or ref.startswith("#"):
                    continue
                ns, ruta = separar(ref)
                if ns == "mccorp" and not existe(ns, "textures", ruta, ".png"):
                    error(path, f"la textura {ref} ({clave}) no existe (assets/{ns}/textures/{ruta}.png)")
    if errores:
        print("Problemas en el resource pack:")
        for e in errores:
            print("  - " + e)
        sys.exit(1)
    print("Resource pack OK")


if __name__ == "__main__":
    main()
