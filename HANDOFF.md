# HANDOFF

## Estado actual
- Repositorio: ulatem71-blip/Viaje; aplicación Android Kotlin en ViajeRentable-v1.5/.
- Tarea actual: deduplicación de ofertas y limitación de diagnósticos, versión 1.14 (versionCode 24).
- Rama: chatgpt/dedupe-ofertas, creada desde la punta remota de main consultada por API: 2794d1109a49cdda9b61b9536ed12681a09d3a4d.
- El deduplicador usa una ventana configurable de 120000 ms y reloj inyectable. Una compacta repetida se suprime si la tarifa ya existe; una detallada se suprime solo si coinciden tarifa y todos los campos de ruta.
- El registro de diagnósticos se limita a una línea cada 30 s y elimina diagnósticos antes que ofertas al alcanzar el límite.
- CI verde en la ejecución https://github.com/ulatem71-blip/Viaje/actions/runs/38178274478 (11-oct-2026): guard anti-clics, assembleDebug, test y artefacto APK pasaron.
- No fue posible ejecutar git pull de main desde la terminal porque no resuelve github.com. La rama se basó en el SHA remoto consultado y no se hará merge hasta poder sincronizar main localmente y confirmar CI verde.
- El paso del keystore y el secret VIAJE_DEBUG_KEYSTORE_BASE64 deben permanecer intactos.

## Reglas del proyecto
- El modo demo solo simula; cualquier aceptación real requiere una decisión explícita del dueño y se hace en una rama aparte, desactivada por defecto.
- Solo se lee el paquete com.ubercab.driver.
- No usar llamadas para pulsar/gesticular ni añadir permisos de gestos. El workflow contiene una comprobación de patrones de automatización de clics.
- Kotlin: no escribir funciones locales de una línea seguidas de punto y coma; una instrucción por línea.
- Crear una rama nueva por tarea. Ejecutar sh ./gradlew assembleDebug y sh ./gradlew test antes de commit cuando el entorno lo permita. Si no es posible, decirlo claramente.
- Subir la rama y esperar CI verde. Solo hacer merge a main cuando CI esté verde y después de sincronizar main.
- Si el CI falla, copiar literalmente las líneas e: file:// o el bloque What went wrong; no adivinar la causa.

## Pendiente
- Ejecutar sh ./gradlew assembleDebug y sh ./gradlew test desde un checkout completo; el directorio local disponible solo contiene README y no incluye Gradle Wrapper ni fuentes.
- CI del código completado en verde; validar también el commit que registra esta actualización de HANDOFF.
- Cuando exista un checkout con acceso a GitHub, hacer git pull de main, verificar conflictos y solo entonces mergear el PR con CI verde.
- Después del merge, actualizar HANDOFF.md con el SHA y el enlace de Actions.
- Probar en dispositivo con texto real expuesto por Uber Driver y verificar que 27 ofertas únicas ya no generan entradas repetidas por alternancia.

## Historial de cambios
- 2026-10-11: versión 1.14/versionCode 24; agregado OfferDeduplicator.kt con huella detallada por tarifa/ruta, supresión compacta por tarifa, limpieza por ventana y reloj inyectable.
- 2026-10-11: OfferAccessibilityService ahora deduplica antes de persistir o evaluar demo; diagnósticos limitados a uno cada 30 s y priorización de ofertas en LogStore.
- 2026-10-11: agregados cinco tests solicitados más una prueba del reloj inyectable. El workflow y el paso del keystore no se modificaron.
- 2026-10-11: CI verde en https://github.com/ulatem71-blip/Viaje/actions/runs/38178274478; assembleDebug, test, guard anti-clics y subida del APK completados.
