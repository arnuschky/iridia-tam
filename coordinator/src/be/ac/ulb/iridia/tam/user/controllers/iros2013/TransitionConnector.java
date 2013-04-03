package be.ac.ulb.iridia.tam.user.controllers.iros2013;

import org.apache.log4j.Logger;


/**
 * A transition connector allows to connect one transition to another. This
 * is done by adding an action to the "sending" transition, which validates
 * a condition of the "receiving" transition. State is preserved and reset
 * upon execution of the receiving transition.
 */
public class TransitionConnector implements TransitionCondition, TransitionAction
{
    private final static Logger log = Logger.getLogger(TransitionConnector.class);

    // name of connector, only used for debugging
    private String name;
    // state of connector, true if sending transition executed
    private boolean state;

    /**
     * Creates a new transition connector.
     * @param name   name for debugging
     * @param state  initial state
     */
    @SuppressWarnings("unused")
    public TransitionConnector(String name, boolean state)
    {
        this.name = name;
        this.state = state;
    }

    /**
     * Creates a new transition connector. State is initialized to false.
     * @param name   name for debugging
     */
    @SuppressWarnings("unused")
    public TransitionConnector(String name)
    {
        this(name, false);
    }

    /**
     * Creates a new transition connector. State is initialized to false.
     */
    @SuppressWarnings("unused")
    public TransitionConnector()
    {
        this("n/a", false);
    }

    /**
     * Called when the transition executes/switches.
     */
    public void execute(Transition transition)
    {
        log.debug("TransitionConnector of ["+transition+"] "+name+" executes");
        state = true;
    }

    /**
     * Returns true if condition is met.
     * @return true if condition is met
     */
    public boolean evaluate(Transition transition)
    {
        return state;
    }

    /**
     * Resets possible internal values of the condition.
     * Called when transition execute.
     */
    public void reset(Transition transition)
    {
        state = false;
    }
}
