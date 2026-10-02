"""Texturas 16x16 pixel art de las maquinas y los items, dibujadas por codigo."""
import random
from PIL import Image

S = 16


def img():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def hexrgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def ruido(base, var=0.08, seed=1):
    r = random.Random(seed)
    im = img()
    for y in range(S):
        for x in range(S):
            im.putpixel((x, y), shade(base, 1 + r.uniform(-var, var)))
    return im


def borde(im, c, grosor=1):
    for i in range(S):
        for g in range(grosor):
            im.putpixel((i, g), c)
            im.putpixel((i, S - 1 - g), c)
            im.putpixel((g, i), c)
            im.putpixel((S - 1 - g, i), c)
    return im


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if 0 <= x < S and 0 <= y < S:
                im.putpixel((x, y), c)
    return im


def maquinas():
    t = {}
    acero = hexrgb("8e959c")
    t["acero"] = borde(ruido(acero, 0.05, 2), shade(acero, 0.8))
    oscuro = hexrgb("4a4f55")
    t["acero_oscuro"] = borde(ruido(oscuro, 0.07, 3), shade(oscuro, 0.75))
    amarillo = hexrgb("e8b923")
    t["amarillo"] = borde(ruido(amarillo, 0.05, 4), shade(amarillo, 0.78))
    rayas = img()
    for y in range(S):
        for x in range(S):
            rayas.putpixel((x, y), hexrgb("e8b923") if ((x + y) // 4) % 2 == 0 else hexrgb("26262a"))
    t["rayas"] = rayas
    naranja = hexrgb("d8662a")
    t["naranja"] = borde(ruido(naranja, 0.06, 5), shade(naranja, 0.75))
    cabeza = borde(ruido(naranja, 0.06, 6), shade(naranja, 0.75))
    for y in range(3, 13):
        cabeza.putpixel((7, y), shade(naranja, 0.7))
        cabeza.putpixel((8, y), shade(naranja, 0.7))
    t["cabeza"] = cabeza
    rojo = hexrgb("b3312c")
    t["rojo"] = borde(ruido(rojo, 0.06, 7), shade(rojo, 0.7))
    t["verde"] = ruido(hexrgb("3fae3a"), 0.05, 8)
    t["negro"] = ruido(hexrgb("1b1c1f"), 0.1, 9)
    # Tanque blanco con costuras horizontales y una franja azul.
    blanco = hexrgb("e6e6e1")
    tanque = ruido(blanco, 0.03, 10)
    rect(tanque, 0, 0, 15, 0, shade(blanco, 0.85))
    rect(tanque, 0, 8, 15, 8, shade(blanco, 0.85))
    t["blanco_tanque"] = tanque
    columna = ruido(blanco, 0.03, 11)
    rect(columna, 0, 15, 15, 15, shade(blanco, 0.8))
    rect(columna, 0, 3, 15, 3, hexrgb("2f6db3"))
    t["columna"] = columna
    # Ladrillo refractario.
    for nombre, base, junta in (("ladrillo", hexrgb("9a4b36"), hexrgb("7d7266")),
                                ("ladrillo_oscuro", hexrgb("6e3a2e"), hexrgb("5d554d"))):
        lad = ruido(base, 0.08, 12)
        for y in (3, 7, 11, 15):
            rect(lad, 0, y, 15, y, junta)
        for i, y in enumerate((0, 4, 8, 12)):
            off = 0 if i % 2 == 0 else 4
            for x in (off, off + 8):
                rect(lad, x % 16, y, x % 16, y + 2, junta)
        t[nombre] = lad
    t["hormigon"] = borde(ruido(hexrgb("9d9d97"), 0.06, 13), hexrgb("85857f"))
    # Puerta del horno: hierro con remaches y manija.
    puerta = borde(ruido(hexrgb("3b3d40"), 0.06, 14), hexrgb("26272a"))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        puerta.putpixel((x, y), hexrgb("8a8f95"))
    rect(puerta, 6, 7, 9, 8, hexrgb("8a8f95"))
    rect(puerta, 3, 4, 12, 4, hexrgb("1a1a1c"))
    t["puerta_horno"] = puerta
    luz = img()
    r = random.Random(15)
    for y in range(S):
        for x in range(S):
            luz.putpixel((x, y), hexrgb(r.choice(["ffb02e", "ff8a1c", "ffd25a", "ff6a10"])))
    t["puerta_horno_luz"] = luz
    # Rejilla (piso de chapa perforada): con agujeros transparentes.
    rejilla = img()
    for y in range(S):
        for x in range(S):
            hueco = x % 4 in (1, 2) and y % 4 in (1, 2)
            rejilla.putpixel((x, y), (0, 0, 0, 0) if hueco else hexrgb("6b7178"))
    t["rejilla"] = rejilla
    # Reticulado del mastil: diagonales en X.
    ret = img()
    for i in range(S):
        for d in (0, 1):
            ret.putpixel((i, min(15, i + d)), hexrgb("d9a91e"))
            ret.putpixel((15 - i, min(15, i + d)), hexrgb("d9a91e"))
    rect(ret, 0, 0, 15, 0, hexrgb("d9a91e"))
    t["reticulado"] = ret
    # Cano: gris con anillo de brida.
    tubo = ruido(hexrgb("9aa0a6"), 0.04, 16)
    rect(tubo, 0, 7, 15, 8, hexrgb("6a6f75"))
    t["tubo"] = tubo
    kelly = ruido(hexrgb("6f757b"), 0.05, 17)
    rect(kelly, 7, 0, 8, 15, hexrgb("a4aab0"))
    t["kelly"] = kelly
    # Motor: chapa blanca con rejilla de ventilacion y escape.
    motor = borde(ruido(hexrgb("dcdcd6"), 0.03, 18), hexrgb("9b9b95"))
    for y in range(4, 12, 2):
        rect(motor, 3, y, 12, y, hexrgb("4a4f55"))
    t["motor"] = motor
    motor_lado = borde(ruido(hexrgb("dcdcd6"), 0.03, 19), hexrgb("9b9b95"))
    rect(motor_lado, 2, 10, 13, 11, hexrgb("b3312c"))
    t["motor_lado"] = motor_lado
    reductor = borde(ruido(naranja, 0.06, 20), shade(naranja, 0.7))
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        reductor.putpixel((x, y), hexrgb("3b3d40"))
    rect(reductor, 6, 6, 9, 9, shade(naranja, 0.8))
    t["reductor"] = reductor
    contrapeso = borde(ruido(hexrgb("55595e"), 0.08, 21), hexrgb("2f3236"))
    rect(contrapeso, 2, 7, 13, 8, hexrgb("e8b923"))
    t["contrapeso"] = contrapeso
    calentador = borde(ruido(hexrgb("7b2b24"), 0.07, 22), hexrgb("3b3d40"))
    for x in (5, 10):
        rect(calentador, x, 1, x, 14, hexrgb("3b3d40"))
    t["calentador"] = calentador
    esc = img()
    rect(esc, 0, 0, 2, 15, hexrgb("e8b923"))
    rect(esc, 13, 0, 15, 15, hexrgb("e8b923"))
    for y in (2, 6, 10, 14):
        rect(esc, 3, y, 12, y, hexrgb("e8b923"))
    t["escalera"] = esc
    t["luz_verde"] = ruido(hexrgb("6dff5a"), 0.05, 23)
    t["luz_roja"] = ruido(hexrgb("ff4a3a"), 0.05, 24)
    # Panel de control.
    pf = borde(ruido(hexrgb("b9bec4"), 0.03, 25), hexrgb("7d8389"))
    rect(pf, 2, 2, 13, 7, hexrgb("12241a"))
    for x in range(3, 13):
        pf.putpixel((x, 5 - (x % 3)), hexrgb("54ff7a"))
    for x, c in ((3, "c0392b"), (7, "f1c40f"), (11, "27ae60")):
        rect(pf, x, 10, x + 1, 11, hexrgb(c))
    rect(pf, 2, 13, 13, 13, hexrgb("7d8389"))
    t["panel_frente"] = pf
    pl = borde(ruido(hexrgb("b9bec4"), 0.03, 26), hexrgb("7d8389"))
    for y in (4, 6, 8):
        rect(pl, 4, y, 11, y, hexrgb("8d939a"))
    rect(pl, 5, 11, 10, 12, hexrgb("e8b923"))
    t["panel_lado"] = pl
    t["panel_arriba"] = borde(ruido(hexrgb("a3a9af"), 0.03, 27), hexrgb("7d8389"))
    return t


def dibujar(filas, paleta):
    """Pixel art a partir de 16 filas de 16 caracteres; '.' = transparente."""
    im = img()
    for y, fila in enumerate(filas):
        for x, ch in enumerate(fila):
            if ch != ".":
                im.putpixel((x, y), hexrgb(paleta[ch]))
    return im


def items():
    t = {}
    bidon = [
        "................",
        "......hhhh......",
        "....bbhhhhb.....",
        "...bAAAAAAAAb...",
        "..bAaaaaaaaaAb..",
        "..bAaAAAAAAaAb..",
        "..bAaA....AaAb..",
        "..bAaAAAAAAaAb..",
        "..bAAaaaaaaAAb..",
        "..bAAAAAAAAAAb..",
        "..bAaaaaaaaaAb..",
        "..bAAAAAAAAAAb..",
        "..bAAAAAAAAAAb..",
        "..bddddddddddb..",
        "...bbbbbbbbbb...",
        "................",
    ]
    t["gasolina"] = dibujar(bidon, {"h": "3a3a3a", "b": "5a0e0e", "A": "d4312a", "a": "f0645a", "d": "8f1d18"})
    t["diesel"] = dibujar(bidon, {"h": "3a3a3a", "b": "6b5a10", "A": "e6c227", "a": "f6e27a", "d": "a88c18"})
    barril = [
        "................",
        "...bbbbbbbbbb...",
        "..bAAAAAAAAAAb..",
        "..bssssssssssb..",
        "..bAAAaAAAAAAb..",
        "..bAAAaAAAAAAb..",
        "..bAAAaAAoAAAb..",
        "..bssssssssssb..",
        "..bAAAaAAooAAb..",
        "..bAAAaAAAoAAb..",
        "..bAAAaAAAAAAb..",
        "..bssssssssssb..",
        "..bAAAaAAAAAAb..",
        "..bAAAAAAAAAAb..",
        "...bbbbbbbbbb...",
        "................",
    ]
    t["petroleo_crudo"] = dibujar(barril, {"b": "0e0e10", "A": "2a2c30", "a": "4a4e55", "s": "6f747b", "o": "111111"})
    trozo = [
        "................",
        "................",
        ".....bbbbb......",
        "....bAAaAAbb....",
        "...bAaAAAAAAb...",
        "..bAAAAdAAaAAb..",
        "..bAaAAAAAAAAb..",
        "..bAAAAAAdAAAbb.",
        ".bAAdAAaAAAAAAb.",
        ".bAAAAAAAAAdAAb.",
        ".bAaAAAdAAAAAb..",
        "..bAAAAAAAaAb...",
        "...bbAAAAAAb....",
        ".....bbbbbb.....",
        "................",
        "................",
    ]
    t["carbon_crudo"] = dibujar(trozo, {"b": "141414", "A": "2b2b2b", "a": "6d6d6d", "d": "8a7f72"})
    t["coque"] = dibujar(trozo, {"b": "3d4046", "A": "8f949b", "a": "c9ced4", "d": "55595e"})
    t["asfalto"] = dibujar(trozo, {"b": "101010", "A": "333333", "a": "505050", "d": "6a6a6a"})
    frasco = [
        "................",
        "......cccc......",
        "......kkkk......",
        ".....g....g.....",
        "....g......g....",
        "...g.AAAAAA.g...",
        "...gAAaAAAAAg...",
        "...gAaAAAAAAg...",
        "...gAAAAAAAAg...",
        "...gAAAAAAAAg...",
        "...gAAAAAAaAg...",
        "...gAAAAAAAAg...",
        "....gAAAAAAg....",
        ".....gggggg.....",
        "................",
        "................",
    ]
    t["alquitran"] = dibujar(frasco, {"c": "8b5a2b", "k": "6b4220", "g": "a9c7d1", "A": "1a1410", "a": "5a4a3a"})
    cristales = [
        "................",
        "................",
        ".......b........",
        "......bAb.......",
        "......bAab..b...",
        "..b...bAAb.bAb..",
        ".bAb..bAAbbAab..",
        ".bAab.bAAbbAAb..",
        "..bAabbAaAbAAb..",
        "..bAAbAAAAbAb...",
        "...bAAAAAAAAb...",
        "...bAAaAAAAb....",
        "....bAAAAAAb....",
        ".....bbbbbb.....",
        "................",
        "................",
    ]
    t["azufre"] = dibujar(cristales, {"b": "8a7d10", "A": "e9dd3a", "a": "fff79a"})
    # Iconos de las maquinas.
    t["perforadora_carbon"] = dibujar([
        "........b.......",
        ".......bbb......",
        ".......y.y......",
        ".......yyy......",
        "......y...y.....",
        "......yyyyy.....",
        "......y.k.y.....",
        ".....y..k..y....",
        ".....yyykyyy....",
        ".....y..k..y....",
        "....y...k...y...",
        "...ggggggggggg..",
        "...gssssssssgww.",
        "...g.y..k..ygww.",
        "...g.y..k..y.ww.",
        "..ddddddddddddd.",
    ], {"b": "c0392b", "y": "e8b923", "k": "6f757b", "g": "8e959c", "s": "6b7178", "w": "dcdcd6", "d": "4a4f55"})
    t["bomba_petroleo"] = dibujar([
        "................",
        "................",
        "..oo............",
        ".oooooooooooo...",
        ".oo...oo.....o..",
        ".o.....o.....o..",
        ".k.....o.....o..",
        ".k....ooo...ccc.",
        ".k...o...o..ccc.",
        ".k..o.....o.cc..",
        "rkr.o.....oooo..",
        "rrr.o.....oooow.",
        ".g..o.....o.oow.",
        "dddddddddddddddd",
        "................",
        "................",
    ], {"o": "d8662a", "k": "1b1c1f", "r": "b3312c", "c": "55595e", "w": "dcdcd6", "g": "8e959c", "d": "4a4f55"})
    t["horno_coque"] = dibujar([
        "............bb..",
        "............bb..",
        "............bb..",
        "............bb..",
        "............bb..",
        "...gggggggg.bb..",
        "..ggggggggggbb..",
        "..LLLLLLLLLLbb..",
        "..LsLsLsLsLLbb..",
        "..LfLfLfLfLLbb..",
        "..LfLfLfLfLLbb..",
        "..LsLsLsLsLLbb..",
        "..LLLLLLLLLLbb..",
        ".hhhhhhhhhhhhhh.",
        "................",
        "................",
    ], {"b": "6e3a2e", "g": "4a4f55", "L": "9a4b36", "s": "3b3d40", "f": "ff9a1c", "h": "9d9d97"})
    t["refineria"] = dibujar([
        "......w......r..",
        ".....www.....g..",
        ".....wbw.....g..",
        ".....www.....g..",
        "....yyyyy....g..",
        ".....www.....g..",
        ".....wbw.....g..",
        "....yyyyy....g..",
        ".....www.....g..",
        "..rr.www.....g..",
        "..rr.wbw..ww.g..",
        "..rrgggggwwww...",
        "..rr.www.wwww...",
        "..rr.www.wwww...",
        "hhhhhhhhhhhhhhh.",
        "................",
    ], {"w": "e6e6e1", "b": "2f6db3", "y": "e8b923", "r": "7b2b24", "g": "9aa0a6", "h": "9d9d97"})
    return t
