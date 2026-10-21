// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Original generated staff/crop controls; no score pixels.
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RefinedMeterCopiesTest {
    static MeterCopyFontEvidence.Copy refine(MeterCopyFontEvidence.Copy c) {
        var r = c.crop();
        return new MeterCopyFontEvidence.Copy(
                c.measure(),
                new MeterChangeDetector.Crop(
                        r.left(),
                        r.top(),
                        r.right() + 2,
                        r.bottom(),
                        r.firstLine(),
                        r.gap() + .02f));
    }

    static IndependentMeterCopiesTest.Scene four() throws Exception {
        var s = IndependentMeterCopiesTest.scene("1/4", "1/4");
        return new IndependentMeterCopiesTest.Scene(
                s.gray(),
                List.of(
                        s.copies().get(0),
                        s.copies().get(1),
                        refine(s.copies().get(0)),
                        refine(s.copies().get(1))));
    }

    @Test
    public void agreeingRefinementsPreserveTwoIndependentStaffs() throws Exception {
        assertEquals(new ScoreMeterChange(0, 1, 4), IndependentMeterCopiesTest.resolve(four()));
    }

    @Test
    public void refinementOrderDoesNotCreateVotes() throws Exception {
        var s = four();
        var c = new ArrayList<>(s.copies());
        Collections.reverse(c);
        assertEquals(
                new ScoreMeterChange(0, 1, 4),
                IndependentMeterCopiesTest.resolve(
                        new IndependentMeterCopiesTest.Scene(s.gray(), c)));
    }

    @Test
    public void fourCropsOfOnePhysicalStaffStillAbstain() throws Exception {
        var s = four();
        var c = s.copies();
        assertNull(
                IndependentMeterCopiesTest.resolve(
                        new IndependentMeterCopiesTest.Scene(
                                s.gray(), List.of(c.get(0), c.get(2), c.get(0), c.get(2)))));
    }

    @Test
    public void contradictoryAlternativeIsNotDiscarded() throws Exception {
        var s = four();
        int[] calls = {0};
        assertNull(
                MeterCopyFontEvidence.resolve(
                        0,
                        IndependentMeterCopiesTest.choices(),
                        s.copies(),
                        s.gray(),
                        700,
                        440,
                        (p, w, h, m, f, g) ->
                                new MeterCopyFontEvidence.Reading(
                                        ++calls[0] == 3 ? "4/4" : "1/4", .99f, .99f)));
        assertEquals(3, calls[0]);
    }

    @Test
    public void malformedAlternativeCannotBeIgnored() throws Exception {
        var s = four();
        var c = new ArrayList<>(s.copies());
        c.add(
                new MeterCopyFontEvidence.Copy(
                        0, new MeterChangeDetector.Crop(-1, 87, 302, 159, 90, 16)));
        assertNull(
                IndependentMeterCopiesTest.resolve(
                        new IndependentMeterCopiesTest.Scene(s.gray(), c)));
    }

    @Test
    public void intermediateStaffCannotBridgeIndependentGroups() throws Exception {
        var s = four();
        for (int k = 0; k < 5; k++)
            for (int x = 20; x < 680; x++) s.gray()[(170 + 16 * k) * 700 + x] = 0;
        int[] calls = {0};
        var copies =
                List.of(
                        IndependentMeterCopiesTest.copy(0, 90, 260),
                        IndependentMeterCopiesTest.copy(0, 250, 260),
                        IndependentMeterCopiesTest.copy(0, 170, 260));
        assertNull(
                MeterCopyFontEvidence.resolve(
                        0,
                        IndependentMeterCopiesTest.choices(),
                        copies,
                        s.gray(),
                        700,
                        440,
                        (p, w, h, m, f, g) -> {
                            calls[0]++;
                            return new MeterCopyFontEvidence.Reading("1/4", .99f, .99f);
                        }));
        assertEquals(2, calls[0]);
    }
}
