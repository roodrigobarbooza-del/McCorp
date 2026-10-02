"""Armado de modelos de item (formato JSON de Minecraft) desde cajas en el
sistema local de los vehiculos, con textura pintada por pixel.

Sistema local (el mismo de Shape en el plugin): +Z es el frente del
vehiculo, +X su izquierda, +Y arriba, medidas en bloques.

Un ItemDisplay dibuja el modelo con su centro (8, 8, 8) en el origen de la
entidad y girado 180 grados en Y. Por eso el frente del vehiculo (+Z local)
va al lado norte (-Z) del modelo y la izquierda (+X local) al oeste (-X).

Cada cara de cada caja tiene su propio pedazo de textura a 16 pixeles por
bloque (como los bloques de Minecraft) y se pinta con una funcion que recibe
el punto local de cada pixel: asi una puerta, una franja o un faro se pintan
donde van aunque crucen varias cajas.
"""
import hashlib
import json
import math
import os

from png import Image

D = 16  # pixeles por bloque

# cara local -> cara del modelo
MC_FACE = {'front': 'north', 'back': 'south', 'left': 'west', 'right': 'east', 'up': 'up', 'down': 'down'}
NORMAL = {'front': (0, 0, 1), 'back': (0, 0, -1), 'left': (1, 0, 0), 'right': (-1, 0, 0),
          'up': (0, 1, 0), 'down': (0, -1, 0)}
ALL = tuple(MC_FACE)


# ---------------------------------------------------------------- utilidades

def rotate(p, rot):
    """Gira p (local) con rot = (eje, grados, origen), regla de la mano derecha."""
    if not rot:
        return p
    axis, deg, o = rot
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    if axis == 'x':
        y, z = y * c - z * s, y * s + z * c
    elif axis == 'y':
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


def rotate_vec(v, rot):
    if not rot:
        return v
    return rotate(v, (rot[0], rot[1], (0, 0, 0)))


def hsh(*args):
    return int.from_bytes(hashlib.md5(repr(args).encode()).digest()[:4], 'little') / 2 ** 32


def clamp(c):
    return tuple(max(0, min(255, int(round(v)))) for v in c)


def mul(c, f):
    return clamp((c[0] * f, c[1] * f, c[2] * f))


def mix(a, b, t):
    return clamp((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t))


def grain(c, p, amt=6, key='g'):
    """Ruido fijo por pixel del mundo: dos caras en el mismo lugar se pintan igual."""
    n = hsh(key, round(p[0] * D - 0.5), round(p[1] * D - 0.5), round(p[2] * D - 0.5))
    d = (n - 0.5) * 2 * amt
    return clamp((c[0] + d, c[1] + d, c[2] + d))


def face_uv(p, face):
    """Coordenada horizontal de la cara (crece hacia la derecha de quien la mira) y vertical (y)."""
    if face == 'front':
        return p[0], p[1]
    if face == 'back':
        return -p[0], p[1]
    if face == 'left':
        return -p[2], p[1]
    if face == 'right':
        return p[2], p[1]
    if face == 'up':
        return -p[0], -p[2]
    return -p[0], p[2]


# --------------------------------------------------------------------- caja

class Box:
    def __init__(self, a, b, paint, faces=ALL, rot=None, name=''):
        self.lo = tuple(min(a[i], b[i]) for i in range(3))
        self.hi = tuple(max(a[i], b[i]) for i in range(3))
        self.paint = paint
        self.faces = faces
        self.rot = rot
        self.name = name
        self.rects = {}

    def face_size(self, face):
        sx, sy, sz = (self.hi[i] - self.lo[i] for i in range(3))
        if face in ('front', 'back'):
            return sx, sy
        if face in ('left', 'right'):
            return sz, sy
        return sx, sz

    def face_point(self, face, u, v):
        """Punto local (sin girar) de la cara a u bloques de su borde izquierdo y v de su borde de arriba."""
        (x0, y0, z0), (x1, y1, z1) = self.lo, self.hi
        if face == 'front':
            return (x0 + u, y1 - v, z1)
        if face == 'back':
            return (x1 - u, y1 - v, z0)
        if face == 'left':
            return (x1, y1 - v, z1 - u)
        if face == 'right':
            return (x0, y1 - v, z0 + u)
        if face == 'up':
            return (x1 - u, y1, z1 - v)
        return (x1 - u, y0, z0 + v)


class Model:
    """Un modelo: cajas en bloques locales; pivot es el punto local que queda en
    el centro del modelo y scale la escala con la que el plugin lo dibuja."""

    def __init__(self, name, pivot=(0, 0, 0), scale=1.0):
        self.name = name
        self.pivot = pivot
        self.scale = scale
        self.boxes = []

    @property
    def units(self):
        return 16.0 / self.scale

    def box(self, a, b, paint, faces=ALL, rot=None, name=''):
        bx = Box(a, b, paint, faces, rot, name)
        self.boxes.append(bx)
        return bx

    def mirror(self, a, b, paint, faces=ALL, rot=None, name=''):
        """La caja a la izquierda (+X) y su espejo a la derecha."""
        self.box(a, b, paint, faces, rot, name)
        fa = (-a[0], a[1], a[2])
        fb = (-b[0], b[1], b[2])
        mf = tuple({'left': 'right', 'right': 'left'}.get(f, f) for f in faces)
        mrot = None
        if rot:
            axis, deg, o = rot
            mrot = (axis, deg if axis == 'x' else -deg, (-o[0], o[1], o[2]))
        self.box(fa, fb, paint, mf, mrot, name)

    def octagon(self, center, diameter, axis, lo, hi, paint, faces=ALL, name=''):
        """Prisma octogonal (cuatro barras, dos giradas 45 grados) sobre el eje x o z."""
        w = diameter * math.tan(math.radians(22.5)) / 2
        r = diameter / 2
        cx, cy, cz = center
        if axis == 'x':
            bars = [((lo, cy - w, cz - r), (hi, cy + w, cz + r), None),
                    ((lo, cy - r, cz - w), (hi, cy + r, cz + w), None),
                    ((lo, cy - w, cz - r), (hi, cy + w, cz + r), ('x', 45, center)),
                    ((lo, cy - w, cz - r), (hi, cy + w, cz + r), ('x', -45, center))]
        else:
            bars = [((cx - r, cy - w, lo), (cx + r, cy + w, hi), None),
                    ((cx - w, cy - r, lo), (cx + w, cy + r, hi), None),
                    ((cx - r, cy - w, lo), (cx + r, cy + w, hi), ('z', 45, center)),
                    ((cx - r, cy - w, lo), (cx + r, cy + w, hi), ('z', -45, center))]
        for a, b, rot in bars:
            self.box(a, b, paint, faces, rot, name)

    # ------------------------------------------------------------ exportar

    def to_model(self, p):
        px, py, pz = self.pivot
        u = self.units
        return (8 - (p[0] - px) * u, 8 + (p[1] - py) * u, 8 - (p[2] - pz) * u)

    def bake(self, texture_ref):
        """Pinta la textura y arma el JSON del modelo."""
        rects = []
        for bi, bx in enumerate(self.boxes):
            for face in bx.faces:
                w, h = bx.face_size(face)
                pw, ph = max(1, int(round(w * D))), max(1, int(round(h * D)))
                rects.append((bi, face, pw, ph))
        size, placed = pack(rects)
        img = Image(size, size)
        for (bi, face, pw, ph), (x, y) in placed.items():
            bx = self.boxes[bi]
            w, h = bx.face_size(face)
            for j in range(ph):
                for i in range(pw):
                    p = rotate(bx.face_point(face, (i + 0.5) / pw * w, (j + 0.5) / ph * h), bx.rot)
                    c = bx.paint(p, face)
                    img.set(x + i, y + j, c)
            # Borde de 1 pixel copiado para que no se mezcle con la cara vecina.
            for i in range(-1, pw + 1):
                for j in range(-1, ph + 1):
                    if 0 <= i < pw and 0 <= j < ph:
                        continue
                    ci, cj = min(max(i, 0), pw - 1), min(max(j, 0), ph - 1)
                    img.set(x + i, y + j, img.get(x + ci, y + cj))
            bx.rects[face] = (x, y, pw, ph)

        elements = []
        for bx in self.boxes:
            a = self.to_model(bx.lo)
            b = self.to_model(bx.hi)
            frm = [round(min(a[i], b[i]), 4) for i in range(3)]
            to = [round(max(a[i], b[i]), 4) for i in range(3)]
            for v in frm + to:
                if v < -16 or v > 32:
                    raise ValueError(f'{self.name}: caja {bx.name or bx.lo} fuera del limite del modelo ({v})')
            el = {'from': frm, 'to': to, 'faces': {}}
            if bx.name:
                el['name'] = bx.name
            if bx.rot:
                axis, deg, o = bx.rot
                if deg not in (-45, -22.5, 22.5, 45):
                    raise ValueError(f'{self.name}: angulo {deg} no permitido')
                # local -> modelo es un giro de 180 en Y: invierte el sentido en X y Z.
                el['rotation'] = {'angle': deg if axis == 'y' else -deg, 'axis': axis,
                                  'origin': [round(v, 4) for v in self.to_model(o)]}
            for face, (x, y, pw, ph) in bx.rects.items():
                k = 16.0 / size
                el['faces'][MC_FACE[face]] = {
                    'uv': [round(x * k, 4), round(y * k, 4), round((x + pw) * k, 4), round((y + ph) * k, 4)],
                    'texture': '#t'}
            elements.append(el)
        model = {'textures': {'t': texture_ref, 'particle': texture_ref}, 'elements': elements}
        return model, img


def pack(rects):
    """Acomoda rectangulos (con 1 pixel de margen) en la textura cuadrada mas chica posible."""
    order = sorted(rects, key=lambda r: (-r[3], -r[2]))
    size = 64
    while True:
        placed = {}
        x = y = row = 0
        ok = True
        for r in order:
            pw, ph = r[2] + 2, r[3] + 2
            if pw > size:
                ok = False
                break
            if x + pw > size:
                x, y, row = 0, y + row, 0
            if y + ph > size:
                ok = False
                break
            placed[r] = (x + 1, y + 1)
            x += pw
            row = max(row, ph)
        if ok:
            return size, placed
        size *= 2


def write_model(root, model, item_id, texture_dir='item/vehicles'):
    """Escribe items/, models/ y textures/ de un modelo bajo assets/mccorp."""
    ref = f'mccorp:{texture_dir}/{model.name}'
    data, img = model.bake(ref)
    base = os.path.join(root, 'assets', 'mccorp')
    paths = {
        'model': os.path.join(base, 'models', 'item', 'vehicles', model.name + '.json'),
        'texture': os.path.join(base, 'textures', texture_dir, model.name + '.png'),
        'item': os.path.join(base, 'items', item_id + '.json'),
    }
    for p in paths.values():
        os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(paths['model'], 'w') as f:
        json.dump(data, f, separators=(',', ':'))
    img.save(paths['texture'])
    with open(paths['item'], 'w') as f:
        json.dump({'model': {'type': 'minecraft:model', 'model': f'mccorp:item/vehicles/{model.name}'}}, f, indent=2)
        f.write('\n')
    return data, img
