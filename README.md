# Neon Runner

Runner estilo "dinosaurio de Chrome" con estética neón / synthwave, para Android.
Proyecto personal: sin cuentas, sin internet, sin tienda. Lo único que guarda es el récord, en el teléfono.

- Android nativo en **Kotlin + Jetpack Compose**: todo se dibuja por código en un `Canvas`,
  con un game loop basado en `withFrameNanos`.
- Orientación vertical fija, pantalla completa.
- Único permiso: `VIBRATE` (vibración corta al chocar).

## Cómo se juega

Al abrir el juego eliges uno de dos modos tocando su panel:

- **Runner**: el juego original. Solo triángulos; salta y sobrevive.
- **Rogue-lite**: además de triángulos hay **cuadrados con vida**. La pelota dispara sola hacia
  adelante (también en el aire) cuando hay un cuadrado al alcance. Un cuadrado destruido suelta un
  rombo de experiencia que cae y persigue a la pelota por el suelo hasta tocarla (si tardas demasiado
  en bajar, desaparece). Al llenar la barra el juego se detiene y eliges **una de tres mejoras**:
  cadencia, daño, alcance o bala rápida. Los cuadrados que no logres destruir a tiempo hay que
  saltarlos (los blindados piden salto alto), y su vida crece cada 1000 puntos.
  En este modo la velocidad sube con la distancia (menos que en Runner) y también con cada nivel, y la
  puntuación es distancia + 15 puntos por cada punto de experiencia recogido (en una partida típica,
  unos 60 % distancia y 40 % combate).

| Acción | Resultado |
| --- | --- |
| Toque rápido | Salto corto |
| Mantener presionado | Salto más alto |
| Chocar con un triángulo o cuadrado | Fin de la partida. Tras una pausa breve, un toque reinicia el mismo modo |
| Mantener presionado en game over | Vuelve a la selección de modo |

La puntuación (arriba a la derecha, junto con el récord `HI`, uno por modo) es la distancia recorrida;
en Rogue-lite se le suma lo ganado en combate.
Cada 100 puntos suena un "ding" y cada 400 la paleta transiciona suavemente entre noche y día/atardecer.

## Instalar

Descarga el APK de la última [release](../../releases) e instálalo en el teléfono
(hay que permitir "instalar apps de orígenes desconocidos" porque no viene de una tienda).

## Compilar

Requiere Android Studio (o el SDK de Android con `ANDROID_HOME`) y JDK 17.

```bash
./gradlew assembleRelease
```

El APK queda en `app/build/outputs/apk/release/app-release.apk`.

> La llave de firma `keystore/neonrunner.jks` (contraseña `neonrunner`) está en el repositorio a propósito:
> es un proyecto personal que no se publica, y así cualquier compilación (local o en CI) firma igual
> y las actualizaciones se instalan encima sin desinstalar.

## Publicar una release

Las releases las construye GitHub Actions ([`.github/workflows/build.yml`](.github/workflows/build.yml)).
Basta con crear y subir una etiqueta `vX.Y.Z`:

```bash
git tag v1.0.0
git push origin v1.0.0
```

El workflow compila el APK firmado y lo adjunta a una release de GitHub con ese nombre.
Cada push a `main` también compila el APK y lo deja como artefacto del workflow.

## Dónde ajustar cosas

| Archivo | Qué hay |
| --- | --- |
| `app/src/main/java/com/iscalexsoto/neonrunner/GameConfig.kt` | **Todos** los valores ajustables: física del salto, velocidades, tamaños y frecuencia de los triángulos, puntuación, ciclo día/noche, parallax, squash & stretch, audio, y el bloque `Rogue` (vida y tamaño de cuadrados, disparo, experiencia, mejoras por nivel) |
| `.../Palettes.kt` | Las dos paletas (`NIGHT` y `DAY`) y su interpolación |
| `.../GameEngine.kt` | Lógica pura del juego (sin Android): modos, física, obstáculos, cuadrados, disparos, rombos, niveles, colisiones, puntuación, estados |
| `.../Renderer.kt` | Dibujo de todas las capas: cielo, estrellas, sol, montañas, cuadrícula, pelota, triángulos, HUD |
| `.../SoundManager.kt` | Síntesis de los sonidos (salto, choque, ding, disparo, cuadrado destruido, subida de nivel) |
| `.../GameScreen.kt` | Composable con el game loop, el manejo de toques y el Canvas |
| `.../MainActivity.kt` | Pantalla completa y arranque |

Las unidades de `GameConfig` son relativas al **ancho de pantalla** (1.0 = ancho completo),
así que el juego se ve igual en cualquier teléfono.
