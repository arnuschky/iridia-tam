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

import com.rapplogic.xbee.api.*;


/**
 * Supported by series 1 with Digimesh.
 * Allows AT commands to be sent to a remote radio.
 * <p/>
 * Warning: this command may not return a response if the remote radio is unreachable.
 * You will need to set your own timeout when waiting for a response from this command,
 * or you may wait forever.
 * <p/>
 * API ID: 0x17
 * <p/>
 * @author Arne Brutschy
 */
public class DMRemoteAtRequest extends RemoteAtRequest {

	/**
	 * Creates a Remote AT request for setting an AT command on a remote XBee
	 * <p/>
	 * Note: When setting a value, you must set applyChanges for the setting to
	 * take effect.  When sending several requests, you can wait until the last
	 * request before setting applyChanges=true.
	 * <p/>
	 * @param frameId
	 * @param remoteAddress64
	 * @param remoteAddress16
	 * @param applyChanges set to true if setting a value or issuing a command that changes the state of the radio (e.g. FR); not applicable to query requests
	 * @param command two character AT command to set or query
	 * @param value if null then the current setting will be queried
	 */
	public DMRemoteAtRequest(int frameId, XBeeAddress64 remoteAddress64, XBeeAddress16 remoteAddress16, boolean applyChanges, String command, int[] value) {
		super(frameId, remoteAddress64, remoteAddress16, applyChanges, command, value);
	}

	/**
	 * Creates a Remote AT request for querying the current value of an AT command on a remote XBee
	 *
	 * @param frameId
	 * @param remoteAddress64
	 * @param remoteAddress16
	 * @param applyChanges
	 * @param command
	 */
	public DMRemoteAtRequest(int frameId, XBeeAddress64 remoteAddress64, XBeeAddress16 remoteAddress16, boolean applyChanges, String command) {
		this(frameId, remoteAddress64, remoteAddress16, applyChanges, command, null);
	}

	/**
	 * Abbreviated Constructor for setting an AT command on a remote XBee.
	 * This defaults to the DEFAULT_FRAME_ID, and true for apply changes
	 *
	 * @param dest64
	 * @param command
	 * @param value
	 */
	public DMRemoteAtRequest(XBeeAddress64 dest64, String command, int[] value) {
		// Note: the ZNET broadcast also works for series 1.  We could also use ffff but then that wouldn't work for series 2
		this(XBeeRequest.DEFAULT_FRAME_ID, dest64, XBeeAddress16.ZNET_BROADCAST, true, command, value);
	}

	/**
	 * Abbreviated Constructor for querying the value of an AT command on a remote XBee.
	 * This defaults to the DEFAULT_FRAME_ID, and true for apply changes
	 *
	 * @param dest64
	 * @param command
	 */
	public DMRemoteAtRequest(XBeeAddress64 dest64, String command) {
		this(dest64, command, null);
		// apply changes doesn't make sense for a query
		this.setApplyChanges(false);
	}

	/**
	 * Creates a Remote AT instance for querying the value of an AT command on a remote XBee,
	 * by specifying the 16-bit address.  Uses the broadcast address for 64-bit address (00 00 00 00 00 00 ff ff)
	 * <p/>
	 * Defaults are: frame id: 1, applyChanges: true
	 *
	 * @param dest16
	 * @param command
	 */
	public DMRemoteAtRequest(XBeeAddress16 dest16, String command) {
		this(dest16, command, null);
		// apply changes doesn't make sense for a query
		this.setApplyChanges(false);
	}

	/**
	 * Creates a Remote AT instance for setting the value of an AT command on a remote XBee,
	 * by specifying the 16-bit address and value.  Uses the broadcast address for 64-bit address (00 00 00 00 00 00 ff ff)
	 * <p/>
	 * Defaults are: frame id: 1, applyChanges: true
	 *
	 * @param remoteAddress16
	 * @param command
	 */
	public DMRemoteAtRequest(XBeeAddress16 remoteAddress16, String command, int[] value) {
		this(XBeeRequest.DEFAULT_FRAME_ID, XBeeAddress64.BROADCAST, remoteAddress16, true, command, value);
	}

    @Override
	public ApiId getApiId() {
		return ApiId.DM_REMOTE_AT_REQUEST;
	}
}
