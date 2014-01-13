
#define PROX_EMIT_PIN 1

void setup()
{
  Serial.begin(57600);
  Serial.println("IRIDIA TAM initialization OK");

  pinMode(PROX_EMIT_PIN, OUTPUT);
  digitalWrite(PROX_EMIT_PIN, LOW);
}

void loop()
{
  Serial.println("IRIDIA TAM loop");

  // not sending
//  PORTB &= 0xfd;
//  digitalWrite(PROX_EMIT_PIN, LOW);
  delay(200);
  // sending
  PORTB |= 0x02;
//  digitalWrite(PROX_EMIT_PIN, HIGH);
  delay(200);  
}

