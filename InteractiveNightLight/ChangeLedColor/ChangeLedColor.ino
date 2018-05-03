/*
  Fade

  This example shows how to fade an LED on pin 9 using the analogWrite()
  function.

  The analogWrite() function uses PWM, so if you want to change the pin you're
  using, be sure to use another PWM capable pin. On most Arduino, the PWM pins
  are identified with a "~" sign, like ~3, ~5, ~6, ~9, ~10 and ~11.

  This example code is in the public domain.

  http://www.arduino.cc/en/Tutorial/Fade
*/
#if defined(ARDUINO) 
SYSTEM_MODE(SEMI_AUTOMATIC); 
#endif

int photoOut = D11;
int photoIn = A4;

int potIn = A0;

int ledRed = D0;
int ledGreen = D1;
int ledBlue = D2;

// the setup routine runs once when you press reset:
void setup() {
  Serial.begin(9600);
  
  // declare pin 9 to be an output:
  pinMode(ledRed, OUTPUT);
  pinMode(ledBlue, OUTPUT);
  pinMode(ledGreen, OUTPUT);

  pinMode(photoOut, OUTPUT);
  digitalWrite(photoOut, HIGH);
}

// the loop routine runs over and over again forever:
void loop() {
  double potVal = analogRead(potIn);
  setColor(potVal);

// in progress
  int photoValue = analogRead(photoIn);
  Serial.println(photoValue);
  
  delay(30);
}

void setColor(double potVal)
{
  double val = potVal / 4095; // convert to range between 0 and 1

  // color logic based on https://stackoverflow.com/a/30309719
  double red =   min( max(0, 1.5 - abs(1 - 4 * (val-0.5))  ),1) * 255;
  double green = min( max(0, 1.5 - abs(1 - 4 * (val-0.25)) ),1) * 255;
  double blue =  min( max(0, 1.5 - abs(1 - 4 * val)        ),1) * 255;
  
  analogWrite(ledRed, 255 - red);
  analogWrite(ledGreen, 255 - green); 
  analogWrite(ledBlue, 255 - blue);
}

