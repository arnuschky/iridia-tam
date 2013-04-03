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

import com.rapplogic.xbee.api.XBeeAddress16;
import com.rapplogic.xbee.api.XBeeAddress64;
import com.rapplogic.xbee.api.XBeeResponse;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;


/**
 * Xbee Series 1 Digimesh identification frame.
 * @author Arne Brutschy
 */
public class DMNodeIdentificationResponse extends XBeeResponse {

	public enum Option {
		PACKET_ACKNOWLEDGED (0x01),
		BROADCAST_PACKET (0x02);
		
		private static final Map<Integer,Option> lookup = new HashMap<Integer,Option>();
		
		static {
			for(Option s : EnumSet.allOf(Option.class)) {
				lookup.put(s.getValue(), s);
			}
		}
		
		public static Option get(int value) { 
			return lookup.get(value); 
		}
		
	    private final int value;
	    
	    Option(int value) {
	        this.value = value;
	    }

		public int getValue() {
			return value;
		}
	}
	
	private XBeeAddress64 senderAddress64;
	private XBeeAddress16 senderAddress16;
	private Option option;
	private XBeeAddress64 remoteAddress64;
	private XBeeAddress16 remoteAddress16;
	
	private String nodeIdentifier;
	private XBeeAddress16 parentAddress;
	
	public DMNodeIdentificationResponse() {

	}

	public XBeeAddress64 getSenderAddress64() {
		return senderAddress64;
	}

	public void setSenderAddress64(XBeeAddress64 senderAddress64) {
		this.senderAddress64 = senderAddress64;
	}

	public XBeeAddress16 getSenderAddress16() {
		return senderAddress16;
	}

	public void setSenderAddress16(XBeeAddress16 senderAddress16) {
		this.senderAddress16 = senderAddress16;
	}

	public Option getOption() {
		return option;
	}

	public void setOption(Option option) {
		this.option = option;
	}

	public XBeeAddress64 getRemoteAddress64() {
		return remoteAddress64;
	}

	public void setRemoteAddress64(XBeeAddress64 remoteAddress64) {
		this.remoteAddress64 = remoteAddress64;
	}

	public XBeeAddress16 getRemoteAddress16() {
		return remoteAddress16;
	}

	public void setRemoteAddress16(XBeeAddress16 remoteAddress16) {
		this.remoteAddress16 = remoteAddress16;
	}

	public String getNodeIdentifier() {
		return nodeIdentifier;
	}

	public void setNodeIdentifier(String nodeIdentifier) {
		this.nodeIdentifier = nodeIdentifier;
	}

	public XBeeAddress16 getParentAddress() {
		return parentAddress;
	}

	public void setParentAddress(XBeeAddress16 parentAddress) {
		this.parentAddress = parentAddress;
	}

	@Override
	public String toString() {
		return "DMNodeIdentificationResponse [nodeIdentifier=" + nodeIdentifier
				+ ", option=" + option
                + ", parentAddress=" + parentAddress
				+ ", senderAddress16=" + senderAddress16
                + ", remoteAddress16=" + remoteAddress16
				+ ", senderAddress64=" + senderAddress64
				+ ", remoteAddress64=" + remoteAddress64 + "]" +
				super.toString();
	}
}