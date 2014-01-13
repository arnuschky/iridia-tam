/**
 * Generic TLC59116 library taking care of I2C Arduino communication
 * with TLC59116. 
 * Adapted from: http://brmlab.cz/project/ledbar
 * CC Attribution-Noncommercial-Share Alike 3.0 Unported
 */

#include "TLC59116.h"
#include "I2C.h"

TLC59116::TLC59116()
{
}

void TLC59116::begin(uint8_t address_, bool enable_high_speed, bool enable_internal_pullups)
{
  address = address_;
  memset(&rawPinModes, 0, sizeof(rawPinModes));

  // initialize 
  I2c.setSpeed(enable_high_speed);
  I2c.pullup(enable_internal_pullups);
  I2c.begin();

  I2c.write(address, 0x00, (uint8_t)0x01);   // mode register 1: broadcast on, [5bit]=0 turns on oscillator
  I2c.write(address, 0x01, (uint8_t)0x00);   // mode register 2: initialize all flags
}

void TLC59116::end()
{
  I2c.end();
}

void TLC59116::setPinMode(uint8_t pin, enum TLC59116PinMode mode)
{
  rawPinModes[pin / 4] &= ~(0x3  << (pin % 4 * 2));
  rawPinModes[pin / 4] |=  (mode << (pin % 4 * 2));
  I2c.write(address, 0x14 + pin / 4, rawPinModes[pin / 4]);
}

void TLC59116::setPinPWM(uint8_t pin, uint8_t dutyCycle)
{
  I2c.write(address, 0x02 + pin, dutyCycle);
}

void TLC59116::setAllPinPWM(uint8_t dutyCycles[16])
{
  for (uint8_t i = 0; i < 16; i++)
  {
    I2c.write(address, 0x02 + i, dutyCycles[i]);
  }
}

// TODO: fix this by using groups?
void TLC59116::setGroupPinPWM(uint8_t numPins, const uint8_t pins[], uint8_t dutyCycle)
{
  for (int i = 0; i < numPins; i++) 
  {
    I2c.write(address, 0x02 + pins[i], dutyCycle);
  }
}
