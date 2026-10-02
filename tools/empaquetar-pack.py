"""Arma el resource pack de McCorp en un zip y calcula su SHA-1.

    python tools/empaquetar-pack.py            -> build/mccorp-pack.zip
    python tools/empaquetar-pack.py otro.zip   -> donde digas

El zip sale igual byte a byte si no cambia ningun archivo (orden y fechas
fijas), asi el SHA-1 solo cambia cuando cambia el pack de verdad y los
jugadores no lo vuelven a bajar sin motivo. Se saltean .gitkeep y los .md.
"""
import hashlib
import sys
import zipfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
PACK = RAIZ / "resourcepack"
IGNORAR = {".gitkeep"}


def archivos():
    for path in sorted(PACK.rglob("*")):
        if path.is_file() and path.name not in IGNORAR and path.suffix != ".md":
            yield path


def empaquetar(destino: Path) -> str:
    destino.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destino, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for path in archivos():
            info = zipfile.ZipInfo(path.relative_to(PACK).as_posix(), date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            zf.writestr(info, path.read_bytes())
    return hashlib.sha1(destino.read_bytes()).hexdigest()


if __name__ == "__main__":
    destino = Path(sys.argv[1]) if len(sys.argv) > 1 else RAIZ / "build" / "mccorp-pack.zip"
    sha1 = empaquetar(destino)
    (destino.parent / (destino.name + ".sha1")).write_text(sha1 + "\n")
    print(f"{destino}\nsha1: {sha1}")
