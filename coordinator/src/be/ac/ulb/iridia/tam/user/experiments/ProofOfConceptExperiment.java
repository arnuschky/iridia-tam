package be.ac.ulb.iridia.tam.user.experiments;

import be.ac.ulb.iridia.tam.common.AbstractExperiment;
import be.ac.ulb.iridia.tam.common.TAMComparator;
import be.ac.ulb.iridia.tam.coordinator.Coordinator;
import be.ac.ulb.iridia.tam.common.ExperimentInterface;
import be.ac.ulb.iridia.tam.common.TAMInterface;
import be.ac.ulb.iridia.tam.user.controllers.proofofconcept.Controller;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TimerTask;


/**
 *
 */
public class ProofOfConceptExperiment extends AbstractExperiment
{
    private final static Logger log = Logger.getLogger(ProofOfConceptExperiment.class);

    private final static long EXPERIMENT_DURATION_IN_SECONDS = 3600;

    private final static long NUMBER_OF_TASK_INSTANCES = 6;

    private ArrayList<TAMInterface> listOfDiscoveredTams;


    /**
     * Create experiment.
     */
    public ProofOfConceptExperiment()
    {
        this.listOfDiscoveredTams = new ArrayList<TAMInterface>();
    }

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
        if (listOfDiscoveredTams.size() == 3)
            return;

        // create new standard stand-alone controller for a tam
        listOfDiscoveredTams.add(tam);
        log.info("Discovered a new "+tam.getId()+", total number is " + listOfDiscoveredTams.size() +
                " of " + (NUMBER_OF_TASK_INSTANCES * 3) + " required.");

        if (listOfDiscoveredTams.size() == NUMBER_OF_TASK_INSTANCES * 3)
        {
            // sort the list of discovered TAMs by ID
            Collections.sort(listOfDiscoveredTams, new TAMComparator());

            // now, build a task instance by using the unused TAMs with the lowest IDs, in order
            for (int i = 0; i < NUMBER_OF_TASK_INSTANCES; i++) {
                log.info("Building instance " + (i + 1));

                // create a new centralized controller for all involved TAMs
                Controller controller = new Controller();
                controller.init(getPrng().nextInt(),
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
                this.setReady();
            }
            catch (IOException e)
            {
            }
        }
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
        ExperimentInterface experiment = new ProofOfConceptExperiment();
        experiment.init(System.currentTimeMillis());
        coordinator.setExperiment(experiment);

        // run the coordinator send and receive threads that handle all Xbee communication
        // NOTE: this will never return, so must be last!
        coordinator.start();
    }
}
