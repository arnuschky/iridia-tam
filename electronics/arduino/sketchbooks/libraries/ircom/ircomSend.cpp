
/*
    Copyright 2007 Alexandre Campo, Alvaro Guttierez, Valentin Longchamp.

    This file is part of libIrcom.

    libIrcom is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License.

    libIrcom is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with libIrcom.  If not, see <http://www.gnu.org/licenses/>.
*/

// ircom functions to send messages

#ifndef IRCOM_SEND_C
#define IRCOM_SEND_C


#include <string.h>
#include "ircom.h"
#include "ircomTools.h"
#include "ircomSend.h"
#include <avr/io.h>
#include <avr/interrupt.h>

//NEW IMPLEMENTATION PWM PINS
#include <SoftwareSerial.h>
#include <Arduino.h>


void ps(char *fmt, ... )
{
  char tmp[128]; // resulting string limited to 128 chars
  va_list args;
  va_start(args, fmt );
  vsnprintf(tmp, 128, fmt, args);
  va_end (args);
  Serial.print(tmp);
}

// interface for end user
void ircomSend (long int value)
{	
	// stop processing messages
    ircomPause(1);

    // stop any listening (rough, but what do you expect :-))
    if (ircomData.fsm == IRCOM_FSM_RECEIVE)
    {
	ircomStopListening();
	ircomSendData.interruptedListening = 1;
    }
    else 
	ircomSendData.interruptedListening = 0;

    //Precalculated timings for sender
    ircomSendCalcSending(value);

	// initialise fsm
    ircomSendData.done = 0;
    ircomSendData.currentBit = 0;
    ircomSendData.pulseState = 0;
    ircomSendNextBit();

    //ircom to Send State
    ircomData.fsm = IRCOM_FSM_SEND;

    // process messages again
    ircomPause(0);
}

// implement precalculated timings for sender
void ircomSendCalcSending(long int value)
{
    int i;
    ircomWord w;
    ircomInt2Bin(value, w);
	
    // Calc the comp2 of the checksum of 2 bits.
    int bitSum=0;
    for(i = 0; i<IRCOM_WORDSIZE; i++)
    {
	if (w[i] == IRCOM_SPACE)
	    bitSum++;
    }
    bitSum = bitSum % 4;
    bitSum = 4 - bitSum;

    // copy check sum at end of the word
    w [IRCOM_WORDSIZE + 1] = bitSum & 0x0001;
    w [IRCOM_WORDSIZE] = (bitSum >> 1) & 0x0001;

    // Create the 2 start bits of the MARK frequency
    for (i = IRCOM_WORDSIZE + 1 ; i >= 0; i--)
    {
    	w[i+2] = w[i];
    }
    w[0] = IRCOM_MARK;
    w[1] = IRCOM_MARK;

    for (i = 0; i < IRCOM_WORDSIZE + 4; i++)
    {
	//MARK
	if (w[i] == IRCOM_MARK)
    {
		ircomSendData.durations[i] = ircomSendData.markDuration;
		ircomSendData.switchCounts[i] = ircomSendData.markSwitchCount;
    }
	
	// SPACE
	else if (w[i] == IRCOM_SPACE)
    {
	    ircomSendData.durations[i] = ircomSendData.spaceDuration;
		ircomSendData.switchCounts[i] = ircomSendData.spaceSwitchCount;
    }
    }
}


// interface for end user
// this function is a bit special, in principle you would think it is useless...
// in fact, gcc optimizes such that .done is put in a register and the register is read in a loop, simply because gcc does not take care of the fact that interrupts may happen. This optim is therefore not consistent but still takes place. The workaround is to call a function to make sure that done is not cached in a register...
int ircomSendDone ()
{
    return ircomSendData.done;
}

inline int ircomIsSending ()
{
    if (ircomData.fsm == IRCOM_FSM_SEND)
	return 1;
    else 
	return 0;
}


//===============================================================
// FSM Write : entry point
void ircomSendMain()
{
    // do the hardware signal toggle
    if (ircomSendData.pulseState == 0)
    {
	if (ircomSendData.type == IRCOM_SEND_OMNI)
	{
		PORTB |= 0x02;
	}
	else
	{
	    switch (ircomSendData.sensor)
	    {
		case 0 : PORTB |= 0x02;
	    }
	}
	
	ircomSendData.pulseState = 1;
    }
    else
    {
	if (ircomSendData.type == IRCOM_SEND_OMNI)
	{      
		PORTB &= 0xfd;      
	}
	else
	{
	    switch (ircomSendData.sensor)
	    {
		case 0 : PORTB &= 0xfd; break;
	    }
	}
	
	ircomSendData.pulseState = 0;
    }

    ircomSendData.switchCounter++;

    if (ircomSendData.switchCounter >= ircomSendData.switchCountMax){
      	ircomSendNextBit();
    }else {
		TCNT2 = ircomSendData.durations[ircomSendData.currentBit-1];
		
    }
}

void ircomSendNextBit()
{
    // there are bits to send
    if (ircomSendData.currentBit < IRCOM_WORDSIZE + 4)
    {
		
		ircomSendData.switchCounter = 0;
		TCNT2 = ircomSendData.durations[ircomSendData.currentBit];
		
		ircomSendData.switchCountMax = ircomSendData.switchCounts[ircomSendData.currentBit++];
		
    }
    // word sent, go back to idle and setup some informations
    else
    {
		// make sure irs go off at the end of the message ...
		PORTB &= 0xfd;
	
		ircomSendData.done = 1;

		TCNT2 = 210;            // T = 184 us IRcom idle period
		ircomData.fsm = IRCOM_FSM_IDLE;


		// Reset sampling status: default sampling window size and reset indexes
		e_sampling_off();
		e_sampler_default_sampling_window();
		e_sampling_reset();

		// restart listening immediately if previously interrupted
		if (ircomSendData.interruptedListening == 1){
			ircomListen();
		}
    }
}

// IRCOM_SEND_C
#endif
