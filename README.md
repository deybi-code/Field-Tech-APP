<div align="center">

  <img src="app/src/main/res/drawable/ic_logo.xml" width="120" height="120" alt="FieldTech Logo" />

  # 🌿 FieldTech — App Android IoT

  **Sistema Inteligente de Monitoreo Agrícola y Control de Riego para Huertos**  
  *Desarrollado para la gestión agrícola eficiente en Virú y Chao (La Libertad, Perú)*

  [![Android](https://img.shields.io/badge/Android-9.0%2B%20(API%2028%2B)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
  [![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
  [![Material Design 3](https://img.shields.io/badge/Material%20Design-3.0-00A3C4?style=for-the-badge&logo=materialdesign&logoColor=white)](https://m3.material.io/)
  [![ESP32 IoT](https://img.shields.io/badge/Hardware-ESP32%20%7C%20DHT22-002B49?style=for-the-badge&logo=expressif&logoColor=white)](https://www.espressif.com/)

  ---

  ### 📲 Descarga la Aplicación Directamente
  
  [![Descargar APK](https://img.shields.io/badge/🚀_DESCARGAR_APK-FieldTech_v1.0-00A3C4?style=for-the-badge&logo=android)](https://github.com/deybi-code/Field-Tech-APP/releases/download/v1.0.0/app-debug.apk)

  > *O encuentra el archivo compilado en el repositorio: [`app/build/outputs/apk/debug/app-debug.apk`](app/build/outputs/apk/debug/app-debug.apk)*

</div>

---

## 📖 Acerca de FieldTech

**FieldTech** es una aplicación móvil nativa en Kotlin que transforma la agricultura tradicional en **agricultura de precisión**. Conectada a nodos IoT basados en **ESP32**, la aplicación recopila métricas ambientales y del suelo en tiempo real para ayudar a los agricultores a tomar decisiones óptimas sobre **cuándo, cómo y cuánto regar**, previniendo pérdidas de cultivos por estrés hídrico o exceso de humedad.

---

## ✨ Características Principales

### 📊 1. Monitoreo en Tiempo Real
- **Humedad y Temperatura del Suelo**: Medición precisa de la raíz.
- **Temperatura y Humedad Ambiental**: Medición con sensores DHT22.
- **VPD (Déficit de Presión de Vapor)**: Cálculo del nivel de estrés hídrico atmosférico para evitar evapotranspiración excesiva.
- **Estado del Dispositivo**: Batería (%), calidad de señal WiFi (dBm) y última actualización.

### 🧠 2. Algoritmo de Recomendación de Riego
- Analiza la humedad actual versus el rango ideal de cada cultivo.
- Clasifica el estado en 5 niveles: *Riega Ahora, Prepara Riego, No Necesitas Rregar, Exceso de Agua o Sin Datos*.
- Brinda consejos climáticos dinámicos según la temperatura del momento (ej. evitar regar a mediodía para evitar evaporación).

### 📷 3. Escáner de Código QR Integrado (Google ML Kit)
- Vinculación instantánea de sensores y huertos escaneando el código QR del dispositivo ESP32 **sin necesidad de pedir permisos de cámara molestos**.

### 🎨 4. Diseño Ultra Profesional y Modo Oscuro
- Interfaz moderna basada en **Material Design 3**.
- Colores representativos **FieldTech**: *Celeste Eléctrico, Azul Marino, Verde Hoja y Blanco*.
- **Modo Oscuro Adaptativo**: Interfaz en tono Azul Noche con detalles neón para lectura nocturna en el campo.

### 📈 5. Historial, Estadísticas y Gráficos
- Gráficos interactivos de líneas para períodos de **6 h, 24 h y 7 días**.
- Mínimos, promedios y máximos históricos calculados al instante.

### 🌾 6. Motor de Compatibilidad de Cultivos
- Evalúa las condiciones reales registradas en los últimos 7 días con 8 cultivos clave de la región:
  - 🌿 *Espárrago, Arándano, Palta, Uva de Mesa, Alcachofa, Pimiento Piquillo, Maíz Amarillo y Caña de Azúcar*.

### 🔔 7. Sistema de Alertas y Monitoreo en Segundo Plano
- Notificaciones en tiempo real ante eventos críticos (suelo muy seco, heladas, ola de calor, batería baja o dispositivo fuera de línea).
- Monitoreo periódico automático mediante **WorkManager** incluso con la app cerrada.

---

## 🛠️ Arquitectura y Tecnologías

| Componente | Tecnología |
| :--- | :--- |
| **Lenguaje** | Kotlin 1.9 |
| **Arquitectura** | MVVM / Clean Architecture con `StateFlow` y `Coroutines` |
| **UI Framework** | ViewBinding + Material Components 3 (DayNight) |
| **Base de Datos** | SQLite Local + Preferences Manager |
| **Escáner QR** | Google Play Services Code Scanner (`play-services-code-scanner`) |
| **Segundo Plano** | AndroidX WorkManager |
| **Hardware IoT** | ESP32, DHT22, Sonda de humedad de suelo capacitiva |

---

## 📡 Integración con Hardware IoT (ESP32)

FieldTech se comunica mediante peticiones HTTP GET esperando un formato JSON estándar:

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

> 💻 **Firmware de referencia**: Incluido en la carpeta [`firmware/esp32_huerto/esp32_huerto.ino`](firmware/esp32_huerto/esp32_huerto.ino) listo para cargar desde Arduino IDE.

---

## 📂 Estructura del Proyecto

```text
Field-Tech-APP/
├── app/src/main/java/com/huertointeligente/app/
│   ├── data/
│   │   ├── model/         # Modelos de datos (Huerto, SensorReading, Alerta, Riego)
│   │   ├── local/         # Base de datos local SQLite y SharedPreferences
│   │   ├── remote/        # Cliente HTTP y generador de simulación
│   │   └── Repository.kt  # Repositorio unificado
│   ├── domain/            # Motor agronómico, lógica de cultivos y alertas
│   ├── notifications/     # Canal y gestor de notificaciones nativas
│   ├── work/              # Tareas en segundo plano (WorkManager)
│   └── ui/                # Pantallas (Home, Historial, Riego, Cultivos, Alertas, Ajustes)
├── app/src/main/res/      # Layouts XML, íconos vectoriales FieldTech, temas Día/Noche
└── firmware/esp32_huerto/ # Código fuente C++ para el microcontrolador ESP32
```

---

## 👥 Equipo de Desarrollo

Proyecto desarrollado para el curso de **Investigación e Innovación Tecnológica**:

- 👨‍💻 **Gavidia Pérez, Deybi**
- 👨‍💻 **Huaman Rojas, Franco**
- 👨‍💻 **Isla Landa, Nadim**
- 👩‍💻 **Murrugarra Morante, Nicole**
- 👨‍💻 **Rafael Sihuenza, Gabriel**

---

<div align="center">
  <sub>FieldTech © 2026 — Transformando el campo con tecnología inteligente.</sub>
</div>
