// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** The raw vertical ownership proof is independent of stem direction. */
public class MirroredShaftBridgeTest {
    static TallCommonShaftDiagnostic mirror(TallCommonShaftDiagnostic d) throws Exception {
        var l = d.labels.clone();
        var g = d.gray.clone();
        for (int y = 0; y < d.H; y++)
            for (int x = 0; x < d.W; x++) {
                d.labels[y * d.W + x] = l[(d.H - 1 - y) * d.W + x];
                d.gray[y * d.W + x] = g[(d.H - 1 - y) * d.W + x];
            }
        for (int i = 0; i < d.notes.size(); i++) {
            Object n = d.notes.get(i);
            var e = TallCommonShaftChordTest.event(d, n);
            var get = d.detected.getDeclaredMethod("head");
            get.setAccessible(true);
            Object h = get.invoke(n);
            var values = new java.util.HashMap<String, Number>();
            for (var c : d.component.getRecordComponents()) {
                var a = c.getAccessor();
                a.setAccessible(true);
                values.put(c.getName(), (Number) a.invoke(h));
            }
            var hc = d.component.getDeclaredConstructors()[0];
            hc.setAccessible(true);
            Object flipped =
                    hc.newInstance(
                            values.get("area").intValue(),
                            values.get("minX").intValue(),
                            values.get("maxX").intValue(),
                            d.H - 1 - values.get("maxY").intValue(),
                            d.H - 1 - values.get("minY").intValue(),
                            values.get("centerX").floatValue(),
                            d.H - 1 - values.get("centerY").floatValue());
            var changed =
                    new ScoreNoteEvent(
                                    e.measureIndex(),
                                    e.positionInMeasure(),
                                    -e.staffStep(),
                                    e.staffIndex(),
                                    e.staffCount(),
                                    (d.H - 1 - e.pageY() * d.H) / d.H,
                                    false,
                                    e.augmentationDots(),
                                    e.beamCount(),
                                    e.writtenAccidental(),
                                    e.unbeamedDurationBeats())
                            .withStemDirection(1);
            var nc = d.detected.getDeclaredConstructors()[0];
            nc.setAccessible(true);
            d.notes.set(i, nc.newInstance(changed, flipped, (float) d.G));
        }
        return d;
    }

    @Test
    public void mirroredIndependentQuarterCannotBorrowEighthAcrossStaff() throws Exception {
        assertEquals(
                0,
                mirror(TallChordAdversarialTest.independentVoices(true)).run().get(0).beamCount());
    }

    @Test
    public void mirroredContinuousRawShaftKeepsSharedEighth() throws Exception {
        var d = new TallCommonShaftDiagnostic(96, false, false);
        for (int y = 181; y <= 183; y++) for (int x = 20; x <= 300; x++) d.pixel(x, y, 4);
        assertTrue(mirror(d).run().stream().allMatch(n -> n.beamCount() == 1));
    }
}
