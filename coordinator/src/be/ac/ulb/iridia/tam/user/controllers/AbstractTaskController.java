package be.ac.ulb.iridia.tam.user.controllers;

import be.ac.ulb.iridia.tam.common.coordinator.Coordinator;
import be.ac.ulb.iridia.tam.common.tasks.AbstractTask;
import be.ac.ulb.iridia.tam.common.tam.ControllerInterface;
import be.ac.ulb.iridia.tam.common.tam.LedColor;
import be.ac.ulb.iridia.tam.common.tam.TAM;
import be.ac.ulb.iridia.tam.common.tasks.Transition;
import be.ac.ulb.iridia.tam.common.tasks.TransitionAction;
import be.ac.ulb.iridia.tam.common.tasks.TransitionCondition;
import org.apache.log4j.Logger;

import java.util.TimerTask;

/**
 * This controller maps the behavior of a TAM to an abstract task.
 * All the task-related logic comes from the task and its (complex)
 * dependencies with other tasks.
 *
 * The TAM can take 3 colors: GREEN if free, RED if busy (robot is
 * working) and BLUE if the robot needs to wait).
 */
public class AbstractTaskController extends AbstractTask implements ControllerInterface
{
    private final static Logger log = Logger.getLogger(AbstractTaskController.class);

    // duration of the task, in milliseconds
    public static final long WORKING_DURATION = 10 * 1000;
    // dead time during which the TAM is switched off after the robot left, in milliseconds
    public static final long DEAD_TIME_DURATION = 5 * 1000;

    // colors of the RGB leds used (0x19 max value to keep eyes safe)
    public final static LedColor LED_WORKING   = new LedColor(0x19000000);  // red
    public final static LedColor LED_AVAILABLE = new LedColor(0x00190000);  // green
    public final static LedColor LED_WAITING   = new LedColor(0x00001900);  // blue
    public final static LedColor LED_NO_TASK   = new LedColor(0x00000000);  // off/black

    // coordinator
    private Coordinator coordinator;
    // TAM this controller is attached to
    private TAM tam;
    // current task duration timer (we need the reference to cancel the timer when the robot aborts the task)
    private TimerTask taskDurationTimer;
    // timer for switching the TAM of for a certain amount of time after completion of task
    private TimerTask deadTimeTimer;


    /**
     * Sets up the controller.
     * @param coordinator  coordinator that handles the networking
     * @param tam          TAM this controller should be attached to
     */
    public AbstractTaskController(Coordinator coordinator, final TAM tam)
    {
        this.coordinator = coordinator;
        this.tam = tam;

        log.info("New TAM controller, starting in task "+ getState());
        this.taskDurationTimer = null;
        this.deadTimeTimer = null;

        /*
         * State IDLE
         */
        getTransition(State.IDLE, State.AVAILABLE).addCondition(new TransitionCondition()
        {
            public boolean evaluate(Transition transition)
            {
                return deadTimeTimer == null;
            }

            public void reset(Transition transition) {}
        });
        getTransition(State.IDLE, State.AVAILABLE).addAction(new TransitionAction()
        {
            public void execute(Transition transition)
            {
                log.info(tam.getId() + ": Announcing task");
            }
        });

        /*
         * State AVAILABLE
         */
        // we make the transition to WAIT_BEFORE only if a robot is present
        getTransition(State.AVAILABLE, State.WAIT_BEFORE).addCondition(new TransitionCondition()
        {
            public boolean evaluate(Transition transition)
            {
                return tam.isRobotPresent();
            }
            public void reset(Transition transition) {}
        });
        getTransition(State.AVAILABLE, State.WAIT_BEFORE).addAction(new TransitionAction()
        {
            public void execute(Transition transition)
            {
                log.info(tam.getId() + ": Robot starts to wait before working on task");
            }
        });

        /*
         * State WAIT_BEFORE
         */
        // we make the transition to WORKING if the robot is still present
        getTransition(State.WAIT_BEFORE, State.WORKING).addCondition(new TransitionCondition()
        {
            public boolean evaluate(Transition transition)
            {
                return tam.isRobotPresent();
            }
            public void reset(Transition transition) {}
        });

        getTransition(State.WAIT_BEFORE, State.WORKING).addAction(new TransitionAction()
        {
            public void execute(Transition transition)
            {
                log.info(tam.getId() + ": Robot starts to work on task");

                // we set the taskDurationTimer so that we can cancel the timer when the task was aborted
                taskDurationTimer = new TimerTask()
                {
                    @Override
                    public void run()
                    {
                        log.info(tam.getId() + ": Task working time is over");
                        taskDurationTimer = null;
                    }
                };
                getCoordinator().getTimer().schedule(taskDurationTimer, WORKING_DURATION);
            }
        });
        // we make the transition to FAIL if the robot left
        getTransition(State.WAIT_BEFORE, State.FAIL).addCondition(new TransitionCondition()
        {
            public boolean evaluate(Transition transition)
            {
                return !tam.isRobotPresent();
            }
            public void reset(Transition transition) {}
        });
        getTransition(State.WAIT_BEFORE, State.FAIL).addAction(new TransitionAction()
        {
            public void execute(Transition transition)
            {
                log.info(tam.getId() + ": Robot aborted task during wait before");
                if (taskDurationTimer != null)
                {
                    taskDurationTimer.cancel();
                    taskDurationTimer = null;
                }
            }
        });

        /*
         * State WORKING
         */
        // we make the transition to WAIT_AFTER if the robot is still present
        getTransition(State.WORKING, State.WAIT_AFTER).addCondition(new TransitionCondition()
        {
            public boolean evaluate(Transition transition)
            {
                return tam.isRobotPresent() && taskDurationTimer == null;
            }
            public void reset(Transition transition) {}
        });
        getTransition(State.WORKING, State.WAIT_AFTER).addAction(new TransitionAction()
        {
            public void execute(Transition transition)
            {
                log.info(tam.getId() + ": Robot starts to wait after working on task");
            }
        });
        // we make the transition to FAIL if the robot left
        getTransition(State.WORKING, State.FAIL).addCondition(new TransitionCondition()
        {
            public boolean evaluate(Transition transition)
            {
                return !tam.isRobotPresent();
            }
            public void reset(Transition transition) {}
        });
        getTransition(State.WORKING, State.FAIL).addAction(new TransitionAction()
        {
            public void execute(Transition transition)
            {
                log.info(tam.getId() + ": Robot aborted task during working");
                if (taskDurationTimer != null)
                {
                    taskDurationTimer.cancel();
                    taskDurationTimer = null;
                }
            }
        });

        /*
         * State WAIT_AFTER
         */
        // we make the transition to WORKING if the robot is still present
        getTransition(State.WAIT_AFTER, State.SUCCESS).addCondition(new TransitionCondition()
        {
            public boolean evaluate(Transition transition)
            {
                return tam.isRobotPresent();
            }
            public void reset(Transition transition) {}
        });
        getTransition(State.WAIT_AFTER, State.SUCCESS).addAction(new TransitionAction()
        {
            public void execute(Transition transition)
            {
                log.info(tam.getId() + ": Task is successfully completed, robot has to leave");
            }
        });

        // we make the transition to FAIL if the robot left
        getTransition(State.WAIT_AFTER, State.FAIL).addCondition(new TransitionCondition()
        {
            public boolean evaluate(Transition transition)
            {
                return !tam.isRobotPresent();
            }
            public void reset(Transition transition) {}
        });
        getTransition(State.WAIT_AFTER, State.FAIL).addAction(new TransitionAction()
        {
            public void execute(Transition transition)
            {
                log.info(tam.getId() + ": Robot aborted task during wait after");
            }
        });

        /*
         * State SUCCESS
         */
        // we make the transition to IDLE if the robot left
        getTransition(State.SUCCESS, State.IDLE).addCondition(new TransitionCondition()
        {
            public boolean evaluate(Transition transition)
            {
                return !tam.isRobotPresent();
            }
            public void reset(Transition transition) {}
        });
        getTransition(State.SUCCESS, State.IDLE).addAction(new TransitionAction()
        {
            public void execute(Transition transition)
            {
                log.info(tam.getId() + ": Robot left, starting dead time");

                deadTimeTimer = new TimerTask()
                {
                    @Override
                    public void run()
                    {
                        log.info(tam.getId() + ": Dead time is over");
                        deadTimeTimer = null;
                    }
                };
                getCoordinator().getTimer().schedule(deadTimeTimer, DEAD_TIME_DURATION);
            }
        });

        /*
         * State FAIL
         */
        // we make the transition to IDLE if the robot left
        getTransition(State.FAIL, State.IDLE).addCondition(new TransitionCondition()
        {
            public boolean evaluate(Transition transition)
            {
                return !tam.isRobotPresent();
            }
            public void reset(Transition transition) {}
        });
        getTransition(State.FAIL, State.IDLE).addAction(new TransitionAction()
        {
            public void execute(Transition transition)
            {
                log.info(tam.getId() + ": Task failed!");
            }
        });
    }

    /**
     * Step function of the controller. Called every Coordinator.STEP_INTERVAL milliseconds.
     */
    public void step()
    {
        // step the FSM of the abstract task we're associated with
        super.step();

//        log.debug("============== Stepping "+tam.getId()+" task state "+getState()+ " ================");

        // set LEDs according to state
        switch (getState())
        {
            case SUCCESS:
            case FAIL:
            case IDLE:
                if (tam.getLedColorLastUpdated() == 0 || !tam.getLedColor().equals(LED_NO_TASK))
                {
                    getCoordinator().sendSetLedsCommand(tam, LED_NO_TASK);
                }
                break;

            case AVAILABLE:
                if (!tam.getLedColor().equals(LED_AVAILABLE))
                {
                    getCoordinator().sendSetLedsCommand(tam, LED_AVAILABLE);
                }
                break;

            case WAIT_BEFORE:
                if (!tam.getLedColor().equals(LED_WAITING))
                {
                    getCoordinator().sendSetLedsCommand(tam, LED_WAITING);
                }
                break;

            case WORKING:
                if (!tam.getLedColor().equals(LED_WORKING))
                {
                    getCoordinator().sendSetLedsCommand(tam, LED_WORKING);
                }
                break;

            case WAIT_AFTER:
                if (!tam.getLedColor().equals(LED_WAITING))
                {
                    getCoordinator().sendSetLedsCommand(tam, LED_WAITING);
                }
                break;
        }

//        log.debug("==============================");
    }

    /**
     * Returns the associated coordinator.
     * @return coordinator that runs the show
     */
    public Coordinator getCoordinator()
    {
        return coordinator;
    }

    /**
     * Returns a String representation of this controller.
     * @return String representation of this controller
     */
    @Override
    public String toString()
    {
        return "AbstractTaskController{tam=" + tam.getId() +
                ", state=" + getState() + '}';
    }
}