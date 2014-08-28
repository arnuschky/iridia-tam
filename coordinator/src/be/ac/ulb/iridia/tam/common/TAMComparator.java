package be.ac.ulb.iridia.tam.common;

import java.util.Comparator;


/**
 * Compares two TAMs.
 */
public class TAMComparator implements Comparator<TAMInterface>
{
    public int compare(TAMInterface tam1, TAMInterface tam2)
    {
        return tam1.getId().compareTo(tam2.getId());
    }
}
