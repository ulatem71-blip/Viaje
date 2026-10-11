# HANDOFF

## Estado actual
- Repositorio: `ulatem71-blip/Viaje`; aplicación Kotlin en `ViajeRentable-v1.5/`.
- Rama de trabajo: `chatgpt/verify-parser-ci`, creada desde `main` en `14aacef5958f1944ecbe7003464c24bfb956fe3f` después de actualizar la referencia a la punta actual de `main`.
- La compilación `assembleDebug` pasó en el CI anterior. Las pruebas fallaron con tres `NullPointerException` en `OfferParserTest.kt` (líneas 5, 7 y 8).
- Claude incorporó en `main` un cambio al regex de los tramos del parser y diagnósticos adicionales del workflow. Hace falta validar ese cambio en una ejecución nueva.
- No pude ejecutar `git pull`, `sh ./gradlew assembleDebug` ni `sh ./gradlew test` en un checkout local: este entorno no puede resolver `github.com` desde la terminal. Se usa GitHub para consultar el repositorio y el CI para validar.

## Reglas del proyecto
- La app solo registra ofertas y calcula rentabilidad; nunca acepta ni rechaza viajes automáticamente.
- No modificar el paso del keystore ni el secret `VIAJE_DEBUG_KEYSTORE_BASE64` del workflow.
- Solo leer el paquete `com.ubercab.driver`.
- Kotlin: no escribir funciones locales de una línea seguidas de punto y coma; una instrucción por línea.
- Crear una rama nueva por tarea, validar localmente antes del commit cuando sea posible, esperar CI verde y solo entonces actualizar `main` tras sincronizarlo.
- Si el CI falla, reportar literalmente los errores de compilación o el bloque `What went wrong`; no adivinar.

## Pendiente
- Ejecutar `sh ./gradlew assembleDebug` y `sh ./gradlew test` localmente en un entorno con acceso al repo.
- Confirmar que el nuevo CI en `chatgpt/verify-parser-ci` pasa build y tests.
- Si las pruebas siguen fallando, usar el log nuevo para corregir el parser o las expectativas de los tests en esta rama.
- Actualizar este archivo con el enlace y resultado final del CI.
- No hacer merge a `main` hasta que el CI esté verde y se haya sincronizado `main`.

## Historial de cambios
- 2026-10-11: creada la rama `chatgpt/verify-parser-ci` desde la punta de `main` para verificar la corrección del regex del parser incluida por Claude.
- 2026-10-11: creado este archivo porque `HANDOFF.md` no existía en la raíz.
