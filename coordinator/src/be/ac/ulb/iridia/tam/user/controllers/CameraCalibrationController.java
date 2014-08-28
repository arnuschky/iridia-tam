package be.ac.ulb.iridia.tam.user.controllers;

import be.ac.ulb.iridia.tam.common.AbstractController;
import be.ac.ulb.iridia.tam.common.LedColor;
import be.ac.ulb.iridia.tam.common.TAMInterface;
import org.apache.log4j.Logger;


/**
 * This controller is used to calibrate the camera of the epuck robots
 * The controller keeps the LEDs of the TAM on with a red color.
 */
public class CameraCalibrationController extends AbstractController
{
    // colors of the RGB leds used (0x19 max value to keep eyes safe)
    public final static LedColor LED_RED   = new LedColor(0x19000000);
    public final static LedColor LED_GREEN = new LedColor(0x00190000);
    public final static LedColor LED_BLUE  = new LedColor(0x00001900);
    public final static LedColor LED_OFF   = new LedColor(0x00000000);
	
    private final static Logger log = Logger.getLogger(CameraCalibrationController.class);

    enum TAMState
    {
    	LEDS_ON,
    }

    // current state of the FSM that controls the TAM
    private TAMState currentState;
    // TAM this controller is attached two
    private TAMInterface tam;

    /**
     * Sets up the controller.
     * @param randomSeed   seed for the prng
     * @param tam          TAM this controller should be attached to
     */
    public void init(long randomSeed, TAMInterface tam)
    {
        super.init(randomSeed);
        this.tam = tam;
        this.currentState = TAMState.LEDS_ON;

        log.info("New TAM controller, starting in task LEDS_ON");
    }

    /**
     * Step function of the controller. Called every Coordinator.STEP_INTERVAL milliseconds.
     */
    public void step()
    {
        log.debug("============== Stepping "+tam.getId()+" currentState "+currentState+ " ================");
        switch (getState())
        {

            case LEDS_ON:
                if (tam.getLedColor() == null && tam.getLedColor().equals(LED_RED))
                {
                    tam.setLedColor(LED_RED);
                }
                break;
        }
    }
    
    /**
     * Returns the current state of the TAM.
     * @return state of TAM as enum TAMState
     */
    public TAMState getState()
    {
        return currentState;
    }
}