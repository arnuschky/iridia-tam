package be.ac.ulb.iridia.tam.common.tam;

/**
 * Led color class.
 * This class is used to store the state of the RGB leds of the TAMs.
 * The color is represented internally by a long for ease of use and speed.
 * Uses bit operations to set and get specific channels of the color.
 *
 * Note: while this appears to be 32bit RGBA, the alpha channel is ignore making this a 24bit color space
 */
public class LedColor
{
    // mask for all channels
    public final static long CHANNEL_MASK_ALL   = 0xffffff00;
    // mask for red channel
    public final static long CHANNEL_MASK_RED   = 0xff000000;
    // mask for green channel
    public final static long CHANNEL_MASK_GREEN = 0x00ff0000;
    // mask for blue channel
    public final static long CHANNEL_MASK_BLUE  = 0x0000ff00;

    // current value stored as 32bit (lower 8 bits are alpha channel which is ignored)
    private long value;


    /**
     * Default constructor, sets value to black.
     */
    public LedColor()
    {
        this.value = 0;
    }

    /**
     * Constructor that sets value using a single long variable.
     * @param value  long value to set (lower 8 bits are alpha channel which is ignored)
     */
    public LedColor(long value)
    {
        this();
        this.setValue(value);
    }

    /**
     * Constructor that sets value for each channel separately.
     * @param redChannelValue   red channel value
     * @param greenChannelValue green channel value
     * @param blueChannelValue  blue channel value
     */
    public LedColor(byte redChannelValue, byte greenChannelValue, byte blueChannelValue)
    {
        this();
        this.setRedChannelValue(redChannelValue);
        this.setGreenChannelValue(greenChannelValue);
        this.setBlueChannelValue(blueChannelValue);
    }

    /**
     * Returns the current value as a single long variable.
     * @return value as long variable
     */
    public long getValue()
    {
        return value;
    }

    /**
     * Sets value using a single long variable.
     * @param value  long value to set (lower 8 bits are alpha channel which is ignored)
     */
    public void setValue(long value)
    {
        this.value = value;
    }

    /**
     * Returns the value of the red channel of this color.
     * @return value of red channel
     */
    public byte getRedChannelValue()
    {
        return (byte)((value & CHANNEL_MASK_RED) >> 24);
    }

    /**
     * Returns the value of the green channel of this color.
     * @return value of green channel
     */
    public byte getGreenChannelValue()
    {
        return (byte)((value & CHANNEL_MASK_GREEN) >> 16);
    }

    /**
     * Returns the value of the blue channel of this color.
     * @return value of blue channel
     */
    public byte getBlueChannelValue()
    {
        return (byte)((value & CHANNEL_MASK_BLUE) >> 8);
    }

    /**
     * Sets the value of the red channel of this color separately.
     * @param redChannelValue  value of red channel
     */
    public void setRedChannelValue(byte redChannelValue)
    {
        value = (value & ~CHANNEL_MASK_RED) ^ (redChannelValue << 24);
    }

    /**
     * Sets the value of the green channel of this color separately.
     * @param greenChannelValue  value of green channel
     */
    public void setGreenChannelValue(byte greenChannelValue)
    {
        value = (value & ~CHANNEL_MASK_GREEN) ^ (greenChannelValue << 16);
    }

    /**
     * Sets the value of the blue channel of this color separately.
     * @param blueChannelValue  value of blue channel
     */
    public void setBlueChannelValue(byte blueChannelValue)
    {
        value = (value & ~CHANNEL_MASK_BLUE) ^ (blueChannelValue << 8);
    }

    /**
     * Compares an object to this color.
     * @param obj  object to compare
     * @return true if object is an instance of LedColor and all three channels are the same (ignores alpha channel)
     */
    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof LedColor)
        {
            LedColor ledColor = (LedColor)obj;
            // ignore LSB for comparison (ignore ALPHA channel)
            return (this.value & CHANNEL_MASK_ALL) == (ledColor.getValue() & CHANNEL_MASK_ALL);
        }

        return false;
    }

    /**
     * Returns a string representation of this LedColor.
     * @return string representation of this LedColor
     */
    @Override
    public String toString()
    {
        return "R=" + getRedChannelValue() +
                ",G=" + getGreenChannelValue() +
                ",B=" + getBlueChannelValue();
    }
}
