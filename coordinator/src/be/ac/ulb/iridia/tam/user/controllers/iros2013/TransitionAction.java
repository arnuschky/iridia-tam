package be.ac.ulb.iridia.tam.user.controllers.iros2013;


/**
 * Action that gets executed when transition executes/switches.
 * A transition can have multiple actions.
 */
public interface TransitionAction
{
    /**
     * Called when the transition executes/switches.
     */
    public void execute(Transition transition);
}
