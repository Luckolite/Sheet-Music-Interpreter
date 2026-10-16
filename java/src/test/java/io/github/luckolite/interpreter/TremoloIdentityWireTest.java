// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.io.*;
import java.nio.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TremoloIdentityWireTest {
    private ScoreTremoloSource source() {
        var a = new ScoreTremoloSource.Anchor(0, 0);
        var b = new ScoreTremoloSource.Anchor(0, 1);
        var pair =
                new ScoreTremoloSource.Pair(
                        "original-\u266a",
                        List.of(a),
                        List.of(b),
                        3,
                        List.of(new ScoreTremoloSource.Evidence(0, .1f, .2f, .3f, .4f)));
        return new ScoreTremoloSource(List.of(pair), List.of(Optional.of(a), Optional.of(b)));
    }

    @Test
    public void utf8IdentityAndEndpointEvidenceRoundTrip() throws Exception {
        var s = source();
        assertEquals(s, ScoreTremoloWire.decode(ScoreTremoloWire.encode(s), 2));
    }

    @Test
    public void truncatedBodiesCannotBeAcceptedWithRepairedLength() throws Exception {
        byte[] b = ScoreTremoloWire.encode(source());
        for (int n = 8; n < b.length; n++) {
            byte[] cut = Arrays.copyOf(b, n);
            ByteBuffer.wrap(cut).putInt(4, n - 8);
            assertThrows(IOException.class, () -> ScoreTremoloWire.decode(cut, 2));
        }
    }
}
