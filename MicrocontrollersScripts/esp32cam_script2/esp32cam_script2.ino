#include <Arduino.h>
#include <WiFi.h>
#include <WebSocketsClient.h>
#include <stdlib.h>
#include "esp_camera.h"
#include "base64.h"  // Included with Arduino, part of ESP32 core

// Your Wi-Fi credentials
const char* ssid = "";
const char* password = "";
const char* DEVICE_ID = "REAL_ESP32CAM";

// WebSocket server IP and port
const char* websocket_host = "";  // Update with your PC's IP
const uint16_t websocket_port = 443;

WebSocketsClient webSocket;

#define RX_pin 13  // Safe GPIO
#define TX_pin 14  // Safe GPIO

unsigned long lastSend = 0;
unsigned long lastSensorSend = 0;
static unsigned long lastSensorCheck = 0;

void webSocketEvent(WStype_t type, uint8_t* payload, size_t length) {
  switch (type) {
    case WStype_DISCONNECTED:
      //Serial.println("[WebSocket] Disconnected");
      break;

    case WStype_CONNECTED:
      //Serial.println("[WebSocket] Connected to server");
      Serial.println("[WebSocket] Connected to server");
      webSocket.sendTXT(String("device_id:") + DEVICE_ID);
      break;

    case WStype_TEXT:
      Serial.printf("Received from server: %.*s\n", length, payload);
      String cmd((char*)payload, length);
      cmd.trim();

      if (cmd == "ping") {
          webSocket.sendTXT("pong");
          return;
      }

      if (cmd.startsWith("esp32")) {
        if (cmd == "esp32_led_on") {
          digitalWrite(4, HIGH);
        } else if (cmd == "esp32_led_off") {
          digitalWrite(4, LOW);
        } else if (cmd.startsWith("esp32_change_resolution")) {
          // Expected format: esp32_change_resolution:5
          int colonIndex = cmd.lastIndexOf(":");
          if (colonIndex != -1 && colonIndex + 1 < cmd.length()) {
            int frameSize = cmd.substring(colonIndex + 1).toInt();

            sensor_t * s = esp_camera_sensor_get();
            if (s != nullptr) {
              s->set_framesize(s, (framesize_t)frameSize);
              Serial.printf("Resolution changed to frame size: %d\n", frameSize);
            } else {
              Serial.println("Failed to get camera sensor");
            }
          } else {
            Serial.println("Invalid resolution command format.");
          }
        }
      } else {
        cmd += "\n";
        Serial.print(cmd);  // Forward to Arduino via Serial
      }
      break;
  }
}

void setupCamera() {
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer   = LEDC_TIMER_0;
  config.pin_d0       = 5;
  config.pin_d1       = 18;
  config.pin_d2       = 19;
  config.pin_d3       = 21;
  config.pin_d4       = 36;
  config.pin_d5       = 39;
  config.pin_d6       = 34;
  config.pin_d7       = 35;
  config.pin_xclk     = 0;
  config.pin_pclk     = 22;
  config.pin_vsync    = 25;
  config.pin_href     = 23;
  config.pin_sscb_sda = 26;
  config.pin_sscb_scl = 27;
  config.pin_pwdn     = 32;
  config.pin_reset    = -1;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  config.frame_size   = FRAMESIZE_QVGA; // 320x240
  config.jpeg_quality = 20;             // Lower is better quality
  config.fb_count     = 1;

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    //Serial.printf("Camera init failed with error 0x%x", err);
    while (true);
  }

  //Serial.println("Camera initialized.");
}

void setup() {
  Serial.begin(9600);  
  Serial.setTimeout(10);


  WiFi.begin(ssid, password);
  Serial.print("Connecting to WiFi");

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("\nWiFi connected. IP address: ");
  Serial.println(WiFi.localIP());

  setupCamera();

  pinMode(4, OUTPUT);
  digitalWrite(4, LOW);

  //Serial2.begin(9600, SERIAL_8N1, RX_pin, TX_pin);

  webSocket.beginSSL(websocket_host, websocket_port, "/"); 
  webSocket.onEvent(webSocketEvent);
  webSocket.setReconnectInterval(5000); // Reconnect every 5s
}


void loop() {
  webSocket.loop();

  if (millis() - lastSend > 200 && webSocket.isConnected()) {
    //Serial.printf("Free heap before capture: %u bytes\n", ESP.getFreeHeap());

    camera_fb_t* fb = esp_camera_fb_get();
    if (!fb || fb->len == 0 || fb->buf == nullptr) {
      //Serial.println("Camera capture failed or invalid buffer");
      return;
    }

    String encoded = base64::encode(fb->buf, fb->len);
    esp_camera_fb_return(fb);

    //Serial.printf("Free heap after capture: %u bytes\n", ESP.getFreeHeap());
    //Serial.printf("Sending image (%d bytes base64)\n", encoded.length());

    //webSocket.sendTXT(encoded);
    webSocket.sendBIN(fb->buf, fb->len);
    encoded = "";  // Clear memory after sending
    lastSend = millis();
  }

  if (millis() - lastSensorCheck > 100) { // Check often
    while (Serial.available()) {
      String serialData = Serial.readStringUntil('\n');
      serialData.trim();

      if (serialData.startsWith("ard:") && webSocket.isConnected()) {
        String trimmedData = serialData.substring(4);
        webSocket.sendTXT(trimmedData);
      }
    }
    lastSensorCheck = millis();
  }

}
