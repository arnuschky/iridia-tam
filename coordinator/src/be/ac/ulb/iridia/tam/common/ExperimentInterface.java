package be.ac.ulb.iridia.tam.common;


/**
 * Interface for a "TAM experiment". This should be implemented by your main class.
 *
 * The coordinator uses the attachTAMController() function to attach controllers to newly discovered TAMs.
 * You can use the id of the TAM to attach specific controllers to specific TAMs,
 * thereby giving them the different functionality.
 *
 * Your main() function should look similar to this:
 *
 *   Coordinator coordinator = new Coordinator();
 *   ExperimentInterface experiment = new YourExperimentClass();
 *   experiment.init(System.currentTimeMillis());
 *   coordinator.setExperiment(experiment);
 *   coordinator.start();
 *
 */
public interface ExperimentInterface
{
    /**
     * Initializes experiment.
     * @param randomSeed  seed for the prng, set either constant or use System.currentTimeMillis()
     */
    public void init(long randomSeed);

    /**
     * Resets the experiment.
     */
    public void reset();

    /**
     * Called by the coordinator to attach controllers to newly discovered TAMs.
     * You can use the id of the TAM to attach specific controllers
     * to specific TAMs, thereby giving them the different functionality.
     * @param tam  TAM the coordinator requests a coordinator for
     */
    public void attachTAMController(TAMInterface tam);

    /**
     * Checks whether the experiment is ready to start.
     * @return true if ready to start
     */
    public boolean isReady();

    /**
     * Checks whether the experiment should finish.
     * @return true if should finish
     */
    public boolean isFinished();

    /**
     * Called by the coordinator on regular intervals.
     * Can be used for management of TAMs etc.
     */
    public void step();
}
