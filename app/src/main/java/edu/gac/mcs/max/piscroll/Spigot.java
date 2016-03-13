package edu.gac.mcs.max.piscroll;

import java.io.Writer;

/**
 * Created by Max Hailperin max@gustavus.edu on 3/13/16.
 * A Spigot does a bounded amount of computation each time it is run and if run often enough
 * produces an unbounded amount of output on its Writer.
 */
public interface Spigot extends Runnable {
    void setWriter(Writer out);
}
