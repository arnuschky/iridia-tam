package be.ac.ulb.iridia.tam.common.tam;

/**
 * Interface for a user-defined controller that can be attached to a TAM.
 * The controller can set the behavior of the TAM by sending SET_LEDS commands.
 * @see be.ac.ulb.iridia.tam.common.coordinator.Coordinator
 */
public interface ControllerInterface
{
    /**
     * Step function of the controller. Called every Coordinator.STEP_TAMS_INTERVAL milliseconds.
     */
    public void step();
}
