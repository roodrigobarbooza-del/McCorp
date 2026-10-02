# MinerCorp

Sistema de "trabajos" para servidores Paper/Spigot, empezando por mineria de
carbon. Esta repartido en 3 plugins independientes conectados por interfaces
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
minercorp-vehicles/target/MinerCorp-Vehicles-1.0.0.jar
```

## Instalar

Copia los 4 jars a la carpeta `plugins/` del servidor. El orden de carga lo
resuelve Bukkit solo (Mining declara `depend: [MinerCorp-Territory,
MinerCorp-Economy]` en su `plugin.yml`), pero necesitas los 3 presentes:
Mining se deshabilita solo si no encuentra las otras dos APIs registradas.

No hay dependencias externas de pago ni de terceros (ni Vault, ni
ItemsAdder): todo corre con Paper/Spigot vanilla.

## Vehiculos (MinerCorp-Vehicles)

Los vehiculos tienen su propio plugin, `MinerCorp-Vehicles` (depende de
Territory, Economy y Mining). Son **vehiculos que mueve el servidor**,
dibujados con display entities y bloques vanilla, sin resourcepack:

- **Camioneta**: pickup chica de dos tonos con baca, rapida, 9 espacios de carga.
- **Camion de carga**: cabina adelantada con caja de lona, 2 asientos, 27 espacios.
- **Taladro** (3 tiers): el de siempre, de la empresa, perfora tuneles en su territorio.

Todo se configura en `plugins/MinerCorp-Vehicles/config.yml` (precio,
velocidad, aceleracion, giro, tanque, consumo, combustibles, carga y colores
de cada tipo). Con `item-model` un tipo se dibuja con un modelo de
resourcepack en vez de bloques.

### Como se usa

1. Se compran en el concesionario de la gran sede (por ahora tambien con
   `/vehiculo tienda`). Van al garaje: los de jugador los paga el jugador, el
   taladro lo paga la empresa (con nivel minimo y la sede terminada).
2. `/garaje` (o el boton Garaje del `/empresa menu`) para sacarlos delante
   tuyo. El menu tambien muestra donde quedaron los que estan afuera.
3. Click derecho con combustible en la mano para cargarlo (hoy carbon; la
   gasolina y el diesel los registra el plugin de minerales por API).
   `/vehiculo cargar [cantidad]` usa carbon crudo de la empresa.
4. Click derecho para subirte: al volante si es tuyo, si no de acompanante.
   Shift + click derecho abre la carga. Golpearlo lo guarda en el garaje.
5. Controles: **W** acelerar, **S** frenar y reversa, **A/D** doblar (las
   ruedas solo doblan andando; el taladro gira en el lugar), **Espacio**
   freno de mano, **Shift** bajarse. La barra de arriba muestra velocidad y
   combustible.

### Fisica

Se apoya en dos puntos (adelante y atras) que siguen el terreno: sube
escalones de un bloque, baja de a un bloque y se inclina en las subidas. No
atraviesa paredes ni al doblar, no entra al agua y cae si no hay piso.

### El taladro

- Mientras apretas W, perfora una caja de `ancho` x `alto` x `profundidad`
  delante (tipos `taladro-N.perforacion`). El piso nunca se rompe.
- Cada bloque tarda segun su dureza (`taladro.ticks-por-dureza`, dividido
  por la `potencia`) y gasta `taladro.consumo-por-bloque`.
- No rompe irrompibles, `lista-negra`, nada con inventario, nada fuera del
  territorio, ni bloques pegados a agua o lava. No puede salir del territorio.
- Recompensas en `taladro.recompensas` (el carbon sale de la veta del chunk).

### Migracion desde la version anterior

Al instalar el plugin nuevo, los taladros que habia en el mundo se rearman
solos (misma empresa, tier y combustible) cuando se carga su chunk, y los
que estaban guardados en el garaje de la empresa pasan al garaje nuevo. La
seccion `taladros` del config de Mining se borra; los tiers ahora son los
tipos `taladro-1/2/3` de Vehicles. `/vehiculo limpiar` (admin) borra restos
sueltos cerca.

## Comandos (`/empresa`, alias `/mc`)

```
/empresa crear <nombre>
/empresa info [nombre]
/empresa reclamar                      (parado en el chunk, debe tener veta de carbon)
/empresa liberar
/empresa invitar|aceptar|rechazar|expulsar|salir
/empresa disolver
/empresa minion <colocar|quitar|lista>
/empresa refinar <cantidad>
/empresa vender <crudo|refinado> <cantidad>
/empresa depositar|retirar <monto>
/empresa menu                          (abre el HUD de cofre con botones para lo de arriba)
/saldo
/saldo dar <monto> [jugador]           (admin - minercorp.admin, para testear)
/empresa darcarbon <cantidad>          (admin - suma carbon crudo a tu empresa, para testear el taladro)
```

## Comandos de vehiculos (`/vehiculo`, alias `/veh`)

```
/garaje                                (tus vehiculos y los de tu empresa)
/vehiculo tienda                       (concesionario por comando, si tienda-por-comando: true)
/vehiculo guardar                      (guarda el vehiculo cercano)
/vehiculo cargar [cantidad]            (combustible con carbon crudo de la empresa)
/vehiculo dar <jugador> <tipo>         (admin)
/vehiculo limpiar                      (admin - borra restos sueltos cerca)
/vehiculo recargar                     (admin - relee el config)
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
