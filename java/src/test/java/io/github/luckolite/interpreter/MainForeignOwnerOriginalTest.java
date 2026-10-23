// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** An additive ordinary-head index cannot rebind an explicit foreign owner. */
public final class MainForeignOwnerOriginalTest {
    @Test
    public void explicitForeignAndNullOwnerValuesRemainAvailableForRefusal() {
        var notes =
                List.of(
                        MainInstructionOwnerOriginalTest.note(.12f, 0),
                        MainInstructionOwnerOriginalTest.note(.82f, 2));
        var events = MainInstructionOwnerOriginalTest.events();
        events.get(0).put("sourceNoteIndex", 17);
        events.get(1).put("sourceNoteIndex", null);
        Main.attachNotePerformance(notes, events);
        assertEquals(17, events.get(0).get("sourceNoteIndex"));
        assertTrue(events.get(1).containsKey("sourceNoteIndex"));
        assertNull(events.get(1).get("sourceNoteIndex"));
    }
}
