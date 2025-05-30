// Keith Reis

// Pin Outs (FingerPrint Sensor):
// Black to GND
// White to TX1
// Green to RX0
// Red to 3.3v
// Yellow to D2
// Blue to 3.3v
//
// Pin Outs (RFID Scanner):
// VCC to 3.3V
// RST to 9 (Can Change)
// GND to GND
// IRQ left unconnected
// MISO/SCL to D12
// MOSI to D11
// SCK to D13
// SDA/SS (Square) to D10
//
// USB Connection:
// Serial - 9600 baud rate
// Should be in bytes
// RFIDMODE = 1 
// FINGERPRINTMODE = 2
// SIGNUPRFID = 3
// SIGNUPFINGERPRINT = 4 

#include <Adafruit_Fingerprint.h>

#define mySerial Serial1
#include <SPI.h>
#include <RFID.h>
#define SS_PIN 10
#define RST_PIN 9
RFID rfid(SS_PIN, RST_PIN);
String rfidCard;
int incomingByte = -1;
int fingerID = -1;
uint8_t id = 0;
Adafruit_Fingerprint finger = Adafruit_Fingerprint(&mySerial);

void setup() {
  pinMode(2, INPUT); // Yellow cable (Touch Sensor)
  Serial.begin(9600);
  digitalWrite(LEDB, LOW); // Turns the Blue LED on
  while (!Serial);  
  digitalWrite(LEDB, HIGH); // Turns the Blue LED off once Serial Detected (for the first time)

  delay(100);
  SPI.begin();
  rfid.init();

  finger.begin(57600);
  delay(5);
  if (finger.verifyPassword()) {
    //Serial.println("Found fingerprint sensor!");
  } else {
    //Serial.println("Did not find fingerprint sensor :(");
    digitalWrite(LEDR, LOW); // Turns the Red LED on -- Signals that we must restart, Finger print failed to begin
    while (1) { delay(1); }
  }
  finger.getParameters();
  finger.getTemplateCount();


}

void loop() {
  // put your main code here, to run repeatedly:

  // Pathway:
  // 1. Wait to be told what we are scanning for (RFID, FingerPrint, SIGNUPRFID, SIGNUPFINGERPRINT, or stop)
  //     - If Serial is closed, do not loop
  // 2. If RFID, just return the set of numbers, all logic is handled in Java
  // 3. If FingerPrint, we need to determine if Signing up (enrolling) or detecting if it exists in the fingerprint database
  // 4. return to step 1.
  if(!Serial) { // Does not loop while the Serial Port is closed
    digitalWrite(LEDB, HIGH); // Turns the Blue LED off
    incomingByte = -1;
    fingerID = -1;
    return;
  }

  digitalWrite(LEDB, LOW); // Turns the Blue LED on when Serial connected

  if(incomingByte == -1) {
    // No Data
    incomingByte = Serial.read();
    // Serial.println("incomingByte = " + incomingByte);
  } else if (incomingByte == (0+48)) { // Not Used / Stopped ?
    // Hasn't started yet
    return;
  } else if (incomingByte == (1+48) || incomingByte == (3+48)) { // RFIDMode / SIGNUPRFIDMode (Both are the same)
    if (rfid.isCard()) {
      if (rfid.readCardSerial()) {
        rfidCard = String(rfid.serNum[0]) + " " + String(rfid.serNum[1]) + " " + String(rfid.serNum[2]) + " " + String(rfid.serNum[3]);
        Serial.println(rfidCard);
      }
      incomingByte = -1;
      rfid.halt();
    }
    delay(50);
  } else if (incomingByte == (2+48)) { // FingerPrint
    // Serial.println("Finger Print");
    int val = digitalRead(2); // Read in from touch sensor 
    if(val == HIGH){ // if Touch sensor is being pressed, check finger print
      fingerID = getFingerprintIDez();
    }
    delay(50);            //don't ned to run this at full speed.
    if(fingerID != -1) {
      Serial.println(fingerID);
      fingerID = -1;
      incomingByte = -1;
    }
  } else if (incomingByte == (4+48)) { // SignUpFingerPrint (enroll)
    finger.getTemplateCount();
    id = finger.templateCount + 1;
    if(getFingerprintEnroll()) {
      Serial.println(id);
      id = 0;
      incomingByte = -1;
    }
  } else if (incomingByte == 10){ // New Line character, happens after Serial is closed
    incomingByte = -1;
  } else { // Not given 0, 1, 2, 3, 4, or 10 ~  Return byte to Serial Println
    Serial.println(incomingByte);
  }

}

// returns -1 if failed, otherwise returns ID #
int getFingerprintIDez() {
  uint8_t p = finger.getImage();
  if (p != FINGERPRINT_OK)  return -1;

  p = finger.image2Tz();
  if (p != FINGERPRINT_OK)  return -1;

  p = finger.fingerFastSearch();
  if (p != FINGERPRINT_OK)  return -1;

  // found a match!
  // Serial.print("Found ID #"); Serial.print(finger.fingerID);
  // Serial.print(" with confidence of "); Serial.println(finger.confidence);
  return finger.fingerID;
}

uint8_t getFingerprintEnroll() {

  int p = -1;
  
  while (p != FINGERPRINT_OK) {
    p = finger.getImage();
    switch (p) {
    case FINGERPRINT_OK:
      break;
    case FINGERPRINT_NOFINGER:
      break;
    case FINGERPRINT_PACKETRECIEVEERR:
      break;
    case FINGERPRINT_IMAGEFAIL:
      break;
    default:
      break;
    }
  }

  // OK success!

  p = finger.image2Tz(1);
  switch (p) {
    case FINGERPRINT_OK:
      break;
    case FINGERPRINT_IMAGEMESS:
      return p;
    case FINGERPRINT_PACKETRECIEVEERR:
      return p;
    case FINGERPRINT_FEATUREFAIL:
      return p;
    case FINGERPRINT_INVALIDIMAGE:
      return p;
    default:
      return p;
  }

  delay(2000);
  p = 0;
  while (p != FINGERPRINT_NOFINGER) {
    p = finger.getImage();
  }

  p = -1;
  
  while (p != FINGERPRINT_OK) {
    p = finger.getImage();
    switch (p) {
    case FINGERPRINT_OK:
      break;
    case FINGERPRINT_NOFINGER:
      break;
    case FINGERPRINT_PACKETRECIEVEERR:
      break;
    case FINGERPRINT_IMAGEFAIL:
      break;
    default:
      break;
    }
  }

  // OK success!

  p = finger.image2Tz(2);
  switch (p) {
    case FINGERPRINT_OK:
      break;
    case FINGERPRINT_IMAGEMESS:
      return p;
    case FINGERPRINT_PACKETRECIEVEERR:
      return p;
    case FINGERPRINT_FEATUREFAIL:
      return p;
    case FINGERPRINT_INVALIDIMAGE:
      return p;
    default:
      return p;
  }

  // OK converted!

  p = finger.createModel();
  if (p == FINGERPRINT_OK) {
  } else if (p == FINGERPRINT_PACKETRECIEVEERR) {
    return p;
  } else if (p == FINGERPRINT_ENROLLMISMATCH) {
    return p;
  } else {
    return p;
  }

  p = finger.storeModel(id);
  if (p == FINGERPRINT_OK) {
  } else if (p == FINGERPRINT_PACKETRECIEVEERR) {
    return p;
  } else if (p == FINGERPRINT_BADLOCATION) {
    return p;
  } else if (p == FINGERPRINT_FLASHERR) {
    return p;
  } else {
    return p;
  }

  return true;
}
