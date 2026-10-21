/*
  Huerto Inteligente - Firmware de referencia para ESP32
  ---------------------------------------------------------
  Lee un sensor DHT22 (temperatura/humedad del aire) y una sonda de humedad
  de suelo capacitiva, y publica las lecturas en un pequeño servidor HTTP
  para que la app Android las consuma directamente por WiFi.

  Basado en el diseño de carcasa IoT del informe "Lab04-Innovacion" del equipo:
  Gavidia Pérez, Huaman Rojas, Isla Landa, Murrugarra Morante, Rafael Sihuenza
  (curso Investigación e Innovación Tecnológica).

  Conexiones sugeridas:
    DHT22        -> pin de datos en GPIO 4  (con resistencia pull-up de 10k)
    Sonda suelo  -> salida analógica en GPIO 34 (ADC1)
    Batería      -> divisor de voltaje a GPIO 35 (ADC1) para medir carga
    Pantalla LCD/LED local -> opcional, ver sección "PANTALLA" más abajo

  Librerías necesarias (Arduino IDE > Administrar bibliotecas):
    - "DHT sensor library" de Adafruit
    - "Adafruit Unified Sensor"

  El ESP32 crea un servidor HTTP simple: al recibir GET /data, responde JSON.
  La app Android debe apuntar a: http://<IP_DEL_ESP32>/data
*/

#include <WiFi.h>
#include <WebServer.h>
#include <DHT.h>

// ---------------------------------------------------------------- Configuración

const char* WIFI_SSID = "NOMBRE_DE_TU_WIFI";
const char* WIFI_PASSWORD = "CONTRASEÑA_DE_TU_WIFI";

#define DHTPIN 4
#define DHTTYPE DHT22
#define SOIL_PIN 34
#define BATTERY_PIN 35

// Calibración de la sonda de humedad de suelo (ajustar con pruebas reales):
// valor ADC con la sonda al aire (seco) y sumergida en agua (húmedo).
const int SOIL_DRY_RAW = 3000;   // ADC en aire seco  -> 0 %
const int SOIL_WET_RAW = 1200;   // ADC en agua       -> 100 %

// Divisor de voltaje de la batería 18650 (ajustar según resistencias usadas)
const float BATTERY_MIN_V = 3.3f;  // 0 %
const float BATTERY_MAX_V = 4.2f;  // 100 %

DHT dht(DHTPIN, DHTTYPE);
WebServer server(80);

float lastSoil = 0, lastSoilTemp = 0, lastAirTemp = 0, lastAirHum = 0;
int lastBattery = -1;
unsigned long lastReadMillis = 0;
const unsigned long READ_INTERVAL_MS = 10000; // leer sensores cada 10 s

// ------------------------------------------------------------------- Utilidades

float readSoilPercent() {
  int raw = analogRead(SOIL_PIN);
  float pct = (float)(SOIL_DRY_RAW - raw) / (float)(SOIL_DRY_RAW - SOIL_WET_RAW) * 100.0f;
  if (pct < 0) pct = 0;
  if (pct > 100) pct = 100;
  return pct;
}

int readBatteryPercent() {
  int raw = analogRead(BATTERY_PIN);
  // ADC de 12 bits (0-4095) con referencia de 3.3V; ajustar factor según el divisor usado
  float voltage = (raw / 4095.0f) * 3.3f * 2.0f; // *2 si el divisor es 1:1
  float pct = (voltage - BATTERY_MIN_V) / (BATTERY_MAX_V - BATTERY_MIN_V) * 100.0f;
  if (pct < 0) pct = 0;
  if (pct > 100) pct = 100;
  return (int)pct;
}

// Aproximación simple de temperatura de suelo cuando no hay sonda dedicada:
// se usa un promedio suavizado hacia la temperatura del aire (reemplazar por
// un DS18B20 sumergible si se dispone de uno, para mayor precisión).
float estimateSoilTemp(float airTemp) {
  static float soilTemp = airTemp;
  soilTemp = soilTemp * 0.9f + airTemp * 0.1f;
  return soilTemp;
}

void readSensors() {
  float h = dht.readHumidity();
  float t = dht.readTemperature();
  if (isnan(h) || isnan(t)) {
    Serial.println("Error leyendo el DHT22, se mantiene la última lectura válida.");
    return;
  }
  lastAirHum = h;
  lastAirTemp = t;
  lastSoil = readSoilPercent();
  lastSoilTemp = estimateSoilTemp(t);
  lastBattery = readBatteryPercent();
  lastReadMillis = millis();

  Serial.printf(
    "suelo=%.1f%%  temp_suelo=%.1fC  temp_aire=%.1fC  hum_aire=%.1f%%  bateria=%d%%\n",
    lastSoil, lastSoilTemp, lastAirTemp, lastAirHum, lastBattery
  );
}

// ------------------------------------------------------------------- Servidor

void handleData() {
  int rssi = WiFi.RSSI();
  String json = "{";
  json += "\"soil_moisture\":" + String(lastSoil, 1) + ",";
  json += "\"soil_temp\":" + String(lastSoilTemp, 1) + ",";
  json += "\"air_temp\":" + String(lastAirTemp, 1) + ",";
  json += "\"air_humidity\":" + String(lastAirHum, 1) + ",";
  json += "\"battery\":" + String(lastBattery) + ",";
  json += "\"rssi\":" + String(rssi) + ",";
  json += "\"timestamp\":" + String((unsigned long)(millis() / 1000));
  json += "}";

  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", json);
}

void handleRoot() {
  server.send(200, "text/plain", "Huerto Inteligente ESP32 - usa GET /data");
}

void setup() {
  Serial.begin(115200);
  dht.begin();
  analogReadResolution(12);

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Conectando a WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();
  Serial.print("Conectado. IP del dispositivo: ");
  Serial.println(WiFi.localIP());
  Serial.println("Configura esta URL en la app: http://" + WiFi.localIP().toString() + "/data");

  server.on("/", handleRoot);
  server.on("/data", handleData);
  server.begin();

  readSensors();
}

void loop() {
  server.handleClient();

  if (millis() - lastReadMillis >= READ_INTERVAL_MS) {
    readSensors();
  }

  // PANTALLA (opcional): si se usa una pantalla LCD/LED local como en el
  // diseño de la carcasa ("MONITOREO DE CAMPO"), aquí se puede actualizar
  // con lastSoil, lastAirTemp, lastAirHum, etc.
}
