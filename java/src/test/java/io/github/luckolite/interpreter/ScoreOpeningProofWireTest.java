// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScoreOpeningProofWireTest {
    static boolean read(byte[] bytes, int count, int first) throws Exception {
        return ScoreOpeningProofWire.read(
                new DataInputStream(new ByteArrayInputStream(bytes)), count, first);
    }

    @Test
    public void falseRoundTrip() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ScoreOpeningProofWire.write(new DataOutputStream(bytes), false, 1, 0);
        assertFalse(read(bytes.toByteArray(), 1, 0));
    }

    @Test
    public void trueRoundTrip() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ScoreOpeningProofWire.write(new DataOutputStream(bytes), true, 3, 0);
        assertTrue(read(bytes.toByteArray(), 3, 0));
    }

    @Test
    public void firstOneCompatible() throws Exception {
        assertTrue(read(new byte[] {1}, 3, 1));
    }

    @Test(expected = IOException.class)
    public void invalidFlagRejects() throws Exception {
        read(new byte[] {2}, 3, 0);
    }

    @Test(expected = EOFException.class)
    public void truncationRejects() throws Exception {
        read(new byte[] {}, 3, 0);
    }

    @Test(expected = IOException.class)
    public void continuationRejects() throws Exception {
        read(new byte[] {1}, 3, 7);
    }

    @Test(expected = IOException.class)
    public void emptyGeometryRejects() throws Exception {
        read(new byte[] {1}, 0, 0);
    }

    @Test(expected = IOException.class)
    public void writerRejectsContradiction() throws Exception {
        ScoreOpeningProofWire.write(new DataOutputStream(new ByteArrayOutputStream()), true, 0, 0);
    }
}
