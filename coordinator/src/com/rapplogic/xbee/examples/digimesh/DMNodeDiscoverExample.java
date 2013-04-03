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

package com.rapplogic.xbee.examples.digimesh;

import com.rapplogic.xbee.api.*;
import com.rapplogic.xbee.api.digimesh.DMNodeDiscover;
import com.rapplogic.xbee.util.ByteUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;


/** 
 * Example of performing a node discover for Series 2 XBees.
 * You must connect to the coordinator to run this example and
 * have one or more end device/routers that are associated.
 * 
 * @author Arne Brutschy
 */
public class DMNodeDiscoverExample
{

	private final static Logger log = Logger.getLogger(DMNodeDiscoverExample.class);
	
	private XBee xbee = new XBee();
	
	public DMNodeDiscoverExample() throws XBeeException, InterruptedException {
		
		try {
			// replace with your serial port
			xbee.open("/dev/ttyUSB1", 9600);
			
			// get the Node discovery timeout
			xbee.sendAsynchronous(new AtCommand("NT"));
			AtCommandResponse nodeTimeout = (AtCommandResponse) xbee.getResponse();
			
			// default is 6 seconds
			long nodeDiscoveryTimeout = ByteUtils.convertMultiByteToInt(nodeTimeout.getValue()) * 100;
			
			log.debug("Node discovery timeout is " + nodeDiscoveryTimeout + " milliseconds");
						
			xbee.addPacketListener(new PacketListener() {
				
				public void processResponse(XBeeResponse response) {
					if (response.getApiId() == ApiId.AT_RESPONSE) {
						DMNodeDiscover nd = DMNodeDiscover.parse((AtCommandResponse) response);
						System.out.println("Found node: " + nd);
					} else {
						log.debug("Ignoring unexpected response: " + response);	
					}					
				}
				
			});
						
			log.debug("Sending node discover command");
			xbee.sendAsynchronous(new AtCommand("ND"));
			
			// wait for nodeDiscoveryTimeout milliseconds
			Thread.sleep(nodeDiscoveryTimeout);
			
			log.debug("Time is up!  You should have heard back from all nodes by now.  If not make sure all nodes are associated and/or try increasing the node timeout (NT)");
		} finally {
			xbee.close();
		}
	}
	
	public static void main(String[] args) throws XBeeException, InterruptedException {
		PropertyConfigurator.configure("log4j.properties");
		new DMNodeDiscoverExample();
	}
}
