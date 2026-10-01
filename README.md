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

Esto genera 3 jars (uno por modulo):

```
minercorp-territory/target/MinerCorp-Territory-1.0.0.jar
minercorp-economy/target/MinerCorp-Economy-1.0.0.jar
minercorp-mining/target/MinerCorp-Mining-1.0.0.jar
```

## Instalar

Copia los 3 jars a la carpeta `plugins/` del servidor. El orden de carga lo
resuelve Bukkit solo (Mining declara `depend: [MinerCorp-Territory,
MinerCorp-Economy]` en su `plugin.yml`), pero necesitas los 3 presentes:
Mining se deshabilita solo si no encuentra las otras dos APIs registradas.

No hay dependencias externas de pago ni de terceros (ni Vault, ni
ItemsAdder): todo corre con Paper/Spigot vanilla.

## El taladro-vehiculo

La fisica/el asiento los maneja un **bote vanilla** (`org.bukkit.entity.Boat`,
con `setWorkOnLand(true)` para andar en tierra y `setMaxSpeed(...)` por
tier). No se uso ItemsAdder ni Oraxen: ambos son plugins de pago (su codigo
puede estar visible en GitHub, pero la licencia de Oraxen dice explicitamente
"debes comprar una licencia para usarlo", asi que tampoco es una alternativa
gratuita real).

Para que no se vea "como un bote con otro nombre", el bote lleva acoplada una
**carroceria propia** hecha combinando bloques vanilla existentes (sin
resourcepack): un `BlockDisplay` ancho y bajo (bloque de hierro) que cubre el
casco, y otro mas chico adelante (deepslate cincelado) simulando la punta del
taladro. Ambos se reposicionan solos seteando su `Transformation` en
`DrillVehicleManager`, siguen al bote cada `taladros.intervalo-ticks` y se
borran solos si el bote se destruye (`VehicleDestroyEvent`) o con
`/empresa taladro quitar`.

Limitaciones conocidas de este enfoque, a tener en cuenta:
- Al no haber resourcepack, la carroceria esta limitada a texturas/bloques
  que ya existen en el juego combinados de forma original - no es un modelo
  3D propio. Si mas adelante quieren eso, el camino es generar un modelo en
  Blockbench, cargarlo via custom model data y mostrarlo con el mismo
  mecanismo de `BlockDisplay`/`ItemDisplay` (requiere alojar un resourcepack
  para los jugadores), sin tocar la logica de minado.
- El casco del bote puede asomar un poco por los bordes de la carroceria
  (no hay forma de ocultarlo del todo sin un resourcepack); el ajuste fino
  de tamanos/offsets de las cajas esta en `spawnHull`/`spawnBit` de
  `DrillVehicleManager.java` por si hace falta afinarlo una vez probado en
  un server real.
- El reposicionamiento de la carroceria ocurre cada `taladros.intervalo-ticks`
  (10 ticks = 0.5s por defecto), asi que puede verse levemente entrecortada
  en vez de perfectamente fluida; bajar ese valor en `config.yml` la hace
  mas suave a costa de un poco mas de carga en el servidor.
- Mientras el bote tiene un pasajero, cada ciclo escanea una grilla delante
  suyo y **abre un tunel real**: cualquier bloque solido y rompible que
  encuentre ahi (siempre que este dentro de territorio reclamado por tu
  propia empresa) desaparece, no solo el mineral. El carbon que encuentra en
  el camino se acredita a la empresa; el resto de los bloques (tierra,
  piedra, etc) simplemente se destruyen sin dar nada, como el paso de una
  perforadora. No hace falta rieles ni nada especial, solo manejarlo como un
  bote normal.

## Comandos (`/empresa`, alias `/mc`)

```
/empresa crear <nombre>
/empresa info [nombre]
/empresa reclamar                      (parado en el chunk, debe tener veta de carbon)
/empresa liberar
/empresa invitar|aceptar|rechazar|expulsar|salir
/empresa disolver
/empresa taladro comprar <tier>        (parado dentro de tu territorio)
/empresa taladro quitar                (desarma el taladro mas cercano)
/empresa minion <colocar|quitar|lista>
/empresa refinar <cantidad>
/empresa vender <crudo|refinado> <cantidad>
/empresa depositar|retirar <monto>
/empresa menu                          (abre el HUD de cofre con botones para lo de arriba)
/saldo
/saldo dar <monto> [jugador]           (admin - minercorp.admin, para testear)
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
