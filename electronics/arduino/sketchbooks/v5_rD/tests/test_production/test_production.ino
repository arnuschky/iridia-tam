// all libs below are included in the TAM git
#include <I2C.h>
#include <TLC59116.h>        // based on http://brmlab.cz/project/ledbar

/********************************************************************
 *
 * Production Test Arduino Controller for the IRIDIA TAM v5 revC
 *
 ********************************************************************/

/**
 * Debug macros which enable bits of code used for debugging the firmware.
 */
#define DEBUG_ENABLE_SERIAL_OUTPUT    // disabled all serial printout and strings

/**
 * Controls which messages are logged.
 */
// general output classes
#define P_INFO(format, ...) p( "[INFO] %05d " format, cycle, ## __VA_ARGS__ )
//#define P_INFO(format, ...)
#define P_WARN(format, ...) p( "[WARN] %04d " format, cycle, ## __VA_ARGS__ )
//#define P_INFO(format, ...)


/**
 * Definition of hardware pins. Do NOT change this.
 */
#define XBEE_WAKE_PIN     12  // unused at the moment
#define XBEE_CTS_PIN      13  // unused at the moment
#define XBEE_RTS_PIN      18  // unused at the moment
#define XBEE_RST_PIN      15  // unused at the moment

#define NETWORK_LED_PIN   19

#define POWER_OFF_PIN      3  // all-mighty power-off pin for PSU
#define VBATT_SENSE_PIN   A1  // signal VBATT_SENSE

#define PROX_SENSE_PIN     2  // digital!
#define PROX_EMIT_PIN      1

#define IRB_SENSE_PIN     A2  // signal IR_SENSE_L
#define IRF_SENSE_PIN     A0  // signal IR_SENSE_R

// pin definitions of the LEDs connected to the TLC
enum TlcLedPins {
  LED_IR_R,
  LED_RED_R,
  LED_BLUE_R,   // right side leds (IR + RGB)
  LED_GREEN_R,
  LED_RED_M,
  LED_BLUE_M,
  LED_GREEN_M,  // middle led (RGB)
  LED_RED_L,
  LED_BLUE_L,   // left side leds (IR + RGB)
  LED_GREEN_L,
  LED_IR_L,
  LED_BO,       // unused (breakout 0)
  LED_B1,       // unused (breakout 1)
  LED_BLUE_T,
  LED_GREEN_T,
  LED_RED_T     // top led (RGB)
};

// adapt this however your board is populated
const uint8_t NUM_LEDS = 4;
const uint8_t LEDS_RED[NUM_LEDS] = {
  LED_RED_R,
  LED_RED_M,
  LED_RED_L,
  LED_RED_T
};
const uint8_t LEDS_GREEN[NUM_LEDS] = {
  LED_GREEN_R,
  LED_GREEN_M,
  LED_GREEN_L,
  LED_GREEN_T
};
const uint8_t LEDS_BLUE[NUM_LEDS] = {
  LED_BLUE_R,
  LED_BLUE_M,
  LED_BLUE_L,
  LED_BLUE_T
};

// type definition for the state of the TAM
typedef struct {
  uint8_t redLed;
  uint8_t greenLed;
  uint8_t blueLed;
  boolean robotPresent;
  uint8_t robotData;
} state_t;

//TLC59116wire tlc = TLC59116wire();  // TLC that controls the PWM leds
TLC59116 tlc = TLC59116();  // TLC that controls the PWM leds

// count of control cycles, each cycle = 0.1 secs
unsigned long cycle = 0;


/***************************************************************************************************/
/***************************************************************************************************/


/**
 * Small helper function that emulates printf().
 * Disabled by DEBUG_ENABLE_SERIAL_OUTPUT.
 * @param fmt  format string, followed by possible parameters
 */
void p(const char *fmt, ... )
{
#ifdef DEBUG_ENABLE_SERIAL_OUTPUT
  char tmp[128]; // resulting string limited to 128 chars
  va_list args;
  va_start(args, fmt );
  vsnprintf(tmp, 128, fmt, args);
  va_end (args);
  Serial.print(tmp);
#else
#endif
}

/**
 * Prepare serial and set necessary digital pins to output.
 * Also initializes the Xbee module and resolves the address of the coordinator.
 */
void setup()
{
  Serial.begin(57600);

  //
  // Aux devices init (TLC and sensors)
  //

  // configure all pins of TLC to be used as PWM pins
  tlc.begin(B1100000, true, false);
  for (int i = 0; i < NUM_LEDS; i++) 
  {
    tlc.setPinMode(LEDS_RED[i],   PM_PWM);
    tlc.setPinMode(LEDS_GREEN[i], PM_PWM);
    tlc.setPinMode(LEDS_BLUE[i],  PM_PWM);
  }

  // breakout pins currently not used
  tlc.setPinMode(LED_BO, PM_OFF);
  tlc.setPinMode(LED_B1, PM_OFF);

  // set IR leds to full power but currently disabled
  tlc.setPinMode(LED_IR_R, PM_OFF);
  tlc.setPinMode(LED_IR_L, PM_OFF);
  
  pinMode(NETWORK_LED_PIN, OUTPUT);

  pinMode(VBATT_SENSE_PIN, INPUT);
  pinMode(POWER_OFF_PIN, OUTPUT);
  digitalWrite(POWER_OFF_PIN, LOW);

  p("IRIDIA TAM initialization OK");

  setRgbLeds(55, 55, 55);
  digitalWrite(NETWORK_LED_PIN, HIGH);
  
  // broken sim
//  tlc.setPinPWM(LED_GREEN_L, 0);
//  tlc.setPinPWM(LED_RED_R,  0);
}

/**
 * Sets the RGB LEDs (all colors in a single function call).
 * TODO: maybe use the group feature of TLC somehow for these groups
 */
void setRgbLeds(uint8_t redLed, uint8_t greenLed, uint8_t blueLed)
{
  // reduce brightness of reds slightly as red leds is different from blue and green
  tlc.setPinPWM(LED_RED_L,   redLed / 1.5);
  tlc.setPinPWM(LED_RED_M,   redLed / 1.5);
  tlc.setPinPWM(LED_RED_R,   redLed / 1.5);
  tlc.setPinPWM(LED_RED_T,   redLed);

  tlc.setPinPWM(LED_GREEN_L, greenLed);
  tlc.setPinPWM(LED_GREEN_M, greenLed);
  tlc.setPinPWM(LED_GREEN_R, greenLed);
  tlc.setPinPWM(LED_GREEN_T, greenLed);

  tlc.setPinPWM(LED_BLUE_L,  blueLed);
  tlc.setPinPWM(LED_BLUE_M,  blueLed);
  tlc.setPinPWM(LED_BLUE_R,  blueLed);
  tlc.setPinPWM(LED_BLUE_T,  blueLed);
}

/**************************************************************************************
 *
 * Main loop. Loops every CYCLE_LENGTH ms.
 *
 **************************************************************************************/
boolean networkLed = true;
void loop()
{
  // every second
  if (cycle % 10 == 0) {
    int vBatt = analogRead(VBATT_SENSE_PIN) * 33;
    p("[DEBUG] Reading battery voltage: %d.%03d V\n", (vBatt / 1000), (vBatt % 1000));

    if (vBatt >= 4500 && vBatt <= 5500) {
      networkLed = !networkLed;
    }
    else {
      networkLed = false;
    }  
    digitalWrite(NETWORK_LED_PIN, networkLed);
  }
    
  p(".");

  delay(50);
  cycle++;
}

