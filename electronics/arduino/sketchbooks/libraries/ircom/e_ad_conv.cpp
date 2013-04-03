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

    This file was strongly inspired by another e_ad_conv.c from the 
    library released by EPFL. Authors of this "ancestor" are 
    Jean-Joël Borter, Michael Bonani, Valentin Longchamp.
*/

#include <e_ad_conv.h>
#include <ircom.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <avr/io.h>
#include <avr/interrupt.h>
#include <Arduino.h>

int ad_buffer1[NB_IR_SENSORS * SAMPLING_WINDOW];
int ad_buffer2[NB_IR_SENSORS * SAMPLING_WINDOW];

int* ad_sampling;
int* ad_received;

volatile char array_filled = 0;
volatile unsigned int e_last_ir_scan_id = 0;
volatile int e_ad_samples_to_skip = 0;

volatile int ad_disable_ircom;
volatile int pd5_status = 0;


/**
 * Set up the different ADC register to process the AD conversion
 * by scanning the used AD channels. Each value of the channels will
 * be stored in a different AD buffer register and an inturrupt will
 * occure at the end of the scan.
 *
 * @param  void
 * @return void
 */

void e_init_ad_scan(void)
{
    // continuous sampling
    ad_disable_ircom = 0;
    ad_sampling = ad_buffer1;
    ad_received = ad_buffer2;

/***** ADC SETTINGS FOR AUTO TRIGGERED MODE WITH TIMER 1 *****/
	
//	cli();
//	TCCR1A = 0;
//	TCCR1B = 0;	
//	OCR1B = 0;
//	TCNT1 = 0;
//	//TCNT1 = 64355;
//	TCCR1B |= (1 << WGM12);	//Set CTC mode	
//	TCCR1B |= (1 << CS10);    // 1 prescaler 	
//	//OCR1A = 1181;		// To obtain a period of 81 us
//	OCR1A = 1296;
//	//OCR1A=651;		// To obtain a period of 40 us
//	//TIMSK1 |= (1 << TOIE1);
//	TIMSK1 |= (1 << OCIE1A);
//	sei();


//	ADCSRB |= (1 << ADTS2) |(1 << ADTS0);	/* Trigger source = Timer/Counter1 compare match B*/	
//	ADCSRB &= ~(1 << ADTS1);	

	ADCSRB &= ~((1 << ADTS2) | (1 << ADTS1) | (1 << ADTS0));	/* Free running mode*/

//	ADCSRB |= (1 << ADTS2) |(1 << ADTS1);	/* Trigger source = Timer/Counter1 Overflow*/	
//	ADCSRB &= ~(1 << ADTS0);	

	ADMUX |= (1 << MUX0); // Read ADC1 channel*/		
	ADMUX &= ~(1 << MUX1);
	ADMUX &= ~(1 << MUX2);
	ADMUX &= ~(1 << ADLAR); // Right adjust ADC result*/
	ADCSRA |= (1 << ADEN);  // Enable ADC */	
	ADCSRA |= (1 << ADPS2) | (1 << ADPS1) | (1 << ADPS0); // Set ADC prescaler to 128  */
	//ADCSRA &= ~(1 << ADPS0);	

	ADMUX |= (1 << REFS0); // Set ADC reference to AVCC */

	ADCSRA |= (1 << ADIE);  // Enable ADC Interrupt */
	ADCSRA |= (1 << ADATE);  // Set Auto trigger mode*/

//	ADCSRB |= (1 << ADTS2) |(1 << ADTS1); /* Trigger source = Timer/Counter1 Overflow */
//	ADCSRB &= ~(1 << ADTS0);

	ADCSRA |= (1 << ADSC);

	//DDRD |= 0x20;
	//DDRD |= 0x80;
}



void e_ad_scan_on(void)
{
	ADCSRA |= (1 << ADATE);	// Set Auto trigger mode
	ADCSRA |= (1 << ADIE); // Enable AD interrupt
	ADCSRA |= (1 << ADSC); //Start sampling
}

/*inline*/ void e_ad_scan_reset(void)
{
    e_last_ir_scan_id = 0;      // reset array index
    array_filled = 0;	        // reset array filled flag
}

/*inline*/ void e_ad_skip_samples (int samples_count)
{
    e_ad_samples_to_skip = samples_count;
    e_last_ir_scan_id = 0;      // reset array index
    array_filled = 0;	        // reset array filled flag
}

void e_ad_scan_off(void)
{
	ADCSRA &= ~(1 << ADATE); 
//	ADCSRA &= ~(1 << ADEN); // disable AD
	ADCSRA &= ~(1 << ADIE); // disable AD interrupt
}

void e_ad_ircom_interrupt()
{
    if (ad_disable_ircom == 1)
		return;

    if (e_ad_samples_to_skip > 0){
		e_ad_samples_to_skip--;
		return;
    }
	
	memcpy (ad_sampling + e_last_ir_scan_id * NB_IR_SENSORS, (char *) &ADCW, NB_IR_SENSORS * 2);
    e_last_ir_scan_id++;
    
    // Array full ?
    if (e_last_ir_scan_id >= SAMPLING_WINDOW){
		e_last_ir_scan_id = 0;
		array_filled = 1;
	
		// swap buffers
		int* tmp = ad_received;
		ad_received = ad_sampling;
		ad_sampling = tmp;
    }
}

/**
 * Save the AD buffer registers in differents arrays
 *
 * @param  void
 * @return void
 */
ISR(ADC_vect){
    // clear the A/D Interrupt flag bit
//    IFS0bits.ADIF = 0;

	//TOGGLE PD5 FOR DEGUB
//	if(pd5_status == 0){
//		PORTD |= 0x20;
//		pd5_status = 1;
//	} else {
//		PORTD &= 0xDF;
//		pd5_status = 0;
//	}
	
	//TIFR1 |= (1 << OCF1B);

	e_ad_ircom_interrupt();
}

/*inline*/ char e_ad_is_array_filled(void)
{
    char result = array_filled;
    array_filled = 0;
    return result;
}
