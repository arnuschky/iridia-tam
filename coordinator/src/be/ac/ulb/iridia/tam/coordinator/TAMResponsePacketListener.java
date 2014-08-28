package be.ac.ulb.iridia.tam.coordinator;

import be.ac.ulb.iridia.tam.common.LedColor;
import com.rapplogic.xbee.api.ApiId;
import com.rapplogic.xbee.api.PacketListener;
import com.rapplogic.xbee.api.XBeeResponse;
import com.rapplogic.xbee.api.digimesh.DMRxResponse;
import com.rapplogic.xbee.api.digimesh.DMTxStatusResponse;
import com.rapplogic.xbee.util.ByteUtils;
import org.apache.log4j.Logger;


/**
 * This class is a packet listener used by the coordinator to parse
 * normal network packets (usually sent by the TAMs) and responses to packets
 * sent by the coordinator.
 *
 * Each packet is treated according to its custom type signified by the first
 * byte in the packet.
 *
 * @see Coordinator PACKET_TYPE_TC_CURRENT_STATE
 * @see PacketListener
 */
class TAMResponsePacketListener implements PacketListener
{
    private final static Logger log = Logger.getLogger(TAMResponsePacketListener.class);

    // coordinator the packet listener is attached to
    Coordinator coordinator;


    /**
     * Creates packet listener.
     * @param coordinator  coordinator the packet listener is attached to
     */
    TAMResponsePacketListener(Coordinator coordinator)
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
        // treat only normal packets
        if (response.getApiId() == ApiId.DM_RX_RESPONSE)
        {
            DMRxResponse rxResponse = (DMRxResponse) response;

            int data[] = rxResponse.getData();
            log.debug("Received RX packet, option is " + rxResponse.getOption() + ", sender 64 address is " + rxResponse.getRemoteAddress64() + ", remote 16-bit address is " + rxResponse.getRemoteAddress16() + ", data is " + ByteUtils.toBase16(rxResponse.getData())) ;

            // status reports sent by the TAM, sent because
            // 1) heartbeat (status sent in intervals)
            // 2) state change (robot came or went)
            // 3) reply to command received from coordinator
            if (data[0] == Coordinator.PACKET_TYPE_TC_CURRENT_STATE)
            {
                String address = rxResponse.getRemoteAddress64().toString();

                // check if the TAM is already in the database
                if (!coordinator.listOfTAMs.containsKey(address))
                {
                    // if not, add the TAM that we just discovered
                    log.debug("Adding unknown TAM...");
                    coordinator.updateDiscoveredTAM(null,
                            rxResponse.getRemoteAddress64());
                }

                // update the TAM's data with the data from the packet that we just received
                TAM tam = coordinator.listOfTAMs.get(address);
                tam.updateLedColor(new LedColor((byte) data[1], (byte) data[2], (byte) data[3]));
                tam.updateRobotPresent(data[4] == 1);
                tam.updateRobotData(data[7]);
                tam.updateVoltage(data[5], data[6]);
                tam.updateLastSeenTimestamp();
                log.debug("TAM status updated: " + tam);

                // cancel the timeout task and clear it in the TAM
                if (tam.getSetLedsCmdTimeoutTask() != null)
                {
                    tam.getSetLedsCmdTimeoutTask().cancel();
                    tam.setSetLedsCmdTimeoutTask(null);
                }

                if (tam.getId() == null)
                {
                    // TODO: just try to resolve this specific TAM instead of running an open node discovery
                    log.debug("Requesting ND for unknown TAM...");
                    coordinator.setNodeDiscoveryRequested(true);
                }
            }

        }
        else if (response.getApiId() == ApiId.DM_TX_STATUS_RESPONSE)
        {
            DMTxStatusResponse statusResponse = (DMTxStatusResponse) response;
            if (statusResponse.getDeliveryStatus() == DMTxStatusResponse.DeliveryStatus.SUCCESS)
            {
                log.debug("Received response for sent packet after " + statusResponse.getRetryCount() +  " retries.");
            }
            else
            {
                log.error("Packet sent failed due to error: " + statusResponse.getDeliveryStatus());
            }
        }
    }
}
