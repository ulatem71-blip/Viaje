# Viaje Rentable 1.14

App Android Kotlin que lee el texto accesible de Uber Driver y calcula rentabilidad localmente. **El modo demo solo simula una decisión; no pulsa controles ni acepta/rechaza viajes en Uber.** Cualquier aceptación real requiere una decisión explícita del dueño y debe desarrollarse en una rama aparte, desactivada por defecto.

- **Modo demo:** activado por defecto. Para cada oferta detallada detectada, simula si la habría aceptado o rechazado con base en neto, neto/km y neto/hora. No interactúa con Uber.
- **Simular oferta:** pega el texto de una oferta, pulsa **Usar ejemplo** para cargar una oferta de muestra o pulsa **Simular**. Muestra tarifa, neto, neto/km, neto/hora y decisión con motivos. No guarda nada en el historial ni toca Uber.
- **Ofertas registradas** y **Exportar CSV:** historial local independiente offers_log, hasta 200 ofertas; la compacta inicial y su detalle pueden quedar como dos entradas, pero las alternancias repetidas y duplicados exactos se suprimen durante 120 segundos. Una ruta distinta con la misma tarifa se conserva.
- **Actualizar datos de Uber:** actualiza la vista del texto expuesto por Accesibilidad (no es una imagen).
- **Guardar como viaje aceptado:** guarda manualmente el motivo y el texto visible de Uber en el teléfono.
- **Costos configurables:** kmPerLiter=18, fuelPricePerLiter=720, maintenancePerKm=25, minimumNetPerHour=2500 y minimumNetPerKm=250. Se usan en nuevos cálculos.
- **Diagnóstico de captura:** log independiente de 4000 caracteres; los diagnósticos se limitan a una línea cada 30 segundos y se eliminan antes que las ofertas cuando el log llega al límite.
- **Seguridad:** el servicio solo lee el paquete com.ubercab.driver; el workflow falla si encuentra patrones de automatización de clics en app/src/main.

Habilita el servicio de Accesibilidad, abre Uber Driver y deja una oferta visible. La lectura depende de los datos que Uber exponga; no se envían registros a servidores.
