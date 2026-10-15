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


## Tarea: ensayo de localización del botón Aceptar (versión 1.15.0)
- Rama: `chatgpt/ensayo-boton`, creada desde main; no hacer push ni merge a main desde esta tarea.
- Versión: `versionName = "1.15.0"`, `versionCode = 28`.
- Nuevo `OfferButtonLocator.kt`: inspecciona el árbol de accesibilidad del paquete `com.ubercab.driver`, encuentra botones `Aceptar` y `Me interesa`, obtiene la tarifa/texto de la tarjeta, límites del botón principal y de la X cuando cumple los umbrales geométricos.
- Ignora botones aislados sin tarifa; tarjetas con varias tarifas o más de un botón Aceptar en el subárbol se registran como ambiguas. No interactúa con controles de Uber.
- El servicio evalúa cada tarjeta mediante `DemoDecision`, escribe el resultado de ensayo en `LogStore` y cruza tarifa/ruta con `OffersStore`.
- Añadidos contadores locales para ACEPTARIA con botón localizado, ACEPTARIA sin botón, tarjetas ambiguas y discrepancias, más botón para reiniciarlos.
- Pruebas unitarias del árbol simulado: tarjeta única con X, dos tarjetas visibles, diálogo sin tarifa, tarjeta ambigua con dos tarifas y paquete distinto.
- No se modificó `.github/workflows/build.yml`; la verificación `Ensure app contains no click automation` queda intacta. No se usan acciones de interacción sobre Uber.
- CI de esta rama: pendiente al iniciar esta nota; confirmar guard, compilación, pruebas y artefacto APK en GitHub Actions antes de dar por terminada la tarea.


## Interfaz gráfica, A/C y distancia máxima de recogida (versión 1.15.1)
- Rama: `chatgpt/interfaz-ac`, creada desde `main`; no se integra ni se hace push a `main`.
- Versión Android: `versionName = "1.15.1"`, `versionCode = 30`. Se usa 30 porque la rama de aceptación real ya reserva el código 29.
- `MainActivity` fue reescrita con Kotlin multilínea y cuatro pestañas: **Inicio** (estado del servicio, última oferta, decisiones simuladas, captura del árbol y contadores diarios); **Ofertas** (tarjetas de oferta, decisión, motivos y exportación CSV); **Costos** (vehículo, mínimos, distancia máxima y A/C); **Diagnóstico** (simulador, registro en vivo, viajes aceptados manuales, volcado, contadores y acciones de copiar/borrar).
- La interfaz usa vistas nativas, colores adaptados al tema del sistema, tarjetas redondeadas, texto corporal de 16sp o mayor y botones de al menos 48dp.
- Añadidos a `DriverCostsStore`: A/C apagado por defecto, consumo extra 10 % (0–50 %) y recogida máxima 2,0 km (0,1–20 km).
- Con A/C activo, solo el costo de combustible por km se multiplica por `1 + porcentaje/100`; el mantenimiento no cambia.
- `DemoDecision` rechaza una oferta detallada si la recogida excede el máximo, con motivo `Recogida X km > máximo Y km`; las ofertas compactas siguen como `SIN_DATOS`.
- Cada oferta registra el estado A/C, y el CSV añade la columna `ac` al final sin cambiar el orden de las columnas anteriores.
- Pruebas unitarias nuevas cubren el ajuste de combustible por A/C, mantenimiento sin cambios, A/C apagado y límites de recogida incluyendo oferta compacta.
- El workflow de CI `Ensure app contains no click automation` se deja intacto; esta tarea no introduce acciones de clic ni automatización.
- CI de esta rama: pendiente de finalizar compilación, pruebas y artefacto APK.

 
## Interruptor maestro Sistema activo (versión 1.15.2)
- Rama: `chatgpt/interruptor-maestro`, creada desde `main`; no se modifica ni se integra a `main`.
- Versión: `versionName = "1.15.2"`, `versionCode = 31`.
- Nuevo `SystemPowerStore` con SharedPreferences: encendido por defecto, `isOn()`, `setOn(Boolean)` y hora persistida del último cambio.
- `OfferAccessibilityService.onAccessibilityEvent` consulta el interruptor antes de inspeccionar eventos; apagado significa retorno inmediato, sin leer nodos, parsear, guardar ofertas, capturar estructura, ejecutar el ensayo ni escribir diagnósticos. El servicio de accesibilidad Android no se desactiva.
- La pestaña Inicio muestra arriba el interruptor grande `Sistema ENCENDIDO/APAGADO`, su hora de cambio, estado `En pausa` y última oferta `En pausa` cuando está apagado. Una franja roja `EN PAUSA` aparece sobre las pestañas mientras permanece apagado.
- No se añaden notificaciones ni servicios en primer plano, y no se implementa ningún clic automático. El método `SystemPowerStore.isOn()` queda disponible para una futura aceptación real.
- Añadidas pruebas de la política pura de procesamiento: estado predeterminado y rechazo de eventos cuando el sistema está apagado. No se dispone de un contexto Android simulado existente para una prueba directa de SharedPreferences; el límite de procesamiento se prueba de forma pura.
- La verificación de CI `Ensure app contains no click automation` y el resto del workflow permanecen intactos.
- CI de esta rama: pendiente; comprobar guard, compilación, pruebas y artefacto APK en Actions antes de considerar terminada la tarea.

 
## Renovación visual de la app (versión 1.15.3)
- Rama: `chatgpt/diseno-visual`, basada en `chatgpt/interruptor-maestro` para conservar el interruptor maestro; no integrar ni hacer push a `main`.
- Versión: `versionName = "1.15.3"`, `versionCode = 32`.
- Renovación limitada a presentación y recursos gráficos; no se cambió el parser, servicio de accesibilidad, localizador, rentabilidad ni las decisiones. No se añadieron librerías ni pruebas de interfaz.
- `VisualTheme.kt` centraliza la paleta clara/oscura siguiendo el modo del sistema: azul noche `#0F1724`, tarjetas `#182235`, claro `#F4F6FA`, blanco, marca turquesa `#14B8A6`, decisiones verde/rojo/gris y bordes adaptados a cada modo.
- Tarjetas con esquinas de 16dp, contornos sin sombras, botones de 52dp, campos delineados, números clave grandes y motivos en tamaño pequeño.
- Inicio: interruptor maestro destacado, estado de servicio compacto, última oferta con insignia de decisión, métricas de neto frente a mínimos y tres contadores en mini tarjetas.
- Ofertas: filtros Todas/Aceptaría/Rechazaría, tarjetas con franja lateral por decisión, tarifa grande y motivos; estado vacío amable.
- Costos: campos agrupados, tarjeta de costo por km y botón Guardar fijo al pie con confirmación breve.
- Diagnóstico: secciones plegables cerradas por defecto y textos de diagnóstico en monoespaciada de 12sp.
- Navegación inferior con iconos dibujados en código, estado activo turquesa y transiciones de 200 ms; sin animaciones continuas.
- Añadido icono adaptativo propio turquesa con símbolo de moneda y ruta. Sin cambios al workflow; `Ensure app contains no click automation` queda intacto.
- CI verde en https://github.com/ulatem71-blip/Viaje/actions/runs/38678023367: `Ensure app contains no click automation`, compilación del APK, pruebas unitarias existentes y subida del artefacto completadas correctamente.
