SYSTEM_MODE(MANUAL); 

const int SERVO_OUTPUT_PIN = D2;
const int POT_INPUT_PIN = A0;
const int DELAY_MS = 50;

int _stepAmount = 1; // the amount to change the angle of servo on each pass
int _minAngle = 0;
int _maxAngle = 180;
int _curAngle = 0;
Servo _servo;  



void setup() {
  // put your setup code here, to run once:
  _servo.attach(SERVO_OUTPUT_PIN);

  Serial.begin(9600);
}

void loop() {
   _servo.write(_curAngle);
  
  // put your main code here, to run repeatedly:
  int potVal = analogRead(POT_INPUT_PIN);
  _curAngle = map(potVal, 0, 4095, _minAngle, _maxAngle);

  Serial.print(potVal);
  Serial.print(",");
  Serial.println(_curAngle);

  delay(DELAY_MS);
}
