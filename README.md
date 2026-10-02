# MinerCorp

Sistema de "trabajos" para servidores Paper/Spigot, empezando por mineria de
carbon. Esta repartido en 4 plugins independientes conectados por interfaces
estilo Vault (via el `ServicesManager` de Bukkit):

- **MinerCorp-Territory**: reclamo de chunks y vetas de mineral. No sabe que
  existen "empresas" ni "carbon" especificamente para el dueno del territorio
  (identifica todo por un UUID generico), asi que es reutilizable por
  cualquier otro trabajo futuro (pesca, tala, etc).
- **MinerCorp-Economy**: el dinero. Cuentas de jugadores y empresas (todo
  UUID), transacciones atomicas con motivo e historial, ranking, `/pagar` y
  menu `/billetera`. Los otros plugins cobran y pagan con `EconomyAPI`; la
  guia para usarla esta en la seccion "Economia" mas abajo.
- **MinerCorp-Mining**: el trabajo de mineria en si - empresas, taladros
  vehiculo, minions, refineria y venta de carbon. Es el unico que sabe que
  existe "carbon".
- **MinerCorp-Recursos**: petroleo y otros recursos industriales, y las
  maquinas que los extraen y refinan (perforadora, bomba de petroleo, horno de
  coque, refineria). Ver [minercorp-resources/README.md](minercorp-resources/README.md).
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
minercorp-vehicles/target/MinerCorp-Vehicles-1.0.0.jar
minercorp-gransede/target/MinerCorp-GranSede-1.0.0.jar
minercorp-pack/target/MinerCorp-Pack-1.0.0.jar
```

## Instalar

Copia los jars a la carpeta `plugins/` del servidor. El orden de carga lo
resuelve Bukkit solo (Mining declara `depend: [MinerCorp-Territory,
MinerCorp-Economy]` en su `plugin.yml`), pero necesitas los 3 presentes:
Mining se deshabilita solo si no encuentra las otras dos APIs registradas.

No hay dependencias externas de pago ni de terceros (ni Vault, ni
ItemsAdder): todo corre con Paper/Spigot vanilla.

## Resource pack (MinerCorp-Pack)

Los modelos e imagenes propias (vehiculos, maquinas, bidones...) estan en la
carpeta [`resourcepack/`](resourcepack/LEEME.md) del repo. Al compilar, el
pack queda **adentro** de `MinerCorp-Pack-1.0.0.jar`, y ese plugin se lo
manda a cada jugador cuando entra. Sin MinerCorp-Pack todo sigue andando y
los items se ven como items vanilla.

Para usarlo en el server (Windows):

1. Copia `MinerCorp-Pack-1.0.0.jar` a `plugins\` con los demas y reinicia.
2. Listo para jugar en tu PC o en tu red de casa: el plugin sirve el pack en
   el puerto **8164**. Si Windows pregunta por el firewall, deja pasar Java.
3. Si tus amigos entran desde afuera, abri en el router el puerto **8164
   TCP** hacia la PC del server, igual que hiciste con el 25565.
4. No hay que tocar `server.properties` (dejar `resource-pack=` vacio).

Si preferis no abrir otro puerto, subi `build/mccorp-pack.zip` (sale de
`tools\empaquetar-pack.ps1` o del artefacto "resourcepack" de GitHub
Actions) a un link directo y en `plugins/MinerCorp-Pack/config.yml` pone
`modo: url` y `url.direccion: <link>`; el sha1 lo calcula solo.

Comandos: `/pack` te lo vuelve a mandar; `/pack info`, `/pack recargar` y
`/pack reenviar <jugador|todos>` para admins.

Como agregar modelos: [resourcepack/LEEME.md](resourcepack/LEEME.md).

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

1. Se compran en la Gran Sede: el resto en la concesionaria, los taladros en
   la tienda de taladros (tambien con `/vehiculo tienda`). Van al garaje: los de jugador los paga el jugador, el
   taladro lo paga la empresa (con nivel minimo y la sede terminada).
2. `/garaje` (o el boton Garaje del `/empresa menu`) para sacarlos delante
   tuyo. El menu tambien muestra donde quedaron los que estan afuera.
3. Click derecho con combustible en la mano para cargarlo: carbon, o con
   MinerCorp-Recursos los bidones de gasolina y diesel y el carbon crudo.
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
/empresa minion <colocar|quitar|lista>
/empresa carbon <cantidad|todo>        (saca carbon crudo como items para el Horno de coque)
/empresa vender <crudo|refinado> <cantidad>
/empresa depositar|retirar <monto>
/empresa menu                          (abre el HUD de cofre con botones para lo de arriba)
/saldo [jugador]                       (alias /dinero, /bal; muestra tambien el balance de tus empresas)
/pagar <jugador o empresa> <monto>     (montos como 500, 2,5, 1.5k; los grandes piden confirmar)
/movimientos [pagina]                  (historial con detalle al pasar el mouse)
/top [jugadores|empresas]              (ranking de fortunas)
/billetera                             (menu: saldo, movimientos, ranking, cambiar entre tus cuentas)
/eco dar|quitar|fijar <cuenta> <monto> (admin - minercorp.economy.admin)
/eco ver <cuenta> | stats | recargar   (admin)
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

## Economia

- Los montos se guardan en centavos (`long`), asi no hay errores de redondeo
  y no se aceptan montos negativos ni NaN.
- `plugins/MinerCorp-Economy/cuentas.yml` se guarda en segundo plano cada 30
  segundos si hubo cambios, con escritura atomica (archivo temporal + rename),
  y al apagar el server. Al prender se hace una copia diaria en `copias/`.
- Cada movimiento queda en el historial de la cuenta (ultimos 100) y en un log
  de auditoria mensual en `transacciones/AAAA-MM.log`.
- El `accounts.yml` de la version anterior se migra solo la primera vez y queda
  como `accounts.yml.v1.bak`.
- Al cerrar una empresa, lo que quedaba en su cuenta vuelve al dueno.
- Todos los textos y colores estan en `config.yml` (MiniMessage).

Para otros plugins (cobrar, pagar, vender al sistema, mercados):

```java
EconomyAPI eco = getServer().getServicesManager().load(EconomyAPI.class);

// Comprar algo en la gran sede (el dinero sale de la economia)
TransactionResult r = eco.withdraw(jugador, 2500, Reason.of(Reason.COMPRA, "Camion tier 1"));
if (!r.success()) player.sendMessage(r.message());

// Vender recursos al sistema (el dinero entra a la economia)
eco.deposit(empresa, 640, Reason.of(Reason.VENTA, "64 barriles de petroleo"));

// Varias patas atomicas: pago al vendedor + comision al servidor
eco.execute(Transaction.builder(Reason.of(Reason.VENTA, "Mercado: 32 de cobre"))
        .move(comprador, vendedor, 300)
        .move(comprador, EconomyAPI.SERVER, 15)
        .build());

// Empresas: registrarlas para que salgan en el ranking y avisen al dueno
eco.registerAccount(empresa.getId(), AccountType.COMPANY, empresa.getName(), empresa.getOwner());
```

`MoneyTransactionEvent` se lanza en el hilo principal despues de cada
transaccion.

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
7. `/empresa retirar <monto>` (dueno) y `/saldo` - el dinero debe pasar de la empresa al jugador, y los dos movimientos deben verse en `/movimientos` y `/billetera`.
8. Reiniciar el server y confirmar que empresas, territorios, vetas y minions persistieron (los taladros tambien, son entidades vanilla que el mundo guarda solo).
9. Caminar (o manejar el taladro) hacia un chunk reclamado - debe aparecer el action bar; salir y volver a entrar a un chunk distinto de la misma empresa no deberia repetir el mensaje si ya se mostro para ese chunk.
10. `/empresa menu` sin empresa - solo debe ofrecer "Fundar empresa"; clickearlo, escribir un nombre por chat y confirmar que se crea igual que con `/empresa crear`.
11. Con empresa, probar cada boton del menu (info, reclamar, taladro ▸ tier 1/2/3, minion, refinar todo, vender todo crudo/refinado) y comparar contra el comando equivalente.
