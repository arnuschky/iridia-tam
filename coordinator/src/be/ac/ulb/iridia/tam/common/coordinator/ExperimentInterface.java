package be.ac.ulb.iridia.tam.common.coordinator;

import be.ac.ulb.iridia.tam.common.tam.TAM;
import com.rapplogic.xbee.api.XBeeException;

/**
 * Interface for a "TAM experiment". This should be implemented by your main class.
 * The coordinator uses the attachTAMController() function to attach controllers to newly discovered TAMs.
 * You can use the id or address of the TAM to attach specific controllers to specific TAMs,
 * thereby giving them the different functionality.
 *
 * Your main() function should look similar to this:
 *
 *   Coordinator coordinator = new Coordinator();
 *   ExperimentInterface experiment = new YourExperimentClass(coordinator);
 *   coordinator.setExperiment(experiment);
 *   coordinator.runThreads();
 *
 */
public interface ExperimentInterface
{
    /**
     * Called by the coordinator to attach controllers to newly discovered TAMs.
     * You can use the id or address of the TAM to attach specific controllers
     * to specific TAMs, thereby giving them the different functionality.
     * @param tam  TAM the coordinator requests a coordinator for
     */
    public void attachTAMController(TAM tam);

    /**
     * Called by the coordinator after it shuts down the main loop.
     * Used to clean up, save files and, if required, shut down or switch off all TAMs.
     * @see Coordinator sendShutdownCommandToAllTAMs() and sendSwitchOffLedsCommandToAllTAMs()
     * @throws XBeeException
     */
    public void shutdownAction() throws XBeeException;
}
