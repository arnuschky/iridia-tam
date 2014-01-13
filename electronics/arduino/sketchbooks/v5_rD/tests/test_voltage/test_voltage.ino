
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
#define VBATT_SENSE_PIN   A1  // signal VBATT_SENSE

#define PROX_SENSE_PIN    14  // ICP pin fixed to internal Timer
#define PROX_EMIT_PIN      1

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

/**
 * Prepare serial and set necessary digital pins to output.
 * Also initializes the Xbee module and resolves the address of the coordinator.
 */
void setup()
{
  Serial.begin(57600);

  pinMode(VBATT_SENSE_PIN, INPUT);
  pinMode(POWER_OFF_PIN, OUTPUT);
  digitalWrite(POWER_OFF_PIN, LOW);

  p("IRIDIA TAM initialization OK\n\n");
}

/**************************************************************************************
 *
 * Main loop. Loops every CYCLE_LENGTH ms.
 *
 **************************************************************************************/

int emptyBattCycles = 0;

void loop()
{
  // every second
  if (cycle % 10 == 0) {
    int vBatt = analogRead(VBATT_SENSE_PIN) * 33;
     p("[DEBUG] Reading battery voltage: %d.%03d V\n", (vBatt / 1000), (vBatt % 1000));

    if (vBatt <= 2700) {
      P_WARN("Battery empty!\n");
      emptyBattCycles++;
    }  
    else if (vBatt <= 3000) {
      P_WARN("Battery low!\n");
    }
    else {
      emptyBattCycles = 0;
    }  
  }
  
  if (emptyBattCycles == 10) {
    P_WARN("Battery empty, shutting down!\n");
    delay(1000);
    P_WARN("Shutting down now.\n");
    delay(500);
    digitalWrite(POWER_OFF_PIN, HIGH);
    delay(500);
  }

  delay(100);

  cycle++;
} 
