#!/usr/bin/env python3
"""Genera los modelos 3D y texturas de los vehiculos para el resource pack.

    python3 tools/vehicles-pack/generar.py [carpeta-del-pack] [--vista carpeta]

Escribe en <pack>/assets/mccorp/{items,models/item,textures/item}/vehicles/.
Con --vista ademas dibuja una imagen de cada vehiculo para revisarlo sin
abrir el juego.

Las medidas estan en bloques en el sistema local del plugin (+Z frente, +X
izquierda). Las posiciones de ruedas y punta, el pivot y la escala de cada
modelo tienen que coincidir con Shapes.java (se imprimen al final).

Referencias: el camion es un IFA W50 (cabina celeste adelantada, lona roja
con franja blanca), la camioneta un Trabant 601 de dos tonos con baca, y el
taladro una perforadora sobre orugas con el panel de control aparte, atras.
"""
import math
import os
import sys

from modelo import Model, grain, mix, mul, face_uv, write_model
import vista

HERE = os.path.dirname(os.path.abspath(__file__))


def frac(v):
    return v - math.floor(v)


def solid(c, amt=5, key='s'):
    return lambda p, f: grain(c, p, amt, key)


# ===================================================================== colores

DARK = (46, 47, 50)
DARKER = (30, 30, 32)
STEEL = (120, 123, 128)
CHROME = (205, 210, 214)
RUBBER = (36, 36, 38)
WHITE = (226, 224, 214)
ORANGE = (235, 140, 40)
RED_LIGHT = (200, 30, 30)
LAMP = (250, 248, 225)


def glass(tint=(50, 64, 78)):
    def paint(p, f):
        h, v = face_uv(p, f)
        t = frac(h * 0.8 + v * 0.55)
        c = tint
        if 0.06 < t < 0.15:
            c = mix(tint, (150, 170, 185), 0.55)
        elif 0.2 < t < 0.23:
            c = mix(tint, (150, 170, 185), 0.35)
        return grain(c, p, 3, 'gl')
    return paint


def headlight(cx, cy, r, housing=CHROME, lens=LAMP):
    """Faro redondo mirando al frente (o atras) centrado en (cx, cy)."""
    def paint(p, f):
        if f not in ('front', 'back'):
            return grain(housing, p, 4)
        d = math.hypot(abs(p[0]) - abs(cx), p[1] - cy)
        if d < r * 0.62:
            c = lens
            if math.hypot(abs(p[0]) - abs(cx) + r * 0.25, p[1] - cy - r * 0.25) < r * 0.2:
                c = (255, 255, 255)
            return grain(c, p, 3)
        if d < r * 0.9:
            return grain(CHROME, p, 3)
        return grain(mul(housing, 0.8), p, 3)
    return paint


def lamp(c, rim=DARK):
    def paint(p, f):
        if f in ('front', 'back'):
            return grain(c, p, 6)
        return grain(rim, p, 4)
    return paint


def plate():
    """Patente: blanca con trazos oscuros (a esta escala no se leen letras)."""
    def paint(p, f):
        if f not in ('front', 'back'):
            return grain(DARK, p, 3)
        h, v = face_uv(p, f)
        c = (60, 62, 80) if int(math.floor(h * 16)) % 3 == 1 else (232, 232, 226)
        return grain(c, p, 3)
    return paint


# ================================================================== ruedas

def rueda(name, rim_color, hub_color, holes):
    """Rueda de diametro 1 sobre el eje X, centrada en el origen. El plugin la escala al diametro real."""
    m = Model(name, pivot=(0, 0, 0), scale=1.0)

    def tire(p, f):
        x, y, z = p
        r = math.hypot(y, z)
        if f in ('left', 'right'):
            c = RUBBER
            if 0.4 < r < 0.43:
                c = (52, 52, 55)
            return grain(c, p, 4, 'tire')
        ang = math.atan2(y, z)
        c = RUBBER
        if frac(ang / (2 * math.pi) * 20) < 0.45 and abs(x) > 0.035:
            c = (24, 24, 26)
        return grain(c, p, 3, 'tread')

    def rim(p, f):
        x, y, z = p
        r = math.hypot(y, z)
        if f not in ('left', 'right'):
            return grain(mul(rim_color, 0.85), p, 3)
        if r > 0.3:
            return grain(mul(rim_color, 0.75), p, 3)
        if holes:
            for k in range(holes):
                a = 2 * math.pi * k / holes
                if math.hypot(y - 0.21 * math.sin(a), z - 0.21 * math.cos(a)) < 0.045:
                    return grain(DARKER, p, 3)
        if r > 0.27:
            return grain(mul(rim_color, 0.9), p, 3)
        return grain(rim_color, p, 4, 'rim')

    def hub(p, f):
        x, y, z = p
        if f not in ('left', 'right'):
            return grain(mul(hub_color, 0.8), p, 3)
        r = math.hypot(y, z)
        if r < 0.04:
            return grain(mul(hub_color, 0.6), p, 3)
        for k in range(5):
            a = 2 * math.pi * k / 5
            if math.hypot(y - 0.075 * math.sin(a), z - 0.075 * math.cos(a)) < 0.022:
                return grain(DARK, p, 3)
        return grain(hub_color, p, 4)

    m.octagon((0, 0, 0), 1.0, 'x', -0.1875, 0.1875, tire, name='cubierta')
    m.octagon((0, 0, 0), 0.66, 'x', -0.2, 0.2, rim, name='llanta')
    m.box((-0.215, -0.13, -0.13), (0.215, 0.13, 0.13), hub, name='maza')
    return m


# ===================================================================== punta

def punta():
    """Cabezal del taladro sobre el eje Z, centrado en el origen (la cara del disco en z=0)."""
    m = Model('taladro_punta', pivot=(0, 0, 0.45), scale=1.0)

    def disc(p, f):
        x, y, z = p
        r = math.hypot(x, y)
        if f == 'front':
            ang = math.atan2(y, x)
            c = (128, 130, 134)
            if frac(ang / (2 * math.pi) * 8 + 0.06) < 0.12 and r > 0.6:
                c = (60, 60, 64)
            if 1.0 < r < 1.04:
                c = (95, 96, 100)
            return grain(c, p, 6, 'disc')
        return grain((105, 107, 112), p, 5, 'disc')

    def cone(p, f):
        x, y, z = p
        ang = math.atan2(y, x)
        t = frac(ang / (2 * math.pi) * 3 + z * 1.8)
        c = (150, 152, 156) if t < 0.62 else (88, 90, 94)
        return grain(c, p, 6, 'cone')

    def tooth(p, f):
        c = (60, 60, 66)
        if f == 'front':
            c = (200, 205, 210)
        return grain(c, p, 4)

    m.octagon((0, 0, 0), 2.3, 'z', -0.3, 0.0, disc, name='disco')
    m.octagon((0, 0, 0), 1.65, 'z', 0.0, 0.3, cone, name='cono-1')
    m.octagon((0, 0, 0), 1.1, 'z', 0.3, 0.6, cone, name='cono-2')
    m.octagon((0, 0, 0), 0.6, 'z', 0.6, 0.85, cone, name='cono-3')
    m.box((-0.12, -0.12, 0.85), (0.12, 0.12, 1.1), tooth, name='punta')
    # Dientes de widia en el borde del disco, cada 45 grados.
    for a, b, rot in (((-0.08, 0.86, 0), (0.08, 1.06, 0.18), None),
                      ((-0.08, -1.06, 0), (0.08, -0.86, 0.18), None),
                      ((0.86, -0.08, 0), (1.06, 0.08, 0.18), None),
                      ((-1.06, -0.08, 0), (-0.86, 0.08, 0.18), None),
                      ((-0.08, 0.86, 0), (0.08, 1.06, 0.18), ('z', 45, (0, 0, 0))),
                      ((-0.08, 0.86, 0), (0.08, 1.06, 0.18), ('z', -45, (0, 0, 0))),
                      ((-0.08, -1.06, 0), (0.08, -0.86, 0.18), ('z', 45, (0, 0, 0))),
                      ((-0.08, -1.06, 0), (0.08, -0.86, 0.18), ('z', -45, (0, 0, 0)))):
        m.box(a, b, tooth, rot=rot, name='diente')
    return m


# ==================================================================== camion

IFA_BLUE = (132, 168, 186)
IFA_BLUE_DARK = (96, 126, 142)
TARP_RED = (164, 40, 36)


def camion():
    """IFA W50: cabina adelantada celeste, paragolpes negro con faros redondos y caja con lona roja."""
    m = Model('camion', pivot=(0, 1.75, 0.05), scale=2.5)

    def cab(p, f):
        x, y, z = p
        c = IFA_BLUE
        if f in ('left', 'right'):
            # Puerta: costuras adelante y atras, manija.
            if abs(z - 1.78) < 0.03 or abs(z - 3.22) < 0.03 or (abs(y - 1.36) < 0.03 and 1.78 < z < 3.22):
                c = IFA_BLUE_DARK
            elif 1.9 < z < 2.12 and 2.02 < y < 2.09:
                c = CHROME
        elif f == 'front':
            # Separacion entre frente y laterales, mas la lineas del capot bajo el parabrisas.
            if abs(y - 2.17) < 0.025:
                c = IFA_BLUE_DARK
        elif f == 'back':
            if 2.35 < y < 2.8 and abs(x) < 0.5:
                return glass()(p, f)
        return grain(c, p, 5, 'cab')

    def roof(p, f):
        c = mix(IFA_BLUE, WHITE, 0.25)
        return grain(c, p, 4, 'roof')

    def grille(p, f):
        x, y, z = p
        if f != 'front':
            return grain(IFA_BLUE_DARK, p, 4)
        if abs(x) > 0.57 or y > 2.07 or y < 1.4:
            return grain(mix(IFA_BLUE, CHROME, 0.4), p, 3)
        # Rombo de IFA arriba al centro.
        if abs(x) / 0.13 + abs(y - 1.88) / 0.15 < 1:
            if abs(x) / 0.13 + abs(y - 1.88) / 0.15 > 0.6:
                return grain(CHROME, p, 3)
            return grain(DARK, p, 3)
        if y > 1.7:
            return grain(DARK if frac(x * 16 / 2) < 0.5 else (150, 160, 165), p, 3)
        if abs(y - 1.68) < 0.03:
            return grain(mix(IFA_BLUE, CHROME, 0.4), p, 3)
        return grain(DARK if frac(y * 16 / 2) < 0.5 else (130, 138, 142), p, 3)

    def bumper(p, f):
        x, y, z = p
        c = (40, 41, 44)
        if f == 'front' and abs(y - 0.95) < 0.02:
            c = (60, 61, 64)
        return grain(c, p, 4, 'bump')

    def tarp(p, f):
        x, y, z = p
        h, v = face_uv(p, f)
        c = TARP_RED
        if f in ('left', 'right', 'back', 'front') and y < 2.1:
            c = WHITE
            if y < 1.96 and frac(h / 0.5) < 0.08:
                c = DARK  # sogas de la lona
        else:
            c = mul(c, 0.94 + 0.06 * math.sin(h * 2 * math.pi / 0.6))
            if f == 'back' and abs(x) < 0.03:
                c = mul(TARP_RED, 0.6)
            if f == 'back' and abs(abs(x) - 0.7) < 0.04 and y > 2.1:
                c = DARK
        return grain(c, p, 5, 'tarp')

    def dropside(p, f):
        x, y, z = p
        h, v = face_uv(p, f)
        c = (78, 80, 82)
        if f in ('left', 'right', 'back'):
            if frac(h / 0.65) < 0.07:
                c = (58, 60, 62)
            if abs(y - 1.58) < 0.02:
                c = (95, 97, 99)
        return grain(c, p, 5, 'drop')

    blue = cab
    dark = solid(DARK, 4, 'dark')

    # --- chasis
    m.box((-0.65, 0.55, -3.45), (0.65, 0.85, 3.3), dark, name='chasis')
    m.box((-0.8, 0.52, 2.2), (0.8, 0.7, 2.4), dark, name='eje-delantero')
    m.box((-0.8, 0.52, -2.0), (0.8, 0.72, -1.8), dark, name='eje-trasero')
    m.box((-0.22, 0.38, -2.12), (0.22, 0.82, -1.68), dark, name='diferencial')

    # --- cabina
    m.box((-1.3, 1.12, 1.52), (1.3, 1.3, 3.45), dark, name='guardabarros')
    m.box((-1.28, 1.3, 1.55), (1.28, 2.2, 3.45), blue, name='cabina')
    m.box((-1.24, 2.2, 1.6), (1.24, 3.0, 3.36), glass(), name='vidrios')
    m.box((-1.18, 2.22, 3.36), (1.18, 2.98, 3.42), glass(), faces=('front',), name='parabrisas')
    m.box((-0.04, 2.2, 3.34), (0.04, 3.0, 3.44), blue, name='parante-centro')
    m.mirror((1.17, 2.2, 3.24), (1.28, 3.0, 3.45), blue, name='parante-a')
    m.mirror((1.22, 2.2, 1.55), (1.28, 3.0, 1.8), blue, name='parante-b')
    m.mirror((1.24, 2.2, 3.17), (1.28, 3.0, 3.24), blue, name='marco-puerta')
    m.box((-1.28, 2.2, 1.55), (1.28, 3.0, 1.62), blue, faces=('back',), name='cabina-atras')
    m.box((-1.3, 3.0, 1.52), (1.3, 3.2, 3.47), roof, name='techo')
    m.box((-1.18, 3.2, 1.62), (1.18, 3.27, 3.37), roof, name='techo-alto')
    m.box((-0.62, 1.35, 3.45), (0.62, 2.12, 3.5), grille, name='parrilla')
    m.mirror((0.82, 1.48, 3.45), (1.1, 1.6, 3.49), lamp(ORANGE), name='giro')
    m.box((-1.3, 0.78, 3.38), (1.3, 1.12, 3.62), bumper, name='paragolpes')
    m.mirror((0.96, 1.12, 3.42), (1.22, 1.4, 3.6), headlight(1.09, 1.26, 0.13), name='faro')
    m.mirror((0.64, 1.12, 3.42), (0.88, 1.38, 3.58), headlight(0.76, 1.25, 0.12), name='faro-2')
    m.box((-0.32, 0.84, 3.62), (0.32, 1.02, 3.64), plate(), name='patente')
    m.mirror((1.28, 2.6, 3.24), (1.52, 2.65, 3.3), dark, name='espejo-brazo')
    m.mirror((1.48, 2.22, 3.16), (1.55, 2.85, 3.36), dark, name='espejo')
    m.mirror((1.12, 0.82, 1.32), (1.32, 0.9, 1.62), solid(STEEL), name='estribo')

    # --- entre cabina y caja
    m.box((-1.12, 1.3, 1.3), (-1.0, 3.3, 1.42), solid((60, 60, 62)), name='escape')
    m.box((-1.14, 3.3, 1.28), (-0.98, 3.36, 1.44), dark, name='escape-boca')
    m.box((0.85, 0.6, 0.25), (1.22, 1.05, 1.2), solid((150, 152, 150), 4), name='tanque')
    m.box((-1.2, 0.6, 0.4), (-0.85, 0.95, 1.0), dark, name='bateria')

    # --- caja
    m.box((-1.3, 1.3, -3.45), (1.3, 1.9, 1.3), dropside, name='caja')
    m.box((-1.27, 1.9, -3.42), (1.27, 3.42, 1.27), tarp, name='lona')
    m.box((-1.15, 3.42, -3.35), (1.15, 3.5, 1.2), tarp, name='lona-techo')
    m.mirror((0.75, 1.2, -2.62), (1.3, 1.3, -1.18), dark, name='guardabarros-tras')
    m.mirror((0.8, 0.42, -2.66), (1.25, 1.2, -2.6), solid(DARKER, 3), name='barrero')
    m.box((-1.0, 0.75, -3.56), (1.0, 0.95, -3.42), dark, name='paragolpes-tras')
    m.mirror((0.7, 0.95, -3.56), (1.02, 1.15, -3.44), lamp(RED_LIGHT), name='luz-trasera')
    m.box((-0.3, 0.77, -3.58), (0.3, 0.93, -3.56), plate(), name='patente-tras')
    return m


CAMION_RUEDAS = [(1.02, 0.62, 2.3, 1.24, True), (1.02, 0.62, -1.9, 1.24, False)]


# ================================================================= camioneta

TRAB_RED = (172, 46, 40)
TRAB_WHITE = (228, 224, 210)


def camioneta():
    """Trabant 601 de dos tonos con baca en el techo."""
    m = Model('camioneta', pivot=(0, 1.1, 0), scale=1.75)

    def seams(p, f, c, dark):
        x, y, z = p
        if f in ('left', 'right'):
            if (abs(z - 1.02) < 0.025 or abs(z + 0.55) < 0.025) and y < 1.12:
                return dark
            if -0.42 < z < -0.25 and 0.98 < y < 1.03:
                return CHROME
        return c

    def red(p, f):
        x, y, z = p
        c = seams(p, f, TRAB_RED, mul(TRAB_RED, 0.62))
        if f == 'up' and abs(x) < 0.02 and z > 1.0:
            c = mul(TRAB_RED, 0.85)
        return grain(c, p, 5, 'red')

    def white(p, f):
        c = seams(p, f, TRAB_WHITE, mul(TRAB_WHITE, 0.65))
        return grain(c, p, 4, 'white')

    def grille(p, f):
        x, y, z = p
        if f != 'front':
            return grain(CHROME, p, 3)
        if abs(x) < 0.06 and abs(y - 0.78) < 0.06:
            return grain(TRAB_RED, p, 3)  # escudo
        c = CHROME if frac(y * 16 / 2) < 0.5 else (70, 72, 76)
        return grain(c, p, 3)

    def bumper(p, f):
        x, y, z = p
        c = CHROME
        if abs(y - 0.37) < 0.025:
            c = (55, 55, 58)
        return grain(c, p, 4, 'chr')

    chrome = solid(CHROME, 4, 'chr')
    dark = solid(DARK, 4, 'dark')

    # --- piso y huecos de las ruedas
    m.box((-0.72, 0.22, -2.1), (0.72, 0.32, 2.1), dark, name='piso')
    m.mirror((0.4, 0.25, 1.0), (0.72, 0.86, 1.92), solid(DARKER, 3), name='hueco-del')
    m.mirror((0.4, 0.25, -1.97), (0.72, 0.86, -1.05), solid(DARKER, 3), name='hueco-tras')

    # --- carroceria: banda blanca abajo (cortada en las ruedas), roja arriba
    m.box((-1.0, 0.3, 1.92), (1.0, 0.78, 2.25), white, name='trompa')
    m.box((-1.0, 0.28, -1.05), (1.0, 0.78, 1.0), white, name='zocalo')
    m.box((-1.0, 0.3, -2.25), (1.0, 0.78, -1.97), white, name='cola')
    m.mirror((0.72, 0.62, 1.0), (1.0, 0.78, 1.92), white, name='arco-del')
    m.mirror((0.72, 0.62, -1.97), (1.0, 0.78, -1.05), white, name='arco-tras')
    m.box((-1.0, 0.78, -2.25), (1.0, 1.12, 2.25), red, name='cuerpo')
    m.box((-1.012, 0.765, -2.2), (1.012, 0.795, 2.2), chrome, faces=('left', 'right'), name='moldura')

    # --- frente
    m.box((-0.55, 0.58, 2.24), (0.55, 0.95, 2.29), grille, name='parrilla')
    m.mirror((0.6, 0.72, 2.23), (0.94, 1.06, 2.29), headlight(0.77, 0.89, 0.17, housing=TRAB_RED), name='faro')
    m.mirror((0.66, 0.5, 2.24), (0.9, 0.6, 2.29), lamp(ORANGE, CHROME), name='giro')
    m.box((-1.03, 0.3, 2.22), (1.03, 0.46, 2.38), bumper, name='paragolpes')
    m.mirror((0.93, 0.3, 1.98), (1.04, 0.46, 2.3), bumper, name='paragolpes-punta')
    m.box((-0.28, 0.31, 2.38), (0.28, 0.44, 2.4), plate(), name='patente')

    # --- cabina (vidrios y parantes blancos)
    m.box((-0.93, 1.12, -1.1), (0.93, 1.78, 0.52), glass(), name='vidrios')
    rot_front = ('x', -22.5, (0, 1.12, 0.8))
    m.box((-0.93, 1.12, 0.4), (0.93, 1.84, 0.8), glass(), rot=rot_front, name='parabrisas')
    m.mirror((0.9, 1.12, 0.72), (0.955, 1.84, 0.82), white, rot=rot_front, name='parante-a')
    rot_back = ('x', 22.5, (0, 1.12, -1.42))
    m.box((-0.93, 1.12, -1.42), (0.93, 1.82, -1.02), glass(), rot=rot_back, name='luneta')
    m.mirror((0.9, 1.12, -1.44), (0.955, 1.82, -1.34), white, rot=rot_back, name='parante-c')
    m.mirror((0.92, 1.12, -0.6), (0.96, 1.78, -0.5), white, name='parante-b')
    m.box((-0.96, 1.78, -1.16), (0.96, 1.9, 0.56), white, name='techo')

    # --- baca
    for z in (0.42, -0.98):
        m.box((-0.78, 1.98, z), (0.78, 2.03, z + 0.05), chrome, name='baca-travesano')
    m.mirror((0.73, 1.98, -0.98), (0.78, 2.03, 0.47), chrome, name='baca-larguero')
    for z in (0.1, -0.28, -0.66):
        m.box((-0.73, 1.99, z), (0.73, 2.02, z + 0.05), chrome, name='baca-tabla')
    for z in (0.36, -0.92):
        m.mirror((0.73, 1.9, z), (0.78, 2.16, z + 0.05), chrome, name='baca-pata')
    m.mirror((0.73, 2.12, -0.98), (0.78, 2.16, 0.47), chrome, name='baca-baranda')
    m.box((-0.78, 2.12, 0.42), (0.78, 2.16, 0.47), chrome, name='baca-baranda-frente')
    m.box((-0.78, 2.12, -0.98), (0.78, 2.16, -0.93), chrome, name='baca-baranda-atras')

    # --- cola
    m.mirror((0.58, 0.8, -2.29), (0.95, 1.07, -2.22), lamp(RED_LIGHT, CHROME), name='luz-trasera')
    m.box((-1.03, 0.3, -2.38), (1.03, 0.46, -2.22), bumper, name='paragolpes-tras')
    m.box((-0.28, 0.48, -2.27), (0.28, 0.62, -2.25), plate(), name='patente-tras')
    m.box((-0.78, 0.24, -2.45), (-0.68, 0.32, -2.1), dark, name='escape')

    # --- espejos
    m.mirror((1.0, 1.12, 0.66), (1.12, 1.18, 0.72), white, name='espejo-brazo')
    m.mirror((1.08, 1.15, 0.6), (1.16, 1.3, 0.74), chrome, name='espejo')
    return m


CAMIONETA_RUEDAS = [(0.86, 0.41, 1.46, 0.82, True), (0.86, 0.41, -1.51, 0.82, False)]


# ===================================================================== taladro

YELLOW = (228, 176, 34)
YELLOW_DARK = (170, 128, 22)


def taladro():
    """Perforadora sobre orugas: cabina a la izquierda, motor atras, cabezal
    adelante (la punta es otro modelo que gira) y el panel de control en un
    pedestal aparte, detras, enganchado con una barra."""
    m = Model('taladro', pivot=(0, 1.45, -0.6), scale=1.6)

    def yellow(p, f):
        x, y, z = p
        c = YELLOW
        if f in ('left', 'right') and abs(y - 1.15) < 0.02:
            c = YELLOW_DARK
        return grain(c, p, 6, 'yel')

    def hazard(p, f):
        h, v = face_uv(p, f)
        c = YELLOW if frac((h + v) / 0.3) < 0.5 else (35, 35, 35)
        return grain(c, p, 5, 'haz')

    def track(p, f):
        x, y, z = p
        h, v = face_uv(p, f)
        c = (44, 44, 46)
        if f in ('left', 'right'):
            # Ruedas de la oruga vistas de costado.
            for wz in (-1.45, -0.75, -0.05, 0.65):
                d = math.hypot(z - wz, y - 0.4)
                if d < 0.24:
                    c = (95, 96, 98) if d > 0.08 else (60, 60, 62)
                    return grain(c, p, 4, 'trk')
            if y > 0.75 or y < 0.1:
                c = (30, 30, 32) if frac(z / 0.18) < 0.5 else (52, 52, 54)
            return grain(c, p, 4, 'trk')
        if frac((z if f in ('up', 'down') else y) / 0.18) < 0.4:
            c = (28, 28, 30)
        return grain(c, p, 4, 'trk')

    def engine(p, f):
        x, y, z = p
        c = YELLOW
        if f in ('left', 'right') and -1.85 < z < -0.95 and 1.35 < y < 2.0:
            c = (40, 40, 42) if frac(y / 0.12) < 0.45 else YELLOW_DARK  # rejillas
        if f == 'back' and abs(x) < 0.85 and 1.3 < y < 2.05:
            c = (40, 40, 42) if frac(x / 0.1) < 0.5 else (70, 70, 72)  # radiador
        if f == 'up' and abs(x) < 0.6 and -1.7 < z < -1.1:
            c = (50, 50, 52) if frac(z / 0.1) < 0.5 else YELLOW_DARK
        return grain(c, p, 6, 'yel')

    def steel(p, f):
        x, y, z = p
        c = (112, 115, 120)
        if f == 'front' and frac(math.atan2(y - 1.35, x) / (2 * math.pi) * 12) < 0.1 and \
                0.75 < math.hypot(x, y - 1.35) < 0.85:
            c = (70, 70, 74)  # bulones
        return grain(c, p, 6, 'stl')

    def console(p, f):
        x, y, z = p
        if f != 'back':
            return yellow(p, f)
        h, v = face_uv(p, f)
        # Pantalla verde arriba, botones y palancas abajo.
        if 1.24 < y < 1.52 and abs(x) < 0.38:
            if abs(x) > 0.35 or y > 1.49 or y < 1.27:
                return grain((25, 25, 28), p, 3)
            line = frac((y - 1.27) / 0.06)
            col = (40, 200, 90) if line < 0.35 and frac(h * 3.1) < 0.7 else (18, 50, 30)
            return grain(col, p, 3)
        if 0.98 < y < 1.18 and abs(x) < 0.4:
            for bx, col in ((-0.28, (210, 40, 40)), (-0.12, (40, 190, 60)), (0.04, (240, 200, 40))):
                if math.hypot(h - bx, y - 1.08) < 0.05:
                    return grain(col, p, 3)
            if 0.18 < h < 0.36 and abs(frac((h - 0.18) / 0.06) - 0.5) < 0.2:
                return grain((25, 25, 28), p, 3)
            return grain((70, 72, 76), p, 3)
        return yellow(p, f)

    dark = solid(DARK, 4, 'dark')

    # --- orugas
    m.mirror((0.86, 0.0, -1.9), (1.3, 0.85, 1.2), track, name='oruga')
    m.mirror((0.88, 0.12, -2.04), (1.28, 0.72, -1.9), track, name='oruga-atras')
    m.mirror((0.88, 0.12, 1.2), (1.28, 0.72, 1.34), track, name='oruga-frente')
    m.mirror((0.82, 0.85, -1.98), (1.32, 0.95, 1.32), yellow, name='guardabarros')

    # --- chasis y cubierta
    m.box((-0.86, 0.3, -1.95), (0.86, 0.95, 1.2), dark, name='chasis')
    m.box((-1.3, 0.95, -2.0), (1.3, 1.15, 1.3), yellow, name='cubierta')
    m.box((-1.3, 0.95, 1.3), (1.3, 1.15, 1.36), hazard, faces=('front', 'up', 'left', 'right'), name='borde-frente')
    m.box((-1.3, 0.95, -2.06), (1.3, 1.15, -2.0), hazard, faces=('back', 'up', 'left', 'right'), name='borde-atras')

    # --- motor atras
    m.box((-1.2, 1.15, -1.95), (1.2, 2.2, -0.75), engine, name='motor')
    m.box((-1.15, 2.2, -1.9), (1.15, 2.28, -0.8), yellow, name='motor-tapa')
    m.box((-0.98, 2.2, -1.3), (-0.8, 2.9, -1.12), solid((60, 60, 62)), name='escape')
    m.box((-1.0, 2.9, -1.32), (-0.78, 2.95, -1.1), dark, name='escape-boca')
    m.box((0.55, 2.28, -1.7), (0.85, 2.58, -1.4), solid((90, 92, 95)), name='filtro')

    # --- cabina del operador (izquierda)
    m.box((0.12, 1.15, -0.7), (1.25, 1.5, 0.65), yellow, name='cabina-base')
    m.box((0.17, 1.5, -0.65), (1.2, 2.6, 0.6), glass(), name='cabina-vidrios')
    for zz in (-0.7, 0.57):
        for xx in (0.12, 1.17):
            m.box((xx, 1.5, zz), (xx + 0.08, 2.6, zz + 0.08), yellow, name='cabina-parante')
    m.box((0.08, 2.6, -0.75), (1.3, 2.74, 0.7), yellow, name='cabina-techo')
    m.box((0.3, 2.62, 0.7), (0.5, 2.74, 0.76), lamp(LAMP), name='luz-trabajo')
    m.box((0.85, 2.62, 0.7), (1.05, 2.74, 0.76), lamp(LAMP), name='luz-trabajo')
    m.box((0.62, 2.74, -0.1), (0.76, 2.86, 0.04), lamp(ORANGE, ORANGE), name='baliza')

    # --- caja hidraulica (derecha)
    m.box((-1.2, 1.15, -0.65), (-0.1, 1.85, 0.6), solid((70, 72, 76), 5), name='hidraulica')
    m.box((-0.9, 1.85, -0.3), (-0.4, 1.95, 0.2), dark, name='hidraulica-tapa')

    # --- cabezal (la punta va aparte)
    m.box((-0.9, 0.5, 0.65), (0.9, 2.2, 1.3), steel, name='reductor')
    m.box((-1.05, 0.3, 1.3), (1.05, 2.4, 1.42), steel, name='brida')
    m.mirror((0.92, 1.15, 0.7), (1.05, 2.1, 0.82), dark, name='piston')

    # --- panel de control aparte, enganchado con una barra
    m.box((-0.12, 0.35, -2.42), (0.12, 0.5, -1.95), dark, name='barra')
    m.box((-0.55, 0.0, -2.78), (0.55, 0.28, -2.32), hazard, name='base-panel')
    m.box((-0.18, 0.28, -2.66), (0.18, 0.92, -2.44), dark, name='pedestal')
    m.box((-0.48, 0.92, -2.72), (0.48, 1.6, -2.38), console, name='panel')
    m.box((-0.53, 1.6, -2.78), (0.53, 1.66, -2.34), yellow, name='panel-visera')
    m.box((-0.05, 1.66, -2.6), (0.05, 1.8, -2.5), lamp((240, 60, 40), (240, 60, 40)), name='panel-luz')
    return m


TALADRO_PUNTA = (0.0, 1.35, 1.57)


# ===================================================================== main

def main(argv):
    out = os.path.join(HERE, '..', '..', 'resourcepack')
    vista_dir = None
    args = list(argv)
    if '--vista' in args:
        i = args.index('--vista')
        vista_dir = args[i + 1]
        del args[i:i + 2]
    if args:
        out = args[0]
    out = os.path.abspath(out)

    built = {}
    for model, item in ((rueda('rueda_camion', (214, 214, 206), (150, 150, 148), 6), 'vehicles/rueda_camion'),
                        (rueda('rueda_auto', (196, 198, 202), CHROME, 0), 'vehicles/rueda_auto'),
                        (punta(), 'vehicles/taladro_punta'),
                        (camion(), 'vehicles/camion'),
                        (camioneta(), 'vehicles/camioneta'),
                        (taladro(), 'vehicles/taladro')):
        data, img = write_model(out, model, item)
        built[model.name] = (model, data, img)
        print(f'{model.name}: {len(data["elements"])} cajas, textura {img.w}x{img.h}, '
              f'pivot {model.pivot} escala {model.scale}')

    if vista_dir:
        os.makedirs(vista_dir, exist_ok=True)
        previews(built, vista_dir)


def body_quads(built, name):
    model, data, img = built[name]
    s, P = model.scale, model.pivot
    return vista.quads(data, img, lambda q: (P[0] + q[0] * s, P[1] + q[1] * s, P[2] + q[2] * s))


def wheel_quads(built, name, wheels):
    model, data, img = built[name]
    out = []
    for x, y, z, d, _ in wheels:
        for sx in (x, -x):
            out += vista.quads(data, img, lambda q, sx=sx, y=y, z=z, d=d: (sx + q[0] * d, y + q[1] * d, z + q[2] * d))
    return out


def previews(built, folder):
    scenes = {
        'camion': body_quads(built, 'camion') + wheel_quads(built, 'rueda_camion', CAMION_RUEDAS),
        'camioneta': body_quads(built, 'camioneta') + wheel_quads(built, 'rueda_auto', CAMIONETA_RUEDAS),
        'taladro': body_quads(built, 'taladro') + vista.quads(
            built['taladro_punta'][1], built['taladro_punta'][2],
            lambda q: (TALADRO_PUNTA[0] + q[0], TALADRO_PUNTA[1] + q[1], TALADRO_PUNTA[2] + q[2] + 0.45)),
    }
    views = {
        'camion': [((7.5, 3.6, 8.5), (0, 1.5, 0.3)), ((-7.5, 4.0, -8.0), (0, 1.6, -0.5))],
        'camioneta': [((5.0, 2.6, 6.0), (0, 0.9, 0.2)), ((-5.0, 2.8, -5.5), (0, 0.9, -0.2))],
        'taladro': [((5.5, 3.6, 6.5), (0, 1.3, 0.2)), ((-5.0, 3.6, -6.5), (0, 1.2, -0.8))],
    }
    for name, quads in scenes.items():
        for i, (eye, target) in enumerate(views[name]):
            img = vista.render(quads, eye, target)
            path = os.path.join(folder, f'{name}-{"frente" if i == 0 else "atras"}.png')
            img.save(path)
            print('vista:', path)


if __name__ == '__main__':
    main(sys.argv[1:])
