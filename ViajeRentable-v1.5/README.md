# Viaje Rentable 1.13

App Android Kotlin que lee el texto accesible de Uber Driver y calcula rentabilidad localmente. **El modo demo solo simula una decisión; no pulsa controles ni acepta/rechaza viajes en Uber.** Cualquier aceptación real requiere una decisión explícita del dueño y debe desarrollarse en una rama aparte, desactivada por defecto.

- **Modo demo:** activado por defecto. Para cada oferta detallada detectada, simula si la habría aceptado o rechazado con base en neto, neto/km y neto/hora. No interactúa con Uber.
- **Simular oferta:** pega el texto de una oferta, pulsa **Usar ejemplo** para cargar una oferta de muestra o pulsa **Simular**. Muestra tarifa, neto, neto/km, neto/hora y decisión con motivos. No guarda nada en el historial ni toca Uber.
- **Ofertas registradas** y **Exportar CSV:** historial local independiente offers_log, hasta 200 entradas completas; el CSV incluye decisionDemo y motivos. Las entradas anteriores a la versión 1.13 sin decisión demo se siguen leyendo.
- **Actualizar datos de Uber:** actualiza la vista del texto expuesto por Accesibilidad (no es una imagen).
- **Guardar como viaje aceptado:** guarda manualmente el motivo y el texto visible de Uber en el teléfono.
- **Costos configurables:** kmPerLiter=18, fuelPricePerLiter=720, maintenancePerKm=25, minimumNetPerHour=2500 y minimumNetPerKm=250. Se usan en nuevos cálculos.
- **Diagnóstico de captura:** log independiente de 4000 caracteres.
- **Seguridad:** el servicio solo lee el paquete com.ubercab.driver; el workflow falla si encuentra patrones de automatización de clics en app/src/main.

Habilita el servicio de Accesibilidad, abre Uber Driver y deja una oferta visible. La lectura depende de los datos que Uber exponga; no se envían registros a servidores.
