package be.ac.ulb.iridia.tam.user.experiments;

import be.ac.ulb.iridia.tam.common.coordinator.Coordinator;
import be.ac.ulb.iridia.tam.common.coordinator.ExperimentInterface;
import be.ac.ulb.iridia.tam.common.tam.TAM;
import be.ac.ulb.iridia.tam.user.controllers.TaskSequencingController;
import com.rapplogic.xbee.api.XBeeException;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class TaskSequencingExperiment implements ExperimentInterface
{
    private final static Logger log = Logger.getLogger(TaskSequencingExperiment.class);

    private final static long EXPERIMENT_DURATION_IN_SECONDS = 360000;

    // coordinator that handles all the network stuff
    Coordinator coordinator;


    /**
     * Create experiment.
     * @param coordinator  coordinator object
     */
    public TaskSequencingExperiment(Coordinator coordinator)
    {
        this.coordinator = coordinator;
    }

    /**
     * Called by the coordinator on regular intervals.
     * Can be used for management of TAMs etc.
     */
    public void step()
    {
    }

    public void attachTAMController(TAM tam)
    {
        // create new controller for a tam
        log.info("Creating new Controller for " + tam.getId());
        if(tam.getId().equals("TAM04")){
        	tam.setController(new TaskSequencingController(coordinator, tam, 1));
        	log.info("Controller for TAM04 created");
        } else if(tam.getId().equals("TAM05")){
        	tam.setController(new TaskSequencingController(coordinator, tam, 2));
        	log.info("Controller for TAM05 created");
        } else if(tam.getId().equals("TAM06")){
        	tam.setController(new TaskSequencingController(coordinator, tam, 3));
        	log.info("Controller for TAM06 created");
        } 
//        else if(tam.getId().equals("TAM04")){
//        	tam.setController(new TaskSequencingController(coordinator, tam, 3));
//        	log.info("Controller for TAM04 created");
//        }

    }

    /**
     * Called by the coordinator after it shuts down the main loop.
     * Used to clean up, save files and, if required, shut down or switch off all TAMs.
     * @see Coordinator sendShutdownCommandToAllTAMs() and sendSwitchOffLedsCommandToAllTAMs()
     * @throws XBeeException
     */
    public void shutdownAction() throws XBeeException
    {
        log.fatal("Shutting down all TAMs...");
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
        Coordinator coordinator = new Coordinator("/dev/ttyUSB0", 9600);

        // create our experiment (see above)
        ExperimentInterface experiment = new TaskSequencingExperiment(coordinator);
        coordinator.setExperiment(experiment);

        // request shutdown after EXPERIMENT_DURATION_IN_SECONDS seconds
        coordinator.scheduleShutdown(TaskSequencingExperiment.EXPERIMENT_DURATION_IN_SECONDS);

        // run the coordinator send and receive threads that handle all Xbee communication
        // NOTE: this will never return, so must be last!
        coordinator.runThreads();
    }
}
