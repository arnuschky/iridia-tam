
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


// Main file of the ircom module

#ifndef IRCOM_C
#define IRCOM_C


#include <string.h>

#include "ircom.h"
#include "ircomMessages.h"
#include "ircomReceive.h"
#include "ircomSend.h"
#include "ircomTools.h"
#include <avr/io.h>
#include <avr/interrupt.h>
#include <Arduino.h>


// main structures are stored uniquely here.
volatile Ircom ircomData;
volatile IrcomReceive ircomReceiveData;
volatile IrcomSend ircomSendData;


// start the timer that operates ircom
// default values are set and ircom module is waiting for further instructions
void ircomStart ()
{
    ircomFlushMessages();    
    ircomDisableContinuousListening();
    ircomPause(0);
    ircomSetOmnidirectional();

    // set a number of default values
    ircomData.fsm = IRCOM_FSM_IDLE;
    ircomData.time = 0;
    ircomReceiveData.receiving = 0;
    ircomReceiveData.done = 1;
    ircomSendData.done = 1; 
	
	ircomSendData.markSwitchCount = 10;
	ircomSendData.spaceSwitchCount = 5;
	ircomSendData.markDuration = 191;	//FOR TIMER2
	ircomSendData.spaceDuration = 126;	//FOR TIMER2


	// Initialize timer 2 interrupt
	cli();
	TCCR2A = 0;                 // clear control register A 
	TCCR2B = 0;    
	TCNT2 = 210;            // T = 184 us IRcom idle period
	TCCR2B |= (1 << CS22);    // 64 prescaler 
	TIMSK2 |= (1 << TOIE2);   // enable timer overflow interrupt
	sei();
}

// stop the timer that operates ircom
// no ircom functions are anymore available
/*inline*/ void ircomStop (void)
{
	TIMSK2 &= ~(1 << TOIE2);   // disable timer overflow interrupt
}

/*inline*/ void ircomRestart (void)
{
	TCNT2 = 210;            // T = 184 us
	TIMSK2 |= (1 << TOIE2);	// enable timer overflow interrupt
}

/*inline*/ void ircomPause(int status)
{
    ircomData.paused = status;
}

void ircomEnableContinuousListening()
{
    ircomReceiveData.continuousListening = 1;
}

void ircomDisableContinuousListening()
{
    ircomReceiveData.continuousListening = 0;
}

void ircomSetOmnidirectional()
{
    ircomSendData.type = IRCOM_SEND_OMNI;
}

void ircomSetDirectional(int sensor)
{
    ircomSendData.type = IRCOM_SEND_DIRECTIONAL;
    ircomSendData.sensor = sensor % 4; // ir emitters are coupled... -> only 4 pulses
}



//==================================================================================
// the 1st interrupt is continuously called, it implements all the functions
// of ircom using finite state machines
//void _ISRFAST _T1Interrupt(void)

ISR(TIMER2_OVF_vect){

	TCNT2 = 210;            // T = 184 us IRcom idle period

    // system is not on pause
    if (ircomData.paused == 0){
		// use appropriate FSM, if any
		switch (ircomData.fsm)
		{
		case IRCOM_FSM_RECEIVE :
			//The receive main is now called inside the "sampling" interrupt in ircomReceive.cpp
			/*ircomReceiveMain();*/ break;
		case IRCOM_FSM_SEND :
			ircomSendMain(); break;
		}
    }
   
    // update time counter
    ircomData.time ++;
}

// IRCOM_C
#endif






