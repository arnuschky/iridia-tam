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

package com.rapplogic.xbee.api;

import java.io.IOException;
import java.io.InputStream;

import org.apache.log4j.Logger;

import com.rapplogic.xbee.api.AtCommandResponse.Status;
import com.rapplogic.xbee.api.wpan.RxBaseResponse;
import com.rapplogic.xbee.api.wpan.RxResponse;
import com.rapplogic.xbee.api.wpan.RxResponse16;
import com.rapplogic.xbee.api.wpan.RxResponse64;
import com.rapplogic.xbee.api.wpan.RxResponseIoSample;
import com.rapplogic.xbee.api.wpan.TxStatusResponse;
import com.rapplogic.xbee.api.digimesh.DMExplicitRxResponse;
import com.rapplogic.xbee.api.digimesh.DMNodeIdentificationResponse;
import com.rapplogic.xbee.api.digimesh.DMRxBaseResponse;
import com.rapplogic.xbee.api.digimesh.DMRxIoSampleResponse;
import com.rapplogic.xbee.api.digimesh.DMRxResponse;
import com.rapplogic.xbee.api.digimesh.DMTxStatusResponse;
import com.rapplogic.xbee.util.ByteUtils;
import com.rapplogic.xbee.util.DoubleByte;
import com.rapplogic.xbee.util.IIntArrayInputStream;
import com.rapplogic.xbee.util.InputStreamWrapper;
import com.rapplogic.xbee.util.IntArrayOutputStream;

/**
 * Reads a packet from the input stream, verifies checksum and creates an XBeeResponse object
 * <p/>
 * Notes:
 * <p/>
 * Escaped bytes increase packet length but packet stated length only indicates un-escaped bytes.
 * Stated length includes all bytes after Length bytes, not including the checksum
 * <p/>
 * @author Andrew Rapp
 *
 */
public class PacketParser implements IIntArrayInputStream {

	private final static Logger log = Logger.getLogger(PacketParser.class);

	private IIntArrayInputStream in;
	
	// size of packet after special bytes have been escaped
	private XBeePacketLength length;
	private Checksum checksum = new Checksum();
	
	private boolean done = false;
	
	private int bytesRead;
	private int escapeBytes;

	private XBeeResponse response;
	private ApiId apiId;
	
	// experiment to preserve original byte array for transfer over network (Starts with length)
	private IntArrayOutputStream rawBytes = new IntArrayOutputStream();
	
	public PacketParser(InputStream in) {
		this.in = new InputStreamWrapper(in);
	}
	
	// for parsing a packet from a byte array
	public PacketParser(IIntArrayInputStream in) {
		this.in = in;
	}
	
	/**
	 * This method is guaranteed (unless I screwed up) to return an instance of XBeeResponse and should never throw an exception
	 * If an exception occurs, it will be packaged and returned as an ErrorResponse. 
	 * 
	 * @return
	 */
	public XBeeResponse parsePacket() {
		
		Exception exception = null;
		
		try {
			// BTW, length doesn't account for escaped bytes
			int msbLength = this.read("Length MSB");
			int lsbLength = this.read("Length LSB");
			
			// length of api structure, starting here (not including start byte or length bytes, or checksum)
			this.length = new XBeePacketLength(msbLength, lsbLength);

			log.debug("packet length is " + ByteUtils.formatByte(length.getLength()));
			
			// total packet length = stated length + 1 start byte + 1 checksum byte + 2 length bytes
			
			int intApiId = this.read("API ID");
			
			this.apiId = ApiId.get(intApiId);
			
			if (apiId == null) {
				this.apiId = ApiId.UNKNOWN;	
			}
			
			log.info("Handling ApiId: " + apiId);
			
			// TODO parse I/O data page 12. 82 API Identifier Byte for 64 bit address A/D data (83 is for 16bit A/D data)
			// TODO XBeeResponse should implement an abstract parse method
			
			if (apiId == ApiId.MODEM_STATUS_RESPONSE) {
				parseModemStatusResponse();
			} else if (apiId == ApiId.RX_16_RESPONSE) {
				parseRxResponse();
			} else if (apiId == ApiId.RX_16_IO_RESPONSE) {
				parseRxResponse();
			} else if (apiId == ApiId.RX_64_RESPONSE) {
				parseRxResponse();
			} else if (apiId == ApiId.RX_64_IO_RESPONSE) {
				parseRxResponse();
			} else if (apiId == ApiId.AT_RESPONSE) {
				parseAtResponse();
			} else if (apiId == ApiId.TX_STATUS_RESPONSE) {
				parseTxStatusResponse();
			} else if (apiId == ApiId.REMOTE_AT_RESPONSE) {
				parseRemoteAtResponse();
			} else if (apiId == ApiId.DM_TX_STATUS_RESPONSE) { 
				parseDMTxStatusResponse();
			} else if (apiId == ApiId.DM_RX_RESPONSE) {
				parseDMRxResponse();
			} else if (apiId == ApiId.DM_EXPLICIT_RX_RESPONSE) {
				parseDMRxResponse();
			} else if (apiId == ApiId.DM_IO_SAMPLE_RESPONSE) {
				parseDMRxResponse();
			} else if (apiId == ApiId.DM_IO_NODE_IDENTIFIER_RESPONSE) {
				parseDMNodeIdentifierResponse();		
			} else {
				// a new or unsupported api id
				log.info("Encountered unknown API type: " + ByteUtils.toBase16(intApiId) + ".  returning GenericResponse");
				parseGeneric(intApiId);
			}
			
			response.setChecksum(this.read("Checksum"));
			
			if (!this.isDone()) {
				throw new XBeeParseException("There are remaining bytes according to stated packet length but we have read all the bytes we thought were required for this packet (if that makes sense)");
			}
			
			response.finish();
		} catch (Exception e) {
			// added bytes read for troubleshooting
			log.error("Failed due to exception.  Returning ErrorResponse.  bytes read: " + ByteUtils.toBase16(rawBytes.getIntArray()), e);
			exception = e;
			
			response = new ErrorResponse();
			
			((ErrorResponse)response).setErrorMsg(exception.getMessage());	
			// but this isn't
			((ErrorResponse)response).setException(e);
		} finally {
			response.setLength(length);
			response.setApiId(apiId);			
			// preserve original byte array for transfer over networks
			response.setRawPacketBytes(rawBytes.getIntArray());
		}
		
		return response;
	}
	
	/**
	 * Same as read() but logs the context of the byte being read.  useful for debugging
	 */
	public int read(String context) throws IOException {
		int b = this.read();
		log.debug("Read " + context + " byte, val is " + ByteUtils.formatByte(b));
		return b;
	}
	
	/**
	 * This method should only be called by read()
	 * 
	 * @throws IOException
	 */
	private int readFromStream() throws IOException {
		int b = in.read();
		// save raw bytes to transfer via network
		rawBytes.write(b);		
		
		return b;
	}
	
	/**
	 * This method reads bytes from the underlying input stream and performs the following tasks:
	 * 1. Keeps track of how many bytes we've read
	 * 2. Un-escapes bytes if necessary and verifies the checksum.
	 */
	public int read() throws IOException {

		if (done) {
			throw new XBeeParseException("Packet has read all of its bytes");
		}
		
		int b = this.readFromStream();

		
		if (b == -1) {
			throw new XBeeParseException("Read -1 from input stream while reading packet!");
		}
		
		if (XBeePacket.isSpecialByte(b)) {
			log.debug("Read special byte that needs to be unescaped"); 
			
			if (b == XBeePacket.SpecialByte.ESCAPE.getValue()) {
				log.debug("found escape byte");
				// read next byte
				b = this.readFromStream();
				
				log.debug("next byte is " + ByteUtils.formatByte(b));
				b = 0x20 ^ b;
				log.debug("unescaped (xor) byte is " + ByteUtils.formatByte(b));
					
				escapeBytes++;
			} else {
				// TODO some responses such as AT Response for node discover do not escape the bytes?? shouldn't occur if AP mode is 2?
				// while reading remote at response Found unescaped special byte base10=19,base16=0x13,base2=00010011 at position 5 
				log.warn("Found unescaped special byte " + ByteUtils.formatByte(b) + " at position " + bytesRead);
			}
		}
	
		bytesRead++;

		// do this only after reading length bytes
		if (bytesRead > 2) {

			// when verifying checksum you must add the checksum that we are verifying
			// checksum should only include unescaped bytes!!!!
			// when computing checksum, do not include start byte, length, or checksum; when verifying, include checksum
			checksum.addByte(b);
			
			log.debug("Read byte " + ByteUtils.formatByte(b) + " at position " + bytesRead + ", packet length is " + this.length.get16BitValue() + ", #escapeBytes is " + escapeBytes + ", remaining bytes is " + this.getRemainingBytes());
			
			// escape bytes are not included in the stated packet length
			if (this.getFrameDataBytesRead() >= (length.get16BitValue() + 1)) {
				// this is checksum and final byte of packet
				done = true;
				
				log.debug("Checksum byte is " + b);
				
				if (!checksum.verify()) {
					throw new XBeeParseException("Checksum is incorrect.  Expected 0xff, but got " + checksum.getChecksum());
				}
			}
		}

		return b;
	}

	private void parseRemoteAtResponse() throws IOException {
		
		response = new RemoteAtResponse();
		
		((RemoteAtResponse)response).setFrameId(this.read("Remote AT Response Frame Id"));

		((RemoteAtResponse)response).setRemoteAddress64(this.parseAddress64());
		((RemoteAtResponse)response).setRemoteAddress16(this.parseAddress16());
		
		char cmd1 = (char)this.read("Command char 1");
		char cmd2 = (char)this.read("Command char 2");
		//((RemoteAtResponse)response).setCommand(new String(new char[] {cmd1, cmd2}));
		((RemoteAtResponse)response).setChar1(cmd1);
		((RemoteAtResponse)response).setChar2(cmd2);
		
		int status = this.read("AT Response Status");
		((RemoteAtResponse)response).setStatus(RemoteAtResponse.Status.get(status));
		
		((RemoteAtResponse)response).setValue(this.readRemainingBytes());
	}
	
	private void parseAtResponse() throws IOException {
		//log.debug("AT Response");
		
		response = new AtCommandResponse();
		
		((AtCommandResponse)response).setFrameId(this.read("AT Response Frame Id"));
		((AtCommandResponse)response).setChar1(this.read("AT Response Char 1"));
		((AtCommandResponse)response).setChar2(this.read("AT Response Char 2"));
		((AtCommandResponse)response).setStatus(Status.get(this.read("AT Response Status")));
							
		((AtCommandResponse)response).setValue(this.readRemainingBytes());
	}

	private void parseDMTxStatusResponse() throws IOException {
		
		response = new DMTxStatusResponse();
		
		((DMTxStatusResponse)response).setFrameId(this.read("DM Tx Status Frame Id"));

		((DMTxStatusResponse)response).setRemoteAddress16(this.parseAddress16());
		((DMTxStatusResponse)response).setRetryCount(this.read("DM Tx Status Tx Count"));
		
		int deliveryStatus = this.read("DM Tx Status Delivery Status");
		((DMTxStatusResponse)response).setDeliveryStatus(DMTxStatusResponse.DeliveryStatus.get(deliveryStatus));
		
		int discoveryStatus = this.read("DM Tx Status Discovery Status");
		((DMTxStatusResponse)response).setDiscoveryStatus(DMTxStatusResponse.DiscoveryStatus.get(discoveryStatus));
	}

	private void parseDMRxResponse() throws IOException {
		
		// TODO this needs OO refactoring
		if (this.apiId == ApiId.DM_IO_SAMPLE_RESPONSE) {
			response = new DMRxIoSampleResponse();
		} else if (this.apiId == ApiId.DM_RX_RESPONSE){
			response = new DMRxResponse();	
		} else {
			response = new DMExplicitRxResponse();
		}
		
		((DMRxBaseResponse)response).setRemoteAddress64(this.parseAddress64());
		((DMRxBaseResponse)response).setRemoteAddress16(this.parseAddress16());
		
		if (this.apiId == ApiId.DM_EXPLICIT_RX_RESPONSE) {
			((DMExplicitRxResponse)response).setSourceEndpoint(this.read("Reading Source Endpoint"));
			((DMExplicitRxResponse)response).setDestinationEndpoint(this.read("Reading Destination Endpoint"));
			DoubleByte clusterId = new DoubleByte();
			clusterId.setMsb(this.read("Reading Cluster Id MSB"));
			clusterId.setLsb(this.read("Reading Cluster Id LSB"));
			((DMExplicitRxResponse)response).setClusterId(clusterId);
			
			DoubleByte profileId = new DoubleByte();
			profileId.setMsb(this.read("Reading Profile Id MSB"));
			profileId.setMsb(this.read("Reading Profile Id LSB"));
			((DMExplicitRxResponse)response).setProfileId(profileId);
		}
		
		int option = this.read("DM RX Response Option");
		((DMRxBaseResponse)response).setOption(DMRxBaseResponse.Option.get(option));
		
		if (this.apiId == ApiId.DM_IO_SAMPLE_RESPONSE) {
			parseDMIoSampleResponse((DMRxIoSampleResponse)response);
		} else {		
			((DMRxResponse)response).setData(this.readRemainingBytes());			
		}
	}
	
	private void parseDMNodeIdentifierResponse() throws IOException {
			
		response = new DMNodeIdentificationResponse();


        ((DMNodeIdentificationResponse)response).setSenderAddress64(this.parseAddress64());
        ((DMNodeIdentificationResponse)response).setSenderAddress16(this.parseAddress16());

        int option = this.read("Option");
		((DMNodeIdentificationResponse)response).setOption(DMNodeIdentificationResponse.Option.get(option));		

		// again with the addresses
        ((DMNodeIdentificationResponse)response).setRemoteAddress64(this.parseAddress64());
        ((DMNodeIdentificationResponse)response).setRemoteAddress16(this.parseAddress16());

		StringBuffer ni = new StringBuffer();
		
		int ch = 0;
		
		// NI is terminated with 0
		while ((ch = this.read("Node Identifier")) != 0) {
			ni.append((char)ch);			
		}
		
		((DMNodeIdentificationResponse)response).setNodeIdentifier(ni.toString());
		((DMNodeIdentificationResponse)response).setParentAddress(this.parseAddress16());		
    }
	
	private void parseDMIoSampleResponse(DMRxIoSampleResponse response) throws IOException {
		// TODO expose as interface
		response.parse(this);
	}
	
	private void parseModemStatusResponse() throws IOException {		
		response = new ModemStatusResponse();
		((ModemStatusResponse)response).setStatus(ModemStatusResponse.Status.get(this.read("Modem Status")));
	}
	
	private void parseGeneric(int intApiId) throws IOException {
		//eat packet bytes -- they will be save to bytearray and stored in response
		this.readRemainingBytes();
		response = new GenericResponse();
		// TODO gotta save it because it isn't know to the enum apiId won't
		((GenericResponse)response).setGenericApiId(intApiId);
	}	
	
	/**
	 * 
	 * @throws IOException
	 */
	private void parseRxResponse() throws IOException {
		//TODO untested after 64-bit refactoring
		if (apiId == ApiId.RX_16_RESPONSE || apiId == ApiId.RX_64_RESPONSE) {
			if (apiId == ApiId.RX_16_RESPONSE) {
				response = new RxResponse16();	
				((RxBaseResponse)response).setSourceAddress(this.parseAddress16());
			} else {
				response = new RxResponse64();
				((RxBaseResponse)response).setSourceAddress(this.parseAddress64());
			}
		} else {
			response = new RxResponseIoSample();
			
			if (apiId == ApiId.RX_16_IO_RESPONSE) {
				((RxBaseResponse)response).setSourceAddress(this.parseAddress16());	
			} else {
				((RxBaseResponse)response).setSourceAddress(this.parseAddress64());
			}	
		}
		
		int rssi = this.read("RSSI");
		
		// rssi is a negative dbm value
		((RxBaseResponse)response).setRssi(-rssi);
		
		int options = this.read("Options");
		
		((RxBaseResponse)response).setOptions(options);
		
		if (apiId == ApiId.RX_16_RESPONSE || apiId == ApiId.RX_64_RESPONSE) {
			int[] payload = new int[length.getLength() - this.getFrameDataBytesRead()];
			
			int bytesRead = this.getFrameDataBytesRead();
			
			for (int i = 0; i < length.getLength() - bytesRead; i++) {
				payload[i] = this.read("Payload byte " + i);
				//log.debug("rx data payload [" + i + "] " + payload[i]);
			}				
			
			((RxResponse)response).setData(payload);
		} else {
			// I/O sample
			log.debug("this is a I/O sample!");
			((RxResponseIoSample)response).parse(this);
		}
	}
	
	private void parseTxStatusResponse() throws IOException {
		//log.debug("TxStatus");
		
		response = new TxStatusResponse();
		
		// parse TxStatus
		
		// frame id
		int frameId = this.read("TxStatus Frame Id");
		((TxStatusResponse)response).setFrameId(frameId);
		
		//log.debug("frame id is " + frameId);

		// Status: 0=Success, 1= No Ack, 2= CCA Failure, 3= Purge
		int status = this.read("TX Status");
		((TxStatusResponse)response).setStatus(TxStatusResponse.Status.get(status));
		
		//log.debug("status is " + status);
	}
		
	/**
	 * Reads all remaining bytes except for checksum
	 * @return
	 * @throws IOException
	 */
	private int[] readRemainingBytes() throws IOException {
		
		// minus one since we don't read the checksum
		int[] value = new int[this.getRemainingBytes() - 1];
		
		log.debug("There are " + value.length + " remaining bytes");
		
		for (int i = 0; i < value.length; i++) {
			value[i] = this.read("Remaining bytes " + i);
		}
		
		return value;
	}
	
	public XBeeAddress64 parseAddress64() throws IOException {
		XBeeAddress64 addr = new XBeeAddress64();
		
		for (int i = 0; i < 8; i++) {
			addr.getAddress()[i] = this.read("64-bit Address byte " + i);
		}	
		
		return addr;
	}
	
	public XBeeAddress16 parseAddress16() throws IOException {
		XBeeAddress16 addr16 = new XBeeAddress16();
		
		addr16.setMsb(this.read("Address 16 MSB"));
		addr16.setLsb(this.read("Address 16 LSB"));
		
		return addr16;
	}
	
	/**
	 * Returns number of bytes remaining, relative to the stated packet length (not including checksum).
	 * @return
	 */
	public int getFrameDataBytesRead() {
		// subtract out the 2 length bytes
		return this.getBytesRead() - 2;
	}
	
	/**
	 * Number of bytes remaining to be read, including the checksum
	 * @return
	 */
	public int getRemainingBytes() {
		// add one for checksum byte (not included) in packet length
		return this.length.get16BitValue() - this.getFrameDataBytesRead() + 1;
	}
	
	// get unescaped packet length
	// get escaped packet length
	
	/**
	 * Does not include any escape bytes
	 * @return
	 */
	public int getBytesRead() {
		return bytesRead;
	}

	public void setBytesRead(int bytesRead) {
		this.bytesRead = bytesRead;
	}

	public boolean isDone() {
		return done;
	}

	public void setDone(boolean done) {
		this.done = done;
	}

	public int getChecksum() {
		return checksum.getChecksum();
	}
}
