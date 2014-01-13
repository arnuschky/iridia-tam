package be.ac.ulb.iridia.tam.common.tasks;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.LinkedList;


/**
 * Sequential task: one task has to be completed after the other.
 */
public class SequentialTask extends AbstractTask
{
    private final static Logger log = Logger.getLogger(SequentialTask.class);

    // list of subtasks
    private LinkedList<AbstractTask> subtasks;

    // connector used to track the success of the *last* subtask
    private TransitionConnector successConnector;
    // connector used to track the failure of *any* subtask
    private TransitionConnector failConnector;
    // connectors used to track the idle state of the all subtasks
    private ArrayList<TransitionConnector> idleConnectors;

    // true if a robot has to wait for a robot to arrive in the subsequent task
    private boolean directTransfer;


    /**
     * Creates a new sequential task without subtask.
     * @param directTransfer  if true, a subtask cannot be completed before the next one is started
     */
    public SequentialTask(boolean directTransfer)
    {
        this.directTransfer = directTransfer;
        this.subtasks = new LinkedList<AbstractTask>();
        this.idleConnectors = new ArrayList<TransitionConnector>();
    }

    /**
     * Adds a subtask at the end of the chain of sequentially dependent subtasks.
     * @param newSubtask  new subtask to add
     */
    public void addSubtask(AbstractTask newSubtask)
    {
        if (subtasks.size() == 0)
        {
            log.debug("Adding first subtask " + newSubtask);

            /*
             * supertask <-> subtask transitions
             */
            // if the super-task goes from IDLE to AVAILABLE, the *first* subtask does the same
            TransitionConnector availableConnector = new TransitionConnector("seq task->AVAILABLE");
            this.getTransition(State.IDLE, State.AVAILABLE).addAction(availableConnector);
            newSubtask.getTransition(State.IDLE, State.AVAILABLE).addCondition(availableConnector);

            // if the *first* subtask goes from AVAILABLE to WAIT_BEFORE, the super-task does the same
            TransitionConnector workConnector = new TransitionConnector("first subtask->WAIT_BEFORE");
            newSubtask.getTransition(State.AVAILABLE, State.WAIT_BEFORE).addAction(workConnector);
            this.getTransition(State.WAIT_BEFORE, State.WORKING).addCondition(workConnector);

            // if the *last* subtask goes to SUCCESS, we consider the whole task SUCCESS
            successConnector = new TransitionConnector("last subtask->SUCCESS");
            newSubtask.getTransition(State.WAIT_AFTER, State.SUCCESS).addAction(successConnector);
            this.getTransition(State.WORKING, State.WAIT_AFTER).addCondition(successConnector);

//            // Note: wait before
//            TransitionConnector waitBeforeConnector = new TransitionConnector("supertask->WAIT_BEFORE, subtask->WAIT_BEFORE");
//            this.getTransition(State.AVAILABLE, State.WAIT_BEFORE).addAction(waitBeforeConnector);
//            newSubtask.getTransition(State.AVAILABLE, State.WAIT_BEFORE).addCondition(waitBeforeConnector);

            // the fail connector FAILs the supertask if *any* subtask fails
            failConnector = new TransitionConnector("any subtask->FAIL");
            this.getTransition(State.AVAILABLE,   State.FAIL).addCondition(failConnector);
            this.getTransition(State.WAIT_BEFORE, State.FAIL).addCondition(failConnector);
            this.getTransition(State.WORKING,     State.FAIL).addCondition(failConnector);
            this.getTransition(State.WAIT_AFTER,  State.FAIL).addCondition(failConnector);
        }
        else
        {
            AbstractTask lastSubtask = subtasks.getLast();
            log.debug("Adding a new subtask " + newSubtask + " after " + lastSubtask);

            /*
             * supertask <-> subtask transitions
             */
            // remove the SUCCESS connector from the previously last task and add it to the new task
            lastSubtask.getTransition(State.WAIT_AFTER, State.SUCCESS).removeAction(successConnector);
            newSubtask.getTransition(State.WAIT_AFTER, State.SUCCESS).addAction(successConnector);

            /*
             * subtask <-> subtask transitions
             */
            // first task is done, activate the second
            TransitionConnector firstDoneActivateSecondConnector = new TransitionConnector("subtask1->WAIT_AFTER => subtask1->AVAILABLE");
            lastSubtask.getTransition(State.WORKING, State.WAIT_AFTER).addAction(firstDoneActivateSecondConnector);
            newSubtask.getTransition(State.IDLE, State.AVAILABLE).addCondition(firstDoneActivateSecondConnector);

            // if we have a direct transfer, the first robot has to wait for the second to arrive
            if (directTransfer)
            {
                // second task AVAILABLE->WAIT_BEFORE allows first task to move to SUCCESS
                TransitionConnector firstWaitsForSecondConnector = new TransitionConnector("subtask2->WAIT_BEFORE => subtask1->SUCCESS");
                newSubtask.getTransition(State.AVAILABLE, State.WAIT_BEFORE).addAction(firstWaitsForSecondConnector);
                lastSubtask.getTransition(State.WAIT_AFTER, State.SUCCESS).addCondition(firstWaitsForSecondConnector);

                // second task AVAILABLE->WAIT_BEFORE allows first task to move to SUCCESS
                TransitionConnector secondWaitsForFirstConnector = new TransitionConnector("subtask1->SUCCESS => subtask1->WORKING");
                lastSubtask.getTransition(State.WAIT_AFTER, State.SUCCESS).addAction(secondWaitsForFirstConnector);
                newSubtask.getTransition(State.WAIT_BEFORE, State.WORKING).addCondition(secondWaitsForFirstConnector);

                // if first does does not wait, both go to IDLE
                TransitionConnector firstToSecondFailConnector = new TransitionConnector("subtask1->FAIL => subtask2->FAIL");
                lastSubtask.getTransition(State.WAIT_AFTER, State.FAIL).addAction(firstToSecondFailConnector);
                newSubtask.getTransition(State.AVAILABLE, State.FAIL).addCondition(firstToSecondFailConnector);
            }

            // allow only for new task if next in line is done
            // Note: we have to initialize this with true
            TransitionConnector secondDoneConnector = new TransitionConnector("subtask2->IDLE => subtask1->AVAILABLE", true);
            newSubtask.getTransition(State.SUCCESS, State.IDLE).addAction(secondDoneConnector);
            newSubtask.getTransition(State.FAIL, State.IDLE).addAction(secondDoneConnector);
            lastSubtask.getTransition(State.IDLE, State.AVAILABLE).addCondition(secondDoneConnector);

//            // Note: wait after
//            TransitionConnector waitAfterConnector = new TransitionConnector("supertask->WAIT_AFTER, subtask->WAIT_AFTER");
//            this.getTransition(State.WORKING, State.WAIT_AFTER).addAction(waitAfterConnector);
//            newSubtask.getTransition(State.WORKING, State.WAIT_AFTER).addCondition(waitAfterConnector);
        }

        // TODO: implement WAIT_BEFORE and WAIT_AFTER on supertask properly to allow nesting

        // every subtask must go to IDLE before the supertask can go to IDLE again
        TransitionConnector idleConnector = new TransitionConnector("subtask->IDLE", true);
        newSubtask.getTransition(State.SUCCESS, State.IDLE).addAction(idleConnector);
        newSubtask.getTransition(State.FAIL, State.IDLE).addAction(idleConnector);
        this.getTransition(State.SUCCESS, State.IDLE).addCondition(idleConnector);
        this.getTransition(State.FAIL, State.IDLE).addCondition(idleConnector);
        idleConnectors.add(idleConnector);

        // if any subtask goes to FAIL, we consider the whole task FAIL
        newSubtask.getTransition(State.WAIT_BEFORE, State.FAIL).addAction(failConnector);
        newSubtask.getTransition(State.WORKING,     State.FAIL).addAction(failConnector);
        newSubtask.getTransition(State.WAIT_AFTER,  State.FAIL).addAction(failConnector);

        // reset everything if the super task resets
        this.getTransition(State.IDLE, State.AVAILABLE).addAction(new TransitionAction()
        {
            public void execute(Transition transition)
            {
                // reset the idle connectors of all subtasks and the fail connector
                // if the supertask goes from IDLE -> AVAILABLE (ie, when it restarts)
                for (TransitionConnector idleConnector : idleConnectors)
                {
                    idleConnector.reset(transition);
                }
                failConnector.reset(transition);
            }
        });

        newSubtask.setSupertask(this);
        subtasks.add(newSubtask);
    }

    /**
     * Returns the number of subtasks.
     * @return number of subtasks
     */
    public int getNumSubtasks()
    {
        return subtasks.size();
    }

    /**
     * Returns a String representation of this task.
     * @return String representation of this task
     */
    @Override
    public String toString()
    {
        return "SequentialTask{state=" + getState() + ", numSubtasks="+ getNumSubtasks() +'}';
    }
}
