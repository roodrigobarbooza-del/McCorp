# Resource pack de McCorp

Un solo pack para todo el server. Lo arma Maven dentro del jar de
**MinerCorp-Pack**, que se lo manda a cada jugador al entrar. Cada plugin
pone sus archivos en su propia carpeta, así nadie pisa a nadie.

## Dónde va cada cosa

```
resourcepack/
  pack.mcmeta                         <- no tocar (formato 69 en adelante, 1.21.9+ / 26.x)
  assets/mccorp/
    items/<plugin>/<nombre>.json      <- definición del ítem (el id que usa el código)
    models/item/<plugin>/<nombre>.json  <- el modelo (Blockbench o item/generated)
    textures/item/<plugin>/<nombre>.png <- las texturas
```

`<plugin>` es una de estas carpetas, cada una de su hilo:

| carpeta     | dueño                                   |
|-------------|-----------------------------------------|
| `core`      | MinerCorp-Pack (cosas compartidas)      |
| `resources` | MinerCorp-Recursos (bidones, coque, máquinas) |
| `vehicles`  | MinerCorp-Vehicles (autos, camiones, taladros) |
| `gransede`  | MinerCorp-GranSede (muebles, carteles, obra) |

El **id del modelo** es la ruta dentro de `items/` sin el `.json`:
`items/resources/gasolina.json` → `resources/gasolina` (o completo,
`mccorp:resources/gasolina`).

Reglas de Minecraft: nombres de archivo y carpetas **solo en minúsculas**,
números, `_`, `-` y `.`. Nada de espacios ni tildes.

## Agregar un ítem plano (imagen 2D, como el bidón)

1. Textura de 16x16 (o 32x32) en `textures/item/<plugin>/<nombre>.png`.
2. Modelo en `models/item/<plugin>/<nombre>.json`:
   ```json
   { "parent": "minecraft:item/generated",
     "textures": { "layer0": "mccorp:item/<plugin>/<nombre>" } }
   ```
3. Definición en `items/<plugin>/<nombre>.json`:
   ```json
   { "model": { "type": "minecraft:model", "model": "mccorp:item/<plugin>/<nombre>" } }
   ```

El ejemplo completo es el bidón de gasolina (`resources/gasolina`).

## Agregar un modelo 3D (vehículo, máquina, horno)

1. Armalo en **Blockbench** como "Java Block/Item". Exportá el `.json` a
   `models/item/<plugin>/<nombre>.json` y las texturas a
   `textures/item/<plugin>/`. Revisá que en el json las texturas queden como
   `mccorp:item/<plugin>/<textura>` (Blockbench a veces pone rutas locales).
2. Creá la definición en `items/<plugin>/<nombre>.json` igual que arriba.
3. **Tamaño**: un modelo de ítem entra en un cubo de -16 a 32 por eje (3x3x3
   bloques). Para algo más grande (un camión, una refinería) armalo dentro
   de ese cubo y agrandalo en el juego con la `escala` del ItemDisplay
   (ej. 2.5). Si se ve mal de cerca, partilo en varias piezas, cada una con
   su propio id (`vehicles/camion_cabina`, `vehicles/camion_caja`...) y su
   propio ItemDisplay; así también pueden girar las ruedas.
4. Si tiene partes que se mueven o cambian (luces, máquina prendida),
   usá dos modelos distintos y cambiá el id desde el código, o una
   definición con `minecraft:select`/`minecraft:condition`.

## Usarlo desde el código

En el `pom.xml` del plugin:

```xml
<dependency>
    <groupId>com.isjbar</groupId>
    <artifactId>minercorp-pack</artifactId>
    <version>${project.version}</version>
    <scope>provided</scope>
</dependency>
```

En el `plugin.yml`: `softdepend: [MinerCorp-Pack]` (agregalo a la lista que
ya tengas).

`com.isjbar.minercorp.pack.api.Modelos` tiene todo:

```java
Modelos.activo();                                   // ¿los jugadores tienen el pack?
Modelos.aplicar(item, "resources/gasolina");         // ítem existente -> con modelo
Modelos.item(Material.PAPER, "vehicles/camion");     // ítem nuevo con modelo
Modelos.aplicar(itemDisplay, "vehicles/camion", Material.PAPER, 2.5f);
Modelos.mostrar(location, "resources/refineria", Material.FURNACE, 2f); // crea el ItemDisplay
```

Si el pack está apagado no se toca nada y el ítem se ve como su material
vanilla. Para que tu plugin siga andando sin MinerCorp-Pack, nombrá
`Modelos` solo dentro de una clase puente que se llama después de
comprobar `Bukkit.getPluginManager().isPluginEnabled("MinerCorp-Pack")`.
Copiá `minercorp-resources/.../resource/PackModels.java`, son 10 líneas.

Guardá el id del modelo en el `config.yml` del plugin (como
`modelo: resources/gasolina` en Recursos), así se puede cambiar sin
recompilar.

## Probar

- `python tools/validar-pack.py` revisa que cada id apunte a un modelo y
  cada modelo a texturas que existen. El CI lo corre en cada PR y falla si
  algo no cierra.
- `python tools/empaquetar-pack.py` (o `tools\empaquetar-pack.ps1` en
  Windows) arma `build/mccorp-pack.zip` y su sha1. Ese zip se puede
  poner en `.minecraft/resourcepacks` para mirarlo sin server.
- En el server, `/pack recargar` vuelve a leer el pack y se lo manda a
  todos. Para probar texturas sin recompilar: poné el zip en
  `plugins/MinerCorp-Pack/`, escribí su nombre en `propio.archivo` del
  config y `/pack recargar`.
