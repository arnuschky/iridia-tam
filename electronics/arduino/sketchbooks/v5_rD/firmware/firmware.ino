//IRcom library
#include <ircom.h>
#include <ircomReceive.h>
#include <ircomMessages.h>
#include <ircomTools.h>
#include <ircomSend.h>

// all libs below are included in the TAM git
#include <I2C.h>
#include <TLC59116.h>        // based on http://brmlab.cz/project/ledbar
#include <XBee.h>            // based on http://code.google.com/p/xbee-arduino/ (v0.4b)


/********************************************************************
 *
 * Example Arduino Controller for the IRIDIA TAM
 *
 ********************************************************************/

#define TAM_FIRMWARE_VERSION  "v5rD-0.2"

/**
 * Debug macros which enable bits of code used for debugging the firmware.
 */
#define DEBUG_ENABLE_SERIAL_OUTPUT    // disabled all serial printout and strings

#define ENABLE_IRCOM

#define DEBUG_TAM_INFO
#define DEBUG_XBEE_INFO


/**
 * Controls which messages are logged.
 */
// general output classes
#define P_INFO(format, ...) p( "[INFO] %04d " format, cycle, ## __VA_ARGS__ )
//#define P_INFO(format, ...)
#define P_WARN(format, ...) p( "[WARN] %04d " format, cycle, ## __VA_ARGS__ )
//#define P_INFO(format, ...)

// xbee-related output
#define P_XBEE(format, ...) p( "[XBEE] " format, ## __VA_ARGS__ )
//#define P_XBEE(format, ...)
//#define P_XBEE_RECV(format, ...) p( "[XBRX] %04d " format, cycle, ## __VA_ARGS__ )
#define P_XBEE_RECV(format, ...)
#define P_XBEE_SEND(format, ...) p( "[XBTX] %04d " format, cycle, ## __VA_ARGS__ )
//#define P_XBEE_SEND(format, ...)
//#define P_XBEE_AT(format, ...) p( "[XBAT] %04d " format, cycle, ## __VA_ARGS__ )
#define P_XBEE_AT(format, ...)

// light barrier output/debug
#define P_LIBA(format, ...) p( "[LIBA] %04d " format, cycle, ## __VA_ARGS__ )
//#define P_LIBA(format, ...)

// proximity sensor output/debug
//#define P_PROX(format, ...) p( "[PROX] %04d " format, cycle, ## __VA_ARGS__ )
#define P_PROX(format, ...)

// ircom output/debug
#define P_IRCOM(format, ...) p( "[IRCOM] %04d " format, cycle, ## __VA_ARGS__ )
//#define P_IRCOM(format, ...)


/**
 * Definition of packet types used in Xbee communication.
 * Packet type MUST be designated in first byte.
 */
// TAM to coordinator
#define PACKET_TYPE_TC_CURRENT_STATE      0  // state report
#define PACKET_TYPE_TC_READ_ROBOT_RESULT  1  // result of read robot operation
#define PACKET_TYPE_TC_WRITE_ROBOT_RESULT 2  // result of write robot operation
// coordinator to TAM
#define PACKET_TYPE_CT_SET_LEDS          10
#define PACKET_TYPE_CT_READ_ROBOT        11
#define PACKET_TYPE_CT_WRITE_ROBOT       12
#define PACKET_TYPE_CT_SHUTDOWN          13


/**
 * Definition of constants (thresholds, intervals and times). 
 * Adjust only if you know what you are doing, ESPECIALLY the network stuff!
 */
// delay between switching the light barrier on and off
#define IR_WAIT_TIME     2
// threshold required between IR LED on and off in the reading of the IR transistor
#define IR_THRESHOLD   120


// threshold for low battery voltage detection
#define BATT_VOLTAGE_WARN     3000
// threshold for low battery voltage detection
#define BATT_VOLTAGE_SHUTDOWN 2700
// number of cycles the battery has to be below BATT_VOLTAGE_SHUTDOWN before we shut down
// used to filter battery voltage sensing
#define BATT_UNDER_VOLTAGE_COUNTER_THRESHOLD 10


// if defined, the TAM tries to resolve the coordinator via network on boot
// if undefined, uses hard-coded coordinator address defined below
#define RESOLVE_COORDINATOR
// maximum number of resolve commands, if it fails it uses the hard-coded
// coordinator address defined below
#define RESOLVE_COORDINATOR_MAX_TRIES 500


// minimum length of main control cycle in ms
// cycle might be slightly longer due to fuction calls
#define CYCLE_LENGTH 10
// interval at which we send our status to the coordinator, expressed in milliseconds
#define HEARTBEAT_INTERVAL 10000
// timeout for waiting for responses for the packets sent by us, expressed in milliseconds
#define RESPONSE_TIMEOUT 1000

// interval at which we send the ircom data while the robot is inside the TAM, expressed in ms
#define IRCOM_SEND_INTERVAL 200


/**
 * Definition of hardware pins. Do NOT change this.
 */
#define XBEE_WAKE_PIN     12  // xbee sleep control
#define XBEE_CTS_PIN      13  // xbee serial cts (unused at the moment, use for large data)
#define XBEE_RTS_PIN      18  // xbee serial rts (unused at the moment, use for large data)
#define XBEE_RST_PIN      15  // xbee reset

#define XBEE_NET_ID_0_PIN  2  // bit 0 of xbee PAN id
#define XBEE_NET_ID_1_PIN  4  // bit 1 of xbee PAN id

#define NETWORK_LED_PIN   19

#define POWER_OFF_PIN      3  // all-mighty power-off pin for PSU
#define VBATT_SENSE_PIN   A1  // signal VBATT_SENSE

#define PROX_SENSE_PIN    14  // ICP pin fixed to internal Timer
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


/**
 * Global variables
 */

// current state (RGB LED value and robot presence)
state_t currentState;
// state as reported last to coordinator via Xbee
state_t lastReportedState;
// if true, the TAM reports the state to the coordinator in the next cycle
boolean reportState = false;

//
// main objects initialization
// if you don't to this here the bloody TAM resets
//
TLC59116 tlc = TLC59116();  // TLC that controls the PWM leds
XBee xbee = XBee();         // Xbee library

// Variables for reading light barriers analog
int irReadPreF;
int irReadPreB;
// stages of checking light barriers
byte irStage = 0;
// this can be considered as 8 booleans, used to filter the IR light barrier reading
uint8_t irBoolsF = 0;
uint8_t irBoolsB = 0;
uint8_t irBit = 1;

// voltage check variables
int battVoltage = 0;
uint8_t battUnderVoltageCounter = 0;

// Xbee variables
uint32_t xbeeCoordinatorAddressHigh;    // 64bit coordinator address, HSBs
uint32_t xbeeCoordinatorAddressLow;     // 64bit coordinator address, LSBs
// fallback coordinator address, MSB and LSB
const uint32_t xbeeHardcodedCoordinatorAddressesHigh = 0x0013A200;
const uint32_t xbeeHardcodedCoordinatorAddressesLow[4] = { 0x408C04A4, 0x408C04C5, 0x408D6EB4, 0x408D6EA2 };
XBeeAddress64 coordAddr64;              // struct for address
DMTxRequest statePacket;                // packet for sending TAM state
DMTxStatusResponse statePacketResponse; // response for sending TAM state packet
uint16_t nodeDiscoveryTimeout;

// variables used for AT requests (management commands for Xbee)
uint8_t atCmd[2];
AtCommandRequest atRequest;
AtCommandResponse atResponse;

// count of control cycles, each cycle = 0.1 secs
int cycle = 0;

// set to true if the last packet sent is still awaiting response
// used for getting packet ACKs asynchronously
boolean lastPacketAwaitingResponse = false;
// frame id of the last packet sent
uint8_t lastPacketFrameId = 0;
// timestamp of the last packet sent
unsigned long lastPacketSentAt = 0;
// timestamp of last heartbeat
unsigned long lastHeartbeatSentAt = 0;

// payload of the state packet
uint8_t statePayload[8];

// IRcom variables
#ifdef ENABLE_IRCOM
int irComDataToWrite = 0;
unsigned long IrComLastSendAt = 0;
#endif

// current time
unsigned long now = 0;


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
  Serial.println("Starting IRIDIA TAM");
  
#ifdef DEBUG_TAM_INFO
  p("Firmware version: %s\n\r", TAM_FIRMWARE_VERSION);
#endif

  // currently unused
  pinMode(XBEE_WAKE_PIN, OUTPUT);
  digitalWrite(XBEE_WAKE_PIN, LOW);
  pinMode(XBEE_CTS_PIN, OUTPUT);
  digitalWrite(XBEE_CTS_PIN, LOW);
  pinMode(XBEE_RTS_PIN, OUTPUT);
  digitalWrite(XBEE_RTS_PIN, LOW);
  
  // sensing for front and back IR barrier
  pinMode(IRF_SENSE_PIN, INPUT);
  pinMode(IRB_SENSE_PIN, INPUT);
  
  // setup psu pins  
  pinMode(VBATT_SENSE_PIN, INPUT);
  pinMode(POWER_OFF_PIN, OUTPUT);
  digitalWrite(POWER_OFF_PIN, LOW);

  // emit and sensing for proximity sensor
  pinMode(PROX_EMIT_PIN, OUTPUT);
  digitalWrite(PROX_EMIT_PIN, LOW);
  pinMode(PROX_SENSE_PIN, INPUT);

//IRcom initialization and start  
#ifdef ENABLE_IRCOM
  ircomStart();
  e_init_sampling();
  ircomEnableContinuousListening();
  ircomListen();
#endif  
  
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

  // XBee pan id selector
  pinMode(XBEE_NET_ID_0_PIN, INPUT);
  digitalWrite(XBEE_NET_ID_0_PIN, HIGH);  // enable internal pullup
  pinMode(XBEE_NET_ID_1_PIN, INPUT);
  digitalWrite(XBEE_NET_ID_1_PIN, HIGH);  // enable internal pullup

  //
  // Xbee initialization
  //
  p("Initializing network");
  pinMode(NETWORK_LED_PIN, OUTPUT);
  digitalWrite(NETWORK_LED_PIN, HIGH);

  // start the xbee
  Serial1.begin(9600); // xbee serial
  xbee.begin(Serial1);

  P_XBEE("\nResetting XBee, please wait.\n");
  pinMode(XBEE_RST_PIN, OUTPUT); 
  digitalWrite(XBEE_RST_PIN, HIGH);
  delay(120);
  digitalWrite(XBEE_RST_PIN, LOW);

  P_XBEE("Waiting for XBee to become ready.");
  // xbee will send MODEM_STATUS frame (0x8a) on power-up
  do {
    xbee.readPacket();
    delay(100);
    p(".");
  } while (!xbee.getResponse().isAvailable() && xbee.getResponse().getApiId() != MODEM_STATUS_RESPONSE);
  P_XBEE("OK\n");
  
  // setup AT requests
  atRequest = AtCommandRequest(atCmd);
  atResponse = AtCommandResponse();
  
  // set and read node discover timeout
  // we do this in a loop because the module sometimes isn't there yet
  P_XBEE("Set/read node discover timeout.\n");
  uint8_t ntValue = 0x82; // default 0x82
  atCmd[0] = 'N';
  atCmd[1] = 'T';
  atRequest.setCommandValue(&ntValue);
  atRequest.setCommandValueLength(1);
  atRequest.setCommand(atCmd);  
  uint16_t nodeDiscoveryTimeout = 0; 
  do {
    sendAtCommand(100, &ntValue);
    nodeDiscoveryTimeout = ntValue * 100;
    p(".");
  } while (nodeDiscoveryTimeout == 0);
  P_XBEE("OK\n");

  P_XBEE("Set/read PAN ID.\n");
  uint8_t coordinatorNumber = (digitalRead(XBEE_NET_ID_1_PIN) << 1) + digitalRead(XBEE_NET_ID_0_PIN);
  uint16_t panId = 0x01b2 + coordinatorNumber;
  swapEndian16(panId);
  atCmd[0] = 'I';
  atCmd[1] = 'D';
  atRequest.setCommandValue((uint8_t*)&panId);
  atRequest.setCommandValueLength(2);
  atRequest.setCommand(atCmd);
  sendAtCommand(50, (uint8_t*)&panId);
  swapEndian16(panId);
  P_XBEE("OK\n");

#ifdef RESOLVE_COORDINATOR
  // The code below resolves the coordinator addresss by a DN command.
  uint8_t coordinatorId[5] = {
    'C', 'O', 'O', 'R', 'D'
  };
  xbeeCoordinatorAddressHigh = 0;
  xbeeCoordinatorAddressLow = 0;
  atCmd[0] = 'D';
  atCmd[1] = 'N';
  atRequest.setCommand(atCmd);
  atRequest.setCommandValue(coordinatorId);
  atRequest.setCommandValueLength(5);
  uint8_t tries = 0;
  boolean networkLed = true;
  boolean sendRequest = true;
  unsigned long lastPacketAt = 0;

  P_XBEE("Resolving XBee coordinator address. (this might take a few seconds)");
  do {
    
    if (sendRequest) { 
      P_XBEE_AT("Sending AT command %c%c to the XBee\n\r", atRequest.getCommand()[0], atRequest.getCommand()[1]);
      xbee.send(atRequest);

      sendRequest = false;
      lastPacketAt = millis();
    }
    
    // check for timeout
    if (millis() - lastPacketAt >= nodeDiscoveryTimeout) {      
      sendRequest = true;
      P_XBEE_AT("Answer timeout!\n\r");
    }
    
    // check if we received a new packet
    xbee.readPacket();    
    if (xbee.getResponse().isAvailable()) {
      if (xbee.getResponse().getApiId() == AT_COMMAND_RESPONSE)
      {
        xbee.getResponse().getAtCommandResponse(atResponse);        
        if (atResponse.getCommand()[0] == 'D' && atResponse.getCommand()[1] == 'N')
        {
          sendRequest = true;
        }
      }
    }
       
    networkLed = !networkLed;
    digitalWrite(NETWORK_LED_PIN, networkLed);
    delay(500);
    p(".");
    tries++;
  } while (tries < RESOLVE_COORDINATOR_MAX_TRIES && (atResponse.getStatus() != 0x40 || atResponse.getValueLength() != 10));

  if (atResponse.getStatus() == 0x40 && atResponse.getValueLength() == 10) 
  {
    memcpy(&xbeeCoordinatorAddressHigh, atResponse.getValue()+2, 4);
    memcpy(&xbeeCoordinatorAddressLow,  atResponse.getValue()+6, 4);

    // swap endianess of addresses
    swapEndian32(xbeeCoordinatorAddressHigh);
    swapEndian32(xbeeCoordinatorAddressLow);

    P_XBEE("SUCCESS.\r\n");
  }
  else
  {
    P_XBEE("FAILURE.\r\n");       
  }
#endif

  // use the hardcoded coordinator address as fall-back
  if (xbeeCoordinatorAddressHigh == 0 || xbeeCoordinatorAddressLow == 0)
  {
    P_XBEE("Using hard-coded coordinator address.\n\r");
    xbeeCoordinatorAddressHigh = xbeeHardcodedCoordinatorAddressesHigh;
    xbeeCoordinatorAddressLow = xbeeHardcodedCoordinatorAddressesLow[coordinatorNumber];
  }
  digitalWrite(NETWORK_LED_PIN, LOW);

  // clear state and setup packets to coordinator
  memset(&currentState, sizeof(state_t), 0);
  memset(&lastReportedState, sizeof(state_t), 0);
  coordAddr64 = XBeeAddress64(xbeeCoordinatorAddressHigh, xbeeCoordinatorAddressLow);
  statePacket = DMTxRequest(coordAddr64, statePayload, sizeof(statePayload));
  statePacketResponse = DMTxStatusResponse();  
  
  atRequest.setCommandValueLength(0);
  atRequest.setCommandValue(NULL);

  p("OK\n");
  
  
#ifdef DEBUG_XBEE_INFO
  p("Xbee variables:\n\r");
  p("  Discovery time: 0x%4d\n\r", nodeDiscoveryTimeout);

  uint8_t myName[26];
  memset(myName, 0, 26);
  atCmd[0] = 'N';
  atCmd[1] = 'I';
  atRequest.setCommand(atCmd);
  sendAtCommand(50, myName);
  myName[25] = 0;
  p("  TAM network ID: %s\n\r", myName);

  uint32_t highAddress = 0;
  atCmd[0] = 'S';
  atCmd[1] = 'H';
  atRequest.setCommand(atCmd);
  sendAtCommand(50, (uint8_t*)&highAddress);
  swapEndian32(highAddress);
  p("  High address  : 0x%08lx\n\r", highAddress);

  uint32_t lowAddress = 0;
  atCmd[0] = 'S';
  atCmd[1] = 'L';
  atRequest.setCommand(atCmd);
  sendAtCommand(50, (uint8_t*)&lowAddress);
  swapEndian32(lowAddress);
  p("  Low address   : 0x%08lx\n\r", lowAddress);

  p("  PAN ID        : 0x%04x\n\r", panId);

  uint8_t channel = 0;
  atCmd[0] = 'C';
  atCmd[1] = 'H';
  atRequest.setCommand(atCmd);
  sendAtCommand(50, &channel);
  p("  Channel       : 0x%02x\n\r", channel);
  
  // coordinator address
  p("  Coordinator h.: 0x%08lx\n\r", xbeeCoordinatorAddressHigh);
  p("  Coordinator l.: 0x%08lx\n\r", xbeeCoordinatorAddressLow);
#endif

  p("IRIDIA TAM initialization: ");
  Serial.println("OK");
}


/**
 * Sends a synchronous AT command to the Xbee and waits for a reply.
 * Used in initializing the Xbee. Do not use afterwards as synchronized.
 * @param timeout  time to wait for response in ms
 * @param buffer   pointer to buffer for result - you have to take care to make this large enough - NULL if not desired
 * @return 0 if success, error code else (255 if unspecified error)
 */
uint8_t sendAtCommand(int timeout, uint8_t* buffer)
{
  P_XBEE_AT("Sending AT command %c%c to the XBee\n\r", atRequest.getCommand()[0], atRequest.getCommand()[1]);

  // send the command
  xbee.send(atRequest);

  unsigned long start = millis();
  do {
    xbee.readPacket();    
    if (xbee.getResponse().isAvailable()) {
      if (xbee.getResponse().getApiId() == AT_COMMAND_RESPONSE)
      {
        xbee.getResponse().getAtCommandResponse(atResponse);
        //printResponseFrameData(xbee.getResponse());
        
        if (atResponse.getCommand()[0] == atRequest.getCommand()[0] && atResponse.getCommand()[1] == atRequest.getCommand()[1])
        {
          P_XBEE_AT("Got response for correct AT Command %c%c\n\r", atResponse.getCommand()[0], atResponse.getCommand()[1]);
    
          if (atResponse.isOk())
          {
            P_XBEE_AT("AT command %c%c was successful!\n\r", atResponse.getCommand()[0], atResponse.getCommand()[1]);
            if (atResponse.getValueLength() > 0)
            {
              P_XBEE_AT("AT command %c%c value length is %d\n\r", atResponse.getCommand()[0], atResponse.getCommand()[1], atResponse.getValueLength());
    
              if (buffer != NULL)
                memcpy(buffer, atResponse.getValue(), atResponse.getValueLength());
            }
    
            return 0;
          }
          else
          {
            P_XBEE_AT("AT command %c%c return error code: 0x%02x\n\r", atResponse.getCommand()[0], atResponse.getCommand()[1], atResponse.getStatus());                        
            if (atResponse.getValueLength() > 0)
            {
              P_XBEE_AT("AT command %c%c value length is %d\n\r", atResponse.getCommand()[0], atResponse.getCommand()[1], atResponse.getValueLength());
            }
            
            return atResponse.getStatus();
          }
        }
        else
        {
          P_XBEE_AT("Got response for wrong AT Command %c%c, ignoring\n\r", atResponse.getCommand()[0], atResponse.getCommand()[1]);
        }
      }
      else
      {
          P_XBEE_AT("Expected AT response but got 0x%02x\n\r", xbee.getResponse().getApiId());
      }
    }
  } while ((millis() - start) < timeout);
  
  P_XBEE_AT("No response from radio in %d ms\n\r", timeout);
  return 255;
}


/**
 * Shutdown by switching of the PSU.
 * This is irreversible by software.
 * Called when we detect a low battery.
 */
void shutdown()
{
  P_WARN("Shutting down now.\n");
  delay(500);
  digitalWrite(POWER_OFF_PIN, HIGH);
  delay(500);
}


/**
 * Check for battery voltage and shut down the TAM
 * in case it's too low for a couple of steps in a row.
 */
void checkBatteryVoltage()
{
  battVoltage = analogRead(VBATT_SENSE_PIN) * 33;
//  p("[DEBUG] Battery voltage: %d.%03d V\n", (battVoltage / 1000), (battVoltage % 1000));
     
  if (battVoltage <= BATT_VOLTAGE_SHUTDOWN) {
    P_WARN("Battery empty!\n");
    battUnderVoltageCounter++;
        
    if (battUnderVoltageCounter == BATT_UNDER_VOLTAGE_COUNTER_THRESHOLD)
    {
      P_WARN("Battery empty, shutting down!\n");
      delay(1000);
      shutdown();
    }
  }  
  else if (battVoltage <= BATT_VOLTAGE_WARN) {
    P_WARN("Battery low!\n");
  }
  else {
    battUnderVoltageCounter = 0;
  }  
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
  currentState.redLed = redLed;

  tlc.setPinPWM(LED_GREEN_L, greenLed);
  tlc.setPinPWM(LED_GREEN_M, greenLed);
  tlc.setPinPWM(LED_GREEN_R, greenLed);
  tlc.setPinPWM(LED_GREEN_T, greenLed);
  currentState.greenLed = greenLed;

  tlc.setPinPWM(LED_BLUE_L,  blueLed);
  tlc.setPinPWM(LED_BLUE_M,  blueLed);
  tlc.setPinPWM(LED_BLUE_R,  blueLed);
  tlc.setPinPWM(LED_BLUE_T,  blueLed);
  currentState.blueLed = blueLed;
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

    boolean frontIrBarrierBlocked = (abs(irReadPostF - irReadPreF) < IR_THRESHOLD);
    boolean backIrBarrierBlocked  = (abs(irReadPostB - irReadPreB) < IR_THRESHOLD);

/*
    P_LIBA("Front IR barrier: pre=%d post=%d difference=%d threshold=%d\n\r",
      irReadPreF, irReadPostF, abs(irReadPostF - irReadPreF), IR_THRESHOLD);
    P_LIBA("Back IR barrier : pre=%d post=%d difference=%d threshold=%d\n\r",
      irReadPreB, irReadPostB, abs(irReadPostB - irReadPreB), IR_THRESHOLD);
*/

    // Check if we detected the presence of a robot that is fully in the TAM.
    // For a robot to be fully in the TAM, the front light barrier needs to be free
    // and the back light barrier needs to be blocked (back = further inside).
    // The code below sets bits in a byte to the according reading. There are
    // 8 bits, thus, it takes 8 identical readings to switch state.
    // This is done to filter the sensor reading.
    if (frontIrBarrierBlocked)
    {
      irBoolsF |= irBit;
    }
    else 
    {
      irBoolsF &= ~irBit;
    }
    if (backIrBarrierBlocked) 
    {
      irBoolsB |= irBit;
    }
    else 
    {
      irBoolsB &= ~irBit;
    }

    irBit = irBit << 1; // shift next-bit marker
    if (irBit == 0)     // and overflow if neccessary
    {
      irBit = 1;
    }
    
    
    // if all 8 booleans are (un)set, we change state
    if (currentState.robotPresent == false && irBoolsF == 0x00 && irBoolsB == 0xff) 
    {
      currentState.robotPresent = true;
      P_LIBA("[DEBUG] IR changed to robot present!\n\r");
    } 
    else if (currentState.robotPresent == true && irBoolsF == 0x00 && irBoolsB == 0x00) 
    {
      currentState.robotPresent = false;
      P_LIBA("[DEBUG] IR changed to robot NOT present!\n\r");
    }
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


/**
 * Send the current state of the TAM to the coordinator.
 * Uses global variable currentState for sending.
 * @return frame id of packet sent
 */
uint8_t sendStateToCoordinator()
{
  statePayload[0] = PACKET_TYPE_TC_CURRENT_STATE;  // packet type
  statePayload[1] = currentState.redLed;
  statePayload[2] = currentState.greenLed;
  statePayload[3] = currentState.blueLed;
  statePayload[4] = currentState.robotPresent;
  statePayload[5] = battVoltage & 0xff;
  statePayload[6] = (battVoltage >> 8) & 0xff;
  statePayload[7] = currentState.robotData;

  statePacket.setFrameId(xbee.getNextFrameId());
  xbee.send(statePacket);

/*
  P_XBEE_SEND("Sending packet (frame id %d):\n\r", statePacket.getFrameId());
  P_XBEE_SEND("  Packet type: %02x\n\r", statePayload[0]);
  P_XBEE_SEND("  State r,g,b: %02x,%02x,%02x\n\r", statePayload[1], statePayload[2], statePayload[3]);
  P_XBEE_SEND("  Robot there: %02x\n\r", statePayload[4]);
  P_XBEE_SEND("  Battery V  : %02x, %02x\n\r", statePayload[5], statePayload[6]);
 */
 
  return statePacket.getFrameId();
}


/**************************************************************************************
 *
 * Main loop. Loops every CYCLE_LENGTH ms.
 *
 **************************************************************************************/
void loop()
{
  // Check battery voltage and light barrier state. DO NOT swap the two lines
  checkBatteryVoltage();            
  checkLightBarriersAnalog();

#ifdef ENABLE_IRCOM
  // IRcom Send data. We send every IRCOM_SEND_INTERVAL ms in order to have enough time for receiving
  now = millis();
  if (currentState.robotPresent && ((IrComLastSendAt + IRCOM_SEND_INTERVAL) < now))
  {
    IrComLastSendAt = now;
    ircomSend(irComDataToWrite);    
    while (ircomSendDone() == 0);
    P_IRCOM("Send done. Value: %d\n\r", irComDataToWrite);
  }
#endif


  // report it to the coordinator if
  //  - HEARTBEAT_INTERVAL passed, no matter what
  //  - status changed and hasn't been sent yet
  now = millis();
  if (!lastPacketAwaitingResponse &&
      (((lastHeartbeatSentAt + HEARTBEAT_INTERVAL) < now) ||
       (reportState == true) ||
       (memcmp(&lastReportedState, &currentState, sizeof(state_t)) != 0)))
  {
    lastPacketFrameId = sendStateToCoordinator();
    lastPacketSentAt = now;
    lastPacketAwaitingResponse = true;
    P_XBEE_SEND("Sent state packet at cycle %d frame id %d\n\r", cycle, lastPacketFrameId);

    if ((lastHeartbeatSentAt + HEARTBEAT_INTERVAL) < now)
    {
      lastHeartbeatSentAt = now; 
      P_XBEE_SEND("  -> heartbeat\n\r");
    }
    else if (reportState == true)
    {
      P_XBEE_SEND("  -> reply after command\n\r");
      reportState = false;
    }
    else
    {
      P_XBEE_SEND("  -> state change: currentState.robotPresent=%d\n\r", currentState.robotPresent );
    }
  }

  // check if we last packet sent timed out
  now = millis();
  if (lastPacketAwaitingResponse && (lastPacketSentAt + RESPONSE_TIMEOUT) < now)
  {
    P_XBEE_SEND("Error: didn't get a response for previous packet frame id %d.\n\r", lastPacketFrameId);
    lastPacketAwaitingResponse = false;
  }

  // try to read a packet and treat it if we did
  xbee.readPacket();
  if (xbee.getResponse().isAvailable())
  {
    // we got a reply to one of our previous packets; see if it's expected
    if (xbee.getResponse().getApiId() == DM_TX_STATUS_RESPONSE)
    {
      xbee.getResponse().getDMTxStatusResponse(statePacketResponse);

      // get the delivery status, the fifth byte
      if (statePacketResponse.getDeliveryStatus() == SUCCESS)
      {
        if (lastPacketAwaitingResponse && statePacketResponse.getFrameId() == lastPacketFrameId)
        {
          P_XBEE_SEND("  -> success, got response for frame id %d\n\r", lastPacketFrameId);
          // update state as coordinator received it
          memcpy(&lastReportedState, &currentState, sizeof(state_t));
          lastPacketAwaitingResponse = false;
        }
        else
        {
          P_XBEE_SEND("  -> wtf? got response for unexpected frame id %d\n\r", lastPacketFrameId);
          lastPacketAwaitingResponse = false; // TODO: check if this is ok?
        }
      }
      else
      {
        P_XBEE_SEND("  -> FAIL! The remote XBee did not receive our packet. Is it powered on?\n\r");
      }
    }
    else if (xbee.getResponse().getApiId() == DM_RX_RESPONSE)
    {
      DMRxResponse rxResponse = DMRxResponse();
      xbee.getResponse().getDMRxResponse(rxResponse);
      P_XBEE_RECV("Received packet DM_RX_RESPONSE with data length = %d\n\r", rxResponse.getDataLength());

      // check first byte if
      if (rxResponse.getData(0) == PACKET_TYPE_CT_SET_LEDS)
      {
        P_INFO("Got command SET_LEDS r=%d,g=%d,b=%d\n\r", rxResponse.getData(1), rxResponse.getData(2), rxResponse.getData(3));
        setRgbLeds(rxResponse.getData(1), rxResponse.getData(2), rxResponse.getData(3));
        reportState = true; // always reply with a state report after getting a SET_LEDS command
      }
#ifdef ENABLE_IRCOM      
      else if (rxResponse.getData(0) == PACKET_TYPE_CT_WRITE_ROBOT)
      {
        P_INFO("Got command WRITE_ROBOT value=%d\n\r", rxResponse.getData(1));
        irComDataToWrite = rxResponse.getData(1);
      }
#endif
      else if (rxResponse.getData(0) == PACKET_TYPE_CT_SHUTDOWN)
      {
        P_WARN("Got shutdown command, powering off...\n\r");
        shutdown();
      }
      else
      {
        P_XBEE_RECV("Got unknown packet type!\n\r");
      }
    }
    else
    {
      reportState = true;
      P_XBEE_RECV("Received packet with unexpeced api id %d\n\r", xbee.getResponse().getApiId());
    }
  }
  else if (xbee.getResponse().isError())
  {
    //  1 - CHECKSUM_FAILURE 2 - PACKET_EXCEEDS_BYTE_ARRAY_LENGTH 3 - UNEXPECTED_START_BYTE
    reportState = true; // reply with report when package was garbled
    P_XBEE_RECV("Error reading packet.  Error code: %d\n\r", xbee.getResponse().getErrorCode());
  }
  

#ifdef ENABLE_IRCOM
  // receive data from the robot through IRcom
  if (currentState.robotPresent)
  {
      IrcomMessage imsg;
      ircomPopMessage(&imsg);
      if (imsg.error == 0)
      {
        int val = (int)imsg.value;         
        if (val != currentState.robotData)
        {
          reportState = true;
          currentState.robotData = val;
        }
        P_IRCOM("currentState.robotData : %d\n\r", currentState.robotData);
        P_IRCOM("Receive successful value : %d\n\r", val);
      }
      else if (imsg.error > 0)
      {
        int val = (int) imsg.value;
        P_IRCOM("Receive FAILED with value : %d, ERROR: %d\n\r", val, imsg.error);
      }
  } 
#endif


  // we keep the cycle under 10000 for easy display
  cycle++;
  if (cycle == 10000)
  {
    cycle = 0;
  }
  delay(CYCLE_LENGTH);
}

