"""
Geometria de las maquinas de MinerCorp-Recursos.

Una sola fuente para los dos modos de dibujo:
  - sin resource pack: cada caja se arma con un BlockDisplay del bloque "b"
  - con resource pack: cada parte es un modelo de item (cajas con texturas "t")

Unidades: bloques. Sistema local: el bloque del panel (donde se abre el menu)
ocupa [0,1]^3, el jugador lo coloca mirando hacia +Z y el cuerpo de la maquina
queda detras del panel (z >= 2), con un bloque de separacion.
"""
import math

C = math.cos(math.pi / 8)  # para octagonos


class Parte:
    def __init__(self, id, anim=None, pivote=None, **anim_args):
        self.id = id
        self.anim = anim          # None | "giro-y" | "balanceo-x" | "giro-x" | "vaiven-y" | "luz"
        self.pivote = pivote or [0, 0, 0]
        self.anim_args = anim_args
        self.cajas = []

    def caja(self, x0, y0, z0, x1, y1, z1, b, t, rot=None, solo=None):
        """b: bloque vanilla del modo sin pack. t: textura (o dict por cara). rot: (eje, origen) a 45 grados."""
        c = {"de": [x0, y0, z0], "a": [x1, y1, z1], "b": b, "t": t}
        if rot:
            c["rot"] = {"eje": rot[0], "angulo": 45, "origen": rot[1]}
        if solo:
            c["solo"] = solo  # "pack" o "bloques"
        self.cajas.append(c)
        return self

    def cil_y(self, cx, cz, r, y0, y1, b, t):
        h = r * C
        self.caja(cx - h, y0, cz - h, cx + h, y1, cz + h, b, t)
        self.caja(cx - h, y0, cz - h, cx + h, y1, cz + h, b, t, rot=("y", [cx, (y0 + y1) / 2, cz]))
        return self

    def cil_x(self, cy, cz, r, x0, x1, b, t):
        h = r * C
        self.caja(x0, cy - h, cz - h, x1, cy + h, cz + h, b, t)
        self.caja(x0, cy - h, cz - h, x1, cy + h, cz + h, b, t, rot=("x", [(x0 + x1) / 2, cy, cz]))
        return self

    def cil_z(self, cx, cy, r, z0, z1, b, t):
        h = r * C
        self.caja(cx - h, cy - h, z0, cx + h, cy + h, z1, b, t)
        self.caja(cx - h, cy - h, z0, cx + h, cy + h, z1, b, t, rot=("z", [cx, cy, (z0 + z1) / 2]))
        return self


def panel(partes):
    """Gabinete de control: el bloque real + pantalla, botonera, luz y el conducto al cuerpo."""
    p = Parte("panel")
    caras = {"north": "panel_frente", "south": "panel_lado", "east": "panel_lado",
             "west": "panel_lado", "up": "panel_arriba", "down": "acero_oscuro"}
    p.caja(-0.01, -0.01, -0.01, 1.01, 1.01, 1.01, "IRON_BLOCK", caras, solo="pack")
    # Sin pack: pantalla y botones pegados al frente del bloque.
    p.caja(0.15, 0.45, -0.03, 0.85, 0.85, 0.0, "BLACK_STAINED_GLASS", "negro", solo="bloques")
    p.caja(0.2, 0.2, -0.03, 0.3, 0.3, 0.0, "RED_CONCRETE", "rojo", solo="bloques")
    p.caja(0.45, 0.2, -0.03, 0.55, 0.3, 0.0, "LIME_CONCRETE", "verde", solo="bloques")
    p.caja(0.7, 0.2, -0.03, 0.8, 0.3, 0.0, "YELLOW_CONCRETE", "amarillo", solo="bloques")
    # Conducto de cables hasta la maquina.
    p.caja(0.38, 0.0, 1.0, 0.62, 0.18, 2.05, "GRAY_CONCRETE", "acero_oscuro")
    p.caja(0.4, 1.0, 0.4, 0.6, 1.08, 0.6, "GRAY_CONCRETE", "acero_oscuro")
    partes.append(p)
    luz = Parte("panel-luz", anim="luz")
    luz.caja(0.42, 1.08, 0.42, 0.58, 1.22, 0.58, "LIME_STAINED_GLASS", "luz_verde")
    partes.append(luz)


# --------------------------------------------------------------------------
# Perforadora de carbon: torre de perforacion de 7 bloques con su plataforma,
# mesa rotary, sarta que gira, aparejo y casa de motores.
# --------------------------------------------------------------------------
def perforadora():
    partes = []
    panel(partes)
    base = Parte("base")
    # Subestructura: 4 columnas y el piso de perforacion (rejilla) a 1.25.
    for x, z in [(-1, 2), (1.75, 2), (-1, 4.75), (1.75, 4.75)]:
        base.caja(x, 0, z, x + 0.25, 1.25, z + 0.25, "YELLOW_CONCRETE", "amarillo")
    base.caja(-1, 1.0, 2, 2, 1.25, 2.12, "YELLOW_CONCRETE", "rayas")
    base.caja(-1, 1.0, 4.88, 2, 1.25, 5, "YELLOW_CONCRETE", "rayas")
    base.caja(-1, 1.25, 2, 2, 1.37, 5, "IRON_TRAPDOOR", "rejilla")
    # Barandas.
    for z in (2, 4.94):
        base.caja(-1, 1.37, z, 2, 1.45, z + 0.06, "YELLOW_CONCRETE", "amarillo")
        base.caja(-1, 1.85, z, 2, 1.93, z + 0.06, "YELLOW_CONCRETE", "amarillo")
    for x in (-1, 1.94):
        base.caja(x, 1.37, 2, x + 0.06, 1.45, 5, "YELLOW_CONCRETE", "amarillo")
        base.caja(x, 1.85, 2, x + 0.06, 1.93, 5, "YELLOW_CONCRETE", "amarillo")
    # Mastil: 4 patas y anillos de travesanos, con reticulado en las caras.
    x0, x1, z0, z1 = -0.35, 1.35, 2.65, 4.35
    for x, z in [(x0, z0), (x1 - 0.14, z0), (x0, z1 - 0.14), (x1 - 0.14, z1 - 0.14)]:
        base.caja(x, 1.37, z, x + 0.14, 8.0, z + 0.14, "YELLOW_CONCRETE", "amarillo")
    for y in (2.6, 3.9, 5.2, 6.5, 7.8):
        base.caja(x0, y, z0, x1, y + 0.1, z0 + 0.1, "YELLOW_CONCRETE", "amarillo")
        base.caja(x0, y, z1 - 0.1, x1, y + 0.1, z1, "YELLOW_CONCRETE", "amarillo")
        base.caja(x0, y, z0, x0 + 0.1, y + 0.1, z1, "YELLOW_CONCRETE", "amarillo")
        base.caja(x1 - 0.1, y, z0, x1, y + 0.1, z1, "YELLOW_CONCRETE", "amarillo")
    base.caja(x0 + 0.02, 1.4, z1 - 0.03, x1 - 0.02, 7.8, z1 - 0.01, "AIR", "reticulado", solo="pack")
    base.caja(x0 + 0.01, 1.4, z0 + 0.02, x0 + 0.03, 7.8, z1 - 0.02, "AIR", "reticulado", solo="pack")
    base.caja(x1 - 0.03, 1.4, z0 + 0.02, x1 - 0.01, 7.8, z1 - 0.02, "AIR", "reticulado", solo="pack")
    # Corona y polea arriba, aparejo (bloque viajero) y cable.
    base.caja(x0 - 0.1, 8.0, z0 - 0.1, x1 + 0.1, 8.3, z1 + 0.1, "YELLOW_CONCRETE", "amarillo")
    base.caja(0.2, 8.3, 3.2, 0.8, 8.6, 3.8, "GRAY_CONCRETE", "acero")
    base.caja(0.47, 5.6, 3.47, 0.53, 8.0, 3.53, "BLACK_CONCRETE", "negro")
    base.caja(0.25, 4.9, 3.25, 0.75, 5.6, 3.75, "YELLOW_CONCRETE", "rayas")
    # Mesa rotary y BOP (preventor) bajo el piso.
    base.cil_y(0.5, 3.5, 0.45, 1.37, 1.6, "GRAY_CONCRETE", "acero_oscuro")
    base.cil_y(0.5, 3.5, 0.35, 0.0, 1.0, "RED_CONCRETE", "rojo")
    # Casa de motores y malacate al costado.
    base.caja(1.55, 1.37, 2.2, 2.6, 2.4, 3.6, "WHITE_CONCRETE", {"north": "motor", "south": "motor", "east": "motor_lado",
                                                                 "west": "motor_lado", "up": "acero", "down": "acero"})
    base.cil_y(2.35, 3.3, 0.1, 2.4, 3.1, "GRAY_CONCRETE", "tubo")
    base.cil_z(1.2, 1.7, 0.25, 2.4, 3.4, "RED_CONCRETE", "rojo")
    # Escalera del piso al suelo.
    for i in range(5):
        base.caja(-1.6, i * 0.25, 2.2 + i * 0.25, -1.0, i * 0.25 + 0.08, 2.5 + i * 0.25, "YELLOW_CONCRETE", "rejilla")
    # Pileta de lodo.
    base.caja(-2.0, 0, 3.3, -1.1, 0.8, 4.9, "GRAY_CONCRETE", "acero_oscuro")
    partes.append(base)

    sarta = Parte("sarta", anim="giro-y", pivote=[0.5, 0, 3.5], paso=45, ticks=5)
    sarta.caja(0.38, 1.6, 3.38, 0.62, 4.9, 3.62, "IRON_BLOCK", "kelly")
    sarta.cil_y(0.5, 3.5, 0.08, 0.0, 1.6, "IRON_BLOCK", "tubo")
    sarta.caja(0.3, 1.6, 3.3, 0.7, 1.8, 3.7, "GRAY_CONCRETE", "acero_oscuro")
    partes.append(sarta)

    luz = Parte("luz", anim="luz")
    luz.caja(0.45, 8.6, 3.45, 0.55, 8.75, 3.55, "RED_STAINED_GLASS", "luz_roja")
    partes.append(luz)
    return {
        "colision": celdas(-1, 0, 2, 1, 1, 4) + celdas(-2, 0, 3, -2, 0, 4),
        "cartel": [0.5, 9.2, 3.5],
        "efectos": {"humo": [[2.35, 3.2, 3.3]], "chispas": [[0.5, 1.5, 3.5]]},
        "sonido": {"nombre": "block.grindstone.use", "cada": 10, "volumen": 0.4, "tono": 0.6},
        "partes": partes,
    }


# --------------------------------------------------------------------------
# Bomba de petroleo (balancin / "caballito"): patin, boca de pozo, poste,
# balancin con cabeza de caballo, reductor, manivela con contrapesos y motor.
# --------------------------------------------------------------------------
def bomba():
    partes = []
    panel(partes)
    base = Parte("base")
    base.caja(-0.3, 0, 2.6, 1.3, 0.3, 8.2, "BLACK_CONCRETE", "acero_oscuro")
    # Boca de pozo ("arbolito") adelante.
    base.cil_y(0.5, 2.3, 0.22, 0, 0.7, "GRAY_CONCRETE", "acero")
    base.caja(0.2, 0.7, 2.0, 0.8, 0.95, 2.6, "RED_CONCRETE", "rojo")
    base.cil_x(0.82, 2.3, 0.1, -0.3, 1.3, "GRAY_CONCRETE", "tubo")
    base.cil_y(0.5, 2.3, 0.05, 0.95, 1.6, "IRON_BLOCK", "tubo")
    # Poste Samson (A).
    for x in (-0.15, 1.0):
        base.caja(x, 0.3, 4.1, x + 0.15, 3.6, 4.35, "ORANGE_CONCRETE", "naranja")
        base.caja(x, 0.3, 5.0, x + 0.15, 3.3, 5.2, "ORANGE_CONCRETE", "naranja")
    base.caja(-0.15, 3.5, 4.1, 1.15, 3.75, 4.6, "ORANGE_CONCRETE", "naranja")
    # Reductor y motor atras.
    base.caja(0.1, 0.3, 6.3, 0.9, 1.6, 7.3, "ORANGE_CONCRETE", {"north": "naranja", "south": "naranja", "east": "reductor",
                                                               "west": "reductor", "up": "naranja", "down": "naranja"})
    base.caja(0.15, 0.3, 7.5, 0.85, 1.0, 8.1, "GRAY_CONCRETE", {"north": "motor", "south": "motor", "east": "motor_lado",
                                                                "west": "motor_lado", "up": "acero", "down": "acero"})
    base.caja(0.85, 0.5, 6.9, 0.95, 1.2, 7.9, "YELLOW_CONCRETE", "rayas")
    # Bielas desde la manivela al balancin (fijas).
    for x in (-0.3, 1.2):
        base.caja(x, 1.0, 6.7, x + 0.1, 3.4, 6.8, "GRAY_CONCRETE", "acero")
    partes.append(base)

    pivote = [0.5, 3.85, 4.45]
    bal = Parte("balancin", anim="balanceo-x", pivote=pivote, angulo=14, ticks=30)
    bal.caja(0.3, 3.75, 2.6, 0.7, 4.15, 7.0, "ORANGE_CONCRETE", "naranja")
    # Cabeza de caballo (curva hacia adelante).
    bal.caja(0.25, 3.0, 2.0, 0.75, 4.4, 2.6, "ORANGE_CONCRETE", "cabeza")
    bal.caja(0.25, 3.3, 1.85, 0.75, 4.2, 2.0, "ORANGE_CONCRETE", "cabeza")
    bal.caja(0.35, 3.75, 6.6, 0.65, 3.95, 7.0, "GRAY_CONCRETE", "acero")
    bal.cil_x(3.85, 4.45, 0.18, 0.2, 0.8, "GRAY_CONCRETE", "acero_oscuro")
    partes.append(bal)

    varilla = Parte("varilla", anim="vaiven-y", pivote=[0.5, 0, 2.3], amplitud=0.45, ticks=30)
    varilla.caja(0.48, 1.6, 1.88, 0.52, 3.2, 1.92, "BLACK_CONCRETE", "negro")
    varilla.caja(0.4, 1.55, 1.8, 0.6, 1.65, 2.0, "GRAY_CONCRETE", "acero")
    partes.append(varilla)

    man = Parte("manivela", anim="giro-x", pivote=[0.5, 1.0, 6.8], paso=90, ticks=10)
    for x in (-0.35, 1.15):
        man.caja(x, 0.9, 6.0, x + 0.2, 1.1, 7.6, "GRAY_CONCRETE", "acero_oscuro")
        man.caja(x - 0.05, 0.55, 5.6, x + 0.25, 1.45, 6.1, "GRAY_CONCRETE", "contrapeso")
    man.cil_x(1.0, 6.8, 0.15, -0.35, 1.35, "GRAY_CONCRETE", "acero")
    partes.append(man)
    return {
        "colision": celdas(0, 0, 2, 0, 0, 8) + celdas(0, 1, 6, 0, 1, 7),
        "cartel": [0.5, 5.0, 4.5],
        "efectos": {"humo": [[0.5, 1.1, 8.0]]},
        "sonido": {"nombre": "block.piston.extend", "cada": 30, "volumen": 0.3, "tono": 0.5},
        "partes": partes,
    }


# --------------------------------------------------------------------------
# Horno de coque: bateria de hornos de ladrillo con sus puertas de hierro,
# tapas de carga arriba, colector de gas y una chimenea alta.
# --------------------------------------------------------------------------
def horno():
    partes = []
    panel(partes)
    base = Parte("base")
    base.caja(-2, 0, 2.2, 3, 0.4, 5.2, "STONE_BRICKS", "hormigon")
    base.caja(-1.9, 0.4, 2.4, 2.9, 2.6, 5.0, "BRICKS", "ladrillo")
    base.caja(-2, 2.6, 2.3, 3, 2.8, 5.1, "STONE_BRICKS", "hormigon")
    # Contrafuertes de acero entre hornos.
    for x in (-1.95, -0.75, 0.45, 1.65, 2.75):
        base.caja(x, 0.4, 2.3, x + 0.18, 2.75, 2.4, "GRAY_CONCRETE", "acero_oscuro")
        base.caja(x, 0.4, 5.0, x + 0.18, 2.75, 5.1, "GRAY_CONCRETE", "acero_oscuro")
    # Puertas (frente, hacia el panel).
    for x in (-1.6, -0.4, 0.8, 2.0):
        base.caja(x, 0.55, 2.3, x + 0.75, 2.35, 2.4, "IRON_BLOCK", "puerta_horno")
    # Tapas de carga y colector de gas.
    for x in (-1.2, 0.0, 1.2, 2.4):
        base.cil_y(x, 3.4, 0.18, 2.8, 2.95, "GRAY_CONCRETE", "acero_oscuro")
    base.cil_x(3.2, 4.6, 0.22, -2, 3.0, "GRAY_CONCRETE", "tubo")
    for x in (-1.2, 0.0, 1.2, 2.4):
        base.cil_y(x, 4.6, 0.08, 2.8, 3.1, "GRAY_CONCRETE", "tubo")
    # Chimenea.
    base.caja(3.1, 0, 4.1, 4.3, 0.5, 5.3, "STONE_BRICKS", "hormigon")
    base.cil_y(3.7, 4.7, 0.5, 0.5, 8.0, "BRICKS", "ladrillo_oscuro")
    for y in (2.5, 5.0, 7.6):
        base.cil_y(3.7, 4.7, 0.56, y, y + 0.15, "GRAY_CONCRETE", "acero_oscuro")
    base.cil_x(3.2, 4.6, 0.22, 3.0, 3.4, "GRAY_CONCRETE", "tubo")
    partes.append(base)

    luz = Parte("luz", anim="luz")
    for x in (-1.6, -0.4, 0.8, 2.0):
        luz.caja(x + 0.05, 0.6, 2.28, x + 0.7, 2.3, 2.3, "ORANGE_STAINED_GLASS", "puerta_horno_luz")
    partes.append(luz)
    return {
        "colision": celdas(-2, 0, 2, 2, 1, 4) + celdas(3, 0, 4, 3, 1, 4),
        "cartel": [0.5, 3.9, 3.7],
        "efectos": {"humo": [[3.7, 8.2, 4.7]], "llama": [[-1.2, 1.5, 2.2], [1.2, 1.5, 2.2]]},
        "sonido": {"nombre": "block.furnace.fire_crackle", "cada": 40, "volumen": 0.7, "tono": 0.8},
        "partes": partes,
    }


# --------------------------------------------------------------------------
# Refineria: horno calentador, columna de destilacion con plataformas,
# tanques de producto, canerias y antorcha de gas.
# --------------------------------------------------------------------------
def refineria():
    partes = []
    panel(partes)
    base = Parte("base")
    base.caja(-2, 0, 2, 3.5, 0.2, 7, "SMOOTH_STONE", "hormigon")
    # Horno calentador (caja con chimenea).
    base.caja(-1.8, 0.2, 2.3, -0.2, 2.0, 3.9, "RED_NETHER_BRICKS", {"north": "calentador", "south": "calentador",
                                                                     "east": "calentador", "west": "calentador",
                                                                     "up": "acero_oscuro", "down": "acero_oscuro"})
    base.cil_y(-1.0, 3.1, 0.25, 2.0, 4.5, "GRAY_CONCRETE", "tubo")
    # Columna de destilacion.
    cx, cz = 1.7, 5.0
    base.cil_y(cx, cz, 0.65, 0.2, 8.6, "WHITE_CONCRETE", "columna")
    base.cil_y(cx, cz, 0.35, 8.6, 9.0, "WHITE_CONCRETE", "acero")
    for y in (2.4, 4.6, 6.8):
        base.caja(cx - 0.95, y, cz - 0.95, cx + 0.95, y + 0.1, cz + 0.95, "IRON_TRAPDOOR", "rejilla")
        base.caja(cx - 0.95, y + 0.5, cz - 0.95, cx + 0.95, y + 0.56, cz - 0.9, "YELLOW_CONCRETE", "amarillo")
    base.caja(cx + 0.62, 0.2, cz - 0.15, cx + 0.72, 8.4, cz + 0.15, "YELLOW_CONCRETE", "escalera")
    # Tanques de producto.
    for tx, tz, col in ((-1.0, 5.6, "blanco_tanque"), (0.0, 6.4, "blanco_tanque")):
        base.cil_y(tx, tz, 0.55, 0.2, 1.8, "WHITE_CONCRETE", col)
        base.cil_y(tx, tz, 0.4, 1.8, 1.95, "LIGHT_GRAY_CONCRETE", "acero")
    # Canerias: calentador -> columna, columna -> tanques.
    base.cil_x(1.2, 3.1, 0.12, -0.2, cx, "GRAY_CONCRETE", "tubo")
    base.cil_z(cx, 1.2, 0.12, 3.1, cz - 0.6, "GRAY_CONCRETE", "tubo")
    base.cil_x(5.0, 6.0, 0.08, -1.0, cx, "GRAY_CONCRETE", "tubo")
    base.cil_y(-1.0, 6.0, 0.08, 1.8, 5.0, "GRAY_CONCRETE", "tubo")
    base.cil_x(3.0, 6.4, 0.08, 0.0, cx, "GRAY_CONCRETE", "tubo")
    base.cil_y(0.0, 6.4, 0.08, 1.9, 3.0, "GRAY_CONCRETE", "tubo")
    # Antorcha (flare).
    base.cil_y(3.0, 2.5, 0.12, 0.2, 9.5, "GRAY_CONCRETE", "tubo")
    base.cil_y(3.0, 2.5, 0.2, 9.5, 9.8, "RED_CONCRETE", "rojo")
    for y in (3.0, 6.0):
        base.caja(2.9, y, 2.4, 3.6, y + 0.06, 2.6, "GRAY_CONCRETE", "acero")
    partes.append(base)

    luz = Parte("luz", anim="luz")
    luz.caja(-1.5, 0.5, 2.27, -0.5, 0.9, 2.3, "ORANGE_STAINED_GLASS", "puerta_horno_luz")
    partes.append(luz)
    return {
        "colision": celdas(-2, 0, 2, 0, 1, 3) + celdas(1, 0, 4, 2, 1, 5) + celdas(-2, 0, 5, 0, 0, 6) + celdas(3, 0, 2, 3, 2, 2),
        "cartel": [0.5, 3.0, 3.0],
        "efectos": {"llama": [[3.0, 10.0, 2.5]], "humo": [[-1.0, 4.7, 3.1]]},
        "sonido": {"nombre": "block.fire.ambient", "cada": 60, "volumen": 0.6, "tono": 0.7},
        "partes": partes,
    }


def celdas(x0, y0, z0, x1, y1, z1):
    return [[x, y, z] for x in range(x0, x1 + 1) for y in range(y0, y1 + 1) for z in range(z0, z1 + 1)]


MAQUINAS = {
    "perforadora": perforadora,
    "bomba": bomba,
    "horno": horno,
    "refineria": refineria,
}
