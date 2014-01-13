package be.ac.ulb.iridia.tam.common.coordinator;

import be.ac.ulb.iridia.tam.common.tam.LedColor;
import be.ac.ulb.iridia.tam.common.tam.TAM;
import com.rapplogic.xbee.api.*;
import com.rapplogic.xbee.api.digimesh.DMTxRequest;
import com.rapplogic.xbee.api.digimesh.DMTxStatusResponse;
import com.rapplogic.xbee.util.ByteUtils;
import org.apache.log4j.Logger;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;


/**
 * This is the main class of the coordinator. This class communicates with the Xbee module
 * and schedules all the different tasks that the coordinator has to do. These tasks include:
 *
 *  - discover new nodes by Xbee command (regularly)
 *  - resolve unknown nodes by node discovery (only when unknown node was encountered)
 *  - request the current signal strength from the local Xbee module (regularly)
 *  - send packets from the send queue (regularly)
 *  - call the step() function of the controllers of all known TAMs (regularly)
 *
 * Additionally, this class starts two packet listeners:
 *
 *  1) takes care of normal network packets that have been received and the status
 *     responses to the packets sent by us
 *  2) takes care of the responses to AT commands issued by the coordinator
 *
 * The most important packet is the status report of the TAM. The TAM sends its status when:
 *
 *  - battery voltage is under BATT_VOLTAGE_THRESHOLD
 *  - every HEARTBEAT_INTERVAL_SEC seconds
 *  - every time something changed in status (robot left or arrived)
 *  - as response to a command of the coordinator (eg, SET_LEDS command)
 *
 * After initializing the coordinator with the parameters of the Xbee's serial port,
 * the user's main class MUST call the runThreads() method in order to schedule all the
 * tasks described above and start the packet listeners. After completing these tasks,
 * the coordinator goes into an infinite loop that can be interrupted by calling
 * setShutdownRequested(true).
 */
public class Coordinator
{
    private final static Logger log = Logger.getLogger(Coordinator.class);

    // intervals of various tasks that the coordinator performs regularly
    public static final long SEND_QUEUE_INTERVAL =         10; // send all items in the queue every 10ms
    public static final long CHECK_DB_INTERVAL   =  60 * 1000; // check signal strength every minute
    public static final long TRIGGER_ND_INTERVAL = 600 * 1000; // trigger node discovery every 10 minutes
    public static final long STEP_INTERVAL =               10; // step experiment and all TAMs every 10ms

    // packet types of TAM->Coordinator (TC)
    protected static final int PACKET_TYPE_TC_CURRENT_STATE = 0;
//    protected static final int PACKET_TYPE_TC_READ_ROBOT_RESULT = 1;
//    protected static final int PACKET_TYPE_TC_WRITE_ROBOT_RESULT = 2;

    // packet types of Coordinator->TAM (CT)
    protected static final int PACKET_TYPE_CT_SET_LEDS = 10;
//    protected static final int PACKET_TYPE_CT_READ_ROBOT = 11; //useless?
    protected static final int PACKET_TYPE_CT_WRITE_ROBOT = 12;
    protected static final int PACKET_TYPE_CT_SHUTDOWN = 13;

    // timeout for the SET_LEDS command in seconds
    public static final long SET_LEDS_CMD_TIMEOUT = 3;
    // timeout for the WRITE_ROBOT_CMD_TIMEOUT command in seconds
    public static final long WRITE_ROBOT_CMD_TIMEOUT = 5; //TODO set to a realistic value

    // list of known coordinator nodes, will be added to blacklist directly
    protected static final String COORDINATOR_ADDRESSES[] = {
            "0x00,0x13,0xa2,0x00,0x40,0x8c,0x04,0xa4",  // coordinator 0
            "0x00,0x13,0xa2,0x00,0x40,0x8c,0x04,0xc5",  // coordinator 1
            "0x00,0x13,0xa2,0x00,0x40,0x8d,0x6e,0xb4",  // coordinator 2
            "0x00,0x13,0xa2,0x00,0x40,0x8d,0x6e,0xa2"   // coordinator 3
    };

    // main Xbee object used to communicate with the attached Xbee module
    private XBee xbee;
    // serial device used to access the Xbee module
    private String device;
    // speed of serial port used to access the Xbee module
    private int baudRate;

    // hash map that stores all currently known TAMs by using the 64bit address of the TAM as key
    protected ConcurrentHashMap<String, TAM> listOfTAMs;

    // a list of nodes on the network that are not TAMs
    protected ConcurrentSkipListSet<String> addressBlacklist;

    // queue of requests that should be sent to different TAMs
    private ConcurrentLinkedQueue<DMTxRequest> sendRequestQueue;

    // timer used to schedule reoccurring tasks of the coordinator and the controllers of all TAMs
    private Timer timer;

    // experiment attached to this coordinator
    private ExperimentInterface experiment;

    // flags that designates if the coordinator should terminate
    private boolean shutdownRequested;

    // signal strength of the attached Xbee module
    private int signalStrength;

    // flag that designates if a node discovery has been requested
    private boolean nodeDiscoveryRequested;

    // flag that designates if a node discovery is currently running
    private boolean nodeDiscoveryRunning;

    // flag that is true if TAM controllers should be executed
    private boolean tamControllersEnabled;

    // pseudo-random number generator
    private Random prng;


    /**
     * Creates the coordinator and initializes all variables.
     * This constructor uses the current time to initialize the pseudo-random number generator.
     * @param device      serial device used to access the Xbee module
     * @param baudRate    speed of serial port used to access the Xbee module
     */
    public Coordinator(String device, int baudRate)
    {
        this(device, baudRate, System.currentTimeMillis());
    }

    /**
     * Creates the coordinator and initializes all variables.
     * @param device      serial device used to access the Xbee module
     * @param baudRate    speed of serial port used to access the Xbee module
     * @param randomSeed  long seed for pseudo-random number generator
     */
    public Coordinator(String device, int baudRate, long randomSeed)
    {
        this.device = device;
        this.baudRate = baudRate;
        this.prng = new Random(randomSeed);

        // create global Xbee object
        this.xbee = new XBee();

        // initialize flags
        setSignalStrength(0);
        setNodeDiscoveryRequested(false);
        setNodeDiscoveryRunning(false);
        setTamControllersEnabled(true);

        // create list of TAMs
        this.listOfTAMs = new ConcurrentHashMap<String, TAM>();

        // create queue used for send requests
        this.sendRequestQueue = new ConcurrentLinkedQueue<DMTxRequest>();

        // create queue used for send requests
        this.addressBlacklist = new ConcurrentSkipListSet<String>();
        for (String address : COORDINATOR_ADDRESSES)
        {
            this.addressBlacklist.add(address);
        }

        // create timer used to schedule all tasks done by the coordinator
        this.timer = new Timer();
    }

    /**
     * Returns the current experiment attached to the coordinator.
     * @return object that implements ExperimentInterface
     */
    public ExperimentInterface getExperiment()
    {
        return experiment;
    }

    /**
     * Sets the current experiment attached to the coordinator.
     * The experiment is used to attach controllers to newly discovered TAMs.
     * @param experiment  object that implements ExperimentInterface
     */
    public void setExperiment(ExperimentInterface experiment)
    {
        this.experiment = experiment;
    }

    /**
     * Returns the current timer instance.
     * The coordinator has a single timer that is used to schedule all tasks.
     * @return timer instance
     */
    public synchronized Timer getTimer()
    {
        return timer;
    }

    /**
     * Returns the current instance of the pseudo-random generator.
     * @return pseudo-random generator
     */
    public synchronized Random getPrng()
    {
        return prng;
    }

    /**
     * Returns the signal strength of the attached local Xbee module
     * @return signal strength in dB
     */
    @SuppressWarnings("unused")
    public synchronized int getSignalStrength()
    {
        return signalStrength;
    }

    /**
     * Sets the signal strength of the attached local Xbee module.
     * Requested by AT command DB, parsed in @see ATCommandPacketListener
     * @param signalStrength  signal strength as reported by Xbee module
     */
    public synchronized void setSignalStrength(int signalStrength)
    {
        this.signalStrength = signalStrength;
    }

    /**
     * Returns if a node discovery has been requested
     * @return true if node a discovery has been requested
     */
    public synchronized boolean isNodeDiscoveryRequested()
    {
        return nodeDiscoveryRequested;
    }

    /**
     * Requests a node discovery.
     * The coordinator will send a request for node discovery if
     * there is none running already.
     * @param nodeDiscoveryRequested  true if a node discovery is requested
     */
    public synchronized void setNodeDiscoveryRequested(boolean nodeDiscoveryRequested)
    {
        if (isNodeDiscoveryRunning())
        {
            log.debug("Warning: Node discovery is currently running!");
            return;
        }

        this.nodeDiscoveryRequested = nodeDiscoveryRequested;
    }

    /**
     * Returns if a node discovery is currently running.
     * @return true if node discovery is currently running
     */
    public synchronized boolean isNodeDiscoveryRunning()
    {
        return nodeDiscoveryRunning;
    }

    /**
     * Sets if there's currently a node discovery running. Used to prevent the
     * coordinator to send multiple, overlapping requests for node discovery.
     * This is set to true when the coordinator sends a node discovery request.
     * This is set to false when the node discovery request times out.
     * @param nodeDiscoveryRunning  true if a node discovery currently running
     */
    public synchronized void setNodeDiscoveryRunning(boolean nodeDiscoveryRunning)
    {
        this.nodeDiscoveryRunning = nodeDiscoveryRunning;
    }

    /**
     * Has a shutdown be requested? Used to terminate the coordinator.
     * @return true if user requested shutdown of coordinator
     */
    public synchronized boolean isShutdownRequested()
    {
        return shutdownRequested;
    }

    /**
     * Set shutdown flag. Used to terminate the coordinator.
     * @param shutdownRequested  true if the coordinator should be terminated
     */
    public synchronized void setShutdownRequested(boolean shutdownRequested)
    {
        this.shutdownRequested = shutdownRequested;
    }

    /**
     * Returns true if the execution of TAM controllers is currently enabled.
     * The default on startup is true.
     * @return true if execution of TAM controllers is currently enabled
     */
    public boolean isTamControllersEnabled()
    {
        return tamControllersEnabled;
    }

    /**
     * Enables and disables the execution of TAM controllers. This allows
     * to disable the execution upon start until all required TAMs are
     * found in the network.
     * @param tamControllersEnabled  true if execution should be enabled
     */
    public void setTamControllersEnabled(boolean tamControllersEnabled)
    {
        this.tamControllersEnabled = tamControllersEnabled;
    }

    /**
     * Method that is triggered on the discovery of a (partially) unknown TAM.
     * The coordinator can discover new TAMs in two ways:
     *  1) by receiving a status update of an unknown TAM @see TAMResponsePacketListener
     *  2) by receiving a reply of an (partially) unknown TAM to the node discovery @see ATCommandPacketListener
     * In first case, the new TAM is added to the list of TAMs.
     * In the second case, the TAM might be partially known already due to a status update.
     * Only the second case can provide the id of the TAM.
     * @param id            id of the TAM (null if yet unknown)
     * @param address64     64bit address of the TAM
     */
    protected void updateDiscoveredTAM(String id, XBeeAddress64 address64)
    {
        TAM tam;
        // convert address to string, which is the key to the map of all known TAMs
        String address = address64.toString();

        // ignore blacklisted TAMs
        if (addressBlacklist.contains(address))
            return;

        // check if this is a newly discovered node that is not a TAM
        if ((id != null) && !("TAM[0-9][1-9]".matches(id)))
        {
            // if it's not a TAM, remove and blacklist
            listOfTAMs.remove(address);
            addressBlacklist.add(address);
        }


        // check if we know this TAM already
        if (listOfTAMs.containsKey(address))
        {
            // if we have that TAM already, update it (can't change addresses!)
            tam = listOfTAMs.get(address);
            tam.setId(id);
            tam.updateLastSeenTimestamp();
            log.info("TAM " + tam.getId() + " is already recorded in the database, record updated.");
        }
        else
        {
            // tam is completely unknown, so create a new object and stick it into the database
            tam = new TAM(id, address64);
            listOfTAMs.put(address, tam);
            log.info("Added TAM with address " + address + " to database.");
        }

        // if we haven't attached a controller to this TAM yet, do it now
        // (if we have the id already)
        if (id != null && tam.getController() == null)
        {
            getExperiment().attachTAMController(tam);
        }
    }

    /**
     * Sends a SET_LEDS command to the specified TAM.
     * The request is ignored if we sent the command already (and we haven't had
     * a response from the TAM yet) and if the command does not change actually the LEDs.
     * @param tam        TAM to send command to
     * @param ledColor   new color of leds to send
     */
    public synchronized void sendSetLedsCommand(final TAM tam, final LedColor ledColor)
    {
        // check if we've sent already a SET_LEDS command
        if (tam.getSetLedsCmdTimeoutTask() != null)
        {
//            log.debug("Not sending SET_LEDS command again as we sent one already.");
            return;
        }

        // check if we're trying to send a command with a color that is already set
        // ignore this check if we haven't gotten an update yet (because color is invalid on init)
        if (tam.getLedColorLastUpdated() != 0 && tam.getLedColor().equals(ledColor))
        {
//            log.debug("Not sending SET_LEDS command again as leds are set already to requested values.");
            return;
        }

        // after above checks, we are sure that we want to change the leds to different values and
        // that we haven't tried to do this before

        // create an array of arbitrary data to send
        int[] payload = new int[] {
                PACKET_TYPE_CT_SET_LEDS,
                ledColor.getRedChannelValue(),
                ledColor.getGreenChannelValue(),
                ledColor.getBlueChannelValue()
        };

        // prepare the request and append it to the queue of
        DMTxRequest request = new DMTxRequest(tam.getAddress64(), payload);

        // create a timer task that handles the timeout of the SET_LEDS command
        TimerTask setLedsCmdTimeoutTask = new TimerTask()
        {
            public void run()
            {
                log.warn("SET_LED command sent to " + tam.getId() + " timed out!");
                tam.setSetLedsCmdTimeoutTask(null);
            }
        };
        tam.setSetLedsCmdTimeoutTask(setLedsCmdTimeoutTask);

        // append request to the queue and schedule timer
        log.debug("Sending SET_LEDS command to " + tam.getId() + " value (" + ledColor + ")");
        sendRequestQueue.add(request);
        getTimer().schedule(setLedsCmdTimeoutTask, SET_LEDS_CMD_TIMEOUT * 1000);
    }
    
    /**
     * Sends a WRITE_ROBOT command to the specified TAM.
     * The request is ignored if we sent the command already (and we haven't had
     * a response from the TAM yet).
     * @param tam        TAM to send command to
     * @param value      value to send to the robot
     */
    public synchronized void sendWriteRobotCommand(final TAM tam, final int value)
    {
        // check if we've sent already a WRITE_ROBOT command
        if (tam.getWriteRobotCmdTimeoutTask() != null)
        {
            log.debug("Not sending WRITE_ROBOT command again as we sent one already.");
            return;
        }

        // create an array of arbitrary data to send
        int[] payload = new int[] {
                PACKET_TYPE_CT_WRITE_ROBOT,
                value
        };

        // prepare the request and append it to the queue of
        DMTxRequest request = new DMTxRequest(tam.getAddress64(), payload);
        sendRequestQueue.add(request);
        log.debug("Sending WRITE_ROBOT command to " + tam.getId() + " value (" + value + ")");

        // create a timer task that handles the timeout of the SET_LEDS command
        TimerTask setWriteRobotTimeoutTask = new TimerTask()
        {
            public void run()
            {
                log.warn("WRITE_ROBOT command sent to " + tam.getId() + " timed out!");
                tam.setWriteRobotCmdTimeoutTask(null);
            }
        };

        // set the task in the TAM and schedule it
        tam.setWriteRobotCmdTimeoutTask(setWriteRobotTimeoutTask);
        getTimer().schedule(setWriteRobotTimeoutTask, WRITE_ROBOT_CMD_TIMEOUT * 1000);
    }

    /**
     * Sends the shutdown command to all TAMs.
     * TAMs need to be switched off and on after receiving this command.
     * Commands are send iteratively and synchronously (that is, method blocks until completion).
     * Note: after sending this command, you have to remove power from the TAM completely before starting up again
     * @throws XBeeException on communication error
     */
    public synchronized void sendShutdownCommandToAllTAMs() throws XBeeException
    {
        int[] payload = new int[] { PACKET_TYPE_CT_SHUTDOWN };
        DMTxRequest sendRequest = new DMTxRequest(null, payload);
        for (TAM tam : listOfTAMs.values())
        {
            log.debug("Shutting down " + tam.getId() + "...");
            sendRequest.setDestAddr64(tam.getAddress64());

            XBeeResponse response = xbee.sendSynchronous(sendRequest, 10000);
            if (response instanceof DMTxStatusResponse)
            {
                DMTxStatusResponse statusResponse = (DMTxStatusResponse) response;
                if (statusResponse.getDeliveryStatus() == DMTxStatusResponse.DeliveryStatus.SUCCESS)
                {
                    log.debug("Successfully shut down " + tam.getId());
                }
                else
                {
                    log.error("Failure to shut down " + tam.getId() + " due to error " + statusResponse.getDeliveryStatus());
                }
            }
            else
            {
                log.warn("Send response is of unexpected class " + response.getClass());
            }
        }
    }

    /**
     * Sends a command to all TAMs that switches of the LEDs.
     * Commands are send iteratively and synchronously (that is, method blocks until completion).
     * @throws XBeeException on communication error
     */
    public synchronized void sendSwitchOffLedsCommandToAllTAMs() throws XBeeException
    {
        int[] payload = new int[] { PACKET_TYPE_CT_SET_LEDS, 0, 0, 0 };
        DMTxRequest sendRequest = new DMTxRequest(null, payload);
        for (TAM tam : listOfTAMs.values())
        {
            log.debug("Switching off LEDs of " + tam.getId() + "...");
            sendRequest.setDestAddr64(tam.getAddress64());

            XBeeResponse response = xbee.sendSynchronous(sendRequest, 10000);
            if (response instanceof DMTxStatusResponse)
            {
                DMTxStatusResponse statusResponse = (DMTxStatusResponse) response;
                if (statusResponse.getDeliveryStatus() == DMTxStatusResponse.DeliveryStatus.SUCCESS)
                {
                    log.debug("Successfully switched off LEDs of " + tam.getId());
                }
                else
                {
                    log.error("Failure to switch off LEDs of " + tam.getId() + " due to error " + statusResponse.getDeliveryStatus());
                }
            }
            else
            {
                log.warn("Send response is of unexpected class " + response.getClass());
            }
        }
    }

    /**
     * Catch interrupt signals and terminate the application gracefully.
     * TODO: for some reason, this does not work at all!
     */
    public void setupShutdownHandlers()
    {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable()
        {
            public void run()
            {
                log.info("Shutdown hook called.");
                setShutdownRequested(true);
            }
        }));

        SignalHandler handler = new SignalHandler()
        {
            public void handle(Signal signal)
            {
                log.info("Shutdown signal handler called.");
                setShutdownRequested(true);
            }
        };
        Signal.handle(new Signal("INT"), handler);
        Signal.handle(new Signal("TERM"), handler);
    }

    /**
     * Schedules shutdown of the coordinator after a give number of seconds.
     * @param shutdownAfterSeconds  time after which we shutdown, in seconds
     */
    public void scheduleShutdown(final long shutdownAfterSeconds)
    {
        getTimer().schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                log.fatal(shutdownAfterSeconds + " seconds expired, executing scheduled shutdown of experiment.");
                setShutdownRequested(true);
            }
        }, shutdownAfterSeconds * 1000);
    }

    /**
     * Primary method of the coordinator.
     * This method schedules all regularly occurring tasks (controller step functions, node discovery requests,
     * send requests in the queue etc) and starts the packet listeners that parse incoming packets.
     * Note: this method does not return until setShutdownRequested(true) is called
     * @throws Exception on Xbee communication failure
     */
    public void runThreads() throws Exception
    {
        try
        {
            // start communication with Xbee module
            xbee.open(device, baudRate);

            // retrieve the node discovery timeout from the Xbee
            AtCommandResponse nodeTimeout = (AtCommandResponse)xbee.sendSynchronous(new AtCommand("NT"));
            final long nodeDiscoveryTimeout = ByteUtils.convertMultiByteToInt(nodeTimeout.getValue()) * 100;
            log.debug("Node discovery timeout is " + nodeDiscoveryTimeout + " milliseconds");

            // add packet listener that parses replies to AT commands
            xbee.addPacketListener(new ATCommandPacketListener(this));

            // add packet listener for all normal packets
            xbee.addPacketListener(new TAMResponsePacketListener(this));

            // schedule a task that steps all tam controllers at a regular interval
            // it also steps the experiment
            final ConcurrentHashMap<String, TAM> myListOfTAMs = listOfTAMs;
            getTimer().scheduleAtFixedRate(new TimerTask()
            {
                @Override
                public void run()
                {
                    experiment.step();

                    if (isTamControllersEnabled())
                    {
                        for (TAM tam : myListOfTAMs.values())
                        {
                            if (tam.getController() != null)
                                tam.getController().step();
                        }
                    }
                }
            }, 0, STEP_INTERVAL);

            // schedule a task that sends all requests that are currently in the queue
            getTimer().scheduleAtFixedRate(new TimerTask()
            {
                @Override
                public void run()
                {
                    try
                    {
                        // send all packets in the send queue asynchronously
                        while (sendRequestQueue.size() > 0)
                        {
                            // retrieves and removes the head of the queue
                            DMTxRequest sendRequest = sendRequestQueue.poll();

                            // send the packet
                            log.debug("Sending a packet to " + sendRequest.getDestAddr64().toString());
                            xbee.sendAsynchronous(sendRequest);
                        }

                        // if a node discovery has been requested (and there isn't one already running)
                        // send the request for it here
                        if (isNodeDiscoveryRequested() && !isNodeDiscoveryRunning())
                        {
                            setNodeDiscoveryRequested(false);
                            setNodeDiscoveryRunning(true);
                            log.debug("Requesting node discovery (AT command ND)");
                            xbee.sendAsynchronous(new AtCommand("ND"));

                            getTimer().schedule(new TimerTask()
                            {
                                @Override
                                public void run()
                                {
                                    log.debug("Node discovery timed out.");
                                    setNodeDiscoveryRunning(false);
                                }
                            }, nodeDiscoveryTimeout);
                        }

                    }
                    catch (XBeeException xe)
                    {
                        log.error(xe);
                    }

                }
            }, 0, SEND_QUEUE_INTERVAL);


            // schedule task updates the signal strength from time to time
            getTimer().scheduleAtFixedRate(new TimerTask()
            {
                @Override
                public void run()
                {
                    try
                    {
                        log.debug("Requesting signal strength (AT command DB)");
                        xbee.sendAsynchronous(new AtCommand("DB"));
                    }
                    catch (XBeeException xe)
                    {
                        log.error(xe);
                    }
                }
            }, 0, CHECK_DB_INTERVAL);


            // schedule task that requests a node discovery from time to time
            getTimer().scheduleAtFixedRate(new TimerTask()
            {
                @Override
                public void run()
                {
                    setNodeDiscoveryRequested(true);
                }
            }, 0, TRIGGER_ND_INTERVAL);


            // loop until the controller calls shutdown
            while (!isShutdownRequested())
            {
                Thread.sleep(10);
            }

            // call the shutdown action as defined by the experiment
            experiment.shutdownAction();
        }
        catch (XBeeException xbe)
        {
            log.fatal("Xbee exception "+xbe);
        }
        finally
        {
            // cancel all  timers still running
            getTimer().cancel();

            // disconnect xbee
            if (xbee.isConnected())
            {
                log.info("Closing xbee");
                xbee.close();
            }
        }
    }
}
