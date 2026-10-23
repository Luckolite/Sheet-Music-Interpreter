// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original full detector calls: printed level ink and wedge rails, without supplied arrivals. */
public final class HairpinArrivalCallerOriginalTest {
    static final int W = 600, H = 240;
    static final PlayingTechniqueDetector.Staff STAFF =
            new PlayingTechniqueDetector.Staff(80, 120, 10, 0, 1);
    static final List<MeasureRegion> BARS = List.of(new MeasureRegion(.1f, .9f, .3f, .55f));

    static ScoreNoteEvent note(int x) {
        return new ScoreNoteEvent(0, (x - 60) / 480f, 2, 0, 1, .4f, false, 0, 0, 0, 1f);
    }

    static byte[] pixels(boolean letter) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int x = 120; x <= 340; x++) {
            int d = Math.round(8 * (x - 120) / 220f);
            g[(160 - d) * W + x] = 0;
            g[(160 + d) * W + x] = 0;
        }
        if (letter) {
            for (int y = 145; y <= 163; y++) for (int x = 383; x <= 385; x++) g[y * W + x] = 0;
            for (int y = 149; y <= 151; y++) for (int x = 378; x <= 391; x++) g[y * W + x] = 0;
        }
        return g;
    }

    static List<ScoreDynamicChange> detect(boolean ink, boolean owner) {
        var gray = pixels(ink);
        return ScoreDynamicsDetector.detectDirectionCore(
                        List.of(
                                new PlayingTechniqueDetector.Word(
                                        "f", 375f / W, 144f / H, 395f / W, 165f / H)),
                        List.of(STAFF),
                        BARS,
                        owner ? List.of(note(120), note(385)) : List.of(note(120)),
                        gray,
                        W,
                        H,
                        gray,
                        false)
                .changes();
    }

    @Test
    public void admittedLiteralSuppliesLaterColumnToGeometricWedge() {
        var changes = detect(true, true);
        var level = changes.stream().filter(c -> c.direction() == 0).findFirst().orElseThrow();
        var wedge = changes.stream().filter(c -> c.direction() == 1).findFirst().orElseThrow();
        assertEquals(note(385).positionInMeasure(), level.positionInMeasure(), 0);
        assertEquals(level.positionInMeasure(), wedge.endPosition(), 0);
    }

    @Test
    public void whiteOcrWordCannotSupplyArrival() {
        var changes = detect(false, true);
        assertTrue(changes.stream().anyMatch(c -> c.direction() == 0));
        var wedge = changes.stream().filter(c -> c.direction() == 1).findFirst().orElseThrow();
        assertTrue(wedge.endPosition() < note(385).positionInMeasure() - .02f);
    }

    @Test
    public void admittedButUnownedLevelCannotInventAnAttack() {
        var changes = detect(true, false);
        assertTrue(changes.stream().anyMatch(c -> c.direction() == 0));
        var wedge = changes.stream().filter(c -> c.direction() == 1).findFirst().orElseThrow();
        assertTrue(wedge.endPosition() < note(385).positionInMeasure() - .02f);
    }
}
