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
public class PiSpigot implements Runnable {

    private Writer mOut;

    public PiSpigot(Writer out) {
        mOut = out;
    }

    @Override
    public void run() {
        BigInteger q, r, t, k, n;
        final BigInteger
                two = BigInteger.valueOf(2),
                three = BigInteger.valueOf(3),
                four = BigInteger.valueOf(4),
                seven = BigInteger.valueOf(7);
        q = ONE;
        r = ZERO;
        t = ONE;
        k = ONE;
        n = three;
        while (true) {
            if (four.multiply(q).add(r).subtract(t).compareTo(n.multiply(t)) < 0) {
                try {
                    mOut.append(n.toString());
                    if (k.equals(two)) {
                        mOut.append('.');
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Writer threw an IOException", e);
                }
                BigInteger newQ = TEN.multiply(q);
                BigInteger newR = TEN.multiply(r.subtract(n.multiply(t)));
                n = TEN.multiply(three.multiply(q).add(r)).divide(t)
                        .subtract(TEN.multiply(n));
                q = newQ;
                r = newR;
            } else {
                BigInteger l = two.multiply(k).add(ONE);
                BigInteger newQ = q.multiply(k);
                BigInteger newR = two.multiply(q).add(r).multiply(l);
                BigInteger newT = t.multiply(l);
                BigInteger newK = k.add(ONE);
                n = q.multiply(seven.multiply(k).add(two)).add(r.multiply(l))
                        .divide(t.multiply(l));
                q = newQ;
                r = newR;
                t = newT;
                k = newK;
            }
        }
    }

    public static void main(String[] args) { // run as a standalone program (e.g. for testing)
        new PiSpigot(new OutputStreamWriter(System.out)).run();
    }
}