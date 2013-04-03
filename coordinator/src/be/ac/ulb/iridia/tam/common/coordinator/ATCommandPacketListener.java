package be.ac.ulb.iridia.tam.common.coordinator;

import com.rapplogic.xbee.api.ApiId;
import com.rapplogic.xbee.api.AtCommandResponse;
import com.rapplogic.xbee.api.PacketListener;
import com.rapplogic.xbee.api.XBeeResponse;
import com.rapplogic.xbee.api.digimesh.DMNodeDiscover;
import org.apache.log4j.Logger;

/**
 * This class is a packet listener used by the coordinator to parse
 * responses to AT commands.
 *
 * AT commands are used to write and read settings in the Xbee module or
 * to request specific functionality from the network (eg, triggering a
 * node discovery).
 *
 * @see PacketListener
 */
class ATCommandPacketListener implements PacketListener
{
    private final static Logger log = Logger.getLogger(ATCommandPacketListener.class);

    // coordinator the packet listener is attached to
    Coordinator coordinator;


    /**
     * Creates packet listener.
     * @param coordinator  coordinator the packet listener is attached to
     */
    ATCommandPacketListener(Coordinator coordinator)
    {
        this.coordinator = coordinator;
    }

    /**
     * Processes a response.
     * @see PacketListener
     * @param response  Xbee response to process
     */
    public void processResponse(XBeeResponse response)
    {
        // treat only responses of type AT_RESPONSE
        if (response.getApiId() == ApiId.AT_RESPONSE)
        {
            AtCommandResponse atResponse = (AtCommandResponse)response;

            // node discovery command response
            if (atResponse.getCommand().equals("ND"))
            {
                // each response is a record of an Xbee node that exists in the network
                DMNodeDiscover ndResponse = DMNodeDiscover.parse((AtCommandResponse) response);
                log.debug("Found node: " + ndResponse);

                // add the TAM that we just discovered
                coordinator.updateDiscoveredTAM(ndResponse.getNodeIdentifier(),
                        ndResponse.getNodeAddress64());

                log.debug("TAM discovered: " + coordinator.listOfTAMs.get(ndResponse.getNodeAddress64().toString()));
            }
            // request signal strength command response
            else if (atResponse.getCommand().equals("DB"))
            {
                // command response contains the signal strength of the local Xbee module in dB
                int rssi = -((AtCommandResponse)response).getValue()[0];
                log.debug("Signal strength on last packet: " + rssi + " dB");
                coordinator.setSignalStrength(rssi);
            }
            else
            {
                log.warn("Unexpected AT command response: " + atResponse.toString());
            }
        }
    }
}
