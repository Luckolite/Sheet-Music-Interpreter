// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.math.BigInteger;
import java.lang.reflect.InvocationTargetException;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exact signed floor and overflow boundaries on the Android-compatible implementation. */
public final class FiniteParallelBeamBandFloorTest {
    private static int floor(String numerator, String denominator) throws Exception {
        Class<?> q = Class.forName(FiniteParallelBeamBand.class.getName() + "$Q");
        var constructor = q.getDeclaredConstructor(BigInteger.class, BigInteger.class);
        constructor.setAccessible(true);
        var method = q.getDeclaredMethod("floor");
        method.setAccessible(true);
        try {
            return (int)
                    method.invoke(
                            constructor.newInstance(
                                    new BigInteger(numerator), new BigInteger(denominator)));
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof ArithmeticException arithmetic) throw arithmetic;
            throw failure;
        }
    }

    @Test
    public void signedFractionsRoundDown() throws Exception {
        assertEquals(0, floor("0", "3"));
        assertEquals(0, floor("1", "3"));
        assertEquals(-1, floor("-1", "3"));
        assertEquals(-2, floor("-4", "3"));
        assertEquals(-2, floor("-6", "3"));
    }

    @Test
    public void signedIntegerEndpointsRemainExact() throws Exception {
        assertEquals(Integer.MIN_VALUE, floor("-2147483648", "1"));
        assertEquals(Integer.MAX_VALUE, floor("2147483647", "1"));
        assertEquals(Integer.MAX_VALUE, floor("4294967295", "2"));
        assertEquals(Integer.MIN_VALUE, floor("-4294967295", "2"));
    }

    @Test
    public void overflowDoesNotWrap() {
        for (String n :
                new String[] {
                    "2147483648",
                    "-2147483649",
                    "99999999999999999999999",
                    "-99999999999999999999999"
                }) assertThrows(ArithmeticException.class, () -> floor(n, "1"));
        assertThrows(ArithmeticException.class, () -> floor("-4294967297", "2"));
    }
}
