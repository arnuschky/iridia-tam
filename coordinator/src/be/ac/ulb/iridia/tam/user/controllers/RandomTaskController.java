package be.ac.ulb.iridia.tam.user.controllers;

import be.ac.ulb.iridia.tam.common.AbstractController;
import be.ac.ulb.iridia.tam.common.LedColor;
import be.ac.ulb.iridia.tam.common.TAMInterface;
import org.apache.log4j.Logger;
import java.util.TimerTask;


/**
 * This example controller implements a TAM that is independent of other TAMs.
 * The TAM can represent one of two tasks, BLUE or GREEN, each running with a different
 * duration. The controller sets a random task when the TAM is free.
 */
public class RandomTaskController extends AbstractController
{
    private final static Logger log = Logger.getLogger(RandomTaskController.class);

    // duration of the green task, in milliseconds
    public static final long GREEN_TASK_DURATION = 5 * 1000;
    // duration of the blue task, in milliseconds
    public static final long BLUE_TASK_DURATION = 20 * 1000;
    // dead time during which the TAM is switched off after the robot left, in milliseconds
    public static final long DEAD_TIME_DURATION = 10 * 1000;

    // colors of the RGB leds used (0x19 max value to keep eyes safe)
    public final static LedColor LED_RED   = new LedColor(0x19000000);
    public final static LedColor LED_GREEN = new LedColor(0x00190000);
    public final static LedColor LED_BLUE  = new LedColor(0x00001900);
    public final static LedColor LED_OFF   = new LedColor(0x00000000);

    // possible states of the FSM that controls the TAM
    enum TAMState
    {
        NEW_DISCOVERED,
        AVAILABLE,
        DEAD_TIME,
        TASK_FINISHED,
        WORKING_GREEN,
        WORKING_BLUE,
        ANNOUNCE_GREEN,
        ANNOUNCE_BLUE
    }

    // TAM this controller is attached two
    private TAMInterface tam;
    // current state of the FSM that controls the TAM
    private TAMState currentState;
    // current task duration timer (we need the reference to cancel the timer when the robot aborts the task)
    private TimerTask currentTaskDurationTimer;


    /**
     * Sets up the controller.
     * @param randomSeed   seed for the prng
     * @param tam          TAM this controller should be attached to
     */
    public void init(long randomSeed, TAMInterface tam)
    {
        super.init(randomSeed);
        this.tam = tam;

        log.info("New TAM controller, starting in task NEW_DISCOVERED");
        this.currentState = TAMState.NEW_DISCOVERED;
        this.currentTaskDurationTimer = null;
    }

    /**
     * Step function of the controller. Called every Coordinator.STEP_INTERVAL milliseconds.
     */
    public void step()
    {
        log.debug("============== Stepping "+tam.getId()+" currentState "+currentState+ " ================");
        switch (getState())
        {
            /*
             * This state is active when the TAM was newly discovered.
             * Used to switch off the LEDs of the TAM.
             * We remain in this state until we receive at least one status report from the TAM.
             */
            case NEW_DISCOVERED:
                if (tam.getLedColor() == null || !tam.getLedColor().equals(LED_OFF))
                {
                    tam.setLedColor(LED_OFF);
                }
                else
                {
                    setState(TAMState.AVAILABLE);
                }
                break;

            /*
             * This state is only used to randomly select a new task.
             * We switch to the READY state for that state directly afterwards
             */
            case AVAILABLE:
                if (getPrng().nextInt(2) == 0)
                {
                    log.info(tam.getId() + ": Announcing green task");
                    setState(TAMState.ANNOUNCE_GREEN);
                }
                else
                {
                    log.info(tam.getId() + ": Announcing blue task");
                    setState(TAMState.ANNOUNCE_BLUE);
                }
                break;

            /*
             * State used to announce the presence of a green task.
             * We switch out of this task when a robot enters the TAM.
             */
            case ANNOUNCE_GREEN:
                // set leds according to state (ignored if done already)
                if (tam.getLedColor() == null || !tam.getLedColor().equals(LED_GREEN))
                {
                    tam.setLedColor(LED_GREEN);
                }
                // robot starts to work
                else if (tam.isRobotPresent())
                {
                    log.info(tam.getId() + ": Robot starts to work on green task");
                    setState(TAMState.WORKING_GREEN);
                }
                break;

            /*
             * State used to announce the presence of a blue task.
             * We switch out of this task when a robot enters the TAM.
             */
            case ANNOUNCE_BLUE:
                // set leds according to state (ignored if done already)
                if (!tam.getLedColor().equals(LED_BLUE))
                {
                    tam.setLedColor(LED_BLUE);
                }
                // robot starts to work
                else if (tam.isRobotPresent())
                {
                    log.info(tam.getId() + ": Robot starts to work on blue task");
                    setState(TAMState.WORKING_BLUE);
                }
                break;

            /*
             * Robots works on green task.
             * We leave this state either when the robots aborts the task (= leaves the TAM)
             * or the task is finished (timer set up in setState())
             */
            case WORKING_GREEN:
                // set leds according to state (ignored if done already)
                // or at least wait for it
                if (!tam.getLedColor().equals(LED_RED))
                {
                    tam.setLedColor(LED_RED);
                }
                else
                {
                    // robot left, task failed, dead time
                    if (!tam.isRobotPresent())
                    {
                        log.info(tam.getId() + ": Robot aborted green task, starting dead time");
                        currentTaskDurationTimer.cancel();
                        currentTaskDurationTimer = null;
                        setState(TAMState.DEAD_TIME);
                    }
                }
                break;

            /*
             * Robots works on blue task.
             * We leave this state either when the robots aborts the task (= leaves the TAM)
             * or the task is finished (timer set up in setState())
             */
            case WORKING_BLUE:
                // set leds according to state (ignored if done already)
                // or at least wait for it
                if (!tam.getLedColor().equals(LED_RED))
                {
                    tam.setLedColor(LED_RED);
                }
                else
                {
                    // robot left, task failed, dead time
                    if (!tam.isRobotPresent())
                    {
                        log.info(tam.getId() + ": Robot aborted blue task, starting dead time");
                        currentTaskDurationTimer.cancel();
                        currentTaskDurationTimer = null;
                        setState(TAMState.DEAD_TIME);
                    }
                }
                break;

            /*
             * We enter this state after the task as finished.
             * We switch the leds off and wait for the robot to leave.
             * Follow up state is DEAD_TIME.
             */
            case TASK_FINISHED:
                // set leds according to state (ignored if done already)
                if (!tam.getLedColor().equals(LED_OFF))
                {
                    tam.setLedColor(LED_OFF);
                }
                else
                {
                    // robot left, task failed, dead time
                    if (!tam.isRobotPresent())
                    {
                        log.info(tam.getId() + ": Robot left, starting dead time");
                        setState(TAMState.DEAD_TIME);
                    }
                }
                break;

            /*
             * This state is the dead time in which the TAM remains switched
             * off for some time after the robot left.
             * We leave this state by timer, set up by the setState() method
             */
            case DEAD_TIME:
                // set leds according to state (ignored if done already)
                if (!tam.getLedColor().equals(LED_OFF))
                {
                    tam.setLedColor(LED_OFF);
                }
                break;
        }

        log.debug("==============================");
    }

    /**
     * This method sets the current state of the TAM.
     * If we switch to certain states, this method also sets up a timer that switches out of
     * that state automatically after a certain time.
     * @param state  new state
     */
    public void setState(TAMState state)
    {
        log.info(tam.getId() + ": Setting new state "+state);
        currentState = state;

        // dead time timer setup
        if (state == TAMState.DEAD_TIME)
        {
            getTimer().schedule(new TimerTask()
            {
                @Override
                public void run()
                {
                    log.info(tam.getId() + ": Dead time is over");
                    setState(TAMState.AVAILABLE);
                }
            }, DEAD_TIME_DURATION);
        }
        // green task duration timer setup
        else if (state == TAMState.WORKING_GREEN)
        {
            // we set the currentTaskDurationTimer so that we can cancel the timer when the task was aborted
            currentTaskDurationTimer = new TimerTask()
            {
                @Override
                public void run()
                {
                    currentTaskDurationTimer = null;
                    log.info(tam.getId() + ": Robot finishes green task");
                    setState(TAMState.TASK_FINISHED);
                }
            };
            getTimer().schedule(currentTaskDurationTimer, GREEN_TASK_DURATION);
        }
        // blue task duration timer setup
        else if (state == TAMState.WORKING_BLUE)
        {
            // we set the currentTaskDurationTimer so that we can cancel the timer when the task was aborted
            currentTaskDurationTimer = new TimerTask()
            {
                @Override
                public void run()
                {
                    currentTaskDurationTimer = null;
                    log.info(tam.getId() + ": Robot finishes blue task");
                    setState(TAMState.TASK_FINISHED);
                }
            };
            getTimer().schedule(currentTaskDurationTimer, BLUE_TASK_DURATION);
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

    /**
     * Returns a String representation of this controller.
     * @return String representation of this controller
     */
    @Override
    public String toString()
    {
        return "RandomTaskController{tam=" + tam.getId() +
                ", currentState=" + currentState + '}';
    }
}