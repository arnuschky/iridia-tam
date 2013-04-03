
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


#ifndef IRCOM_TOOLS_C
#define IRCOM_TOOLS_C

#include <stdio.h>
#include "e_ad_conv.h"

#include "ircom.h"
#include "ircomTools.h"

void ircomInt2Bin (long int n, ircomWord w)
{
    int i;

    for (i = IRCOM_WORDSIZE - 1; i >= 0 ; i--)
    {
		w[i] = n % 2;
		n = n >> 1;
    } 
}

long int ircomBin2Int(volatile ircomWord w)
{
    long int n = 0;
    
    int i; 
    for (i = 0; i < IRCOM_WORDSIZE; i++)
    {
		n = n << 1;
		n += w[i];
    }

    return n;
}

// sampling time = 81.3 usec
long int ircomGetTime( void )
{
    return ircomData.time;
}

void ircomResetTime ( void ) 
{
    ircomData.time = 0;
}


// IRCOM_TOOLS_C
#endif
