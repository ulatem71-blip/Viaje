# Viaje Rentable 1.6 — captura local de ofertas

Aplicación Android en Kotlin. El servicio de accesibilidad examina el árbol de texto que Uber Driver expone y registra localmente los cambios cuyo contenido contiene señales de precio y de viaje. También conserva diagnósticos con el número de nodos y nodos con texto. No pulsa controles ni acepta/rechaza viajes.

## Instalar y probar

1. Abre esta carpeta en Android Studio o descarga el APK generado por GitHub Actions.
2. Instala y abre Viaje Rentable.
3. Pulsa **Abrir ajustes de accesibilidad** y habilita Viaje Rentable.
4. Regresa a la aplicación y deja abierto el registro.
5. Cuando aparezca una oferta, pulsa **Actualizar registro**. Busca una entrada **OFERTA CANDIDATA**.
6. Comprueba los textos guardados contra la oferta visible. Si no aparece, el árbol de accesibilidad puede no exponer precio/distancia o la detección puede necesitar ajustar las señales según el texto real.

## Privacidad y límites

El registro se almacena en las preferencias privadas de esta aplicación en el dispositivo. Una entrada candidata puede contener texto visible de una oferta, así que borra el registro si no deseas conservarlo. La detección usa señales heurísticas y no garantiza reconocer todas las ofertas; el número de nodos por sí solo no identifica una oferta. No se acepta ni se rechaza ningún viaje automáticamente.

La compilación de GitHub Actions genera un APK de depuración y ejecuta pruebas unitarias. La instalación y la lectura real deben verificarse en un dispositivo con Uber Driver, porque el contenido expuesto depende de la versión y de la pantalla.
