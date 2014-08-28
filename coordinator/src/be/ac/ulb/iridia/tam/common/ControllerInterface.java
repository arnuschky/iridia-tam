package be.ac.ulb.iridia.tam.common;

/**
 * Interface for a user-defined controller that can be attached to a one
 * or multiple TAMs. The controller can set the LED color of the TAM, check
 * for the presence of a robot and read/write data from the robot using IRcom.
 * @see be.ac.ulb.iridia.tam.common.TAMInterface
 */
public interface ControllerInterface
{
    /**
     * Resets the controller.
     */
    public void reset();

    /**
     * Step function of the controller. Called every Coordinator.STEP_INTERVAL milliseconds.
     */
    public void step();
}
