
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

#ifndef IRCOM_RECEIVE_H
#define IRCOM_RECEIVE_H

// public
void ircomListen();
int ircomReceiveDone();
inline int ircomIsReceiving ();
inline int ircomIsListening ();
void ircomStopListening ();

// private
void ircomReceiveMain();
void ircomReceiveListen();
void ircomReceiveWord();
int ircomReceiveCheckCRC();
void ircomResetReceive();

void e_init_sampling(void);
void e_sampling_on(void);
/*inline*/ void e_sampling_reset(void);
/*inline*/ void e_sampling_reset_with_samples(int new_sample_index);
/*inline*/ void e_sampler_decrease_sampling_window (void);
/*inline*/ void e_sampler_increase_sampling_window (void);
/*inline*/ void e_sampler_default_sampling_window (void);
void e_ircom_interrupt(void);
void e_sampling_off(void);
/*inline*/ char e_sampler_is_array_filled(void);

extern unsigned int* sampling;
extern unsigned int* received;

#define SAMPLING_WINDOW 5
#define NB_IR_SENSORS 1


// IRCOM_RECEIVE_H
#endif
