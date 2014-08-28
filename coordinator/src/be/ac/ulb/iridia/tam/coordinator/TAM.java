package be.ac.ulb.iridia.tam.coordinator;

import be.ac.ulb.iridia.tam.common.LedColor;
import be.ac.ulb.iridia.tam.common.TAMInterface;
import be.ac.ulb.iridia.tam.common.ControllerInterface;
import com.rapplogic.xbee.api.XBeeAddress64;

import java.util.Date;
import java.util.TimerTask;


/**
 * This class represents a single TAM in the experiment.
 * It holds the basic information such as Xbee-related data (addresses, id etc) and
 * the state of the TAM.
 *
 * The state of a TAM is defined as the current color of the RGB LEDs, and if a
 * robot is present in the TAM or not.
 *
 * The class also additionally stores the voltage of the TAM, which is not considered to be
 * part of the state of the TAM in the sense of the controller.
 *
 * Note: everything in this class MUST be synchronized to render it thread-safe
 */
public class TAM implements TAMInterface
{
    // Xbee network id. Up to 20 characters. In case of the TAM, this must be "TAMXX" with XX being a 2-digit number
    private String id;

    // coordinator that handles this TAM
    private Coordinator coordinator;

    // 64bit address of the TAM in the mesh network.
    private XBeeAddress64 address64;

    // Timestamp that describes when the coordinator has seen the TAM for the first time.
    private long firstSeenTimestamp;
    // Timestamp that describes when the coordinator has seen the TAM for the last time.
    private long lastSeenTimestamp;

    // Class that describes the color of the RGB LEDs of the TAM.
    private LedColor ledColor;
    // Timestamp of last update of led color.
    private long ledColorLastUpdated;

    // Flag that indicates if a robot is present as reported by the TAM.
    private boolean robotPresent;
    // Timestamp of last update of robot presence.
    private long robotPresentLastUpdated;
    
    // the data the robot sent to the TAM.
    private int robotData;
    // Timestamp of last update of robot data.
    private long robotDataLastUpdated;

    // Voltage as double value as reported by the TAM. Should be >= 3.2V.
    private double voltage;

    // Timer task that handles the timeout of the SET_LEDS command. Used to detect timed-out packets.
    private TimerTask setLedsCmdTimeoutTask;
    
    // Timer task that handles the timeout of the WRITE_ROBOT command. Used to detect timed-out packets.
    private TimerTask writeRobotCmdTimeoutTask;

    // The controller of the TAM, as set by the user.
    private ControllerInterface controller;


    /**
     * Constructor of the TAM. A TAM is created in two cases:
     *  1) the coordinator receives a heartbeat of an unknown TAM
     *  2) a unknown TAM replies to the node discover command
     * @param id            id of the TAM
     * @param coordinator   coordinator
     * @param address64     64bit Xbee address of the TAM
     */
    public TAM(String id, Coordinator coordinator, XBeeAddress64 address64)
    {
        this.address64 = address64;
        this.coordinator = coordinator;

        this.firstSeenTimestamp = new Date().getTime();

        this.ledColor = null;
        this.ledColorLastUpdated = 0;

        this.robotPresent = false;
        this.robotPresentLastUpdated = 0;
        this.robotData = 0;
        this.robotDataLastUpdated = 0;

        this.voltage = 0.0;

        setId(id);
    }


    /***************************************************************************
     *
     * Methods below implement TAM interface
     *
     ***************************************************************************/

    /**
     * Returns the color of the RGB LEDs of the TAM as currently known.
     * @return LedColor object reflecting the 24bit color, or null if TAM didn't report status yet
     */
    @Override
    public synchronized LedColor getLedColor()
    {
        return ledColor;
    }

    /**
     * Sets a new LED color.
     * Update is ignored if LED color does not change.
     * @param ledColor  LedColor object reflecting the 24bit color
     */
    @Override
    public void setLedColor(LedColor ledColor)
    {
        coordinator.sendSetLedsCommand(this, ledColor);
    }

    /**
     * Returns true if there is currently a robot in the TAM.
     * @return true if there is currently a robot in the TAM
     */
    @Override
    public synchronized boolean isRobotPresent()
    {
        return robotPresent;
    }

    /**
     * Returns the id of the TAM.
     *  - it's TAMXX with XX being a 2-digit unique integer; or
     *  - it's the last 5 characters of the 64bit address if the id hasn't been resolved yet
     * @return id of TAM as String 5 characters long.
     */
    @Override
    public synchronized String getId()
    {
        return id;
    }

    /**
     * Returns the data received from the robot currently in the TAM.
     * @return the data received from the robot currently in the TAM.
     */
    @Override
    public synchronized int getRobotDataReceived()
    {
        return robotData;
    }

    /**
     * Sets a value for the robotData.
     * Update is ignored if data did not change
     * @param robotData new robotData value
     */
    @Override
    public void setRobotDataToSend(int robotData)
    {
        coordinator.sendWriteRobotCommand(this, robotData);
    }

    /**
     * Returns the user-defined controller of the TAM.
     * @return controller of the TAM
     */
    @Override
    public synchronized ControllerInterface getController()
    {
        return controller;
    }

    /**
     * Sets the user-defined controller of the TAM.
     * The controller should define a step() function that controls the behavior of the TAM.
     * @see ControllerInterface
     * @param controller  user-defined controller of the TAM
     */
    @Override
    public synchronized void setController(ControllerInterface controller)
    {
        this.controller = controller;
    }


    /***************************************************************************
     *
     * Methods below are only used by the coordinator
     *
     ***************************************************************************/

    /**
     * Returns the timestamp of the last update of the LED color.
     * @return timestamp of last update
     */
    protected synchronized long getLedColorLastUpdated()
    {
        return ledColorLastUpdated;
    }

    /**
     * Sets a new LED color and updates the timestamp accordingly.
     * Update is ignored if LED color does not change
     * @param ledColor  LedColor object reflecting the 24bit color
     */
    protected synchronized void updateLedColor(LedColor ledColor)
    {
        // check if we're actually trying to set a new color
        // ignore this check if we never received an update
        if (!this.ledColor.equals(ledColor) || this.ledColorLastUpdated == 0)
        {
            this.ledColor = ledColor;
            this.ledColorLastUpdated = new Date().getTime();
        }
    }

    /**
     * Gets the the timestamp of the last update of the robotPresent flag.
     * @return timestamp of last update
     */
    @SuppressWarnings("unused")
    public synchronized long getRobotPresentLastUpdated()
    {
        return robotPresentLastUpdated;
    }

    /**
     * Sets a value for the robotPresent flag and updates the timestamp accordingly.
     * Update is ignored if flag did not change
     * @param robotPresent  new robotPresent flag, true if robot is in TAM
     */
    protected synchronized void updateRobotPresent(boolean robotPresent)
    {
        if (this.robotPresent != robotPresent)
        {
            this.robotPresent = robotPresent;
            this.robotPresentLastUpdated = new Date().getTime();
        }
    }

    /**
     * Sets the id of te TAM. TAM ids are always 5 characters:
     *  - it's TAMXX with XX being a 2-digit unique integer; or
     *  - it's the last 5 characters of the 64bit address if the id hasn't been resolved yet
     * Updated using data from a node discovery request.
     * @see be.ac.ulb.iridia.tam.coordinator.ATCommandPacketListener
     * @param id  id of tam if resolved already, else null
     */
    protected synchronized void setId(String id)
    {
        if (id == null)
        {
            String address = getAddress64().toString();
            this.id = address.substring(address.length() - 5, address.length());
        }

        this.id = id;
    }

    /**
     * Returns the 64bit Xbee network address of the TAM.
     * @return 64bit Xbee network address of the TAM
     */
    public synchronized XBeeAddress64 getAddress64()
    {
        return address64;
    }

    /**
     * Returns the timestamp of the first time the coordinator has seen this TAM on the network.
     * @return timestamp of the first time the coordinator has seen this TAM on the network
     */
    @SuppressWarnings("unused")
    protected synchronized long getFirstSeenTimestamp()
    {
        return firstSeenTimestamp;
    }

    /**
     * Returns the timestamp of the last time the coordinator has seen this TAM on the network.
     * @return timestamp of the last time the coordinator has seen this TAM on the network
     */
    @SuppressWarnings("unused")
    protected synchronized long getLastSeenTimestamp()
    {
        return lastSeenTimestamp;
    }

    /**
     * Updates the timestamp of the last time the coordinator has seen this TAM on the network.
     * Sets timestamp to current time.
     */
    protected synchronized void updateLastSeenTimestamp()
    {
        this.lastSeenTimestamp = new Date().getTime();
    }

    /**
     * Returns the voltage of the TAM as double value.
     * @return voltage of TAM as double value, in volts
     */
    @SuppressWarnings("unused")
    public synchronized double getVoltage()
    {
        return voltage;
    }

    /**
     * Sets the voltage from two bytes, as reported by the TAM in the status update packet.
     * The TAM reports the voltage as 16bit unsigned int, multiplied by 1000.
     * That is, a voltage of 3.2V gets reported as 3200, split up into two bytes.
     * @param data1  LSB of the voltage
     * @param data2  MSB of the voltage
     */
    protected synchronized void updateVoltage(int data1, int data2)
    {
        this.voltage = ((data1 & 0xff) + ((data2 & 0xff) << 8)) / 1000.0;
    }

    /**
     * Returns the current timer task that handles the timeout of the SET_LEDS command.
     * Return null if no unacknowledged command has been sent.
     * @return current timer task or null
     */
    protected synchronized TimerTask getSetLedsCmdTimeoutTask()
    {
        return setLedsCmdTimeoutTask;
    }

    /**
     * Sets the current timer task that handles the timeout of the SET_LEDS command.
     * Set null last command sent has been acknowledged.
     * @param setLedsCmdTimeoutTask  timer task for timeout or null
     */
    protected synchronized void setSetLedsCmdTimeoutTask(TimerTask setLedsCmdTimeoutTask)
    {
        this.setLedsCmdTimeoutTask = setLedsCmdTimeoutTask;
    }
    
    /**
     * Returns the current timer task that handles the timeout of the WRITE_ROBOT command.
     * Return null if no unacknowledged command has been sent.
     * @return current timer task or null
     */
    protected synchronized TimerTask getWriteRobotCmdTimeoutTask()
    {
        return writeRobotCmdTimeoutTask;
    }

    /**
     * Sets the current timer task that handles the timeout of the WRITE_ROBOT command.
     * Set null last command sent has been acknowledged.
     * @param writeRobotCmdTimeoutTask  timer task for timeout or null
     */
    protected synchronized void setWriteRobotCmdTimeoutTask(TimerTask writeRobotCmdTimeoutTask)
    {
        this.writeRobotCmdTimeoutTask = writeRobotCmdTimeoutTask;
    }

    /**
     * Sets a value for the robotData and updates the timestamp accordingly.
     * Update is ignored if data did not change
     * @param robotData new robotData value
     */
    protected void updateRobotData(int robotData) {
        if (this.robotData != robotData || this.robotDataLastUpdated == 0)
        {
            this.robotData = robotData;
            this.robotDataLastUpdated = new Date().getTime();
        }
    }

    /**
     * Gets the the timestamp of the last update of the robotData value.
     * @return timestamp of last update
     */
    @SuppressWarnings("unused")
    protected synchronized long getRobotDataLastUpdated()
    {
        return robotDataLastUpdated;
    }

    /**
     * Returns a string representation of the TAM.
     * @return string representation of the TAM
     */
    @Override
    public String toString()
    {
        return "TAM{" +
                "id='" + id + '\'' +
                ", address64=" + address64 +
                ", voltage=" + voltage +
                "V, ledColor=(" + ledColor +
                "), ledColorLastUpdated=" + ledColorLastUpdated +
                ", robotPresent=" + robotPresent +
                ", robotPresentLastUpdated=" + robotPresentLastUpdated +
                ", robotData=" + robotData +
                ", robotDataLastUpdate=" + robotDataLastUpdated;
    }
}
