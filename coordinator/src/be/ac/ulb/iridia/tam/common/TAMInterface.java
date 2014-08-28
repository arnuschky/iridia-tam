package be.ac.ulb.iridia.tam.common;


/**
 * This interface implements all methods required to manipulate and
 * query the state of the TAM.
 */
public interface TAMInterface
{
    /**
     * Returns the color of the RGB LEDs of the TAM as currently known.
     * @return LedColor object reflecting the 24bit color, or null if TAM didn't report status yet
     */
    LedColor getLedColor();

    /**
     * Sets a new LED color.
     * Update is ignored if LED color does not change.
     * @param ledColor  LedColor object reflecting the 24bit color
     */
    void setLedColor(LedColor ledColor);

    /**
     * Returns true if there is currently a robot in the TAM.
     * @return true if there is currently a robot in the TAM
     */
    boolean isRobotPresent();

    /**
     * Returns the id of the TAM.
     *  - it's TAMXX with XX being a 2-digit unique integer; or
     *  - it's the last 5 characters of the 64bit address if the id hasn't been resolved yet
     * @return id of TAM as String 5 characters long.
     */
    String getId();

    /**
     * Sets a value for the robotData.
     * Update is ignored if data did not change
     * @param robotData new robotData value
     */
    void setRobotDataToSend(int robotData);

    /**
     * Returns the data received from the robot currently in the TAM.
     * @return the data received from the robot currently in the TAM.
     */
    int getRobotDataReceived();

    /**
     * Returns the user-defined controller of the TAM.
     * @return controller of the TAM
     */
    ControllerInterface getController();

    /**
     * Sets the user-defined controller of the TAM.
     * @see be.ac.ulb.iridia.tam.common.ControllerInterface
     * @param controller  user-defined controller of the TAM
     */
    void setController(ControllerInterface controller);
}

