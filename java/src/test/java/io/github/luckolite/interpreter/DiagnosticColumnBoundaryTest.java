// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural controls for the diagnostic data boundary; no page recognition is run. */
public final class DiagnosticColumnBoundaryTest {
    private static boolean hasPrivateState(Object value, Set<Object> seen) throws Exception {
        if (value == null || !seen.add(value)) return false;
        if (value instanceof PrintedBeamShaftPixels.Targets
                || value instanceof PrintedBeamShaftPixels.Frame
                || value instanceof PrintedBeamShaftPixels.Scan
                || value instanceof PrintedBeamShaftPixels.Certificate
                || value instanceof byte[]) return true;
        if (value instanceof Map<?, ?> map) {
            for (Object item : map.values()) if (hasPrivateState(item, seen)) return true;
        } else if (value instanceof Iterable<?> items) {
            for (Object item : items) if (hasPrivateState(item, seen)) return true;
        } else if (value.getClass().isRecord()) {
            for (var component : value.getClass().getRecordComponents()) {
                if (hasPrivateState(component.getAccessor().invoke(value), seen)) return true;
            }
        }
        return false;
    }

    @Test
    public void diagnosticColumnSeversPrivateStateAndPreservesPhysicalFields() throws Exception {
        byte[] raw = new byte[64];
        Arrays.fill(raw, (byte) 255);
        var frame = new PrintedBeamShaftPixels.Frame(new Object(), raw, 8, 8, 0, n -> {});
        var targets = new PrintedBeamShaftPixels.Targets(frame, 0, List.of());
        var working =
                new PrintedBeamGeometry.Column(
                        0,
                        0,
                        0,
                        1,
                        -1,
                        3,
                        3,
                        3,
                        3,
                        1,
                        List.of(new PrintedBeamGeometry.End(3, 3)),
                        6,
                        targets);
        long units = working.shaftColumns().size() + 32L;
        var budget = new PrintedBeamSource.Work(units);
        var diagnostic = PrintedBeamSource.diagnosticColumn(working, budget);
        assertEquals(0L, budget.remaining);
        assertNotSame(working, diagnostic);
        assertSame(targets, working.targets());
        assertNull(diagnostic.targets());
        for (var component : working.getClass().getRecordComponents()) {
            if (!component.getName().equals("targets")) {
                assertEquals(
                        component.getName(),
                        component.getAccessor().invoke(working),
                        component.getAccessor().invoke(diagnostic));
            }
        }
        Object value = Map.of("column", diagnostic).get("column");
        assertSame(diagnostic, (PrintedBeamGeometry.Column) value);
        var capture =
                new PrintedBeamSource.Capture(
                        ScoreBeamSource.empty(0),
                        new PrintedBeamSource.Statistics(
                                0, 0, 0, 0, 1, 0, 0, 0, 0, 0, "", 0, 0, 0, 0, false),
                        List.of(Map.of("head", 0)),
                        List.of(
                                Map.of(
                                        "column",
                                        diagnostic,
                                        "owners",
                                        List.of(),
                                        "unlinked",
                                        false,
                                        "rival",
                                        false,
                                        "staffGap",
                                        3f,
                                        "staffBottom",
                                        6f)));
        assertTrue(hasPrivateState(working, Collections.newSetFromMap(new IdentityHashMap<>())));
        assertFalse(hasPrivateState(capture, Collections.newSetFromMap(new IdentityHashMap<>())));
        var exhausted = new PrintedBeamSource.Work(units - 1);
        try {
            PrintedBeamSource.diagnosticColumn(working, exhausted);
            fail("Diagnostic metadata must be charged before allocating its copy");
        } catch (PrintedBeamSource.Limit expected) {
            assertEquals(units - 1, exhausted.remaining);
        }
    }
}
