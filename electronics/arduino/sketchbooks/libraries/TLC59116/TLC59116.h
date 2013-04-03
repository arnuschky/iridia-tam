/**
 * Generic TLC59116 library taking care of I2C Arduino communication
 * with TLC59116.
 * Adapted from: http://brmlab.cz/project/ledbar
 * CC Attribution-Noncommercial-Share Alike 3.0 Unported
 */


#include <Arduino.h>

enum TLC59116PinMode {
  PM_OFF,
  PM_ON,
  PM_PWM,
  PM_GRPPWM,
};

class TLC59116 {

  public:
    /**
     * Constructor
     */
    TLC59116();

    /**
     * Sets up communication to a given TLC chip via I2C
     * @param address  7-bit I2C address of the TLC
     */
    void begin(uint8_t address);

    /**
     * Shuts down I2C communication.
     * TODO: investigate if we need to send something to TLC
     */
    void end();

    /**
     * Set pin mode
     * @param pin   0..15
     * @param mode  see enum TLC59116PinMode
     */
    void setPinMode(uint8_t pin, enum TLC59116PinMode mode);

    /**
     * Sets the PWM value of a single pin
     * @param pin        0..15
     * @param dutyCycle  duty cycle 0..255
     */
    void setPinPWM(uint8_t pin, uint8_t dutyCycle);

    /**
     * Sets the PWM value of all pins in a single transmission
     * @param dutyCycles  array of 16 duty cycles 0..255
     */
    void setAllPinPWM(uint8_t dutyCycles[16]);

    /**
     * Sets the PWM value of all pins in a single transmission
     * TODO: fix this, does not work
     */
    void setGroupPinPWM(uint8_t numPins, const uint8_t pins[], uint8_t dutyCycle);

  private:
    uint8_t address;
    uint8_t rawPinModes[4];

};

