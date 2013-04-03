package be.ac.ulb.iridia.tam.common.tasks;


/**
 * Condition for a transition. FSM checks evaluate() for all conditions of a
 * transition. The condition can store internal data which will be reset
 * when transition is executed by calling reset().
 */
public interface TransitionCondition
{
    /**
     * Returns true if condition is met.
     * @return true if condition is met
     */
    public boolean evaluate(Transition transition);

    /**
     * Resets possible internal values of the condition.
     * Called when transition execute.
     */
    public void reset(Transition transition);
}
