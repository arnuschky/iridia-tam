// all libs below are included in the TAM git
#include <I2C.h>
#include <TLC59116.h>        // based on http://brmlab.cz/project/ledbar

/********************************************************************
 *
 * Example Arduino Controller for the IRIDIA TAM v5 revA
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

// light barrier output/debug
#define P_LIBA(format, ...) p( "[LIBA] %04d " format, cycle, ## __VA_ARGS__ )
//#define P_LIBA(format, ...)


/**
 * Definition of hardware pins. Do NOT change this.
 */
#define XBEE_WAKE_PIN     12  // unused at the moment
#define XBEE_CTS_PIN      13  // unused at the moment
#define XBEE_RTS_PIN      14  // unused at the moment
#define XBEE_RST_PIN      15  // unused at the moment

#define POWER_OFF_PIN      4  // all-mighty power-off pin for PSU

#define PROX_SENSE_PIN     2  // digital!
#define PROX_EMIT_PIN      0

#define VBATT_SENSE_PIN   A1  // signal VBATT_SENSE

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

#define IR_WAIT_TIME     2
#define IR_THRESHOLD   120

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

/**
 * Global variables
 */

//
// main objects initialization
// if you don't to this here the bloody TAM resets
//
TLC59116 tlc = TLC59116();  // TLC that controls the PWM leds

// count of control cycles, each cycle = 0.1 secs
unsigned long cycle = 0;

// Variables for reading light barriers analog
int irReadPreF;
int irReadPreB;
byte irStage = 0;
int irInterrupted = -1;
boolean frontIrBarrierBlocked;
boolean backIrBarrierBlocked;



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
 * Swaps the endianness a 16bit unsigned integer.
 * @param val  pointer to integer to swap
 */
void swapEndian16(uint16_t &val)
{
  val = (val<<8) | (val>>8);
}


/**
 * Swaps the endianness a 32bit unsigned integer.
 * @param val  pointer to integer to swap
 */
void swapEndian32(uint32_t &val)
{
  val = (val<<24) | ((val<<8) & 0x00ff0000) | ((val>>8) & 0x0000ff00) | (val>>24);
}


/**
 * Prepare serial and set necessary digital pins to output.
 * Also initializes the Xbee module and resolves the address of the coordinator.
 */
void setup()
{
  Serial.begin(57600);

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
  tlc.setPinPWM(LED_IR_R, 55);
  tlc.setPinPWM(LED_IR_L, 55);

  // sensing for front and back IR barrier
  pinMode(IRF_SENSE_PIN, INPUT);
  pinMode(IRB_SENSE_PIN, INPUT);

  p("IRIDIA TAM initialization OK\n");
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

/**
 * Checks if the light barrier is blocked by using a ANALOG read.
 * Returns true if yes.
 */
void checkLightBarriersAnalog()
{
  irStage++;

  // read value ambient light
  if (irStage == 1)
  {
    // read analog before we turn LED on
    irReadPreF = analogRead(IRF_SENSE_PIN);
    irReadPreB = analogRead(IRB_SENSE_PIN);
  }
  // turn IR LEDs on
  else if (irStage == 2)
  {

    tlc.setPinMode(LED_IR_R, PM_PWM);
    tlc.setPinMode(LED_IR_L, PM_PWM);
  }
  // read value when IR on
  else if (irStage == 3)
  {
    int irReadPostF = analogRead(IRF_SENSE_PIN);
    int irReadPostB = analogRead(IRB_SENSE_PIN);

    frontIrBarrierBlocked = (abs(irReadPostF - irReadPreF) < IR_THRESHOLD);
    backIrBarrierBlocked  = (abs(irReadPostB - irReadPreB) < IR_THRESHOLD);

    P_LIBA("Front IR barrier: pre=%d post=%d difference=%d threshold=%d\n\r",
      irReadPreF, irReadPostF, abs(irReadPostF - irReadPreF), IR_THRESHOLD);
    P_LIBA("Back IR barrier : pre=%d post=%d difference=%d threshold=%d\n\r",
      irReadPreB, irReadPostB, abs(irReadPostB - irReadPreB), IR_THRESHOLD);
  }
  // turn IR LED off after cycle
  else if (irStage == 4)
  {
    // turn IR LED off after cycle
    tlc.setPinMode(LED_IR_R, PM_OFF);
    tlc.setPinMode(LED_IR_L, PM_OFF);
  }
  else if (irStage == 5)
  {
    irStage = 0;
  }
}

/**************************************************************************************
 *
 * Main loop. Loops every CYCLE_LENGTH ms.
 *
 **************************************************************************************/

void loop()
{
  // change RGB LEDs state if IR light barrier is interrupted
  checkLightBarriersAnalog();
  if (irInterrupted != 3 && frontIrBarrierBlocked && backIrBarrierBlocked)
  {
    setRgbLeds(20, 20, 20);
    irInterrupted = 3;    
    p("[DEBUG] IR front and back interrupted!\n");
  }
  else if (irInterrupted != 2 && frontIrBarrierBlocked && !backIrBarrierBlocked)
  {
    setRgbLeds(20, 0, 0);
    irInterrupted = 2;    
    p("[DEBUG] IR front interrupted!\n");
  }
  else if (irInterrupted != 1 && !frontIrBarrierBlocked && backIrBarrierBlocked)
  {
    setRgbLeds(0, 20, 0);
    irInterrupted = 1;    
    p("[DEBUG] IR back interrupted!\n");
  }
  else if (irInterrupted != 0 && !frontIrBarrierBlocked && !backIrBarrierBlocked)
  {    
    irInterrupted = 0;
    setRgbLeds(0, 0, 20);
  }

  delay(100);

  cycle++;
}
 
