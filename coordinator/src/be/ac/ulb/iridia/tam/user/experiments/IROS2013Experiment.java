package be.ac.ulb.iridia.tam.user.experiments;

import be.ac.ulb.iridia.tam.common.coordinator.Coordinator;
import be.ac.ulb.iridia.tam.common.coordinator.ExperimentInterface;
import be.ac.ulb.iridia.tam.common.tam.TAM;
import be.ac.ulb.iridia.tam.user.controllers.iros2013.Controller;
import com.rapplogic.xbee.api.XBeeException;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import java.util.HashMap;


/**
 * This example experiment is a two-task random task-type experiment. Each TAM can
 * represent one of two tasks, BLUE or GREEN, each running with a different duration.
 * The controller sets a random task when the TAM is free.
 * @see be.ac.ulb.iridia.tam.user.controllers.TwoRandomTasksController
 */
public class IROS2013Experiment implements ExperimentInterface
{
    private final static Logger log = Logger.getLogger(IROS2013Experiment.class);

    private final static long EXPERIMENT_DURATION_IN_SECONDS = 3600;

    // coordinator that handles all the network stuff
    private Coordinator coordinator;

    private HashMap<String, TAM> listOfDiscoveredTams;



    /**
     * Create experiment.
     * @param coordinator  coordinator object
     */
    public IROS2013Experiment(Coordinator coordinator)
    {
        this.coordinator = coordinator;
        this.listOfDiscoveredTams = new HashMap<String, TAM>();

        // disables the controllers of all TAM until we discovered all required TAMs on the network
        coordinator.setTamControllersEnabled(false);
    }

    /**
     * Called by the coordinator to attach controllers to newly discovered TAMs.
     * You can use the id or address of the TAM to attach specific controllers
     * to specific TAMs, thereby giving them the different functionality.
     * @param tam  TAM the coordinator requests a controller for
     */
    public void attachTAMController(TAM tam)
    {
        if (coordinator.isTamControllersEnabled() && listOfDiscoveredTams.size() == 3)
            return;

        // we ignore TAMs that do not belong to our experiment
        if (!tam.getId().equals("TAM01") && !tam.getId().equals("TAM02") && !tam.getId().equals("TAM04"))
            return;

        // create new standard stand-alone controller for a tam
        listOfDiscoveredTams.put(tam.getId(), tam);
        log.info("Discovered a new "+tam.getId()+", total number is " + listOfDiscoveredTams.size());

        if (listOfDiscoveredTams.size() == 3)
        {
            // create a new centralized controller for all involved TAMs
            Controller controller = new Controller(coordinator,
                    listOfDiscoveredTams.get("TAM01"),
                    listOfDiscoveredTams.get("TAM02"),
                    listOfDiscoveredTams.get("TAM04"));

            // set controller only on a single tam so it gets executed only once
            listOfDiscoveredTams.get("TAM01").setController(controller);

            log.info("=======================================");
            log.info(" Completed setup, starting experiment! ");
            log.info("=======================================");

            coordinator.setTamControllersEnabled(true);
        }
    }

    /**
     * Called by the coordinator after it shuts down the main loop.
     * Used to clean up, save files and, if required, shut down or switch off all TAMs.
     * @see be.ac.ulb.iridia.tam.common.coordinator.Coordinator sendShutdownCommandToAllTAMs() and sendSwitchOffLedsCommandToAllTAMs()
     * @throws com.rapplogic.xbee.api.XBeeException
     */
    public void shutdownAction() throws XBeeException
    {
        log.fatal("Shutting down all TAMs...");
        //coordinator.sendShutdownCommandToAllTAMs();
        coordinator.sendSwitchOffLedsCommandToAllTAMs();
        log.fatal("Bye bye.");
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
        ExperimentInterface experiment = new IROS2013Experiment(coordinator);
        coordinator.setExperiment(experiment);

        // request shutdown after EXPERIMENT_DURATION_IN_SECONDS seconds
        coordinator.scheduleShutdown(IROS2013Experiment.EXPERIMENT_DURATION_IN_SECONDS);
        coordinator.setupShutdownHandlers();

        // run the coordinator send and receive threads that handle all Xbee communication
        // NOTE: this will never return, so must be last!
        coordinator.runThreads();
    }
}
