package be.ac.ulb.iridia.tam.user.experiments;

import be.ac.ulb.iridia.tam.common.coordinator.Coordinator;
import be.ac.ulb.iridia.tam.common.coordinator.ExperimentInterface;
import be.ac.ulb.iridia.tam.common.tam.TAM;
import be.ac.ulb.iridia.tam.user.controllers.proofofconcept.Controller;
import com.rapplogic.xbee.api.XBeeException;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;


/**
 * This example experiment is a two-task random task-type experiment. Each TAM can
 * represent one of two tasks, BLUE or GREEN, each running with a different duration.
 * The controller sets a random task when the TAM is free.
 * @see be.ac.ulb.iridia.tam.user.controllers.TwoRandomTasksController
 */
public class ProofOfConceptExperiment implements ExperimentInterface
{
    private final static Logger log = Logger.getLogger(ProofOfConceptExperiment.class);

    private final static long EXPERIMENT_DURATION_IN_SECONDS = 3600;

    private final static long NUMBER_OF_TASK_INSTANCES = 6;

    // coordinator that handles all the network stuff
    private Coordinator coordinator;

    private ArrayList<TAM> listOfDiscoveredTams;



    /**
     * Create experiment.
     * @param coordinator  coordinator object
     */
    public ProofOfConceptExperiment(Coordinator coordinator)
    {
        this.coordinator = coordinator;
        this.listOfDiscoveredTams = new ArrayList<TAM>();

        // disables the controllers of all TAM until we discovered all required TAMs on the network
        coordinator.setTamControllersEnabled(false);
    }

    /**
     * Called by the coordinator on regular intervals.
     * Can be used for management of TAMs etc.
     */
    public void step()
    {
    }


    public class CustomComparator implements Comparator<TAM>
    {
        @Override
        public int compare(TAM tam1, TAM tam2) {
            return tam1.getId().compareTo(tam2.getId());
        }

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

        // create new standard stand-alone controller for a tam
        listOfDiscoveredTams.add(tam);
        log.info("Discovered a new "+tam.getId()+", total number is " + listOfDiscoveredTams.size() +
                " of " + (NUMBER_OF_TASK_INSTANCES * 3) + " required.");

        if (listOfDiscoveredTams.size() == NUMBER_OF_TASK_INSTANCES * 3)
        {
            // sort the list of discovered TAMs by ID
            Collections.sort(listOfDiscoveredTams, new CustomComparator());

            // now, build a task instance by using the unused TAMs with the lowest IDs, in order
            for (int i = 0; i < NUMBER_OF_TASK_INSTANCES; i++) {
                log.info("Building instance " + (i + 1));

                // create a new centralized controller for all involved TAMs
                Controller controller = new Controller(coordinator,
                        i,
                        listOfDiscoveredTams.get(i * 3),
                        listOfDiscoveredTams.get(i * 3 + 1),
                        listOfDiscoveredTams.get(i * 3 + 2));

                // set controller only on a single tam so it gets executed only once
                // TODO: this is a bit of a hack
                listOfDiscoveredTams.get(i * 3).setController(controller);
            }
            log.info("===================================================");
            log.info(" Completed setup, press enter to start experiment! ");
            log.info("===================================================");
            try {
                System.in.read();
                log.info("Starting experiment! ");

                coordinator.setTamControllersEnabled(true);
            }
            catch (IOException e)
            {
            }
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
        ExperimentInterface experiment = new ProofOfConceptExperiment(coordinator);
        coordinator.setExperiment(experiment);

        // request shutdown after EXPERIMENT_DURATION_IN_SECONDS seconds
        coordinator.scheduleShutdown(ProofOfConceptExperiment.EXPERIMENT_DURATION_IN_SECONDS);
        coordinator.setupShutdownHandlers();

        // run the coordinator send and receive threads that handle all Xbee communication
        // NOTE: this will never return, so must be last!
        coordinator.runThreads();
    }
}
