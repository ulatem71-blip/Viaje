# HANDOFF

## Estado actual
- Repositorio: ulatem71-blip/Viaje; aplicación Android Kotlin en ViajeRentable-v1.5/.
- Tarea actual: modo demo y simulador, versión 1.13 (versionCode 23).
- Rama: chatgpt/modo-demo, creada desde la punta de main en 89de553bce88b05093445ece16eb19ca736ea608.
- El modo demo solo calcula y registra una decisión hipotética; no interactúa con los controles de Uber.
- CI verde (11-oct-2026): https://github.com/ulatem71-blip/Viaje/actions/runs/38169693127. Pasaron la comprobación anti-clics, assembleDebug, test y la subida del APK ViajeRentable-apk.
- No pude ejecutar git pull ni los comandos Gradle desde un checkout local porque la terminal no puede resolver GitHub. Main fue consultado por API y sigue en 89de553bce88b05093445ece16eb19ca736ea608, pero falta cumplir el git pull local antes del merge.
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
- Revalidar CI después de esta actualización de HANDOFF.
- Con CI verde, ejecutar git pull de main en un checkout local, revisar que no haya cambios nuevos en conflicto y hacer merge del PR.
- Actualizar este archivo después del merge; el merge sigue pendiente porque no hay acceso de terminal a GitHub en este entorno.
- Probar en dispositivo el modo demo y el simulador con texto real expuesto por Uber Driver; confirmar que el simulador no añade registros.

## Historial de cambios
- 2026-10-11: creada chatgpt/modo-demo desde la punta de main consultada a través de GitHub; no se pudo hacer git pull localmente por falta de conectividad de terminal.
- 2026-10-11: versión 1.13/versionCode 23; agregado DemoDecision.kt, interruptor de modo demo, registro compatible con entradas antiguas, columnas decisionDemo/motivos, simulador y tests de decisión.
- 2026-10-11: agregado al workflow el guard de seguridad para que falle si encuentra patrones de automatización de clics en ViajeRentable-v1.5/app/src/main. No se modificó el paso del keystore.
- 2026-10-11: CI verde en https://github.com/ulatem71-blip/Viaje/actions/runs/38169693127; compilación, pruebas y guard anti-clics pasaron. Merge pendiente de sincronización local de main.
