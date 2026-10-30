package com.iscalexsoto.neonrunner

/**
 * TODOS los valores ajustables del juego viven aquí.
 *
 * Unidades:
 *  - Distancias en "anchos de pantalla" (W). 1.0 = el ancho completo del teléfono,
 *    así el juego se ve igual en cualquier dispositivo.
 *  - Tiempo en segundos. Velocidades en W/s, aceleraciones en W/s².
 *  - Las únicas fracciones de ALTURA (H) son las que dicen "fracción de la altura".
 *
 * Los colores están en [Palettes.kt].
 */
object GameConfig {

    /** Posición de los elementos en pantalla. */
    object Layout {
        /** Altura del suelo/horizonte como fracción de la altura de la pantalla. */
        const val GROUND_Y = 0.70f
        /** Posición horizontal fija de la pelota (W). */
        const val BALL_X = 0.24f
        /** Radio de la pelota (W). */
        const val BALL_RADIUS = 0.045f
    }

    /** Física del salto. */
    object Physics {
        /** Gravedad normal (W/s²). */
        const val GRAVITY = 3.7f
        /** Gravedad reducida mientras se mantiene presionado y la pelota sube (salto alto). */
        const val GRAVITY_HOLD = 1.4f
        /** Tiempo máximo que la gravedad reducida está activa (s). */
        const val HOLD_MAX = 0.30f
        /** Velocidad vertical inicial del salto (W/s). Un toque rápido da un salto de ~0.10 W. */
        const val JUMP_VELOCITY = 0.9f
        /** Velocidad máxima de caída (W/s). */
        const val MAX_FALL_SPEED = 3.5f
        /** Si tocas justo antes de aterrizar (a esta altura o menos), el salto se encola. */
        const val JUMP_BUFFER_HEIGHT = 0.03f
        /** Tolerancia de colisión: la hitbox de la pelota es este porcentaje del dibujo. */
        const val HITBOX_BALL = 0.85f
        /** Tolerancia de colisión: la hitbox del triángulo es este porcentaje del dibujo. */
        const val HITBOX_TRIANGLE = 0.88f
    }

    /** Velocidad de avance. */
    object Speed {
        const val START = 0.50f
        const val MAX = 1.40f
        /** W/s que se ganan por cada punto. Con 0.00036 el máximo se alcanza a ~2500 puntos. */
        const val PER_POINT = 0.00036f
    }

    /** Generación de triángulos. */
    object Obstacles {
        const val MIN_HEIGHT = 0.08f
        const val MAX_HEIGHT = 0.18f
        /** Altura máxima de cada triángulo cuando salen en grupos de 2 / 3. */
        const val GROUP2_MAX_HEIGHT = 0.12f
        const val GROUP3_MAX_HEIGHT = 0.09f
        /** Los grupos solo aparecen cuando la velocidad ya alcanza para librarlos de un salto. */
        const val GROUP2_MIN_SPEED = 0.62f
        const val GROUP3_MIN_SPEED = 0.80f
        /** base = altura * BASE_RATIO, con una variación aleatoria de ±BASE_JITTER. */
        const val BASE_RATIO = 1.1f
        const val BASE_JITTER = 0.15f
        /** Separación dentro de un grupo, como fracción de la base (menor que 1 = se tocan). */
        const val GROUP_SPACING = 0.95f
        /** Probabilidad de grupos de 1, 2 y 3 triángulos (deben sumar 1). */
        val GROUP_WEIGHTS = floatArrayOf(0.60f, 0.25f, 0.15f)
        /** Duración del salto más largo posible (s). Se usa para calcular el hueco mínimo. */
        const val MAX_AIR_TIME = 0.80f
        /** El hueco mínimo es velocidad * (MAX_AIR_TIME * GAP_SAFETY + GAP_REACTION).
         *  Estos valores los usa el modo Rogue; Runner es más denso (RUNNER_*). */
        const val GAP_SAFETY = 1.15f
        const val GAP_REACTION = 0.30f
        /** Hueco extra aleatorio: hasta velocidad * GAP_RANDOM_MAX segundos. */
        const val GAP_RANDOM_MAX = 0.90f
        /** Huecos del modo Runner: más obstáculos por minuto. */
        const val RUNNER_GAP_SAFETY = 1.05f
        const val RUNNER_GAP_REACTION = 0.22f
        const val RUNNER_GAP_RANDOM_MAX = 0.45f

        /**
         * Voladores del modo Runner (no se pueden destruir). Salen a cuatro alturas, medidas en el
         * borde inferior sobre el suelo (W). La pelota en el suelo ocupa 0..0.09; el salto corto
         * la lleva a 0.11..0.20 y el salto largo a 0.23..0.32:
         *  - 0.00 SUELO: hay que saltarlo.
         *  - 0.11 SALTO CORTO: pasa por debajo o líbralo con salto largo; el toque corto choca.
         *  - 0.20 SALTO LARGO: pasa por debajo o con toque corto; el salto largo choca.
         *  - 0.30 MUY ALTO: solo por debajo; un salto largo completo choca en su punto más alto.
         */
        val FLYER_HEIGHTS = floatArrayOf(0.00f, 0.11f, 0.20f, 0.30f)
        val FLYER_HEIGHT_WEIGHTS = floatArrayOf(0.20f, 0.30f, 0.30f, 0.20f)
        const val FLYER_CHANCE = 0.30f
        const val FLYER_MIN_SPEED = 0.60f
        const val FLYER_SIZE = 0.08f
        /** Los voladores vienen hacia ti un poco más rápido que el suelo (W/s extra). */
        const val FLYER_EXTRA_SPEED = 0.10f
        const val FLYER_FLAP_SPEED = 11f
        /** Distancia (W) a la que aparece el primer triángulo al empezar. */
        const val FIRST_OBSTACLE_DISTANCE = 1.6f
        /** Los obstáculos se crean cuando entran a esta distancia (W) del borde izquierdo. */
        const val SPAWN_X = 1.4f
    }

    /** Puntuación. */
    object Scoring {
        /** Puntos por cada ancho de pantalla recorrido. */
        const val POINTS_PER_W = 18f
        /** Suena un "ding" y parpadea el marcador cada tantos puntos. */
        const val DING_EVERY = 100
        const val HUD_FLASH_DURATION = 0.7f
        const val HUD_BLINK_INTERVAL = 0.12f
    }

    /** Ciclo día / noche (interpolación de paleta). */
    object Cycle {
        /** Cada tantos puntos se alterna entre la paleta noche y la paleta día. */
        const val PERIOD_POINTS = 400
        /** Duración de la transición entre paletas (s). */
        const val TRANSITION_SECONDS = 2.5f
        /** true = el juego empieza de noche. */
        const val START_AT_NIGHT = true
    }

    /** Flujo de partida. */
    object Flow {
        /** Tiempo tras chocar en el que se ignoran toques (evita reinicios accidentales). */
        const val RESTART_DELAY = 0.8f
        /** En game over, mantener presionado este tiempo vuelve a la selección de modo. */
        const val MODE_SWITCH_HOLD = 0.7f
        /** Pantalla de selección: los toques por encima de esta fracción de la ALTURA eligen
         *  Runner; por debajo, Rogue-lite. El renderer dibuja los paneles alrededor de esta línea. */
        const val MODE_SPLIT_Y = 0.37f
        /** Duración de la sacudida de pantalla y el destello al chocar. */
        const val SHAKE_DURATION = 0.35f
        const val FLASH_DURATION = 0.25f
    }

    /** Parámetros visuales. */
    object Visual {
        const val STAR_COUNT = 80
        const val STAR_PARALLAX = 0.02f
        /** Sol: centro horizontal (W), elevación del centro sobre el horizonte (W) y radio (W). */
        const val SUN_X = 0.60f
        const val SUN_ELEVATION = 0.27f
        const val SUN_RADIUS = 0.19f
        const val SUN_STRIPES = 6

        const val FAR_MOUNTAIN_PARALLAX = 0.08f
        const val FAR_MOUNTAIN_SEGMENT = 0.16f
        const val FAR_MOUNTAIN_MIN = 0.10f
        const val FAR_MOUNTAIN_MAX = 0.30f

        const val NEAR_MOUNTAIN_PARALLAX = 0.22f
        const val NEAR_MOUNTAIN_SEGMENT = 0.24f
        const val NEAR_MOUNTAIN_MIN = 0.03f
        const val NEAR_MOUNTAIN_MAX = 0.17f

        /** Cuadrícula del suelo. */
        const val GRID_PARALLAX = 1.0f
        const val GRID_SPACING = 0.16f
        /** Cuánto se abren las líneas verticales hacia el borde inferior (1 = sin perspectiva). */
        const val GRID_SPREAD = 2.4f
        const val GRID_ROWS = 9
        /** Velocidad a la que las líneas horizontales "vienen" hacia el jugador (0 = fijas). */
        const val GRID_ROW_DRIFT = 0.12f

        /** Grosor base de las líneas neón (W). */
        const val LINE_WIDTH = 0.006f

        /** Textos de inicio / game over: altura de la línea base del título sobre el horizonte (W)
         *  y ancho máximo de cualquier renglón (fracción de W); si no cabe, se reduce la letra. */
        const val MESSAGE_TITLE_ELEVATION = 0.82f
        const val MESSAGE_MAX_WIDTH = 0.84f

        /** Squash & stretch de la pelota. */
        const val SQUASH_ON_LAND = 0.35f
        const val STRETCH_ON_JUMP = 0.22f
        const val STRETCH_PER_VELOCITY = 0.12f
        const val STRETCH_MAX = 0.30f
        const val SQUASH_SPRING = 160f
        const val SQUASH_DAMPING = 16f
    }

    /** Modo Rogue-lite: cuadrados con vida, disparo automático y subida de nivel. */
    object Rogue {
        /** Tope de velocidad en Rogue (más bajo que en Runner: a más velocidad, menos tiempo de disparo). */
        const val MAX_SPEED = 1.15f
        /** Probabilidad de que un grupo de obstáculos sea un cuadrado (el resto, tras el volador, son triángulos). */
        const val SQUARE_CHANCE = 0.50f
        /** Cuadrado normal: lado (W), vida y experiencia que suelta. */
        const val SMALL_SIZE = 0.09f
        const val SMALL_HP = 2f
        const val SMALL_XP = 1
        /** Cuadrado blindado. */
        const val BIG_SIZE = 0.15f
        const val BIG_HP = 5f
        const val BIG_XP = 3
        /** Probabilidad de blindado (del total de cuadrados) y velocidad mínima para que aparezca. */
        const val BIG_CHANCE = 0.30f
        const val BIG_MIN_SPEED = 0.60f
        /** La vida de los enemigos sube un escalón ("tier") cada 1000 puntos: +25 % por escalón.
         *  Cada escalón cambia el color del enemigo y le añade un contorno interior. */
        const val HP_GROWTH_PER_1000 = 0.25f
        /** Escalón a partir del cual el color ya no cambia más (el contorno sí se sigue sumando). */
        const val TIER_COLOR_MAX = 3
        const val HITBOX_SQUARE = 0.88f

        /** Enemigo volador: rombo que flota a media altura. Se pasa por debajo sin saltar;
         *  para derribarlo hay que disparar desde el aire (las balas salen a la altura de la pelota). */
        const val FLYER_CHANCE = 0.18f
        const val FLYER_MIN_SPEED = 0.70f
        const val FLYER_SIZE = 0.08f
        const val FLYER_HP = 2f
        const val FLYER_XP = 2
        /** Altura del borde inferior sobre el suelo (W). La pelota en el suelo llega a 0.09;
         *  en el punto más alto de un salto corto las balas salen a ~0.15 y ya lo alcanzan. */
        const val FLYER_BOTTOM = 0.145f
        const val FLYER_BOB_AMPLITUDE = 0.008f
        const val FLYER_BOB_SPEED = 3.0f

        /**
         * Velocidad en Rogue: la distancia pesa menos que en Runner y cada nivel suma.
         * Con estos valores, 2500 puntos de distancia (+0.55) y nivel 10 (+0.32) llegan juntos
         * al máximo: ~60 % distancia, ~40 % nivel.
         */
        const val SPEED_PER_POINT = 0.00022f
        const val SPEED_PER_LEVEL = 0.035f
        /**
         * Puntuación en Rogue = puntos de distancia + XP recogida * XP_POINTS.
         * En la partida de referencia (2500 de distancia, ~108 XP) el combate aporta ~40 %.
         */
        const val XP_POINTS = 15

        /** Disparo: sale del centro de la pelota hacia adelante, también en el aire. */
        const val BULLET_RADIUS = 0.009f
        /** Estadísticas iniciales. */
        const val FIRE_RATE_START = 2.5f    // disparos por segundo
        const val DAMAGE_START = 1.0f
        const val RANGE_START = 0.55f       // W; solo se dispara si hay un enemigo a este alcance
        /** Alcance extra proporcional a la velocidad (segundos de anticipación): compensa que a más
         *  velocidad los enemigos pasan antes. El alcance total nunca supera RANGE_ON_SCREEN. */
        const val RANGE_LEAD_SECONDS = 0.30f
        const val RANGE_ON_SCREEN = 0.80f
        const val BULLET_SPEED_START = 1.8f // W/s respecto a la pantalla
        /** Cuánto mejora cada estadística al elegirla en una subida de nivel. */
        const val FIRE_RATE_MULT = 1.25f
        const val DAMAGE_ADD = 1.0f
        const val RANGE_ADD = 0.10f
        const val BULLET_SPEED_MULT = 1.25f
        /** Experiencia necesaria para el siguiente nivel: BASE + (nivel - 1) * PER_LEVEL. */
        const val XP_BASE = 4
        const val XP_PER_LEVEL = 2
        const val LEVEL_MESSAGE_DURATION = 1.4f

        /** Subida de nivel: el juego se detiene y se ofrecen UPGRADE_OPTIONS mejoras distintas.
         *  Las cartas se apilan en vertical; posiciones como fracción de la ALTURA de pantalla. */
        const val UPGRADE_OPTIONS = 3
        const val CARD_TOP_Y = 0.15f
        const val CARD_HEIGHT_Y = 0.125f
        const val CARD_GAP_Y = 0.02f
        fun cardTopY(index: Int): Float = CARD_TOP_Y + index * (CARD_HEIGHT_Y + CARD_GAP_Y)

        /** Rombos de experiencia: saltan al morir el cuadrado, caen y persiguen a la pelota. */
        const val GEM_POP_VY = 0.7f
        const val GEM_GRAVITY = 2.6f
        /** Velocidad horizontal con la que el rombo se acerca a la pelota (W/s, en pantalla,
         *  independiente de la velocidad del juego; funciona por delante y por detrás). */
        const val GEM_HOMING_SPEED = 1.2f
        /** Si en estos segundos no toca a la pelota (p. ej. salta sin parar), desaparece. */
        const val GEM_LIFETIME = 6f
        const val GEM_RADIUS = 0.014f
    }

    /** Sonido (generado por código, ver SoundManager). */
    object Audio {
        const val SAMPLE_RATE = 22050
        const val VOLUME = 0.8f
        /** El disparo suena muchas veces por segundo: más bajo que el resto. */
        const val SHOOT_VOLUME = 0.25f
        const val CRASH_VIBRATION_MS = 80L
    }
}
