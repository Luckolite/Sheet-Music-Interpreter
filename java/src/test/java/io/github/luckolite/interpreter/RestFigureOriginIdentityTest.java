// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Identity association is independent of the input Map's equality semantics. */
public final class RestFigureOriginIdentityTest {
    @Test
    public void equalButDistinctRecordKeyCannotSupplyAnOrigin() {
        byte[] gray = new byte[100];
        Object component = new Object();
        var origin =
                RestFigureOrigins.HeadOrigin.accepted(
                        component, gray, 10, 10, List.of(new int[] {1, 1, 2}));
        var actual = new ScoreNoteEvent(0, .25f, 0, 0, 1, .5f, false, 0, 1, 2, 0);
        var equalClone = new ScoreNoteEvent(0, .25f, 0, 0, 1, .5f, false, 0, 1, 2, 0);
        assertNotSame(actual, equalClone);
        assertEquals(actual, equalClone);
        assertNotNull(origin);
        var owned = RestFigureOrigins.bind(List.of(actual), Map.of(actual, origin));
        assertSame(actual, owned.get(0));
        assertSame(origin, RestFigureOrigins.origin(owned, actual, gray, 10, 10));
        var borrowed = RestFigureOrigins.bind(List.of(actual), Map.of(equalClone, origin));
        assertSame(actual, borrowed.get(0));
        assertNull(
                "Value-equal foreign identity must remain UNKNOWN",
                RestFigureOrigins.origin(borrowed, actual, gray, 10, 10));
    }
}
