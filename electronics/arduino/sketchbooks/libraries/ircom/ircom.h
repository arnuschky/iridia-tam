
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

#ifndef IRCOM_H
#define IRCOM_H

//#include "signal_sampler.h"
//#include "math.h"


// constants that define behaviour of ircom module
#define IRCOM_WORDSIZE 8 // bits

#define IRCOM_HALF_BIT_TOT_DURATION_MAX 1500 //microseconds
#define IRCOM_HALF_LAST_BIT_TOT_DURATION_MAX 1100 //microseconds

#define IRCOM_COMPLETE_BIT_TOT_DURATION_MAX 2500 //microseconds
#define IRCOM_COMPLETE_LAST_BIT_TOT_DURATION_MAX 2000 //microseconds


#define IRCOM_SAMPLING_WINDOW 5

#define IRCOM_MARK 0
#define IRCOM_SPACE 1
#define IRCOM_NB_IR_SENSORS 1


typedef char ircomWord[IRCOM_WORDSIZE + 4]; 

// message queue
typedef struct
{
    long int value;
    int error; // -1 = inexistent msg, 0 = all is ok, 1 = error in transmission
} IrcomMessage;


// structure that keeps all relevant information to run ircom module
// mainly which FSM is running
#define IRCOM_FSM_IDLE 0
#define IRCOM_FSM_RECEIVE 1
#define IRCOM_FSM_SEND 2
#define IRCOM_MESSAGES_MAX 20
typedef struct
{        
    int paused;
    int fsm;

    IrcomMessage messages[IRCOM_MESSAGES_MAX];
    int messagesBegin;
    int messagesEnd;
    int messagesCount;

    long int time;
} Ircom;

// structure that keeps track of all information related to receiveing
typedef struct
{
    // public :
    int receiving;
    int done;
    int error;
    ircomWord word;


    // private :
    int state;
    int currentBit;


    int continuousListening;
} IrcomReceive;

// structure that keeps track of all information related to writing
#define IRCOM_SEND_OMNI 0
#define IRCOM_SEND_DIRECTIONAL 1
typedef struct
{
    // public : 
    int done;
    ircomWord word;
    int type; // omni or directionnal        
    int sensor;

    // private : 
    int pulseState;
    int switchCounter;
    int switchCountMax;
    int signal;
    int currentBit;

    int markSwitchCount;
    int spaceSwitchCount;
    double markDuration;
    double spaceDuration;

    double durations[IRCOM_WORDSIZE+4];
    int switchCounts[IRCOM_WORDSIZE+4];
    int interruptedListening;
} IrcomSend;

extern volatile Ircom ircomData;
extern volatile IrcomReceive ircomReceiveData;
extern volatile IrcomSend ircomSendData;

void test();
/*inline*/ void ircomStart ();
/*inline*/ void ircomStop (void);
/*inline*/ void ircomRestart (void);
/*inline*/ void ircomPause (int status);
void ircomEnableContinuousListening ();
void ircomDisableContinuousListening ();
void ircomSetOmnidirectional ();
void ircomSetDirectional (int sensor);

// make available to others read and write public functions
#include "ircomReceive.h"
#include "ircomSend.h"
#include "ircomTools.h"
#include "ircomMessages.h"


#endif
