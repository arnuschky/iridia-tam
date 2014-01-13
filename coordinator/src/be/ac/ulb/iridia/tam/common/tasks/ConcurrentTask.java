package be.ac.ulb.iridia.tam.common.tasks;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.LinkedList;


/**
 * Concurrent task: subtasks have to start together; all subtasks have to be completed
 * to restart task.
 */
public class ConcurrentTask extends AbstractTask
{
    private final static Logger log = Logger.getLogger(ConcurrentTask.class);

    // list of subtasks
    private LinkedList<AbstractTask> subtasks;

    // list of success connectors, one per subtask
    // all have to evaluate to true if supertask should succeed
    private ArrayList<TransitionConnector> successConnectors;
    // connectors used to track the idle state of the all subtasks
    private ArrayList<TransitionConnector> idleConnectors;
    // connector that fails the supertask when any subtask fails
    private TransitionConnector failConnector;

    private ArrayList<TransitionConnector> waitBeforeConnectors;
    private ArrayList<TransitionConnector> waitAfterConnectors;


    /**
     * Creates a new concurrent task without any subtasks.
     */
    public ConcurrentTask()
    {
        this.subtasks = new LinkedList<AbstractTask>();
        this.successConnectors = new ArrayList<TransitionConnector>();
        this.idleConnectors = new ArrayList<TransitionConnector>();

        this.waitBeforeConnectors  = new ArrayList<TransitionConnector>();
        this.waitAfterConnectors  = new ArrayList<TransitionConnector>();
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
            log.debug("Adding a new subtask " + newSubtask + " next to " + lastSubtask);

            /*
             * subtask <-> subtask transitions
             */

            // first task has to wait for the second before work starts
            TransitionConnector firstWaitSecondBeforeConnector = new TransitionConnector("subTaskFirstWaitSecondBefore");
            newSubtask.getTransition(State.AVAILABLE, State.WAIT_BEFORE).addAction(firstWaitSecondBeforeConnector);
            lastSubtask.getTransition(State.WAIT_BEFORE, State.WORKING).addCondition(firstWaitSecondBeforeConnector);
            // and the other way round
            TransitionConnector secondWaitFirstBeforeConnector = new TransitionConnector("subTaskSecondWaitFirstBefore");
            lastSubtask.getTransition(State.AVAILABLE, State.WAIT_BEFORE).addAction(secondWaitFirstBeforeConnector);
            newSubtask.getTransition(State.WAIT_BEFORE, State.WORKING).addCondition(secondWaitFirstBeforeConnector);

            // first task has to wait for the second after work ends
            TransitionConnector firstWaitSecondAfterConnector = new TransitionConnector("subTaskFirstWaitSecondAfter");
            newSubtask.getTransition(State.WORKING, State.WAIT_AFTER).addAction(firstWaitSecondAfterConnector);
            lastSubtask.getTransition(State.WAIT_AFTER, State.SUCCESS).addCondition(firstWaitSecondAfterConnector);
            // and the other way round
            TransitionConnector secondWaitFirstAfterConnector = new TransitionConnector("subTaskSecondWaitFirstAfter");
            lastSubtask.getTransition(State.WORKING, State.WAIT_AFTER).addAction(secondWaitFirstAfterConnector);
            newSubtask.getTransition(State.WAIT_AFTER, State.SUCCESS).addCondition(secondWaitFirstAfterConnector);

            // cascade FAIL state:
            // if first task FAILs, that also FAILs the second task
            TransitionConnector firstFailsSecondConnector = new TransitionConnector("first->FAIL, second->FAIL");
            newSubtask.getTransition( State.WAIT_BEFORE, State.FAIL).addAction(firstFailsSecondConnector);
            newSubtask.getTransition( State.WORKING,     State.FAIL).addAction(firstFailsSecondConnector);
            newSubtask.getTransition( State.WAIT_AFTER,  State.FAIL).addAction(firstFailsSecondConnector);
            lastSubtask.getTransition(State.WAIT_BEFORE, State.FAIL).addCondition(firstFailsSecondConnector);
            lastSubtask.getTransition(State.WORKING,     State.FAIL).addCondition(firstFailsSecondConnector);
            lastSubtask.getTransition(State.WAIT_AFTER,  State.FAIL).addCondition(firstFailsSecondConnector);
            // and the other way round
            TransitionConnector secondFailsFirstConnector = new TransitionConnector("second->FAIL, first->FAIL");
            lastSubtask.getTransition(State.WAIT_BEFORE, State.FAIL).addAction(secondFailsFirstConnector);
            lastSubtask.getTransition(State.WORKING,     State.FAIL).addAction(secondFailsFirstConnector);
            lastSubtask.getTransition(State.WAIT_AFTER,  State.FAIL).addAction(secondFailsFirstConnector);
            newSubtask.getTransition( State.WAIT_BEFORE, State.FAIL).addCondition(secondFailsFirstConnector);
            newSubtask.getTransition( State.WORKING,     State.FAIL).addCondition(secondFailsFirstConnector);
            newSubtask.getTransition( State.WAIT_AFTER,  State.FAIL).addCondition(secondFailsFirstConnector);
        }

        // if the super-task goes from IDLE to AVAILABLE, the all subtask do the same
        // Note: we have to initialize this with true so that we start up all tasks
        TransitionConnector availableConnector = new TransitionConnector("conc task->AVAILABLE", true);
        this.getTransition(State.IDLE, State.AVAILABLE).addAction(availableConnector);
        newSubtask.getTransition(State.IDLE, State.AVAILABLE).addCondition(availableConnector);

        this.getTransition(State.IDLE, State.AVAILABLE).addAction(new TransitionAction()
        {
            public void execute(Transition transition)
            {
                log.fatal("Concurrent task IDLE->AVAILABLE executes");
                for (TransitionCondition condition : transition.getConditions())
                {
                    log.fatal("configured condition: "+condition);
                }
            }
        });


        // if the any subtask goes from AVAILABLE to WAIT_BEFORE, the super-task does the same
        TransitionConnector workConnector = new TransitionConnector("first subtask->WAIT_BEFORE");
        newSubtask.getTransition(State.AVAILABLE, State.WAIT_BEFORE).addAction(workConnector);
        this.getTransition(State.WAIT_BEFORE, State.WORKING).addCondition(workConnector);

        // every subtask must go to SUCCESS to consider the supertask a SUCCESS
//        TransitionConnector successConnector = new TransitionConnector("subtask->SUCCESS");
//        newSubtask.getTransition(State.WAIT_AFTER, State.SUCCESS).addAction(successConnector);
//        this.getTransition(State.WAIT_AFTER, State.SUCCESS).addCondition(successConnector);
//        successConnectors.add(successConnector);

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

        // TODO: implement WAIT_BEFORE and WAIT_AFTER on supertask properly to allow nesting

//        TransitionConnector waitBeforeConnector = new TransitionConnector("supertask->WAIT_BEFORE, subtask->WAIT_BEFORE");
//        this.getTransition(State.AVAILABLE, State.WAIT_BEFORE).addAction(waitBeforeConnector);
//        newSubtask.getTransition(State.AVAILABLE, State.WAIT_BEFORE).addCondition(waitBeforeConnector);
//        waitBeforeConnectors.add(waitBeforeConnector);

        TransitionConnector enterWaitAfterConnector = new TransitionConnector("subtask: WORKING->WAIT_AFTER, supertask: WORKING->WAIT_AFTER");
        newSubtask.getTransition(State.WORKING, State.WAIT_AFTER).addAction(enterWaitAfterConnector);
        this.getTransition(State.WORKING, State.WAIT_AFTER).addCondition(enterWaitAfterConnector);
        waitAfterConnectors.add(enterWaitAfterConnector);

        TransitionConnector leaveWaitAfterConnector = new TransitionConnector("supertask: WAIT_AFTER->SUCCESS, subtask: WAIT_AFTER->SUCCESS");
        this.getTransition(State.WAIT_AFTER, State.SUCCESS).addAction(leaveWaitAfterConnector);
        newSubtask.getTransition(State.WAIT_AFTER, State.SUCCESS).addCondition(leaveWaitAfterConnector);
        waitAfterConnectors.add(leaveWaitAfterConnector);

        // reset everything if the super task resets
        this.getTransition(State.IDLE, State.AVAILABLE).addAction(new TransitionAction()
        {
            public void execute(Transition transition)
            {
                // reset the success connectors of all subtasks and the fail connector
                // if the supertask goes from IDLE -> AVAILABLE (ie, when it restarts)
                for (TransitionConnector successConnector : successConnectors)
                {
                    successConnector.reset(transition);
                }
                for (TransitionConnector idleConnector : idleConnectors)
                {
                    idleConnector.reset(transition);
                }
                for (TransitionConnector waitBeforeConnector : waitBeforeConnectors)
                {
                    waitBeforeConnector.reset(transition);
                }
                for (TransitionConnector waitAfterConnector : waitAfterConnectors)
                {
                    waitAfterConnector.reset(transition);
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
    public int getNumSubTasks()
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
        return "ConcurrentTask ("+getNumSubTasks() +" subtasks)";
    }
}
