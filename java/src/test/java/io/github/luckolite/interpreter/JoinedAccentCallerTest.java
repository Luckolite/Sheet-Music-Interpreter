// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural slur/contact scene; complete detector, unchanged input planes. */
public class JoinedAccentCallerTest {
    static int detect(boolean notation, boolean foreign, boolean missing) {
        var s = SlurEndpointAccentOriginalTest.complete();
        if (foreign) s.line(130, 78, 130, 100);
        if (missing) s.notes.remove(0);
        byte[] labels = new byte[s.gray.length];
        if (notation)
            for (int y = 95; y <= 110; y++)
                for (int x = 184; x <= 207; x++)
                    labels[y * SlurEndpointAccentOriginalTest.W + x] =
                            OmrMeasurePostProcessor.NOTEHEAD;
        byte[] g = s.gray.clone(), l = labels.clone();
        int[] result =
                NoteArticulationDetector.detect(
                        labels,
                        s.gray,
                        SlurEndpointAccentOriginalTest.W,
                        SlurEndpointAccentOriginalTest.H,
                        s.notes);
        assertArrayEquals(g, s.gray);
        assertArrayEquals(l, labels);
        return result[result.length - 1] & NoteArticulation.ACCENT;
    }

    @Test
    public void certifiedJoinedChevronReachesOwningAttack() {
        assertEquals(NoteArticulation.ACCENT, detect(false, false, false));
    }

    @Test
    public void semanticHeadStillVetoesCertifiedShape() {
        assertEquals(0, detect(true, false, false));
    }

    @Test
    public void unexplainedBranchStillVetoesCaller() {
        assertEquals(0, detect(false, true, false));
    }

    @Test
    public void missingGenuineEndpointStillVetoesCaller() {
        assertEquals(0, detect(false, false, true));
    }
}
