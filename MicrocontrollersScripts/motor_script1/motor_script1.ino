#include <AltSoftSerial.h>
#include <EEPROM.h>

AltSoftSerial espSerial;  // Uses pins 8 (RX) and 9 (TX) on Uno/Nano

// MOTOR PINS
const int ENA = 5;
const int IN1 = 6;
const int IN2 = 7;

const int IN3 = 10;
const int IN4 = 12;
const int ENB = 11;

//HC-SR04 PINS
#define TRIG_PIN 2
#define ECHO_PIN 3

int speed = 0;          
String lastTurn = ""; 
String currentMovement="";

static unsigned long lastDistanceSend = 0;
long distance = 1000;
bool assistedStopMode=false;

void turnLeft() {
  digitalWrite(IN1, HIGH);
  digitalWrite(IN2, LOW);
  analogWrite(ENA, 250);

  digitalWrite(IN3, HIGH);
  digitalWrite(IN4, LOW);
  analogWrite(ENB, 250);
  lastTurn = "left";
}

void turnRight() {
  digitalWrite(IN1, LOW);
  digitalWrite(IN2, HIGH);
  analogWrite(ENA, 250);

  digitalWrite(IN3, LOW);
  digitalWrite(IN4, HIGH);
  analogWrite(ENB, 250);
  lastTurn = "right";
}

void driveBackward() {
  digitalWrite(IN1, LOW);
  digitalWrite(IN2, HIGH);
  analogWrite(ENA, 250);

  digitalWrite(IN3, HIGH);
  digitalWrite(IN4, LOW);
  analogWrite(ENB, 250);
  currentMovement="backward";
}

void driveForward() {
  digitalWrite(IN1, HIGH);
  digitalWrite(IN2, LOW);
  analogWrite(ENA, 250);

  digitalWrite(IN3, LOW);
  digitalWrite(IN4, HIGH);
  analogWrite(ENB, 250);
  currentMovement="forward";
}

void stopMotors() {
  digitalWrite(IN1, LOW);
  digitalWrite(IN2, LOW);
  analogWrite(ENA, 0);

  digitalWrite(IN3, LOW);
  digitalWrite(IN4, LOW);
  analogWrite(ENB, 0);
  lastTurn = "";
  currentMovement="stopped";
  speed=0;
}

long readDistanceCM() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH, 30000); // Timeout after 30ms
  long distance = duration * 0.034 / 2; // cm

  if (distance == 0 || distance > 400) return -1; // Invalid range
  return distance;
}

void getSettings(){
  byte value = EEPROM.read(0);
  if(value == 1){
    assistedStopMode=true;
  }else{
    assistedStopMode=false;
  }
}

void setup() {
  espSerial.begin(9600);
  Serial.begin(19200);

  pinMode(ENA, OUTPUT);
  pinMode(IN1, OUTPUT);
  pinMode(IN2, OUTPUT);

  pinMode(ENB, OUTPUT);
  pinMode(IN3, OUTPUT);
  pinMode(IN4, OUTPUT);

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);

  getSettings();
  stopMotors();
}

void loop() {
  if (espSerial.available()) {
    String command = espSerial.readStringUntil('\n');
    command.trim();
    command.toUpperCase();

    Serial.print("Received: [");
    Serial.print(command);
    Serial.println("]");

    if (command == "FORWARD") {
      if (distance < 10 && assistedStopMode) {
        stopMotors();
        return; 
      }
      if (speed < 0) {
        stopMotors();
      } else {
        speed = 250;
        driveForward();
      }
    }
    else if (command == "BACKWARD") {
      if (speed > 0) {
        stopMotors();
      } else {
        speed = -250;
        driveBackward();  // Pass positive value
      }
    }
    else if (command == "LEFT") {
      if (lastTurn == "right") {
        stopMotors();
        lastTurn = "";
      } else {
        turnLeft();
      }
    }
    else if (command == "RIGHT") {
      if (lastTurn == "left") {
        stopMotors();
        lastTurn = "";
      } else {
        turnRight();
      }
    }
    else if (command == "STOP") {
      stopMotors();
    }
    else if (command == "ASON"){
      assistedStopMode = true;
      EEPROM.update(0, 1);
    }
    else if (command == "ASOFF"){
      assistedStopMode = false;
      EEPROM.update(0, 0);
    }
    else if(command == "STATUS"){
      String status = "ard:status:{";
      status += "speed:" + String(speed) + ",";
      status += "movement:" + currentMovement + ",";
      status += "lastTurn:" + lastTurn + ",";
      status += "distance:" + String(distance) + ",";
      status += "assistedStop:" + String(assistedStopMode ? "on" : "off");
      status += "}";
      espSerial.println(status);
    }
    else {
      Serial.println("Unknown command");
    }

    delay(10);
  }

    if (millis() - lastDistanceSend > 500) {
      distance = readDistanceCM();
      if(currentMovement=="forward" && distance < 10 && assistedStopMode){
        stopMotors();
      }
      if (distance > 0) {
        String distanceData="ard:distance:";
        distanceData+=distance;
        espSerial.println(distanceData);
      }
      lastDistanceSend = millis();
    }

}