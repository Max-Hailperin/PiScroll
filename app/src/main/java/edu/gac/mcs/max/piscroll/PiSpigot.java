package edu.gac.mcs.max.piscroll;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigInteger;

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.TEN;
import static java.math.BigInteger.ZERO;

/**
 * Created by Max Hailperin max@gustavus.edu on 3/10/16.
 * Based on "Unbounded Spigot Algorithms for the Digits of Pi," by Jeremy Gibbons,
 * American Mathematical Monthly, April 2006.
 */
public class PiSpigot implements Spigot {

    private Writer mOut;
    private BigInteger q, r, t, k, n;
    private static final BigInteger
            TWO = BigInteger.valueOf(2),
            THREE = BigInteger.valueOf(3),
            FOUR = BigInteger.valueOf(4),
            SEVEN = BigInteger.valueOf(7);

    public PiSpigot() {
        q = ONE;
        r = ZERO;
        t = ONE;
        k = ONE;
        n = THREE;
    }

    @Override
    public void setWriter(Writer out) {
        mOut = out;
    }

    @Override
    public void run() {
        if (FOUR.multiply(q).add(r).subtract(t).compareTo(n.multiply(t)) < 0) {
            try {
                mOut.append(n.toString());
                if (k.equals(TWO)) {
                    mOut.append('.');
                }
            } catch (IOException e) {
                throw new RuntimeException("Writer threw an IOException", e);
            }
            BigInteger newQ = TEN.multiply(q);
            BigInteger newR = TEN.multiply(r.subtract(n.multiply(t)));
            n = TEN.multiply(THREE.multiply(q).add(r)).divide(t)
                    .subtract(TEN.multiply(n));
            q = newQ;
            r = newR;
        } else {
            BigInteger l = TWO.multiply(k).add(ONE);
            BigInteger newQ = q.multiply(k);
            BigInteger newR = TWO.multiply(q).add(r).multiply(l);
            BigInteger newT = t.multiply(l);
            BigInteger newK = k.add(ONE);
            n = q.multiply(SEVEN.multiply(k).add(TWO)).add(r.multiply(l))
                    .divide(t.multiply(l));
            q = newQ;
            r = newR;
            t = newT;
            k = newK;
        }
    }

    public static void main(String[] args) { // run as a standalone program (e.g. for testing)
        Spigot s = new PiSpigot();
        s.setWriter(new OutputStreamWriter(System.out));
        //noinspection InfiniteLoopStatement
        while (true) {
            s.run();
        }
    }
}