#include <Arduino.h>
#include <WiFi.h>
#include <WebSocketsClient.h>
#include <stdlib.h>
#include <EEPROM.h>
#include "esp_camera.h"
#include "base64.h"  // Included with Arduino, part of ESP32 core


const char* backup_ssid = "";
const char* backup_password = "";

char current_ssid[33] = "";     // To hold current SSID string
char current_password[33] = ""; // To hold current password string

const char* DEVICE_ID = "REAL_ESP32CAM";

// WebSocket server IP and port
const char* websocket_host = "";  // Update with your PC's IP
const uint16_t websocket_port = 443;

WebSocketsClient webSocket;

#define RX_pin 13  // Safe GPIO
#define TX_pin 14  // Safe GPIO

//EEPROM
#define EEPROM_SIZE 96
#define STREAM_FLAG_ADDR 0
#define SSID_ADDR 1
#define PASSWORD_ADDR 33

unsigned long lastSend = 0;
unsigned long lastSensorSend = 0;
static unsigned long lastSensorCheck = 0;
bool streamEnabled = false;

void saveStreamFlag(bool enabled) {
  EEPROM.write(STREAM_FLAG_ADDR, enabled ? 1 : 0);
  EEPROM.commit();
}

void loadStreamFlag() {
  streamEnabled = EEPROM.read(STREAM_FLAG_ADDR) == 1;
}

void saveStringToEEPROM(int startAddr, const char* str, int maxLen) {
  int len = strlen(str);
  if (len > maxLen - 1) len = maxLen - 1;  // Leave space for null terminator
  for (int i = 0; i < len; i++) {
    EEPROM.write(startAddr + i, str[i]);
  }
  EEPROM.write(startAddr + len, 0); // Null terminator
  EEPROM.commit();
}

void loadStringFromEEPROM(int startAddr, char* buffer, int maxLen) {
  for (int i = 0; i < maxLen; i++) {
    buffer[i] = EEPROM.read(startAddr + i);
    if (buffer[i] == 0) {
      break;
    }
  }
  buffer[maxLen - 1] = 0;  // Ensure null termination
}


void webSocketEvent(WStype_t type, uint8_t* payload, size_t length) {
  switch (type) {
    case WStype_DISCONNECTED:
      Serial.println("[WebSocket] Disconnected");
      break;

    case WStype_CONNECTED:
      //Serial.println("[WebSocket] Connected to server");
      //Serial.println("[WebSocket] Connected to server");
      webSocket.sendTXT(String("device_id:") + DEVICE_ID);
      break;

    case WStype_TEXT:
      //Serial.printf("Received from server: %.*s\n", length, payload);
      String cmd((char*)payload, length);
      cmd.trim();



      if (cmd.startsWith("esp32")) {
        if (cmd.startsWith("esp32_wifi:")) {
          String newSSID = cmd.substring(strlen("esp32_wifi:"));
          newSSID.trim();

          if (newSSID.length() > 0 && newSSID.length() < sizeof(current_ssid)) {
            newSSID.toCharArray(current_ssid, sizeof(current_ssid));
            saveStringToEEPROM(SSID_ADDR, current_ssid, sizeof(current_ssid));
            webSocket.sendTXT("WiFi SSID updated. Restart device to connect.");
          } else {
            webSocket.sendTXT("Invalid SSID length.");
          }
          return;
        }

        if (cmd.startsWith("esp32_password:")) {
          String newPass = cmd.substring(strlen("esp32_password:"));
          newPass.trim();

          if (newPass.length() > 0 && newPass.length() < sizeof(current_password)) {
            newPass.toCharArray(current_password, sizeof(current_password));
            saveStringToEEPROM(PASSWORD_ADDR, current_password, sizeof(current_password));
            webSocket.sendTXT("WiFi password updated. Restart device to connect.");
          } else {
            webSocket.sendTXT("Invalid password length.");
          }
          return;
        }
        cmd.toLowerCase();
         if (cmd == "esp32_status") {
          String status = "ESP32 STATUS:\n";        
          status += "WiFi SSID: " + String(strlen(current_ssid) > 0 ? current_ssid : "Unknown") + "\n";          
          status += "LED: " + String(digitalRead(4) == HIGH ? "ON" : "OFF") + "\n";         
          status += "Streaming: " + String(streamEnabled ? "ON" : "OFF") + "\n";         
          sensor_t *s = esp_camera_sensor_get();
          if (s != nullptr) {
            status += "Resolution: " + String(s->status.framesize) + "\n";
          } else {
            status += "Resolution: Unknown\n";
          }          
          webSocket.sendTXT(status);          
          return;
        }

        if (cmd == "esp32_stream_on") {
          streamEnabled = true;
          saveStreamFlag(true);
          //Serial.println("Streaming enabled.");
          return;
        }
        if (cmd == "esp32_stream_off") {
          streamEnabled = false;
          saveStreamFlag(false);
          //Serial.println("Streaming disabled.");
          return;
        }


        if (cmd == "esp32_led_on") {
          digitalWrite(4, HIGH);
        } 
        if (cmd == "esp32_led_off") {
          digitalWrite(4, LOW);
        } 
        if (cmd.startsWith("esp32_change_resolution")) {
          // Expected format: esp32_change_resolution:5
          int colonIndex = cmd.lastIndexOf(":");
          if (colonIndex != -1 && colonIndex + 1 < cmd.length()) {
            int frameSize = cmd.substring(colonIndex + 1).toInt();

            sensor_t * s = esp_camera_sensor_get();
            if (s != nullptr) {
              s->set_framesize(s, (framesize_t)frameSize);
              //Serial.printf("Resolution changed to frame size: %d\n", frameSize);
            } else {
              //Serial.println("Failed to get camera sensor");
            }
          } else {
            //Serial.println("Invalid resolution command format.");
          }
        }

      } else {
        cmd.toLowerCase();
          if (cmd == "ping") {
              webSocket.sendTXT("pong");
              return;
          }
          if (cmd == "automaticping") {
              webSocket.sendTXT("automaticpong");
              return;
          }

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

    //Flip the image vertically
  sensor_t *s = esp_camera_sensor_get();
  if (s != nullptr) {
    s->set_vflip(s, 1);  // Flip image vertically
    s->set_hmirror(s, 1); // Optional: Flip image horizontally (mirror)
  }

  //Serial.println("Camera initialized.");
}

bool connectWiFi(const char* ssid, const char* password, unsigned long timeout = 10000) {
  WiFi.begin(ssid, password);
  unsigned long startAttemptTime = millis();

  while (WiFi.status() != WL_CONNECTED && millis() - startAttemptTime < timeout) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();
  return WiFi.status() == WL_CONNECTED;
}

void setup() {
  Serial.begin(9600);  
  Serial.setTimeout(10);
  EEPROM.begin(EEPROM_SIZE);
  loadStringFromEEPROM(SSID_ADDR, current_ssid, sizeof(current_ssid));
  loadStringFromEEPROM(PASSWORD_ADDR, current_password, sizeof(current_password));

  if (strlen(current_ssid) == 0) {
      strcpy(current_ssid, "");
      strcpy(current_password, "");
      saveStringToEEPROM(SSID_ADDR, current_ssid, sizeof(current_ssid));
      saveStringToEEPROM(PASSWORD_ADDR, current_password, sizeof(current_password));
  }

  Serial.print("Trying user WiFi: ");
  Serial.println(current_ssid);
  if (!connectWiFi(current_ssid, current_password)) {
    Serial.println("User WiFi failed, trying backup WiFi...");
    if (!connectWiFi(backup_ssid, backup_password)) {
      Serial.println("Backup WiFi failed, no connection established.");
    }
  }


  Serial.println("\nWiFi connected. IP address: ");
  Serial.println(WiFi.localIP());

  loadStreamFlag();

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

  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi disconnected. Reconnecting...");
    Serial.print("Trying user WiFi: ");
    Serial.println(current_ssid);
    if (!connectWiFi(current_ssid, current_password)) {
      Serial.println("User WiFi failed, trying backup WiFi...");
      if (!connectWiFi(backup_ssid, backup_password)) {
        Serial.println("Backup WiFi failed, no connection established.");
      }
    }
    delay(10000);
    return;
  }

  if (streamEnabled && millis() - lastSend > 200 && webSocket.isConnected()) {
    //Serial.printf("Free heap before capture: %u bytes\n", ESP.getFreeHeap());

    camera_fb_t* fb = esp_camera_fb_get();
    if (!fb || fb->len == 0 || fb->buf == nullptr) {
      //Serial.println("Camera capture failed or invalid buffer");
      return;
    }

    String encoded = base64::encode(fb->buf, fb->len);
    webSocket.sendBIN(fb->buf, fb->len);
    esp_camera_fb_return(fb);

    //Serial.printf("Free heap after capture: %u bytes\n", ESP.getFreeHeap());
    //Serial.printf("Sending image (%d bytes base64)\n", encoded.length());

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
