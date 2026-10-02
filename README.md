# MinerCorp

Sistema de "trabajos" para servidores Paper/Spigot, empezando por mineria de
carbon. Esta repartido en 4 plugins independientes conectados por interfaces
estilo Vault (via el `ServicesManager` de Bukkit):

- **MinerCorp-Territory**: reclamo de chunks y vetas de mineral. No sabe que
  existen "empresas" ni "carbon" especificamente para el dueno del territorio
  (identifica todo por un UUID generico), asi que es reutilizable por
  cualquier otro trabajo futuro (pesca, tala, etc).
- **MinerCorp-Economy**: billetera por cuenta (jugadores y empresas, todo
  UUID). Pensado para reemplazarse por un puente a Vault + Essentials el dia
  que el server los instale, sin tocar los otros plugins.
- **MinerCorp-Mining**: el trabajo de mineria en si - empresas, taladros
  vehiculo, minions, refineria y venta de carbon. Es el unico que sabe que
  existe "carbon".
- **MinerCorp-GranSede**: el lugar central del servidor. Tiendas atendidas
  por vendedores (vehiculos, taladros, ferreteria) y una mina y un bosque
  publicos que pagan jornal para los primeros fondos. Solo depende de
  Economy; los demas plugins le registran productos via `GranSedeAPI`.

## Requisitos

- Java 21+
- Servidor Paper **26.3** (o Spigot 26.3) - el pom usa `paper-api` version
  `26.3.build.142-beta` por defecto. **Si tu servidor corre otro build de
  26.3 o cambia de version mas adelante**, edita `<paper.version>` en el
  `pom.xml` de la raiz con el build que figure en
  https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml;
  el codigo no depende de nada especifico de un build puntual.

## Compilar

```bash
mvn clean package
```

Esto genera 4 jars (uno por modulo):

```
minercorp-territory/target/MinerCorp-Territory-1.0.0.jar
minercorp-economy/target/MinerCorp-Economy-1.0.0.jar
minercorp-mining/target/MinerCorp-Mining-1.0.0.jar
minercorp-gransede/target/MinerCorp-GranSede-1.0.0.jar
```

## Instalar

Copia los jars a la carpeta `plugins/` del servidor. El orden de carga lo
resuelve Bukkit solo (Mining declara `depend: [MinerCorp-Territory,
MinerCorp-Economy]` en su `plugin.yml`), pero necesitas los 3 presentes:
Mining se deshabilita solo si no encuentra las otras dos APIs registradas.

No hay dependencias externas de pago ni de terceros (ni Vault, ni
ItemsAdder): todo corre con Paper/Spigot vanilla.

## El taladro-vehiculo

Es un **vehiculo propio que mueve el servidor**, armado con entidades
vanilla (sin resourcepack ni plugins de terceros):

- una raiz invisible (`BlockDisplay` sin bloque) donde va sentado el jugador
  y que guarda empresa, tier y combustible en su PDC;
- la carroceria `DrillModel` (chasis, orugas, motor, parabrisas, faros y una
  punta conica que gira al perforar), con colores por tier configurables en
  `taladros.tier-N.modelo`;
- un `Interaction` invisible para poder hacerle click derecho.

Antes era un bote vanilla, pero un bote con jugador arriba lo mueve el
cliente: `setMaxSpeed`/`setWorkOnLand` no tenian efecto real, en tierra
andaba lentisimo y todos los tiers iban igual.

### Como se usa

1. `/empresa taladro comprar <tier>` parado dentro de tu territorio.
2. Cargale combustible: click derecho al taladro con carbon, carbon vegetal o
   bloque de carbon en la mano, o `/empresa taladro cargar [cantidad]` para
   usar carbon crudo de la empresa.
3. Click derecho con cualquier otra cosa para subirte.
4. Controles: **W/S** avanzar y retroceder, **A/D** girar, **Espacio**
   frenar, **Shift** bajarse. Arriba ves una barra con el combustible y el
   carbon que sacaste en el viaje.

### Como perfora

- Mientras apretas W, perfora una caja de `ancho` x `alto` x `profundidad`
  delante del vehiculo (por tier en `config.yml`). El piso donde esta apoyado
  nunca se rompe, asi que el tunel sale derecho y no se hunde.
- Cada bloque tarda segun su dureza (`ticks-por-dureza`, dividido por la
  `potencia` del tier) y gasta `combustible.por-bloque`. Sin combustible se
  puede manejar despacio pero no perfora.
- No rompe: bloques irrompibles, los de `lista-negra`, nada con inventario
  o datos (cofres, hornos, spawners, carteles...), nada fuera de tu
  territorio, ni bloques pegados a agua o lava (`frenar-ante-liquidos`). En
  esos casos frena y te avisa en la barra de accion.
- No puede salir del territorio de tu empresa.
- Recompensas por bloque en `taladros.recompensas`: el carbon sale de la
  veta del chunk como antes (si la veta esta agotada, el bloque se rompe
  igual sin recompensa); el resto da una XP chica de empresa por defecto.
- Empresas y vetas se guardan cada `guardado-segundos`, no por bloque.

### Notas

- Golpear el taladro (click izquierdo) lo guarda en el garaje de la empresa,
  con su combustible. Se vuelve a sacar sin pagar desde `/empresa menu` >
  Taladro-vehiculo (ultima fila) o con `/empresa taladro sacar`.
- Los taladros-bote de la version anterior se convierten solos al vehiculo
  nuevo (misma empresa y tier, tanque vacio) cuando se carga su chunk.
- Si la raiz se borra con `/kill`, la carroceria y el asiento quedan sueltos:
  `/empresa taladro quitar` cerca de ellos los limpia.
- La altura del asiento se ajusta con `taladros.efectos.altura-asiento`.
- Al arrancar, el plugin completa tu `config.yml` con las claves nuevas que
  falten. Si todavia tenia la seccion `taladros:` del taladro-bote (con
  `radio`), la reemplaza entera por la nueva y lo avisa en la consola.

## La Gran Sede

Lugar central del servidor (no confundir con la sede de cada empresa). El
edificio lo construyen los admins; el plugin pone las reglas:

1. Marca las esquinas con `/gransede pos1` y `/gransede pos2` y crea la zona
   con `/gransede zona crear <nombre> <SEDE|MINA|BOSQUE>`. En SEDE no se rompe
   ni se pone nada, sin PvP, mobs hostiles, fuego ni explosiones. Una MINA o
   BOSQUE adentro de la SEDE tiene sus propias reglas.
2. `/gransede zona spawn` marca el punto de llegada (jugadores nuevos y
   `/gransede ir`).
3. `/gransede npc crear <concesionaria|taladros|ferreteria>` pone un
   vendedor donde estas parado; `/gransede npc borrar` mirandolo lo saca.
4. Un admin en **creativo** puede construir en todas las zonas.

En la mina y el bosque solo se rompen los bloques de `trabajos.*.pago`: cada
uno paga ese jornal a la billetera, no suelta items, queda como bloque agotado
y vuelve solo (la mina sortea mineral nuevo por peso). Hay un tope por hora
por jugador (`trabajos.tope-por-hora`).

Las tiendas venden items vanilla o comandos de consola definidos en
`tiendas.*.productos`, mas lo que registren otros plugins:

```java
GranSedeAPI api = Bukkit.getServicesManager().load(GranSedeAPI.class);
api.registrarProducto(new Producto("camion", "concesionaria", "Camion",
        new ItemStack(Material.MINECART), List.of("Carga 27 stacks"), 2500.0,
        player -> vehiculos.entregar(player, "camion")));
```

Se cobra de la billetera personal; si `entregar` devuelve false, se devuelve
el dinero. Un producto creado con `cobraPropio = true` lo cobra el plugin que
lo registro (por ejemplo de la cuenta de la empresa) y la Gran Sede solo
muestra el precio.

## Comandos (`/empresa`, alias `/mc`)

```
/empresa crear <nombre>
/empresa info [nombre]
/empresa reclamar                      (parado en el chunk, debe tener veta de carbon)
/empresa liberar
/empresa invitar|aceptar|rechazar|expulsar|salir
/empresa disolver
/empresa taladro comprar <tier>        (parado dentro de tu territorio)
/empresa taladro cargar [cantidad]     (combustible con carbon crudo de la empresa)
/empresa taladro guardar               (guarda el taladro cercano en el garaje de la empresa)
/empresa taladro sacar [numero]        (saca un taladro del garaje, parado en tu territorio)
/empresa taladro quitar                (desarma el taladro mas cercano)
/empresa minion <colocar|quitar|lista>
/empresa refinar <cantidad>
/empresa vender <crudo|refinado> <cantidad>
/empresa depositar|retirar <monto>
/empresa menu                          (abre el HUD de cofre con botones para lo de arriba)
/saldo
/saldo dar <monto> [jugador]           (admin - minercorp.admin, para testear)
/empresa darcarbon <cantidad>          (admin - suma carbon crudo a tu empresa, para testear el taladro)
```

## Aviso de territorio y menu HUD

- **Aviso al entrar a un territorio**: al cruzar a un chunk reclamado (caminando
  o manejando el taladro) aparece un action bar abajo con el nombre de la
  empresa duena y el proposito del territorio (por ahora siempre "Mineria de
  carbon", hardcodeado en `CompanyManager.claim(...)` - el dia que se sume un
  segundo rubro, ese proposito deberia pasar a ser dinamico por tipo de
  trabajo). Vive en MinerCorp-Territory (`TerritoryEntryListener`), no sabe
  nada de mineria especificamente.
- **Menu HUD (`/empresa menu`)**: un inventario tipo cofre con botones para
  fundar empresa (te pide el nombre por chat), ver info, reclamar el chunk
  donde estas parado, comprar taladros (submenu con los 3 tiers), colocar un
  minion, refinar todo el crudo disponible y vender todo el crudo/refinado.
  Las acciones con cantidad especifica (`/empresa refinar 50`,
  `/empresa vender crudo 20`) y la gestion de colaboradores/deposito/retiro
  siguen siendo solo por comando - no entraron en este primer menu para no
  complicar la UI con inputs numericos.

## Checklist de prueba manual

No hay un servidor Paper disponible en este entorno para levantar y probar
en vivo, asi que quedo solo verificado que los 3 modulos compilan. Antes de
darlo por terminado, probar en un server real:

1. `/empresa crear MinaDelSol` - se descuenta el costo de fundar del saldo personal.
2. Pararse sobre un chunk con mineral de carbon visible y `/empresa reclamar` - debe reportar cuantos bloques detecto.
3. Que otro jugador (no colaborador) intente romper un bloque en ese chunk - debe cancelarse.
4. `/empresa taladro comprar 1`, subirse al bote con click derecho y manejarlo contra la veta - el carbon crudo de la empresa debe subir (`/empresa info`) y el bloque debe desaparecer.
5. `/empresa minion colocar` dentro del territorio - la reserva de la veta debe bajar solo con el tiempo, incluso sin nadie conectado al taladro.
6. `/empresa refinar <cantidad>` y `/empresa vender crudo <cantidad>` / `vender refinado <cantidad>` - el balance de la empresa (`/empresa info`) debe reflejar la venta.
7. `/empresa retirar <monto>` (dueno) y `/saldo` - el dinero debe pasar de la empresa al jugador.
8. Reiniciar el server y confirmar que empresas, territorios, vetas y minions persistieron (los taladros tambien, son entidades vanilla que el mundo guarda solo).
9. Caminar (o manejar el taladro) hacia un chunk reclamado - debe aparecer el action bar; salir y volver a entrar a un chunk distinto de la misma empresa no deberia repetir el mensaje si ya se mostro para ese chunk.
10. `/empresa menu` sin empresa - solo debe ofrecer "Fundar empresa"; clickearlo, escribir un nombre por chat y confirmar que se crea igual que con `/empresa crear`.
11. Con empresa, probar cada boton del menu (info, reclamar, taladro ▸ tier 1/2/3, minion, refinar todo, vender todo crudo/refinado) y comparar contra el comando equivalente.
