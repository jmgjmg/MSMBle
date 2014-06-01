/* 
 * Created by Javier Montaner  (twitter: @tumaku_) 
 * This is an enhancement to the MSMBLE Android application 
 * initially coded during M-week (February 2014) of MakeSpaceMadrid
 * http://www.makespacemadrid.org
 * 
 * @ 2014 Javier Montaner
 * Licensed under the MIT Open Source License 
 * http://opensource.org/licenses/MIT
 *
 * Special thanks to @jgalaron and @@jjimenef fot their contribution to understand the behaviour of the HM10 module
 * 
 * This sketch for Arduino Uno is used to demo the Bluetooth HM10 module (Serial pass through)
 * This module implements a Bluetooth Low Energy (BLE)/ Bluetooth 4.0 service to simulate a serial communication.
 * The mechanisms used by BLE are completely different to the ones used by Bluetooth 2.1/3.0 but are abstracted by
 * the firmware of HM10 module through a standard serial connection
 *
 * The sketch reads the input of a potentiometer connected in analg pin AO.
 * The HM10 module is connected to PINs D2/D3 of Arduino (TX/RX of HM10) where a SoftwareSerial
 * connection (9600bpm) is created.
 * 
 * When the value of the potentiometer (0-1023) changes, the new value is sent over the serial
 * connection to the HM10 module.
 * The firmware of HM10 updates characteristic 0000ffe1-0000-1000-8000-00805f9b34fb with the received 
 * value (as a byte array). If a client is connected and has subscribed for notifications for this
 * characteristic, a BLE notification with the value (as a byte/char string) is sent.
 *
 * When the client writes a new value into the BLE characteristic (0000ffe1-0000-1000-8000-00805f9b34fb), 
 * the HM10 module sends the value over the Serial connection to the Arduino.
 *
 * A demonstator Android app that can interact with this sketch is available at:
 * https://github.com/jmgjmg/MSMBle
 * 
 * A compiled version of this android app can be downloaded from Google Play:
 * https://play.google.com/store/apps/details?id=com.tumaku.msmble
 *
 */

#include <SoftwareSerial.h>

int sensorPin = A0;    // select the input pin for the potentiometer
int sensorValue = 0;  // variable to store the value coming from the sensor
SoftwareSerial mySerial(2, 3); // RX, TX

void setup() {
  // declare the ledPin as an OUTPUT:
  Serial.begin(9600);
  mySerial.begin(9600);
  Serial.println("Setup done");
}

void loop() {
  // read the value from the sensor:
  int newSensorValue = analogRead(sensorPin);   
  if (mySerial.available()){ 
    Serial.print("Received data: ");
    while (mySerial.available())  {
      Serial.write(mySerial.read());
    }
    Serial.println("");
  }
  //Serial.println(newSensorValue);
  if (newSensorValue!=sensorValue) {
    Serial.print("Sent data: ");
    Serial.print(newSensorValue);
    mySerial.print(newSensorValue);
    Serial.println();
  }
  sensorValue=newSensorValue;
  delay(1000);                  
}
