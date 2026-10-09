# Cocinas Monitor — app Android de monitoreo y alarmas

App Android nativa (Kotlin + Jetpack Compose + Material 3, MVVM) para supervisar cocinas industriales del proyecto
`cocinas-industriales-uis`. **Estado: v0.1 — corre completa en modo simulado; el modo "servidor real" es un esqueleto
sin probar porque el backend aún no cumple el contrato (ver abajo).** No se compiló en el entorno donde se generó:
el primer *Sync* de Android Studio puede mostrar errores menores que hay que corregir.

## Cómo ejecutarla
1. Android Studio Ladybug o superior, JDK 17. `File > Open` → esta carpeta. Acepta crear el Gradle wrapper si lo pide.
2. Ejecuta la variante **debug** en emulador o teléfono. Es modo simulado (`USE_MOCK = true`), no necesita backend.
3. Login: usuario `admin` u `operador`, contraseña de 4+ caracteres.
4. En el Panel, baja al **Panel de simulación**: "Gas crítico", "Incendio mod.", "Cortar enlace"... Código de
   reconocimiento simulado: `123456`. (Solo existe en debug; el modo release lanza error si se intenta usar el mock.)

## Qué hace
| Requisito | Dónde |
|---|---|
| Login, sesión persistente segura, perfil, cerrar sesión | `data/SecureSessionStore` (token cifrado con AES-GCM en Android Keystore), `ui/screens/LoginScreen`, `ProfileScreen` |
| Panel: estado general (verde/amarillo/naranja/rojo), conexión, última actualización, cocinas, variables reales | `ui/screens/DashboardScreen` |
| Datos desactualizados nunca se muestran como actuales (>15 s → "SIN DATOS") | `KitchenReading.isStale`, `KitchenCard` |
| Conexión Conectado / Reconectando / Sin conexión + backoff 1→30 s | `util/Backoff`, `RemoteMonitoringRepository` |
| Alarma: sonido en bucle (canal de alarma, volumen al máximo del canal), vibración repetida, notificación de alta importancia | `service/AlarmPlayer`, `Notifications`, `MonitoringService` |
| Pantalla completa de emergencia sobre el bloqueo | `ui/EmergencyActivity`, `EmergencyScreen` |
| Desactivar con código, validado por el backend; bloqueo local tras 5 fallos | `PinDialog`, `AckViewModel` |
| Llamar a bomberos: confirmación + marcador (nunca llama solo); número en un solo sitio | `gradle.properties` → `EMERGENCY_FIRE_DEPARTMENT_NUMBER` |
| Historial y detalle de alarma (tiempo activa, quién la atendió) | `HistoryScreens.kt` |
| Roles | Se muestra el rol; **el control real debe hacerlo el backend** |

Variables mostradas = las del firmware real: temperatura, gas (raw/%), presión (raw/%), llama, ΔT, prioridad P0–P9,
ventiladores 1–3, válvula de gas, aspersores. No hay "reactores/reactivos": la unidad es la **cocina**.

Severidad por prioridad: **P0–P3 → EMERGENCIA** (rojo; sonido + vibración + pantalla completa), **P4–P5 → riesgo
elevado** (naranja; notificación + alarma a reconocer), **P6 y P8 → advertencia** (amarillo, solo estado), **P7 y P9 →
normal** (P7 es ventilación proporcional, operación normal). Ajustable en `Severity.fromPriority`.

## Contrato con el backend (propuesto — NO existe todavía en `servidor/`)
Base `API_BASE_URL` (debug `http://10.0.2.2:8000/api/`; release **HTTPS obligatorio**).

| Método y ruta | Cuerpo / respuesta |
|---|---|
| `POST auth/login/` | `{username,password}` → `{access, user:{id,nombre,username,rol:"ADMIN"\|"OPERADOR"}}` |
| `GET lecturas/ultimas/` | lista de lecturas: `cocina_id, temperatura, gas_raw, gas_pct, presion_raw, presion_pct, llama, delta_t, prioridad, v1_pct, v2_pct, v3_pct, valvula, aspersor, confirmando` |
| `GET alarmas/` | `id, cocina_id, tipo, variable, sensor?, valor, umbral, prioridad, inicio (ISO-8601), estado ("ACTIVA"\|"RECONOCIDA"), reconocida_por, reconocida_en` |
| `POST alarmas/{id}/reconocer/` | `{codigo}` → alarma actualizada. 400/403 = código incorrecto. El servidor debe **hashear el código, limitar intentos y registrar usuario y hora** |
| `GET stream/` (SSE) | eventos `reading` (lectura) y `alarm` (alarma, al crearse/cambiar) |

Para probar contra el servidor: poner `USE_MOCK=false` en `app/build.gradle.kts` (debug) y apuntar `API_BASE_URL`
a la IP del PC. Con el backend actual fallará (faltan login, alarmas y stream).

## Pendiente / limitaciones (honestas)
- **Push FCM**: sin él, si Android mata el proceso por ahorro de batería la alarma no llega. Requiere el backend + `google-services.json`. Mientras tanto: desactivar la optimización de batería para la app.
- Permiso **pantalla completa** (Android 14+) y **notificaciones** (13+): la app avisa en el Panel si faltan. "No molestar" total puede silenciar la alarma; Android no permite saltárselo.
- Sin ícono de app propio, sin *certificate pinning*, sin pruebas instrumentadas en dispositivo (sonido, vibración, marcador, bloqueo de pantalla): probar a mano en un teléfono real.
- Pruebas unitarias incluidas (`./gradlew testDebugUnitTest`): mapeo de severidad, alarmas P0–P5, datos desactualizados, backoff, estados de conexión, código correcto/incorrecto/bloqueo, alarma que persiste hasta reconocerse.
- Esta app **no es** el mecanismo de seguridad: el ESP32 actúa localmente (ventilación, válvula, aspersores).
