# Viaje Rentable 1.12

App Android Kotlin que registra texto accesible de Uber Driver y calcula rentabilidad localmente. No pulsa controles ni acepta/rechaza viajes.

- **Actualizar datos de Uber:** actualiza la vista del texto expuesto por Accesibilidad (no es una imagen).
- **OFERTA DETALLADA / OFERTA COMPACTA:** historial local independiente offers_log, hasta 200 entradas completas; las más antiguas se eliminan por entrada.
- **Ofertas registradas** y **Exportar CSV:** muestra las ofertas y comparte CSV con fecha, tarifaCRC, recogidaKm, recogidaMin, viajeKm, viajeMin, netoCRC, netoPorKm, netoPorHora y recomendado.
- **Guardar como viaje aceptado:** conserva el motivo y texto visible de Uber en el teléfono.
- **Costos configurables:** kmPerLiter=18, fuelPricePerLiter=720, maintenancePerKm=25, minimumNetPerHour=2500 y minimumNetPerKm=250. Se usan en nuevos cálculos.
- **Diagnóstico de captura:** log independiente de 4000 caracteres.

Habilita el servicio de Accesibilidad, abre Uber Driver y deja una oferta visible. La lectura depende de los datos que Uber exponga; no se envían registros a servidores.