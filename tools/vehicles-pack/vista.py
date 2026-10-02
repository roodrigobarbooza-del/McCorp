"""Vista previa de los modelos: dibuja el JSON y la textura generados tal como
los interpreta Minecraft (caras, uv, giros de cajas y el giro de 180 grados
del ItemDisplay), en perspectiva y con sombreado simple. Solo para revisar."""
import math

from png import Image


def _corners(frm, to, face):
    x0, y0, z0 = frm
    x1, y1, z1 = to
    return {
        'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        'west': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        'east': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


def _rot(p, axis, deg, o):
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


def quads(model_json, texture, place):
    """Caras del modelo en coordenadas del vehiculo. place(q) lleva un punto
    del espacio del display (modelo centrado, /16, girado 180) al vehiculo."""
    out = []
    for el in model_json['elements']:
        for face, f in el['faces'].items():
            pts = _corners(el['from'], el['to'], face)
            if 'rotation' in el:
                r = el['rotation']
                pts = [_rot(p, r['axis'], r['angle'], r['origin']) for p in pts]
            # ItemDisplay: centro en el origen y 180 grados en Y.
            pts = [place(((8 - p[0]) / 16, (p[1] - 8) / 16, (8 - p[2]) / 16)) for p in pts]
            out.append((pts, f['uv'], texture))
    return out


def render(quad_list, eye, target, w=640, h=420, fov=40, bg=(150, 185, 215)):
    img = Image(w, h, bg + (255,))
    zbuf = [float('inf')] * (w * h)
    fwd = _norm(_sub(target, eye))
    right = _norm(_cross(fwd, (0, 1, 0)))
    up = _cross(right, fwd)
    f = 0.5 * h / math.tan(math.radians(fov) / 2)
    light = _norm((0.4, 1.0, 0.6))

    def proj(p):
        d = _sub(p, eye)
        z = _dot(d, fwd)
        return (w / 2 + _dot(d, right) * f / z, h / 2 - _dot(d, up) * f / z, z)

    # Piso a cuadros para dar escala (un cuadro = un bloque).
    for gx in range(-8, 8):
        for gz in range(-8, 8):
            c = (120, 120, 118) if (gx + gz) % 2 else (135, 135, 132)
            _fill(img, zbuf, [proj((gx, 0, gz)), proj((gx + 1, 0, gz)), proj((gx + 1, 0, gz + 1)), proj((gx, 0, gz + 1))],
                  None, None, c, 0.001)

    for pts, uv, tex in quad_list:
        n = _norm(_cross(_sub(pts[3], pts[0]), _sub(pts[1], pts[0])))
        # Caras de atras no se dibujan (Minecraft tampoco).
        if _dot(n, _sub(pts[0], eye)) >= 0:
            continue
        shade = 0.55 + 0.45 * max(0.0, _dot(n, light))
        _fill(img, zbuf, [proj(p) for p in pts], uv, tex, shade, 0)
    return img


def _fill(img, zbuf, sp, uv, tex, shade_or_color, bias):
    if any(p[2] <= 0.05 for p in sp):
        return
    w, h = img.w, img.h
    # uv de cada esquina: TL, TR, BR, BL
    if uv is not None:
        u0, v0, u1, v1 = uv
        uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
    for tri in ((0, 1, 2), (0, 2, 3)):
        a, b, c = (sp[i] for i in tri)
        minx, maxx = max(0, int(min(a[0], b[0], c[0]))), min(w - 1, int(max(a[0], b[0], c[0])) + 1)
        miny, maxy = max(0, int(min(a[1], b[1], c[1]))), min(h - 1, int(max(a[1], b[1], c[1])) + 1)
        area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
        if abs(area) < 1e-9:
            continue
        for y in range(miny, maxy + 1):
            for x in range(minx, maxx + 1):
                px, py = x + 0.5, y + 0.5
                w0 = ((b[0] - px) * (c[1] - py) - (b[1] - py) * (c[0] - px)) / area
                w1 = ((c[0] - px) * (a[1] - py) - (c[1] - py) * (a[0] - px)) / area
                w2 = 1 - w0 - w1
                if w0 < -1e-6 or w1 < -1e-6 or w2 < -1e-6:
                    continue
                # interpolacion correcta en perspectiva
                iz = w0 / a[2] + w1 / b[2] + w2 / c[2]
                z = 1 / iz + bias
                k = y * w + x
                if z >= zbuf[k]:
                    continue
                if uv is None:
                    col = shade_or_color
                else:
                    ua, ub, uc = (uvs[i] for i in tri)
                    tu = (w0 * ua[0] / a[2] + w1 * ub[0] / b[2] + w2 * uc[0] / c[2]) / iz
                    tv = (w0 * ua[1] / a[2] + w1 * ub[1] / b[2] + w2 * uc[1] / c[2]) / iz
                    tx = min(tex.w - 1, max(0, int(tu / 16 * tex.w)))
                    ty = min(tex.h - 1, max(0, int(tv / 16 * tex.h)))
                    t = tex.get(tx, ty)
                    if len(t) > 3 and t[3] < 128:
                        continue
                    col = (t[0] * shade_or_color, t[1] * shade_or_color, t[2] * shade_or_color)
                zbuf[k] = z
                img.set(x, y, (col[0], col[1], col[2], 255))


def _sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def _dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _norm(a):
    l = math.sqrt(_dot(a, a)) or 1
    return (a[0] / l, a[1] / l, a[2] / l)
