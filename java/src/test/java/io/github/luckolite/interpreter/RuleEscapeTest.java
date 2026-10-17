// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class RuleEscapeTest {
    @Test
    public void physicallyContinuousWaveEscapingAtStaffRuleCannotBindPartialChord() {
        var p = RuleEscapeProbe.page();
        var out =
                ScoreArpeggioDetector.apply(
                        p,
                        RuleEscapeProbe.scene(),
                        260,
                        250,
                        List.of(
                                new PlayingTechniqueDetector.Staff(36, 100, 16, 0, 2),
                                new PlayingTechniqueDetector.Staff(152, 216, 16, 1, 2)),
                        0);
        assertEquals(p.notes(), out.notes());
        assertEquals(
                "Continuous physical wave extends below same-staff territory; no complete RH-only binding",
                0,
                out.expressiveEvents().size());
    }
}
