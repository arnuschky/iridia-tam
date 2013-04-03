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

#ifndef _AD_CONV
#define _AD_CONV


// sampling frequency for the microphones
//#define MIC_SAMP_FREQ 16384.0	// WARNING: must be the highest one. This is the base to calculate ad_cycle
								//16384.0 is max could also use 12288
									

// sampling frequency for the accelerometres and proximetres
//#define ACC_PROX_SAMP_FREQ 256.0	// WARNING: should be a fraction of MIC_SAMP_FREQ
									//			to ensure a good timing precision
// lenght of the IR pulse in seconds
#define PULSE_LENGHT 0.0003		

//Calculation of the periodes in ad_cycle
//ad_cycle = 1/MIC_SAMP_FREQ
//#define ACC_PROX_PERIOD (int)(MIC_SAMP_FREQ/ACC_PROX_SAMP_FREQ)	// 60 acc and prox periode in [ad_cycle]	
//#define PULSE_PERIOD (int)(PULSE_LENGHT*MIC_SAMP_FREQ)			//  pulse length in [ad_cycle]			

//#define MICRO_ONLY 1
//#define ALL_ADC 0

/***********************************************************************
 * -------------------- Functions from ad_conv.c -----------------------
 **********************************************************************/
void e_init_ad_scan(void);
void e_ad_scan_on(void);
/*inline*/ void e_ad_scan_reset(void);
/*inline*/ void e_ad_skip_samples (int samples_count);
void e_ad_scan_off(void);
void e_ad_ircom_interrupt();
/*inline*/ char e_ad_is_array_filled(void);


#define SAMPLING_WINDOW 24
#define NB_IR_SENSORS 1

extern int* ad_sampling;
extern int* ad_received;
extern volatile unsigned int e_last_ir_scan_id;

extern volatile int ad_disable_ircom;

#endif /*_AD_CONV*/

/* End of File : ad_conv.h */
