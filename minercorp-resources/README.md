# MinerCorp-Recursos

Recursos que no existen en Minecraft (petroleo y sus derivados, coque) y las
maquinas para extraerlos y transformarlos. **El refinado ya no es por
comando:** todo pasa por una maquina colocada en el territorio de tu empresa,
que hay que cargar con materia prima y combustible.

Requiere MinerCorp-Territory y MinerCorp-Economy. Diseño completo y fases
siguientes (plataforma marina, gas, electricidad, bauxita, litio) en el plan
del proyecto (`plan/minerales.md`).

## Cadena de produccion

```
veta de carbon ──[Perforadora de carbon]──> carbon crudo ──[Horno de coque]──> coque + alquitran
yacimiento     ──[Bomba de petroleo]─────> petroleo crudo ─[Refineria]──────> gasolina, diesel, asfalto, azufre
```

Combustible: cada maquina acepta los de su lista en `config.yml`
(carbon vanilla, carbon crudo, coque, diesel). El coque y el diesel duran mucho
mas que el carbon.

| Maquina | Cuerpo (detras del panel) | Entrada | Salida |
|---|---|---|---|
| Perforadora de carbon | torre de 8 bloques, piso de perforacion, sarta que gira, casa de motores (3x3) | veta del chunk | carbon crudo |
| Bomba de petroleo | balancin con cabeza de caballo, manivela con contrapesos, motor (1x7) | yacimiento del chunk | petroleo crudo |
| Horno de coque | bateria de 4 hornos de ladrillo con puertas que brillan y chimenea de 8 bloques (5x3) | 2 carbon crudo | coque (+ alquitran 25%) |
| Refineria | horno calentador, columna de destilacion de 9 bloques, tanques y antorcha (5x5) | 1 petroleo crudo | gasolina (+ diesel 60%, asfalto 30%, azufre 10%) |

Cada maquina es un **panel de control** (el bloque que se coloca, donde se
abre el menu) y su **cuerpo**, que se arma solo detras del panel, del lado
opuesto a donde estas parado, dejando un bloque de separacion con un conducto
de cables. El cuerpo necesita lugar libre y tiene que entrar entero en el
territorio de tu empresa. Se llena de barreras invisibles para que no se
atraviese (`colision-maquinas`). Click derecho al panel o al cuerpo abre el
menu; para desarmarla se rompe el panel.

## Resource pack

Con `resourcepack.usar: true` (cuando el server manda el pack a los
jugadores), las maquinas usan modelos 3D propios con texturas (chapa, ladrillo
refractario, rejilla, etc) y los recursos tienen su propia imagen (bidones de
gasolina y diesel, barril de petroleo, coque, etc). Sin el pack se arman con
bloques vanilla, con la misma forma.

Los archivos del pack estan en `resourcepack/assets/mccorp/*/resources/`. Se
generan con `python3 tools/recursos/generar.py` (necesita Pillow), que tambien
escribe `src/main/resources/modelos/maquinas.json`, la geometria que lee el
plugin. Para cambiar la forma de una maquina se edita
`tools/recursos/geometria.py` y se vuelve a generar.

## Como se usa

1. Se compra en la tienda "Taladros" de la Gran Sede (si esta instalada), o un
   admin la da con `/recursos dar <jugador> refineria`.
2. Colocala en un chunk de tu empresa. El modelo se arma solo arriba del bloque.
3. Click derecho: panel con entrada, combustible y salidas. Shift + click
   desde tu inventario manda cada cosa a su lugar. Shift + click derecho a la
   maquina con algo en la mano lo carga sin abrir el panel.
4. Mientras trabaja se mueve (el balancin, la barra) y echa humo/llama; el
   cartel de arriba dice si le falta algo.
5. Romperla devuelve la maquina y lo que tenia adentro.

El carbon crudo que juntan los taladros y minions sigue en la empresa: se saca
como items con `/empresa carbon <cantidad|todo>` (o "Sacar carbon crudo" en el
menu) para llevarlo al horno.

## Comandos (`/recursos`, alias `/rec`)

```
/recursos sondear                    carbon y petroleo que hay en el chunk donde estas
/recursos vender [todo]              vende lo que tenes en la mano (o todo); en tu territorio va a la empresa
/recursos lista                      recursos, precios y maquinas
/recursos dar <jugador> <id> [n]     (admin) un recurso o una maquina
/recursos recargar                   (admin) relee config.yml
```

## Para otros plugins

`ResourcesAPI` por el ServicesManager: crear e identificar items de recursos
(`createItem("gasolina", 4)`, `identify(item)`), precios, y el item de cada
maquina para venderlo en una tienda.
