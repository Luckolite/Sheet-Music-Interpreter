// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Translation and a genuinely continuous sloped raw shaft need no semantic mask. */
public class TranslatedDiagonalShaftTest {
    @Test
    public void translatedDisconnectedPairStillCannotDonateRhythm() throws Exception {
        var d = StrictShaftConnectivityTest.separate(1, 1);
        var l = d.labels.clone();
        var g = d.gray.clone();
        Arrays.fill(d.labels, (byte) 0);
        Arrays.fill(d.gray, (byte) 255);
        for (int y = 0; y < d.H - 30; y++)
            for (int x = 0; x < d.W - 35; x++) {
                d.labels[(y + 30) * d.W + x + 35] = l[y * d.W + x];
                d.gray[(y + 30) * d.W + x + 35] = g[y * d.W + x];
            }
        for (int i = 0; i < d.notes.size(); i++) {
            var n = d.notes.get(i);
            var e = TallCommonShaftChordTest.event(d, n);
            var get = d.detected.getDeclaredMethod("head");
            get.setAccessible(true);
            Object h = get.invoke(n);
            var v = new HashMap<String, Number>();
            for (var c : d.component.getRecordComponents()) {
                var a = c.getAccessor();
                a.setAccessible(true);
                v.put(c.getName(), (Number) a.invoke(h));
            }
            var hc = d.component.getDeclaredConstructors()[0];
            hc.setAccessible(true);
            Object moved =
                    hc.newInstance(
                            v.get("area").intValue(),
                            v.get("minX").intValue() + 35,
                            v.get("maxX").intValue() + 35,
                            v.get("minY").intValue() + 30,
                            v.get("maxY").intValue() + 30,
                            v.get("centerX").floatValue() + 35,
                            v.get("centerY").floatValue() + 30);
            var changed =
                    new ScoreNoteEvent(
                                    e.measureIndex(),
                                    e.positionInMeasure(),
                                    e.staffStep(),
                                    e.staffIndex(),
                                    e.staffCount(),
                                    (e.pageY() * d.H + 30) / d.H,
                                    false,
                                    e.augmentationDots(),
                                    e.beamCount(),
                                    e.writtenAccidental(),
                                    e.unbeamedDurationBeats())
                            .withStemDirection(-1);
            var nc = d.detected.getDeclaredConstructors()[0];
            nc.setAccessible(true);
            d.notes.set(i, nc.newInstance(changed, moved, (float) d.G));
        }
        assertFalse(ShortWhiteGapProbe.connected(d.gray, 135, 208, 135, 216));
        assertEquals(0, d.run().get(0).beamCount());
    }

    @Test
    public void continuousDiagonalRawShaftWithNoShaftLabelsIsAccepted() throws Exception {
        var d = new TallCommonShaftDiagnostic(96, false, false);
        for (int y = 90; y <= 240; y++)
            for (int x = 100; x <= 101; x++) {
                d.gray[y * d.W + x] = (byte) 255;
                d.labels[y * d.W + x] = 0;
            }
        for (int y = 90; y <= 240; y++) {
            int left = 100 + Math.round((y - 90) * 2f / 150);
            for (int x = left; x <= left + 2; x++) d.pixel(x, y, 0);
        }
        assertTrue(ShortWhiteGapProbe.connected(d.gray, 100, 90, 102, 240));
        assertTrue(d.run().stream().allMatch(n -> n.beamCount() == 1));
    }
}
