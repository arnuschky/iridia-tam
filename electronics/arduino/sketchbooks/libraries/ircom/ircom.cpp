
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
#include "e_ad_conv.h"
#include <avr/io.h>
#include <avr/interrupt.h>
#include <Arduino.h>


// main structures are stored uniquely here.
volatile Ircom ircomData;
volatile IrcomReceive ircomReceiveData;
volatile IrcomSend ircomSendData;

volatile int pd7_status = 0;


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

    // smart computation of timings
    //double tad = (TCY_PIC * (ADCS_SETTING + 1.0) / 2.0);   // ns
    //double samplingTime = (tad * 15.0 * 8.0 / 1000.0);    // us 
    //double windowDuration = (samplingTime * SAMPLING_WINDOW);    
    //ircomSendData.markSwitchCount = ((int)(windowDuration / (samplingTime * IRCOM_MARK_SAMPLE_PER_PERIOD)));
    //ircomSendData.spaceSwitchCount = ((int)(windowDuration / (samplingTime * IRCOM_SPACE_SAMPLE_PER_PERIOD)));
    //ircomSendData.markDuration = ((windowDuration / ((double)ircomSendData.markSwitchCount) * MICROSEC)/8.0);
    //ircomSendData.spaceDuration = ((windowDuration / ((double)ircomSendData.spaceSwitchCount) * MICROSEC)/8.0);    
	
	ircomSendData.markSwitchCount = 10;
	ircomSendData.spaceSwitchCount = 5;
//	ircomSendData.markDuration = 0xEFB9;	//FOR TIMER1
//	ircomSendData.spaceDuration = 0xDF74;	//FOR TIMER1
	ircomSendData.markDuration = 191;	//FOR TIMER2
	ircomSendData.spaceDuration = 126;	//FOR TIMER2

    // activate timer
/*    T1CON = 0;                    // */
/*    T1CONbits.TCKPS = 1;          // prescsaler = 8*/
/*    TMR1 = 0;                     // clear timer 1*/
/*    PR1 = (IRCOM_RECEIVESPEED * MICROSEC)/8.0; */
/*    IFS0bits.T1IF = 0;            // clear interrupt flag*/
/*    IEC0bits.T1IE = 1;            // set interrupt enable bit*/
/*    IPC0bits.T1IP = 6;            // timer1 priority should be above user code*/
/*    T1CONbits.TON = 1;            // start Timer1*/
	  
// FOR TIMER1
	//noInterrupts();           // disable all interrupts
//	cli();
//	TCCR1A = 0;                 // clear control register A 
//	TCCR1B = 0;    
//	//TCNT1 = 0xFA3D;            // T = 92 us
//	TCNT1 = 0xF476;            // T = 184 us
//	//TCNT1 = 0xFC70;            // T = 57 us
//	TCCR1B |= (1 << CS10);    // 1 prescaler 
//	TIMSK1 |= (1 << TOIE1);   // enable timer overflow interrupt
//	//interrupts();             // enable all interrupts
//	sei();

// FOR TIMER2
	cli();
	TCCR2A = 0;                 // clear control register A 
	TCCR2B = 0;    
	TCNT2 = 210;            // T = 184 us
	//TCNT2 = 0;
	TCCR2B |= (1 << CS22);    // 64 prescaler 
	TIMSK2 |= (1 << TOIE2);   // enable timer overflow interrupt
	
	sei();
	DDRD |= 0x80;
}

// stop the timer that operates ircom
// no ircom functions are anymore available
/*inline*/ void ircomStop (void)
{
    //T1CONbits.TON = 0;    // disable Timer1
	//TIMSK2 &= ~(1 << TOIE2);   // disable timer overflow interrupt
	TIMSK2 &= ~(1 << TOIE2);   // disable timer overflow interrupt
}

/*inline*/ void ircomRestart (void)
{
    //T1CONbits.TON = 1;    // enable Timer1
	//TCNT2 = 210;            // T = 184 us
	//TIMSK2 |= (1 << TOIE2);	// enable timer overflow interrupt
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

//void test(){
    // clear interrupt flag
    //IFS0bits.T1IF = 0;
	//TCNT1 = 0xFA3D;            // T = 92 us
	//TCNT1 = 0xF476;            // T = 184 us
	//TCNT1 = 0xFC70;            // T = 57 us
	//Serial.print(ircomData.fsm);
	TCNT2 = 210;            // T = 184 us
	//TCNT2 = 0;

    // system is not on pause
    if (ircomData.paused == 0){
		// use appropriate FSM, if any
		switch (ircomData.fsm)
		{
		case IRCOM_FSM_RECEIVE :
			ircomReceiveMain(); break;
		case IRCOM_FSM_SEND :
			ircomSendMain(); break;
		}
    }

//	if(pd7_status == 0){
//		PORTD |= 0x80;
//		pd7_status = 1;
//	} else {
//		PORTD &= 0x7F;
//		pd7_status = 0;
//	}
	//Serial.print("a\n\r");
    
    // update time counter
    ircomData.time ++;
}

// IRCOM_C
#endif






