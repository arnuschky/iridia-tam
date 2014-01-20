package be.ac.ulb.iridia.tam.user.experiments;

import be.ac.ulb.iridia.tam.common.coordinator.Coordinator;
import be.ac.ulb.iridia.tam.common.coordinator.ExperimentInterface;
import be.ac.ulb.iridia.tam.common.tam.TAM;
import be.ac.ulb.iridia.tam.common.tasks.ConcurrentTask;
import be.ac.ulb.iridia.tam.user.controllers.AbstractTaskController;
import com.rapplogic.xbee.api.XBeeException;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import java.util.TimerTask;

/**
 * This example experiment is a two-task random task-type experiment. Each TAM can
 * represent one of two tasks, BLUE or GREEN, each running with a different duration.
 * The controller sets a random task when the TAM is free.
 * @see be.ac.ulb.iridia.tam.user.controllers.TwoRandomTasksController
 */
public class ConcurrentTasksExperiment implements ExperimentInterface
{
    private final static Logger log = Logger.getLogger(ConcurrentTasksExperiment.class);

    private final static long EXPERIMENT_DURATION_IN_SECONDS = 3600;

    // coordinator that handles all the network stuff
    private Coordinator coordinator;

    private ConcurrentTask concurrentTask;


    /**
     * Create experiment.
     * @param coordinator  coordinator object
     */
    public ConcurrentTasksExperiment(Coordinator coordinator)
    {
        this.coordinator = coordinator;
        this.concurrentTask = new ConcurrentTask();
    }

    /**
     * Called by the coordinator on regular intervals.
     * Can be used for management of TAMs etc.
     */
    public void step()
    {
    }

    /**
     * Called by the coordinator to attach controllers to newly discovered TAMs.
     * You can use the id or address of the TAM to attach specific controllers
     * to specific TAMs, thereby giving them the different functionality.
     * @param tam  TAM the coordinator requests a controller for
     */
    public void attachTAMController(TAM tam)
    {
        // create new controller for a tam
        log.fatal("Creating new AbstractTaskController for " + tam.getId());
        AbstractTaskController newSubTask = new AbstractTaskController(coordinator, tam);
        tam.setController(newSubTask);
        concurrentTask.addSubtask(newSubTask);

        log.fatal("Added new concurrent task, total is " + concurrentTask.getNumSubTasks());
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
        ExperimentInterface experiment = new ConcurrentTasksExperiment(coordinator);
        coordinator.setExperiment(experiment);

        // request shutdown after EXPERIMENT_DURATION_IN_SECONDS seconds
        coordinator.scheduleShutdown(ConcurrentTasksExperiment.EXPERIMENT_DURATION_IN_SECONDS);

        // run the coordinator send and receive threads that handle all Xbee communication
        // NOTE: this will never return, so must be last!
        coordinator.runThreads();
    }
}
