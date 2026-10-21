<p align="center">
  <img src="assets/Logo_plant.png" alt="Logo de FieldTech" width="160">
</p>

<h1 align="center">FieldTech</h1>

<p align="center">
  Aplicación Android para monitorear tu huerto y saber cuándo regar.<br>
  Hecha para productores de Virú y Chao, en La Libertad, Perú.
</p>

<p align="center">
  <a href="https://github.com/deybi-code/Field-Tech-APP/releases/latest">
    <img src="https://img.shields.io/badge/Descargar-APK-00A3C4?style=for-the-badge&logo=android&logoColor=white" alt="Descargar APK">
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-9.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 9.0 o superior">
  <img src="https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 1.9">
  <img src="https://img.shields.io/badge/Material%20Design-3-00A3C4?logo=materialdesign&logoColor=white" alt="Material Design 3">
  <img src="https://img.shields.io/badge/Hardware-ESP32%20%7C%20DHT22-002B49" alt="ESP32 y DHT22">
</p>

---

## Qué es FieldTech

FieldTech te muestra en tu celular cómo está tu huerto en este momento: la humedad del suelo, la temperatura y el estado del sensor. Con esos datos te dice si conviene regar ahora, esperar o no regar, para que no pierdas cultivos por falta o exceso de agua.

La app se conecta a un sensor casero construido con un ESP32, así que no necesitas equipos industriales ni costosos.

## Descarga e instalación

1. Entra a la sección [Releases](https://github.com/deybi-code/Field-Tech-APP/releases/latest) y baja el archivo `.apk`.
2. Ábrelo desde tu celular.
3. Si Android te lo pide, permite instalar aplicaciones de esta fuente.
4. Toca **Instalar** y abre la app.

Requiere Android 9.0 o superior.

## Qué puedes hacer

**Ver tu huerto en tiempo real**
Humedad y temperatura del suelo, temperatura y humedad del ambiente (sensor DHT22) y el VPD, que indica qué tanta agua pierden las plantas por el clima. También ves la batería del sensor, la señal WiFi y la hora de la última lectura.

**Saber cuándo regar**
La app compara la humedad actual con el rango ideal de tu cultivo y te da una de cinco respuestas: riega ahora, prepara el riego, no necesitas regar, exceso de agua o sin datos. Además te da consejos según el clima, por ejemplo evitar regar al mediodía.

**Vincular sensores con un QR**
Escaneas el código QR del ESP32 y el sensor queda ligado a tu huerto. El escáner usa Google Code Scanner, por lo que no necesita pedir permiso de cámara.

**Revisar el historial**
Gráficos de líneas para las últimas 6 horas, 24 horas o 7 días, con mínimos, promedios y máximos.

**Comprobar si tus cultivos son compatibles**
La app revisa las condiciones de los últimos 7 días y las compara con ocho cultivos de la zona: espárrago, arándano, palta, uva de mesa, alcachofa, pimiento piquillo, maíz amarillo y caña de azúcar.

**Recibir alertas**
Te avisa cuando el suelo está muy seco, hay helada u ola de calor, la batería está baja o el sensor se desconecta. Funciona en segundo plano aunque tengas la app cerrada.

**Usarla de día o de noche**
Tiene modo claro y modo oscuro, pensado para leer los datos con comodidad también en el campo por la noche.

## Cómo está hecha

| Componente | Tecnología |
|:--|:--|
| Lenguaje | Kotlin 1.9 |
| Arquitectura | MVVM con `StateFlow` y `Coroutines` |
| Interfaz | ViewBinding y Material Components 3 |
| Datos locales | SQLite y Preferences |
| Escáner QR | Google Play Services Code Scanner |
| Segundo plano | AndroidX WorkManager |
| Hardware | ESP32, DHT22 y sonda capacitiva de humedad de suelo |

## Conexión con el ESP32

La app consulta al sensor con peticiones HTTP GET y espera una respuesta JSON como esta:

```json
{
  "soil_moisture": 54.2,
  "soil_temp": 22.1,
  "air_temp": 24.3,
  "air_humidity": 61.0,
  "battery": 87,
  "rssi": -62,
  "timestamp": 1758400000
}
```

El código del ESP32 está en [`firmware/esp32_huerto/esp32_huerto.ino`](firmware/esp32_huerto/esp32_huerto.ino) y se puede cargar directamente desde el Arduino IDE.

## Estructura del proyecto

```
Field-Tech-APP/
├── app/src/main/java/com/huertointeligente/app/
│   ├── data/
│   │   ├── model/         Modelos de datos (Huerto, SensorReading, Alerta, Riego)
│   │   ├── local/         Base de datos SQLite y SharedPreferences
│   │   ├── remote/        Cliente HTTP y generador de simulación
│   │   └── Repository.kt  Repositorio unificado
│   ├── domain/            Lógica agronómica, de cultivos y de alertas
│   ├── notifications/     Canal y gestor de notificaciones
│   ├── work/              Tareas en segundo plano (WorkManager)
│   └── ui/                Pantallas: Home, Historial, Riego, Cultivos, Alertas y Ajustes
├── app/src/main/res/      Layouts XML, íconos y temas claro y oscuro
├── assets/                Logo del proyecto
└── firmware/esp32_huerto/ Código del microcontrolador ESP32
```

## Equipo

Proyecto desarrollado para el curso de Investigación e Innovación Tecnológica.

- Gavidia Pérez, Deybi
- Huaman Rojas, Franco
- Isla Landa, Nadim
- Murrugarra Morante, Nicole
- Rafael Sihuenza, Gabriel

---

<p align="center">FieldTech, 2026</p>
