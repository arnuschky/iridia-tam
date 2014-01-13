#include <ircom.h>
#include <ircomReceive.h>
#include <ircomMessages.h>
#include <ircomTools.h>
#include <ircomSend.h>

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


/**
 * Definition of hardware pins. Do NOT change this.
 */
#define XBEE_WAKE_PIN     12  // unused at the moment
#define XBEE_CTS_PIN      13  // unused at the moment
#define XBEE_RTS_PIN      18  // unused at the moment
#define XBEE_RST_PIN      15  // unused at the moment

#define POWER_OFF_PIN      3  // all-mighty power-off pin for PSU

#define PROX_SENSE_PIN    14  // ICP pin fixed to internal Timer
#define PROX_EMIT_PIN      1

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
//TLC59116wire tlc = TLC59116wire();  // TLC that controls the PWM leds
TLC59116 tlc = TLC59116();  // TLC that controls the PWM leds

// count of control cycles, each cycle = 0.1 secs
unsigned long cycle = 0;


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

const int prescale = 8;            // prescale factor (each tick 0.5 us @16MHz)
const byte prescaleBits = B010;    // see Table 18-1 or data sheet
// calculate time per counter tick in ns
const long  precision = (1000000/(F_CPU/1000)) * prescale  ;   

//const int numberOfEntries  = 5;    // the max number of pulses to measure
//const int gateSamplePeriod = 1000;   // the sample period in milliseconds
//
//volatile byte index = 0; // index to the stored readings
//volatile byte gate  = 0; // 0 disables capture, 1 enables
//volatile unsigned int results[numberOfEntries]; // note this is 16 bit value
//

/* ICR interrupt vector */
//ISR(TIMER1_CAPT_vect)
//{
//  TCNT1 = 0;                            // reset the counter
//  if(gate)
//  {
//    if( index != 0 || bitRead(TCCR1B ,ICES1) == true)   // wait for rising edge
//    {                                     // falling edge was detected
//      if(index < numberOfEntries)
//      {      
//        results[index] = ICR1;              // save the input capture value
//        index++;
//      } 
//    }
//  }
//  TCCR1B ^= _BV(ICES1);            // toggle bit to trigger on the other edge
//}

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

  // sensing for front and back IR barrier
  pinMode(IRF_SENSE_PIN, INPUT);
  pinMode(IRB_SENSE_PIN, INPUT);

  // emit and sensing for proximity sensor
  pinMode(PROX_EMIT_PIN, OUTPUT);
  digitalWrite(PROX_EMIT_PIN, LOW);
  pinMode(PROX_SENSE_PIN, INPUT);
  
  Serial.println("pulses are sampled while LED is lit");
  Serial.print( precision);     // report duration of each tick in microseconds
  Serial.println(" microseconds per tick");
  
  ircomStart();
  e_init_sampling();
  ircomEnableContinuousListening();
  ircomListen();
  PORTB |= 0x02;
  p("IRIDIA TAM initialization OK\n");
  p("Sampling every other second\n");
  p("Tick precision: %ld (microseconds)\n", precision);     // report duration of each tick in microseconds
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

void loop()
{  
  //setRgbLeds(50, 15, 0);
//  ircomSend(10);	    
//  while (ircomSendDone() == 0);
//  p("SEND DONE\n");
  
  
  while(1){  
  
  int val = 0;
  while( 1){
    IrcomMessage imsg;
    ircomPopMessage(&imsg);
    if (imsg.error == 0){
      val = (int) imsg.value;
      p("RECEIVE SUCCESSFUL: %d\n",val);
    } else if (imsg.error > 0){
      int val = (int) imsg.value;
      p("RECEIVE FAILED: %d , ERROR: %d\n",val, imsg.error);
    }
  }  
  
//  for(int i = 0; i<100; i++){
//    ircomSend(200);
//    while (ircomSendDone() == 0);
//    p("SEND DONE\n");
//    delay(1);
//  }  
 }
  
//  delay(gateSamplePeriod);
//  p("Sampling...");
//  gate = 1; // enable sampling
//  delay(gateSamplePeriod);
//  p("done.\n");
//  gate = 0;  // disable sampling
//  if(index > 0)
//  {
//    p("Durations of pulses in microseconds are:\n") ;
//    unsigned long minDur = -1, maxDur = 0, avgDur = 0;
//    for( byte i=0; i < numberOfEntries; i++)
//    {
//      unsigned long duration;
//      duration = results[i] * precision; // pulse duration in nanoseconds
//      if(duration > 0) {
//        if (duration < minDur)
//          minDur = duration;
//        if (duration > maxDur)
//          maxDur = duration;
//        avgDur += duration;
//      }      
//    }
//    avgDur /= numberOfEntries;
//    p("  min: %ld\n", (minDur/1000));
//    p("  max: %ld\n", (maxDur/1000));
//    p("  avg: %ld\n", (avgDur/1000));
//    index = 0;
//  }
//  else
//  {
//    p("No pulses\n");
//  }

  delay(10);
  
  cycle++;
}
 
