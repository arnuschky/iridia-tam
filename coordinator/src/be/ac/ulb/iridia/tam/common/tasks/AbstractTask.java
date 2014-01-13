package be.ac.ulb.iridia.tam.common.tasks;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;


/**
 * An abstract task, which has a very flexible FSM. On it's own its the
 * simplest task possible with a linear progression through the states.
 * Complexity can be added by interconnecting the FSM of multiple tasks.
 *
 * By default, there are no conditions, that is the task will automatically
 * move through all states without stopping. You have to add conditions to
 * the different conditions in order to make it work. For example, add a
 * condition to switch  from AVAILABLE to WAIT_BEFORE when a robot moves
 * into the TAM.
 *
 * Note that there can be only one transition between two states.
 */
public class AbstractTask
{
    private final static Logger log = Logger.getLogger(AbstractTask.class);

    // supertask of this task, can be null
    protected AbstractTask supertask;

    // possible states of task, no others are possible
    public enum State
    {
        IDLE,
        AVAILABLE,
        WAIT_BEFORE,
        WORKING,
        WAIT_AFTER,
        SUCCESS,
        FAIL
    }
    // current state of task
    protected State state;

    // mapping between states and transitions
    protected HashMap<String, Transition> transitions;
    protected HashMap<State, ArrayList<Transition>> outgoingTransitions;
    protected HashMap<State, ArrayList<Transition>> incomingTransitions;


    /**
     * Creates an abstract task.
     * Sets up all state to transition maps and creates the basic transitions that
     * are possible in a task (no conditions yet).
     */
    public AbstractTask()
    {
        // transition maps
        transitions = new  HashMap<String, Transition>();
        outgoingTransitions = new HashMap<State, ArrayList<Transition>>();
        incomingTransitions = new HashMap<State, ArrayList<Transition>>();
        for (State state : State.values())
        {
            outgoingTransitions.put(state, new ArrayList<Transition>());
            incomingTransitions.put(state, new ArrayList<Transition>());
        }

        // all standard transitions of a task are just a linear chain
        addNewTransition(State.IDLE,        State.AVAILABLE);
        addNewTransition(State.AVAILABLE,   State.WAIT_BEFORE);
        addNewTransition(State.WAIT_BEFORE, State.WORKING);
        addNewTransition(State.WORKING,     State.WAIT_AFTER);
        addNewTransition(State.WAIT_AFTER,  State.SUCCESS);

        // FAIL transitions never fall through if there are no conditions
        // if there are multiple conditions, only one has to be true to trigger the transition
        addNewTransition(State.AVAILABLE,   State.FAIL, Transition.Type.OR, false);
        addNewTransition(State.WAIT_BEFORE, State.FAIL, Transition.Type.OR, false);
        addNewTransition(State.WORKING,     State.FAIL, Transition.Type.OR, false);
        addNewTransition(State.WAIT_AFTER,  State.FAIL, Transition.Type.OR, false);

        // end conditions: success of fail
        addNewTransition(State.SUCCESS, State.IDLE);
        addNewTransition(State.FAIL,    State.IDLE);

        state = State.IDLE;
    }

    /**
     * Returns the supertask of this task. Can return null if no
     * supertask set.
     * @return supertask if set, or null
     */
    @SuppressWarnings("unused")
    public AbstractTask getSupertask()
    {
        return supertask;
    }

    /**
     * Set the supertask of this task. A supertask has to be an
     * AbstractTask as well. Set null to remove supertask.
     * @param supertask  supertask to set
     */
    public void setSupertask(AbstractTask supertask)
    {
        this.supertask = supertask;
    }

    /**
     * Adds a new transition from a state to another. This is a helper method
     * that creates a new transition object and adds it to the appropriate maps.
     * @param fromState    transition from this state
     * @param toState      transition to this state
     * @param type         Transition.Type.AND if all conditions must be true to switch, Transition.Typ.OR if one is sufficient
     * @param fallThrough  set to true if the transition switches if no condition is set
     * @return new transition object
     */
    public Transition addNewTransition(State fromState, State toState, Transition.Type type, boolean fallThrough)
    {
        Transition transition = new Transition(this, fromState, toState, type, fallThrough);

        transitions.put(computeTransitionKey(fromState, toState), transition);
        incomingTransitions.get(toState).add(transition);
        outgoingTransitions.get(fromState).add(transition);

        return transition;
    }

    /**
     * Adds a new transition from a state to another. This is a helper method
     * that creates a new transition object and adds it to the appropriate maps.
     * The type will be set to Transition.Type.AND.
     * @param fromState    transition from this state
     * @param toState      transition to this state
     * @param fallThrough  set to true if the transition switches if no condition is set
     * @return new transition object
     */
    @SuppressWarnings("unused")
    public Transition addNewTransition(State fromState, State toState, boolean fallThrough)
    {
        return addNewTransition(fromState, toState, Transition.Type.AND, fallThrough);
    }

    /**
     * Adds a new transition from a state to another. This is a helper method
     * that creates a new transition object and adds it to the appropriate maps.
     * The type will be set to Transition.Type.AND and fall-through will be true.
     * @param fromState    transition from this state
     * @param toState      transition to this state
     * @return new transition object
     */
    public Transition addNewTransition(State fromState, State toState)
    {
        return addNewTransition(fromState, toState, Transition.Type.AND, true);
    }

    /**
     * Returns a transition between two states.
     * @param fromState  transition from this state
     * @param toState    transition to this state
     * @return transition, or null if does not exist
     */
    public Transition getTransition(State fromState, State toState)
    {
        return transitions.get(computeTransitionKey(fromState, toState));
    }

    /**
     * Returns the incoming transitions of a state.
     * @param toState  all transitions to this state
     * @return list of transitions (can be empty)
     */
    @SuppressWarnings("unused")
    public ArrayList<Transition> getIncomingTransitions(State toState)
    {
        return incomingTransitions.get(toState);
    }

    /**
     * Returns the outgoing transitions of a state.
     * @param fromState  all transitions from this state
     * @return list of transitions (can be empty)
     */
    @SuppressWarnings("unused")
    public ArrayList<Transition> getOutgoingTransitions(State fromState)
    {
        return outgoingTransitions.get(fromState);
    }

    /**
     * Returns the current state of this task.
     * @return current state as AbstractTask.State
     */
    public State getState()
    {
        return state;
    }

    /**
     * Sets a new state of this task.
     * @param newState  new state to set
     */
    // TODO: probably make this private
    public void setState(State newState)
    {
        if (newState != state)
        {
            log.debug(this + " state changed from " + state + " to " + newState);
            state = newState;
        }
    }

    /**
     * Checks and executes outgoing transitions of the current state.
     * First transition that will be found to be executable (whose
     * conditions are met) will be executed. This changes the current
     * state of the task.
     * @return true if state changed
     */
    private boolean checkAndExecuteTransitions()
    {
        // see if we can change the state
        for (Transition transition : outgoingTransitions.get(state))
        {
            if (transition.execute())
            {
                setState(transition.getToState());
                return true;
            }
        }
        return false;
    }

    /**
     * Computes the key for storing a transition in the map.
     * @param fromState  transition from this state
     * @param toState    transition to this state
     * @return key as string
     */
    private String computeTransitionKey(State fromState, State toState)
    {
        return fromState.toString()+">"+toState.toString();
    }

    /**
     * Steps the FSM of the task.
     * This method will step the FSM of the task, if possible by moving
     * through several states in one go. This method will additionally
     * step the FSM of the supertask if present.
     */
    public void step()
    {
        // step the task fsm that check conditions and switches state
        // if we changed state, execute again to see if we can fast-switch through multiple states
        while (checkAndExecuteTransitions()) {}

        // step the supertask
        // TODO: this will be called by all subtasks, so we should introduce a flag to prevent double-exec
        if (getSupertask() != null)
        {
            getSupertask().step();
        }
    }

    /**
     * Returns a String representation of this task.
     * @return String representation of this task
     */
    @Override
    public String toString()
    {
        return "AbstractTask{state=" + getState() + '}';
    }
}
