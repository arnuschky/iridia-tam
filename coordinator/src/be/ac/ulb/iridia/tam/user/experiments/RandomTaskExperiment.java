package be.ac.ulb.iridia.tam.user.experiments;

import be.ac.ulb.iridia.tam.common.AbstractExperiment;
import be.ac.ulb.iridia.tam.coordinator.Coordinator;
import be.ac.ulb.iridia.tam.common.ExperimentInterface;
import be.ac.ulb.iridia.tam.common.TAMInterface;
import be.ac.ulb.iridia.tam.user.controllers.RandomTaskController;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import java.util.TimerTask;


/**
 * This example experiment is a two task-type experiment. Each TAM can
 * represent one of two tasks, BLUE or GREEN, each running with a different duration.
 * The controller sets a random task when the TAM is free.
 * @see be.ac.ulb.iridia.tam.user.controllers.RandomTaskController
 */
public class RandomTaskExperiment extends AbstractExperiment
{
    private final static Logger log = Logger.getLogger(RandomTaskExperiment.class);

    private final static long EXPERIMENT_DURATION_IN_SECONDS = 3600;


    /**
     * Initializes experiment.
     * Sets flags to defaults and initialized the random number generator.
     * @param randomSeed  seed for the prng, set either constant or use System.currentTimeMillis()
     */
    @Override
    public void init(long randomSeed)
    {
        super.init(randomSeed);

        // request shutdown after EXPERIMENT_DURATION_IN_SECONDS seconds
        getTimer().schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                log.fatal("Experiment duration is over, terminating.");
                setFinished();
            }
        }, EXPERIMENT_DURATION_IN_SECONDS * 1000);
    }

    /**
     * Called by the coordinator to attach controllers to newly discovered TAMs.
     * You can use the id or address of the TAM to attach specific controllers
     * to specific TAMs, thereby giving them the different functionality.
     * @param tam  TAM the coordinator requests a controller for
     */
    public void attachTAMController(TAMInterface tam)
    {
        // create new controller for a tam
        log.info("Creating new RandomTaskController for " + tam.getId());
        RandomTaskController controller = new RandomTaskController();
        controller.init(getPrng().nextInt(), tam);
        tam.setController(controller);
    }

    /**
     * Main function used to start the experiment.
     * @param args  array of command line arguments
     * @throws Exception on Xbee comm failure
     */
    public static void main(String[] args) throws Exception
    {
        // init log4j
        PropertyConfigurator.configure("log4j.properties");

        // create the coordinator
        Coordinator coordinator = new Coordinator("/dev/ttyUSB1", 9600);

        // create our experiment (see above)
        ExperimentInterface experiment = new RandomTaskExperiment();
        experiment.init(System.currentTimeMillis());
        coordinator.setExperiment(experiment);

        // run the coordinator send and receive threads that handle all Xbee communication
        // NOTE: this will never return, so must be last!
        coordinator.start();
    }
}
