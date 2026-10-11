# HANDOFF

## Estado actual
- Repositorio: `ulatem71-blip/Viaje`; aplicación Kotlin en `ViajeRentable-v1.5/` (versión 1.12).
- `main` compila y pasa las pruebas en el CI (build, 6 tests de OfferParser, tests de Profitability, APK como artefacto `ViajeRentable-apk`).
- Arreglos ya incluidos: errores de sintaxis en `OffersStore.kt` y `AcceptedTripStore.kt`; el regex de tramos del parser ahora captura la duración (horas, minutos y distancias en metros); el servicio registra OFERTA DETALLADA con el cálculo de rentabilidad.
- El workflow publica el error de Gradle como anotación (`::error`) cuando la compilación falla; se pueden leer sin iniciar sesión en GitHub.
- Nada de esto se ha probado todavía con la aplicación real de Uber Driver en un teléfono.

## Reglas del proyecto
- La app solo registra ofertas y calcula rentabilidad; nunca acepta ni rechaza viajes automáticamente.
- No modificar el paso del keystore ni el secret `VIAJE_DEBUG_KEYSTORE_BASE64` del workflow.
- Solo leer el paquete `com.ubercab.driver`.
- Kotlin: no escribir funciones locales de una línea seguidas de punto y coma; una instrucción por línea.
- Crear una rama nueva por tarea, validar localmente antes del commit cuando sea posible, esperar CI verde y solo entonces actualizar `main` tras sincronizarlo.
- Si el CI falla, reportar literalmente los errores de compilación o el bloque `What went wrong`; no adivinar.

## Pendiente
- Instalar el APK en un teléfono con Uber Driver y comparar, en "Ofertas registradas", tarifa, km y minutos contra la pantalla. Si no coinciden, copiar el texto de "Última pantalla de Uber" y ajustar el parser.
- Confirmar los costos configurables (por defecto 18 km/L, 720 colones/L, 25 colones/km de mantenimiento, 2500 colones/h y 250 colones/km mínimos) y probar "Exportar CSV".
- Decisión abierta: la descripción del proyecto menciona aceptar viajes rentables automáticamente, pero la app actual solo registra y calcula (ver Reglas). Antes de implementar cualquier aceptación automática hay que revisar los términos de Uber, que suelen prohibir herramientas de automatización.

## Historial de cambios
- 2026-10-11: creada la rama `chatgpt/verify-parser-ci` desde la punta de `main` para verificar la corrección del regex del parser incluida por Claude.
- 2026-10-11: creado este archivo porque `HANDOFF.md` no existía en la raíz.
- 2026-10-11 (Claude): arreglados errores de sintaxis de Kotlin, el regex de tramos y el registro detallado; CI en verde en `main`. Actualizado este archivo.
