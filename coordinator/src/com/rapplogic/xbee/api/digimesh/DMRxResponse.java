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
import com.rapplogic.xbee.util.ByteUtils;


/**
 * Series 1 XBee with Digimesh. This packet is received when
 * a remote XBee sends a DMTxRequest
 * <p/>
 * API ID: 0x90 (Digimesh uses same as Znet, const DM_RX_RESPONSE)
 * <p/>
 * @author Arne Brutschy
 */
public class DMRxResponse extends DMRxBaseResponse implements NoRequestResponse {

	private int[] data;
	
	public DMRxResponse() {
		super();
	}

	public int[] getData() {
		return data;
	}

	public void setData(int[] data) {
		this.data = data;
	}
	
	public String toString() {
		return super.toString() + 
			",data=" + ByteUtils.toBase16(this.data);
	}
}