
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


#ifndef IRCOM_RECEIVE_C
#define IRCOM_RECEIVE_C

#include "ircom.h"
#include "ircomTools.h"
#include "ircomReceive.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <avr/io.h>
#include <avr/interrupt.h>
#include <stdio.h>

#include <Arduino.h>

#define LAST_BIT 1 //Last bit of a word
#define NOT_LAST_BIT 0 //All the other bits of the word, excluding the last and the first (only in case we have to take 6 samples with the first)
#define FIRST_BIT 2 //First bit of the word in case the last of the previous word had taken one sample less then necessary

#define RUNNING 0
#define RANDOM_WAIT 1

#define MIN_WAIT_INTERVAL 1
#define MAX_WAIT_INTERVAL 5

//Sampling
unsigned int buffer1[NB_IR_SENSORS * SAMPLING_WINDOW + 1];
unsigned int buffer2[NB_IR_SENSORS * SAMPLING_WINDOW + 1];

unsigned int* sampling;
unsigned int temp;
unsigned int* received;
unsigned int sampling_window = SAMPLING_WINDOW;

volatile char array_filled = 0;
volatile unsigned int e_last_ir_scan_id = 0;
unsigned int wait_fsm = RUNNING;
unsigned long nowReceive = 0;
unsigned long randomWaitSince = 0;
unsigned int randomInterval = 0;


const byte prescaleBits = B010;    // see Table 18-1 or data sheet
volatile byte sampling_enable  = 0; // 0 disables capture, 1 enables
 

volatile byte is_mark = 0; // Flag set to 1 when the bit is a mark (0)
int synch_bit_index = -2;
int waiting_second_buffer = 0; // Flag = true when we are waiting for the second buffer of 5 samples that compose a zero
int odd_edges = 0; //Flag = 1 when the total number of 1 inside a word is odd
int bit = 0; // The type of bit we are receiving
unsigned long totalDur = 0; // Total duration in us of a window of samples

const int prescale = 8;            // prescale factor (each tick 0.5 us @16MHz)
// calculate time per counter tick in us
const long precision = (1000000/(F_CPU/1000)) * prescale; 

void pr(char *fmt, ... )
{
  char tmp[128]; // resulting string limited to 128 chars
  va_list args;
  va_start(args, fmt );
  vsnprintf(tmp, 128, fmt, args);
  va_end (args);
  Serial.print(tmp);
}


// interface for end user
void ircomListen ()
{
    // stop processing messages
    ircomPause(1);

    // initialise FSM
    ircomReceiveData.done = 0;
    ircomReceiveData.receiving = 0;
    ircomReceiveData.state = 0;
	odd_edges = 0;
	
	waiting_second_buffer = 0;
    ircomData.fsm = IRCOM_FSM_RECEIVE;
    
	e_sampling_on();
	TCNT2 = 210;

    // process messages again
    ircomPause(0);
}

// interface for end user
int ircomReceiveDone ()
{
    return ircomReceiveData.done;
}

inline int ircomIsReceiving ()
{
    if (ircomReceiveData.receiving == 1)
	return 1;
    else 
	return 0;
}

inline int ircomIsListening ()
{
    if (ircomData.fsm == IRCOM_FSM_RECEIVE)
	return 1;
    else 
	return 0;
}

void ircomStopListening ()
{
    // stop receiving
    ircomReceiveData.done = 0;
    ircomReceiveData.receiving = 0;    
    ircomReceiveData.state = 0;
    
	e_sampling_off();
    // set fsm in idle mode
    ircomData.fsm = IRCOM_FSM_IDLE;
	// Reset the receiving state machine
	ircomResetReceive();
}

void ircomResetReceive(){
	odd_edges = 0;
	waiting_second_buffer = 0;
	synch_bit_index = -2;	
	bit = NOT_LAST_BIT;
	wait_fsm = RUNNING;
}


//================================================================
// FSM Receive : entry point
// this should be called by AD interrupt 
// after a full window of samples has been acquired
void ircomReceiveMain()
{
    switch (ircomReceiveData.state)
    {
		case 0 :
			ircomReceiveListen(); break;
		case 1 : 
			ircomReceiveWord(); break;
    }
}

void ircomReceiveListen()
{	
	//Recognition of the first two bits (0) of synchronization
	
	int i;
	totalDur = 0;
	int first_sample;
	unsigned long duration;

	if(!waiting_second_buffer){
		if(bit == FIRST_BIT){
			// We have to handle the 6 samples we got. The previous word conteined an even number of 1. Then we go back to the default window size
			e_sampler_default_sampling_window();
			bit = NOT_LAST_BIT;
			for(i = 2; i < SAMPLING_WINDOW + 1; i++){ 
			  duration = received[i] * precision / 1000; // pulse duration in microseconds
			  if(duration > 1000){
			  	// noise, an interval greater than 1000 us is not correct. 
				// We try to recover by keeping this interval as first for a new message transmission
				ircomReceiveData.done = 1;
				ircomReceiveData.error = 1;
				ircomReceiveData.receiving = 0;    
				ircomReceiveData.state = 0;
				e_sampling_off();
				e_sampler_default_sampling_window();
				e_sampling_reset_with_samples(SAMPLING_WINDOW + 1 - i);	
				ircomResetReceive();

				for(int tempIndex = i; tempIndex < SAMPLING_WINDOW + 1; tempIndex++){
					sampling[tempIndex-i] = received[tempIndex];
				}
				// set fsm in idle mode (except continuous listening mode...)
				if (ircomReceiveData.continuousListening == 1){							
					ircomListen();				
				} else {
					ircomData.fsm = IRCOM_FSM_IDLE;
				}
				return;
              }

			  if(duration > 0) {
				totalDur += duration;
			  }      
			}
		} else {
			// The first sample is discarded for the first bit of a word
			for(i = 1; i < SAMPLING_WINDOW; i++){ 
				  duration = received[i] * precision / 1000; // pulse duration in microseconds
				  if(duration > 0) {
					totalDur += duration;
				  } 
				  if(duration > 1000){
				  	// noise, an interval greater than 1000 us is not correct. 
					// We try to recover by keeping this interval as first for a new message transmission
					ircomReceiveData.done = 1;
					ircomReceiveData.error = 1;
					ircomReceiveData.receiving = 0;    
					ircomReceiveData.state = 0;
					e_sampling_off();
					e_sampler_default_sampling_window();
					e_sampling_reset_with_samples(SAMPLING_WINDOW - i);	
					ircomResetReceive();

					for(int tempIndex = i; tempIndex < SAMPLING_WINDOW; tempIndex++){
							sampling[tempIndex-i] = received[tempIndex];
					}

					// set fsm in idle mode (except continuous listening mode...)
					if (ircomReceiveData.continuousListening == 1){			
						ircomListen();
					} else {
						ircomData.fsm = IRCOM_FSM_IDLE;
					}
					return;
              	  }     
			}
		}
	} else {
		synch_bit_index++;
		waiting_second_buffer = 0;
		if(synch_bit_index == 0){
			// We have received the first 2 bits of synchronization correctly, we can now receive the payload
			// ok setup everything : start receiveing word, record all
			ircomReceiveData.currentBit = 0;
			ircomReceiveData.done = 0;
			ircomReceiveData.error = 0;
			ircomReceiveData.receiving = 1;    
			ircomReceiveData.state = 1;
			return;
		}
		return;
	}

	if(totalDur <= IRCOM_HALF_BIT_TOT_DURATION_MAX && !waiting_second_buffer){
		// MARK. We have to take other 5 samples
		waiting_second_buffer = 1;
		return;		
	}else if(totalDur > IRCOM_HALF_BIT_TOT_DURATION_MAX && totalDur <= IRCOM_COMPLETE_BIT_TOT_DURATION_MAX && !waiting_second_buffer) {
		// SPACE. The 5 samples we have are enough
		waiting_second_buffer = 0;
		odd_edges = !odd_edges;
		synch_bit_index++;
	} else {
		// no significant modulation perceived ?
		e_sampling_off();
		e_sampler_default_sampling_window();
		e_sampling_reset();
		ircomResetReceive();
		if (ircomReceiveData.continuousListening == 1){
			//There has been an error in the communication. To recover from this situation we wait a random number of milliseconds before starting sampling again
			wait_fsm = RANDOM_WAIT;
			randomWaitSince = millis();
			randomInterval = random(MIN_WAIT_INTERVAL, MAX_WAIT_INTERVAL);
			ircomListen();
			
		} else {
			ircomData.fsm = IRCOM_FSM_IDLE;
		}
		return;
	}
	
	if(synch_bit_index == 0){
		// We have received the first 2 bits of synchronization correctly, we can now receive the payload
		// ok setup everything : start receiveing word, record all
		ircomReceiveData.currentBit = 0;
		ircomReceiveData.done = 0;
		ircomReceiveData.error = 0;
		ircomReceiveData.receiving = 1;    
		ircomReceiveData.state = 1;
	}
}

// FSM Receive, state 2
// receive a word
void ircomReceiveWord()
{
    int signal;
    long int value;
	int i;
	totalDur = 0;
	unsigned long duration;
	
	switch(bit){
		case NOT_LAST_BIT: {
			// We are not receiveing the LAST BIT of the word
			if(!waiting_second_buffer){
				for(i = 1; i < SAMPLING_WINDOW; i++){ 
					duration = received[i] * precision / 1000; // pulse duration in microseconds
					if(duration > 0) {
						totalDur += duration;
					} 
					if(duration > 1000){
					  	// noise, an interval greater than 1000 us is not correct. 
						// We try to recover by keeping this interval as first for a new message transmission
						ircomReceiveData.done = 1;
						ircomReceiveData.error = 1;
						ircomReceiveData.receiving = 0;    
						ircomReceiveData.state = 0;
						e_sampling_off();
						e_sampler_default_sampling_window();
						e_sampling_reset_with_samples(SAMPLING_WINDOW - i);	
						ircomResetReceive();

						for(int tempIndex = i; tempIndex < SAMPLING_WINDOW; tempIndex++){
							sampling[tempIndex-i] = received[tempIndex];
						}
						// set fsm in idle mode (except continuous listening mode...)
						if (ircomReceiveData.continuousListening == 1){		
							ircomListen();
						} else {
							ircomData.fsm = IRCOM_FSM_IDLE;
						}
						return;
          			}     
				}
			} else {
				waiting_second_buffer = 0;
				ircomReceiveData.word[ircomReceiveData.currentBit++] = IRCOM_MARK;		
				if(ircomReceiveData.currentBit == IRCOM_WORDSIZE + 1){
					//For the last bit of the word we take one sample less, since the bit might have one edge less from the others
					e_sampler_decrease_sampling_window();
					bit = LAST_BIT;
				}
				return;
			}
	
			if(totalDur <= IRCOM_HALF_BIT_TOT_DURATION_MAX && !waiting_second_buffer){
				// MARK. We have to take other 5 samples
				waiting_second_buffer = 1;
				return;	
			} else if(totalDur > IRCOM_HALF_BIT_TOT_DURATION_MAX && totalDur <= IRCOM_COMPLETE_BIT_TOT_DURATION_MAX && !waiting_second_buffer) {
				// SPACE. The 5 samples we have are enough
				waiting_second_buffer = 0;
				odd_edges = !odd_edges;
				ircomReceiveData.word[ircomReceiveData.currentBit++] = IRCOM_SPACE;
			} else {
				// no significant modulation perceived ?
				// stop receiveing, discard message
				ircomReceiveData.done = 1;
				ircomReceiveData.error = 1;
				ircomReceiveData.receiving = 0;    
				ircomReceiveData.state = 0;
				e_sampler_default_sampling_window();
				e_sampling_reset();
				ircomResetReceive();

				// set fsm in idle mode (except continuous listening mode...)
				if (ircomReceiveData.continuousListening == 1){
					//There has been an error in the communication. To recover from this situation we wait a random number of milliseconds before starting sampling again
					wait_fsm = RANDOM_WAIT;
					randomWaitSince = millis();
					randomInterval = random(MIN_WAIT_INTERVAL, MAX_WAIT_INTERVAL);
					ircomListen();
					
				} else {
					e_sampling_off();
					ircomData.fsm = IRCOM_FSM_IDLE;
				}
				return;
			}

			if(ircomReceiveData.currentBit == IRCOM_WORDSIZE + 1){
				//For the last bit of the word we take one sample less, since the bit might have one edge less than the others
				e_sampler_decrease_sampling_window();
				bit = LAST_BIT;
			}	
			break;
		}
		case LAST_BIT: {
			// LAST BIT of the word
			if(!waiting_second_buffer){
				// We have only SAMPLING_WINDOW - 1 samples and we ignore the first as usual
				for(i = 1; i < (SAMPLING_WINDOW - 1); i++){ 
					duration = received[i] * precision / 1000; // pulse duration in microseconds
					  
					if(duration > 0) {
						totalDur += duration;
					}
					if(duration > 1000){
					  	// noise, an interval greater than 2000 us is not correct. 
						// We try to recover by keeping this interval as first for a new message transmission
						ircomReceiveData.done = 1;
						ircomReceiveData.error = 1;
						ircomReceiveData.receiving = 0;    
						ircomReceiveData.state = 0;
						e_sampling_off();
						e_sampler_default_sampling_window();
						e_sampling_reset_with_samples(SAMPLING_WINDOW - 1 - i);	
						ircomResetReceive();

						for(int tempIndex = i; tempIndex < SAMPLING_WINDOW - 1; tempIndex++){
							sampling[tempIndex-i] = received[tempIndex];
						}
						// set fsm in idle mode (except continuous listening mode...)
						if (ircomReceiveData.continuousListening == 1){
							ircomListen();
						} else {
							ircomData.fsm = IRCOM_FSM_IDLE;
						}
						return;
				      }  
				 }
			} else {
				// The last bit is a 0. In both cases (odd or even number of 1) we can go back to the default window size
				e_sampler_default_sampling_window();
				waiting_second_buffer = 0;
				bit = NOT_LAST_BIT;
				ircomReceiveData.word[ircomReceiveData.currentBit++] = IRCOM_MARK;
				// end of message
				// evaluate CRC
				ircomReceiveData.error = ircomReceiveCheckCRC();
				if(ircomReceiveData.error != 0){
					e_sampling_reset();
				}

				// stop receiving
				ircomReceiveData.done = 1;
				ircomReceiveData.receiving = 0;    
				ircomReceiveData.state = 0;
				synch_bit_index = -2;
				odd_edges = 0;
	
				// record in the msg queue
				value = ircomBin2Int(ircomReceiveData.word);
				ircomPushMessage(value, 
						 ircomReceiveData.error);


				// set fsm in idle mode (except continuous listening mode...)
				if (ircomReceiveData.continuousListening == 1){
					e_sampling_off();
					if(ircomReceiveData.error != 0){
						//There has been an error in the communication. To recover from this situation we wait a random number of milliseconds before starting sampling again
						wait_fsm = RANDOM_WAIT;
						e_sampler_default_sampling_window();
						e_sampling_reset();
						ircomResetReceive();
						randomWaitSince = millis();
						randomInterval = random(MIN_WAIT_INTERVAL, MAX_WAIT_INTERVAL);
					}					
					ircomListen();
					
				} else {
					e_sampling_off();
					if(ircomReceiveData.error != 0){
						e_sampler_default_sampling_window();
						e_sampling_reset();
						ircomResetReceive();
					}
					ircomData.fsm = IRCOM_FSM_IDLE;
				}

				return;	
			}
			
			if(totalDur <= IRCOM_HALF_LAST_BIT_TOT_DURATION_MAX && !waiting_second_buffer){
				// MARK. We have to take other 5 samples
				if(!odd_edges){
					//If the last bit is a 0 and the number of 1 in the word is even, we have to take other 6 samples to complete the bit
					e_sampler_increase_sampling_window();
				} else {
					//If the last bit is a 0 and the number of 1 in the word is odd, we have to take other 5 samples to complete the bit
					e_sampler_default_sampling_window();
				}
				waiting_second_buffer = 1;
				return;	
			} else if(totalDur > IRCOM_HALF_LAST_BIT_TOT_DURATION_MAX && totalDur <= IRCOM_COMPLETE_LAST_BIT_TOT_DURATION_MAX && !waiting_second_buffer) {
				// SPACE. The 5 samples we have are enough
				waiting_second_buffer = 0;
				odd_edges = !odd_edges;
				if(!odd_edges){
					//The last bit is a 1 and the number of 1 in the word is even with this last one, we will have to take 6 samples in the first bit of the next word (and skip the first 2)
					e_sampler_increase_sampling_window();
					// The state in which we handle the 6 samples of the first bit of the next word
					bit = FIRST_BIT;
				} else {
					//The last bit is a 1 and the number of 1 in the word is odd with this last one, the 4 samples we took are enough
					e_sampler_default_sampling_window();
					bit = NOT_LAST_BIT;
				}
				
				ircomReceiveData.word[ircomReceiveData.currentBit++] = IRCOM_SPACE;
				// evaluate CRC
				ircomReceiveData.error = ircomReceiveCheckCRC();
				
				
				// stop receiving
				ircomReceiveData.done = 1;
				ircomReceiveData.receiving = 0;    
				ircomReceiveData.state = 0;
				synch_bit_index = -2;
				odd_edges = 0;
		
				// record in the msg queue
				value = ircomBin2Int(ircomReceiveData.word);
				ircomPushMessage(value, 
						 ircomReceiveData.error);

				// set fsm in idle mode (except continuous listening mode...)
				if (ircomReceiveData.continuousListening == 1){
					e_sampling_off();
					if(ircomReceiveData.error != 0){
						wait_fsm = RANDOM_WAIT;
						e_sampler_default_sampling_window();
						e_sampling_reset();
						ircomResetReceive();
						randomWaitSince = millis();
						randomInterval = random(MIN_WAIT_INTERVAL, MAX_WAIT_INTERVAL);
					}					
					ircomListen();
					
				} else {
					e_sampling_off();
					if(ircomReceiveData.error != 0){
						e_sampler_default_sampling_window();
						e_sampling_reset();
						ircomResetReceive();
					}
					ircomData.fsm = IRCOM_FSM_IDLE;
				}

				return;	

			} else {
				// no significant modulation perceived ?
				// stop receiveing, discard message
				ircomReceiveData.done = 1;
				ircomReceiveData.error = 1;
				ircomReceiveData.receiving = 0;    
				ircomReceiveData.state = 0;
				
				e_sampler_default_sampling_window();
				e_sampling_reset();
				ircomResetReceive();

				// set fsm in idle mode (except continuous listening mode...)
				if (ircomReceiveData.continuousListening == 1){
					//There has been an error in the communication. To recover from this situation we wait a random number of milliseconds before starting sampling again
					wait_fsm = RANDOM_WAIT;
					randomWaitSince = millis();
					randomInterval = random(MIN_WAIT_INTERVAL, MAX_WAIT_INTERVAL);
					ircomListen();
				} else {
					e_sampling_off();
					ircomData.fsm = IRCOM_FSM_IDLE;
				}
				return;
			}			
			break;
		}
	}
}

int ircomReceiveCheckCRC()
{
    // compute checksum
    int i;
    int bitSum = 0;
    int crc;
    
    for(i = 0; i < IRCOM_WORDSIZE; i++)
    {
	if (ircomReceiveData.word[i] == IRCOM_SPACE)
	    bitSum++;
    }
    crc = ircomReceiveData.word[i] * 2 + ircomReceiveData.word[i + 1];

    // sum and get only the last 2 bits
    return ((crc + bitSum) & 0x003);
}

// **************************** //

/**
 * Set up the different ADC register to process the AD conversion
 * by scanning the used AD channels. Each value of the channels will
 * be stored in a different AD buffer register and an inturrupt will
 * occure at the end of the scan.
 *
 * @param  void
 * @return void
 */

void e_init_sampling(void)
{
	array_filled = 0;
	e_last_ir_scan_id = 0;
	sampling_window = SAMPLING_WINDOW;
    // continuous sampling
    sampling = buffer1;
    received = buffer2;

    TCCR1A = 0 ;                    // Normal counting mode
    TCCR1B = prescaleBits ;         // set prescale bits
    TCCR1B |= _BV(ICES1);           // enable input capture for rising edge

    bitSet(TIMSK1,ICIE1);           // enable input capture interrupt for timer 1
	e_sampler_default_sampling_window();
	randomSeed(millis());  			// change the starting sequence of random numbers.
}



void e_sampling_on(void)
{
    sampling_enable = 1;
}

/*inline*/ void e_sampling_reset(void)
{
    
	e_last_ir_scan_id = 0;      // reset array index
    array_filled = 0;	        // reset array filled flag
	TCCR1B |= _BV(ICES1);		// enable input capture for rising edge
}

/*inline*/ void e_sampling_reset_with_samples(int new_sample_index)
{
    
	e_last_ir_scan_id = new_sample_index;      // reset array index to 1 keeping the first long sample
    array_filled = 0;	        // reset array filled flag
}

void e_sampling_off(void)
{
    sampling_enable = 0;
}

void e_ircom_interrupt()
{
		sampling[e_last_ir_scan_id] = temp;              // save the input capture value
        e_last_ir_scan_id++;

		if((sampling_window == SAMPLING_WINDOW && e_last_ir_scan_id >= SAMPLING_WINDOW) || (sampling_window == SAMPLING_WINDOW - 1 && e_last_ir_scan_id >= (SAMPLING_WINDOW - 1)) || (sampling_window == SAMPLING_WINDOW + 1 && e_last_ir_scan_id >= SAMPLING_WINDOW + 1)){
	
			e_last_ir_scan_id = 0;
			array_filled = 1;
			
			// swap buffers
			unsigned int* tmp = received;
			received = sampling;
			sampling = tmp;
			
			// Sampling window is full. Bit recognition
			ircomReceiveMain();
		}
}

/* ICR interrupt vector */
ISR(TIMER1_CAPT_vect)
{
  TCNT1 = 0;  // reset the counter
  temp = ICR1;  // Save the value of duration
  TCCR1B ^= _BV(ICES1);      // toggle bit to trigger on the other edge
  nowReceive = millis();
  //If we are in the random wait period
  if(wait_fsm == RANDOM_WAIT && (randomWaitSince + randomInterval) < nowReceive){
	//The random wait is over and we can now get back to sampling
	wait_fsm = RUNNING;
  }
  if(sampling_enable && wait_fsm == RUNNING)
  {
	//We are not in the random wait and the sampling is enabled
	e_ircom_interrupt();
  }
  
}

void e_sampler_decrease_sampling_window (){
	sampling_window = SAMPLING_WINDOW - 1;
}

void e_sampler_increase_sampling_window (){
	sampling_window = SAMPLING_WINDOW + 1;
}

void e_sampler_default_sampling_window (){
	sampling_window = SAMPLING_WINDOW;
}

/*inline*/ char e_sampler_is_array_filled(void)
{
    char result = array_filled;
    array_filled = 0;
    return result;
}

// IRCOM_RECEIVE_C
#endif

