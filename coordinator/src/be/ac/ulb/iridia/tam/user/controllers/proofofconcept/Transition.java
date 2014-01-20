package be.ac.ulb.iridia.tam.user.controllers.proofofconcept;

import org.apache.log4j.Logger;

import java.util.ArrayList;


/**
 * This is a transition between the states of a *single* task.
 * A transition can have multiple conditions and actions attached to it.
 * Conditions are evaluated to decide if the transition can be executed
 * (that is, switching from one state to another). The default behavior
 * when no condition (whether the transition should "fall-through" or
 * not) is configurable. If the transition executes, the attached
 * actions will be executed as well. This allows to set up petri-net
 * like transitions that store values until read or reset.
 */
public class Transition
{
    private final static Logger log = Logger.getLogger(Transition.class);

    // task this transition is attached to
    private Controller task;
    // state this transition is coming from
    private Controller.State fromState;
    // state this transition is going to
    private Controller.State toState;
    // list of conditions for transition to execute
    private ArrayList<TransitionCondition> conditions;
    // list of actions that will be executed on switch
    private ArrayList<TransitionAction> actions;

    // possible types of this transition
    enum Type
    {
        AND,
        OR
    }
    // The type of a transition describes how multiple conditions are
    // combined when evaluating if this transition can execute or not
    private Type type;

    // true if this transition
    private boolean fallThrough;


    /**
     * Creates a new transition.
     * @param task         task this transition is attached to
     * @param fromState    transition leading from this state
     * @param toState      transition going to this state
     * @param type         type of transition
     * @param fallThrough  true if this transition should switch if there are no conditions
     */
    public Transition(Controller task, Controller.State fromState, Controller.State toState,
                      Type type, boolean fallThrough)
    {
        this.task = task;
        this.fromState = fromState;
        this.toState = toState;
        this.type = type;
        this.fallThrough = fallThrough;

        this.conditions = new ArrayList<TransitionCondition>();
        this.actions = new ArrayList<TransitionAction>();

//        log.debug("Adding "+this);
    }

    /**
     * Creates a new transition of type AND.
     * @param task         task this transition is attached to
     * @param fromState    transition leading from this state
     * @param toState      transition going to this state
     * @param fallThrough  true if this transition should switch if there are no conditions
     */
    @SuppressWarnings("unused")
    public Transition(Controller task, Controller.State fromState, Controller.State toState, boolean fallThrough)
    {
        this(task, fromState, toState, Type.AND, fallThrough);
    }

    /**
     * Creates a new transition of type AND.
     * @param task         task this transition is attached to
     * @param fromState    transition leading from this state
     * @param toState      transition going to this state
     */
    @SuppressWarnings("unused")
    public Transition(Controller task, Controller.State fromState, Controller.State toState)
    {
        this(task, fromState, toState, Type.AND, true);
    }

    /**
     * Adds a condition to the list.
     * @param condition  condition that must evaluate to true before being able to execute transition
     * @return true if adding was successful
     */
    public boolean addCondition(TransitionCondition condition)
    {
        return conditions.add(condition);
    }

    /**
     * Removes a condition from the list.
     * @param condition  condition to remove
     * @return true if removal was successful
     */
    @SuppressWarnings("unused")
    public boolean removeCondition(TransitionCondition condition)
    {
        return conditions.remove(condition);
    }

    /**
     * Returns the list of all conditions.
     * @return list of all conditions
     */
    @SuppressWarnings("unused")
    public ArrayList<TransitionCondition> getConditions()
    {
        return conditions;
    }

    /**
     * Adds an action to the list.
     * @param action  action that will be called when the transition executes
     * @return true if adding was successful
     */
    public boolean addAction(TransitionAction action)
    {
        return actions.add(action);
    }

    /**
     * Removes a action from the list.
     * @param action  action to remove
     * @return true if removal was successful
     */
    @SuppressWarnings("unused")
    public boolean removeAction(TransitionAction action)
    {
        return actions.remove(action);
    }

    /**
     * Returns the list of all actions.
     * @return list of all actions
     */
    @SuppressWarnings("unused")
    public ArrayList<TransitionAction> getActions()
    {
        return actions;
    }

    /**
     * Checks all transition conditions and returns true if all conditions evaluate
     * to true. If there are no conditions, fallThrough will be returned.
     * @return true if all conditions evaluate to true.
     */
    public boolean canExecute()
    {
        // if there are no conditions to this transition, we can always execute
        if (conditions.size() == 0)
        {
            return fallThrough;
        }

        boolean result = false;
        for (TransitionCondition condition : conditions)
        {
            // if type is AND, a single transition that evaluates to false ends the method
            if (!condition.evaluate(this))
            {
                if (type == Type.AND)
                    return false;

//                log.debug(this + " condition '" + condition.name + "' evaluates to false");
            }
            else
            {
                result = true;
            }
        }

        // if all/one conditions evaluated as true, we can execute
        return result;
    }

    /**
     * Executes transition. Resets all conditions and executes all actions
     * in the process. Returns true if execution was successful.
     * @return true if execution was successful
     */
    public boolean execute()
    {
        if (type == Type.AND && canExecute())
        {
            log.debug(this + " executes.");

            // reset all conditions of the transition
            for (TransitionCondition condition : conditions)
            {
                condition.reset(this);
            }
            // execute all actions of the transition
            for (TransitionAction action : actions)
            {
                action.execute(this);
            }

            return true;
        }
        else if (type == Type.OR && canExecute())
        {
            log.debug(this + " executes.");

            // reset all conditions of the transition that evaluate to true
            for (TransitionCondition condition : conditions)
            {
                if (condition.evaluate(this))
                {
                    condition.reset(this);
                }
            }
            // execute all actions of the transition
            for (TransitionAction action : actions)
            {
                action.execute(this);
            }

            return true;
        }

        return false;
    }

    /**
     * Resets all conditions attached to this transitions.
     */
    @SuppressWarnings("unused")
    public void resetAllConditions()
    {
        for (TransitionCondition condition : conditions)
        {
            condition.reset(this);
        }
    }

    /**
     * Return the state this transition is coming from.
     * @return state this transition is coming from.
     */
    public Controller.State getFromState()
    {
        return fromState;
    }

    /**
     * Return the state this transition is leading to.
     * @return state this transition is leading to.
     */
    public Controller.State getToState()
    {
        return toState;
    }

    /**
     * Returns a string representation of this transition.
     * @return string representation of this transition
     */
    @Override
    public String toString()
    {
        return "transition of "+task+" ("+getFromState()+"->"+getToState()+')';
    }
}
