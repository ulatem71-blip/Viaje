# HANDOFF

## Estado actual
- Tarea actual: separar tarjetas de ofertas múltiples en OfferParser.parseAll, versión 1.14.2 (versionCode 26).
- Rama: chatgpt/tarjetas-separadas, basada en la punta remota consultada de main: ebd73eb2f637219d55b359cb115486a94c8df7fd.
- `git pull main` y validación local no disponibles en este entorno; se consultó el SHA remoto y la validación debe completarse en Actions.
- No se modifican el guard anti-clics del workflow ni el paso Restore stable APK signing key; no se añadieron acciones sobre Uber.
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
- CI verde para el código de tarjetas separadas en https://github.com/ulatem71-blip/Viaje/actions/runs/38302631120 (run #107): guard anti-clics, assembleDebug, test y artefacto APK pasaron.
- No se hizo merge: aunque el run #107 está verde, el entorno actual no tiene checkout local ni `gradlew`, así que no se pudo ejecutar git pull de main. Hacerlo en un checkout completo antes de mergear.
- Tras sincronizar main localmente y el merge autorizado, actualizar HANDOFF.md con el SHA de merge y el enlace de Actions.
- Probar en dispositivo con texto real expuesto por Uber Driver y verificar que 27 ofertas únicas ya no generan entradas repetidas por alternancia.

## Historial de cambios
- 2026-10-12: iniciado trabajo de tarjetas separadas; parseAll agrupa por botón y procesa cada tarjeta por separado. Diagnóstico VARIAS TARJETAS limitado a 60 s, tarifas repetidas suprimidas y conservación de cinco líneas recientes.
- 2026-10-11: versión 1.14/versionCode 24; agregado OfferDeduplicator.kt con huella detallada por tarifa/ruta, supresión compacta por tarifa, limpieza por ventana y reloj inyectable.
- 2026-10-11: OfferAccessibilityService ahora deduplica antes de persistir o evaluar demo; diagnósticos limitados a uno cada 30 s y priorización de ofertas en LogStore.
- 2026-10-11: agregados cinco tests solicitados más una prueba del reloj inyectable. El workflow y el paso del keystore no se modificaron.
- 2026-10-11: CI verde en https://github.com/ulatem71-blip/Viaje/actions/runs/38178274478; assembleDebug, test, guard anti-clics y subida del APK completados.
- 2026-10-12: parseAll separa tarjetas por botón; el servicio procesa cada tarjeta independientemente; añadido diagnóstico VARIAS TARJETAS y tests. Versión 1.14.2/versionCode 26.
- 2026-10-12: CI verde en https://github.com/ulatem71-blip/Viaje/actions/runs/38302631120 (run #107); assembleDebug, test, guard anti-clics y APK pasaron.


## Tarea: volcado de estructura de pantalla (versión 1.14.3)
- Rama de trabajo: `chatgpt/volcado-arbol`; no hacer push ni merge a `main`.
- Versión actualizada a `versionName = "1.14.3"` y `versionCode = 27`.
- Añadido el switch `Capturar estructura de pantalla`, apagado por defecto y guardado en preferencias locales.
- Con la captura habilitada, solo se vuelca el árbol de una ventana del paquete `com.ubercab.driver` si el texto accesible contiene `Aceptar` o `Me interesa`, con intervalo mínimo de 3 segundos.
- Cada línea del volcado contiene sangría por profundidad, `className`, `viewIdResourceName`, `text`, `contentDescription`, `isClickable`, `isEnabled`, `isVisibleToUser` y `boundsInScreen`.
- Solo se guarda el último volcado en SharedPreferences y se limita a 20000 caracteres. El botón `Copiar último volcado` copia el texto al portapapeles.
- El volcado y los textos permanecen localmente en el teléfono; no se agregó carga de datos.
- No se añadió interacción con controles de Uber ni permisos de gestos. La verificación existente de CI `Ensure app contains no click automation` permanece sin cambios.
- CI de esta rama: pendiente al momento de actualizar esta nota. Confirmar guard, compilación, pruebas y artefacto en GitHub Actions antes de dar por terminada la tarea.
