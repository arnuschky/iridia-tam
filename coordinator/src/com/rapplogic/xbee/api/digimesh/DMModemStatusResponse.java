/**
 * Copyright (c) 2008 Andrew Rapp. All rights reserved.
 *  
 * This file is part of XBee-API.
 *  
 * XBee-API is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * XBee-API is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *  
 * You should have received a copy of the GNU General Public License
 * along with XBee-API.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.rapplogic.xbee.api.digimesh;

import com.rapplogic.xbee.api.NoRequestResponse;
import com.rapplogic.xbee.api.XBeeResponse;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;


/**
 * XBee Series 1 with Digimesh
 * RF module status messages are sent from the module in response to specific conditions.
 * <p/>
 * API ID: 0x8a
 * <p/>
 * @author Arne Brutschy
 *
 */
public class DMModemStatusResponse extends XBeeResponse implements NoRequestResponse {

	public enum Status {
		HARDWARE_RESET (0x00),
		WATCHDOG_TIMER_RESET (0x01),
		NETWORK_WOKE_UP (0x0b),
		NETWORK_WENT_TO_SLEEP (0x0c);

		private static final Map<Integer,Status> lookup = new HashMap<Integer,Status>();

		static {
			for(Status s : EnumSet.allOf(Status.class)) {
				lookup.put(s.getValue(), s);
			}
		}

		public static Status get(int value) {
			return lookup.get(value);
		}

	    private final int value;

	    Status(int value) {
	        this.value = value;
	    }

		public int getValue() {
			return value;
		}
	}

	private Status status;

	public DMModemStatusResponse() {

	}
	
	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public String toString() {
		return super.toString() + ",status=" + this.status;
	}
}