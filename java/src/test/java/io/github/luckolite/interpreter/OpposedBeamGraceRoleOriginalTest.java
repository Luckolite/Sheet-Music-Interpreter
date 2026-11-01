// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class OpposedBeamGraceRoleOriginalTest {
    @Test
    public void fullSizeGraceCannotIssueMetricalBeamSustain() {
        var n = new ArrayList<>(OpposedBeamVoiceOriginalTest.notes());
        n.set(0, n.get(0).withArticulations(NoteOrnament.GRACE));
        assertEquals(
                n,
                CrossStaffBeamDetector.mark(
                        n,
                        OpposedBeamVoiceOriginalTest.heads(),
                        OpposedBeamVoiceOriginalTest.ink(true, true),
                        OpposedBeamVoiceOriginalTest.W,
                        OpposedBeamVoiceOriginalTest.H));
        var a = n.get(0).withCrossStaffBeam();
        var b = n.get(2).withCrossStaffBeam();
        assertFalse(ScoreNoteTiming.hasIndependentDuration(a, List.of(a, b)));
        assertFalse(ScoreNoteTiming.hasIndependentDuration(b, List.of(a, b)));
    }
}
