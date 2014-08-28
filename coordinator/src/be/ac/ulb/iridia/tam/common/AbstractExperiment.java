package be.ac.ulb.iridia.tam.common;

import java.util.Random;
import java.util.Timer;


/**
 * Abstract implementation for "TAM experiment".
 *
 * This implementation provides some convenience methods, mostly a pseudo-random number generator
 * and a timer/scheduler for scheduling tasks.
 *
 * @see be.ac.ulb.iridia.tam.common.ExperimentInterface
 */
public abstract class AbstractExperiment implements ExperimentInterface
{
    // random seed for the pseudo-random number generator
    private long randomSeed;

    // pseudo-random number generator
    private Random prng;

    // timer for scheduling
    private Timer timer;

    // flag that signals if experiment is ready to start
    private boolean ready;

    // flag that signals if experiment is done
    private boolean finished;


    /**
     * Initializes experiment.
     * Sets flags to defaults and initialized the random number generator.
     * @param randomSeed  seed for the prng, set either constant or use System.currentTimeMillis()
     */
    @Override
    public void init(long randomSeed)
    {
        this.finished = false;
        this.ready = true;
        this.randomSeed = randomSeed;

        this.reset();
    }

    /**
     * Resets the experiment.
     * Resets the random number generator and the scheduler.
     */
    @Override
    public void reset()
    {
        this.prng = new Random(randomSeed);
        this.timer = new Timer();
    }

    /**
     * Checks whether the experiment is ready to start.
     * @return true if ready to start
     */
    @Override
    public boolean isReady()
    {
        return ready;
    }

    /**
     * Sets that experiment is ready to start.
     */
    protected void setReady()
    {
        this.ready = true;
    }

    /**
     * Checks whether the experiment should finish.
     * @return true if should finish
     */
    @Override
    public boolean isFinished()
    {
        return finished;
    }

    /**
     * Sets that the experiment should finish.
     */
    protected void setFinished()
    {
        this.finished = true;
    }

    /**
     * Called by the coordinator on regular intervals.
     * Can be used for management of TAMs etc.
     * This does nothing in this base version, override if necessary.
     */
    @Override
    public void step()
    {
    }

    /**
     * Returns the PRNG for this experiment.
     * @return prng
     */
    protected Random getPrng()
    {
        return prng;
    }

    /**
     * Returns the timer for this experiment.
     * @return timer
     */
    protected Timer getTimer()
    {
        return timer;
    }
}
