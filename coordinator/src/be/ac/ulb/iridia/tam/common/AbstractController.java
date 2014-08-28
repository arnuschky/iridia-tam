package be.ac.ulb.iridia.tam.common;

import java.util.Random;
import java.util.Timer;


/**
 * Abstract implementation for a TAM controller.
 *
 * This implementation provides some convenience methods, mostly a pseudo-random number generator
 * and a timer/scheduler for scheduling tasks.
 *
 * @see be.ac.ulb.iridia.tam.common.ControllerInterface
 */
public abstract class AbstractController implements ControllerInterface
{
    // random seed for the pseudo-random number generator
    private long randomSeed;

    // pseudo-random number generator
    private Random prng;

    // timer for scheduling
    private Timer timer;


    /**
     * Initialize PRNG and timer.
     * @param randomSeed  seed for PRNG
     */
    public void init(long randomSeed)
    {
        this.randomSeed = randomSeed;
        this.reset();
    }

    /**
     * Resets the controller.
     * Resets PRNG and timer.
     */
    @Override
    public void reset()
    {
        this.prng = new Random(randomSeed);
        this.timer = new Timer();
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
