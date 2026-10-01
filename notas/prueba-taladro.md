# Prueba del taladro-vehículo (revisión del 2026-10-01)

No se pudo levantar un servidor desde la nube: la red del entorno bloquea
`repo.papermc.io` y `fill.papermc.io` (403), así que ni compila ni se puede
bajar Paper. Además, manejar el bote requiere un jugador real. Esto es una
revisión del código de `DrillVehicleManager.java` contra lo que pedía la
prueba, con lo que casi seguro va a fallar en el server.

## Fallos que se ven en el código

1. **El túnel del tier 1 es más angosto que el bote.** `radio: 1` rompe un
   solo bloque (1 de ancho), pero el hitbox del bote mide 1.375 de ancho.
   El bote roza los bloques de los costados y se traba. No rompe "todos los
   bloques sólidos de delante", solo el del centro.
2. **Los tiers 2 y 3 se hunden.** La grilla va de `up = -half` a `+half`
   centrada en los pies del bote, así que también rompe el piso de delante.
   El bote cae un bloque, el siguiente ciclo vuelve a romper el piso y el
   taladro baja en escalera.
3. **Carbón de veta agotada = muro.** Si `extractFromVein` devuelve 0, el
   código hace `continue` y no rompe el mineral. Con la veta vacía, el
   bloque de carbón queda ahí y el taladro no avanza.
4. **Avanza a tirones.** Rompe un solo bloque de fondo cada
   `intervalo-ticks` (0,5 s). El bote va a 0,4 bloques/tick (8 por ciclo),
   choca, pierde la velocidad y espera. Máximo ~2 bloques/s.
5. **La punta del taladro probablemente queda atrás.** `spawnBit` la
   desplaza hacia -Z local, pero el frente del bote con yaw 0 es +Z. Hay que
   confirmarlo a ojo en el server.
6. **La carrocería se ve entrecortada.** Se teletransporta cada 10 ticks;
   `setInterpolationDuration` no suaviza teleports (eso es
   `setTeleportDuration`). Si el bote se borra con `/kill`, los BlockDisplay
   quedan huérfanos porque no salta `VehicleDestroyEvent`.
7. **Destruye cofres con contenido.** `setType(AIR)` sobre un cofre,
   horno, etc. borra lo que tenga dentro. Tampoco frena agua/lava (no son
   sólidos), así que el túnel se puede inundar.
8. **Rendimiento.** `companies().save()` escribe a disco en el hilo
   principal cada ciclo en el que entra carbón, y `tick()` recorre todos los
   botes de todos los mundos.

## Lo que sí parece correcto

- No rompe fuera del territorio propio: cada bloque se chequea contra
  `territory.getOwner(chunk)` antes de tocarlo (pero el bote puede salir
  igual del territorio, solo deja de romper).
- El carbón se acredita a la empresa (`addRawCoal` + XP + `checkLevelUp`).
- Solo miembros de la empresa pueden subirse (`DrillVehicleListener`).
- La dirección de avance (`loc.getDirection()`) coincide con cómo el
  vanilla mueve el bote.

## Ideas para el rediseño

- Romper una caja delante del hitbox real (mín. 2 de ancho × 2 de alto,
  nunca el piso), y varias capas de profundidad según la velocidad.
- Correr el minado cada 1–2 ticks, o limitar la velocidad a lo que se
  rompe por ciclo, para que avance fluido.
- Si la veta está agotada, romper el carbón igual (sin acreditar).
- Montar la carrocería como `ItemDisplay`/`BlockDisplay` con
  `setTeleportDuration` o rotarla por `Transformation` en lugar de teleport.
- Saltar bloques con inventario (o soltar su contenido) y tapar líquidos.
- Guardar de forma asíncrona o cada X segundos.

## Checklist para probar en el server real

1. `/empresa taladro comprar 1` dentro del territorio, subirse, ir contra
   una pared de piedra: ¿avanza o se traba? (fallo 1)
2. Mismo con tier 2: ¿baja en escalera? (fallo 2)
3. ¿La punta de deepslate queda adelante o atrás? (fallo 5)
4. Manejar contra carbón y mirar `/empresa info`: ¿sube el crudo?
5. Agotar la veta y volver a chocar el carbón: ¿se frena? (fallo 3)
6. Cruzar el borde del chunk reclamado: no debe romper nada afuera.
