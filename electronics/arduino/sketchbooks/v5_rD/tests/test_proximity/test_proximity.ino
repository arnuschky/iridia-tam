#include <SoftwareSerial.h>

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


/**
 * Global variables
 */

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

const int prescale = 8;            // prescale factor (each tick 0.5 us @16MHz)
const byte prescaleBits = B010;    // see Table 18-1 or data sheet
// calculate time per counter tick in ns
const long  precision = (1000000/(F_CPU/1000)) * prescale  ;   

const int numberOfEntries  = 64;    // the max number of pulses to measure
const int gateSamplePeriod = 1000;   // the sample period in milliseconds

volatile byte index = 0; // index to the stored readings
volatile byte gate  = 0; // 0 disables capture, 1 enables
volatile unsigned int results[numberOfEntries]; // note this is 16 bit value


/* ICR interrupt vector */
ISR(TIMER1_CAPT_vect)
{
  TCNT1 = 0;                            // reset the counter
  if(gate)
  {
    if( index != 0 || bitRead(TCCR1B ,ICES1) == true)   // wait for rising edge
    {                                     // falling edge was detected
      if(index < numberOfEntries)
      {      
        results[index] = ICR1;              // save the input capture value
        index++;
      } 
    }
  }
  TCCR1B ^= _BV(ICES1);            // toggle bit to trigger on the other edge
}

/**
 * Prepare serial and set necessary digital pins to output.
 * Also initializes the Xbee module and resolves the address of the coordinator.
 */
void setup()
{
  Serial.begin(57600);

  // sensing for front and back IR barrier
  pinMode(IRF_SENSE_PIN, INPUT);
  pinMode(IRB_SENSE_PIN, INPUT);

  // emit and sensing for proximity sensor
  pinMode(PROX_EMIT_PIN, OUTPUT);
  digitalWrite(PROX_EMIT_PIN, LOW);
  pinMode(PROX_SENSE_PIN, INPUT);

  TCCR1A = 0 ;                    // Normal counting mode
  TCCR1B = prescaleBits ;         // set prescale bits
  TCCR1B |= _BV(ICES1);           // enable input capture

  bitSet(TIMSK1,ICIE1);           // enable input capture interrupt for timer 1 
  
  Serial.println("pulses are sampled while LED is lit");
  Serial.print( precision);     // report duration of each tick in microseconds
  Serial.println(" microseconds per tick");
  
  p("IRIDIA TAM initialization OK\n");
  p("Sampling every other second\n");
  p("Tick precision: %ld (microseconds)\n", precision);     // report duration of each tick in microseconds
}

/**************************************************************************************
 *
 * Main loop. Loops every CYCLE_LENGTH ms.
 *
 **************************************************************************************/

void loop()
{  
  delay(gateSamplePeriod);
  p("Sampling...");
  digitalWrite(PROX_EMIT_PIN, LOW);
  gate = 1; // enable sampling
  delay(gateSamplePeriod);
  p("done, sending now.\n");  
  digitalWrite(PROX_EMIT_PIN, HIGH);
  gate = 0;  // disable sampling
  if(index > 0)
  {
    p("Durations of pulses in microseconds are:\n") ;
    unsigned long minDur = -1, maxDur = 0, avgDur = 0;
    for( byte i=0; i < numberOfEntries; i++)
    {
      unsigned long duration;
      duration = results[i] * precision; // pulse duration in nanoseconds
      if(duration >0) {
        if (duration < minDur)
          minDur = duration;
        if (duration > maxDur)
          maxDur = duration;
        avgDur += duration;
      }      
    }
    avgDur /= numberOfEntries;
    p("  min: %ld\n", (minDur/1000));
    p("  max: %ld\n", (maxDur/1000));
    p("  avg: %ld\n", (avgDur/1000));
    index = 0;
  }
  else
  {
    p("No pulses\n");
  }

  cycle++;
}
 
