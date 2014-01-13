package be.ac.ulb.iridia.tam.user.experiments;

import be.ac.ulb.iridia.tam.common.coordinator.Coordinator;
import be.ac.ulb.iridia.tam.common.coordinator.ExperimentInterface;
import be.ac.ulb.iridia.tam.common.tam.TAM;
import be.ac.ulb.iridia.tam.user.controllers.CameraCalibrationController;
import com.rapplogic.xbee.api.XBeeException;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import java.util.TimerTask;

/**
 * This is a test experiment to calibrate the camera of the epuck robots.
 * The controller simply keeps the LEDs of the TAM on with a red color.
 * @see CameraCalibrationController
 */
public class CameraCalibrationExperiment implements ExperimentInterface
{
    private final static Logger log = Logger.getLogger(CameraCalibrationExperiment.class);

    private final static long EXPERIMENT_DURATION_IN_SECONDS = 600000000;

    // coordinator that handles all the network stuff
    Coordinator coordinator;


    /**
     * Create experiment.
     * @param coordinator  coordinator object
     */
    public CameraCalibrationExperiment(Coordinator coordinator)
    {
        this.coordinator = coordinator;
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
        log.info("Creating new CameraCalibrationController for " + tam.getId());
        tam.setController(new CameraCalibrationController(coordinator, tam));
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
        coordinator.sendShutdownCommandToAllTAMs();
//        coordinator.sendSwitchOffLedsCommandToAllTAMs();
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
        ExperimentInterface experiment = new CameraCalibrationExperiment(coordinator);
        coordinator.setExperiment(experiment);

        // request shutdown after EXPERIMENT_DURATION_IN_SECONDS seconds
        final Coordinator finalCoordinator = coordinator;
        coordinator.getTimer().schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                log.fatal("Shutdown of experiment requested.");
                finalCoordinator.setShutdownRequested(true);
            }
        }, CameraCalibrationExperiment.EXPERIMENT_DURATION_IN_SECONDS * 1000);

        // run the coordinator send and receive threads that handle all Xbee communication
        // NOTE: this will never return, so must be last!
        coordinator.runThreads();
    }
}
