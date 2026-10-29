// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric ovals, shaft and rail. No score pixels or font outlines. */
public class WholeMovingUnisonOriginalTest {
    static final int W = 400, H = 250;

    static class Page {
        final byte[] labels = new byte[W * H], gray = new byte[W * H];

        Page() {
            Arrays.fill(gray, (byte) 255);
            for (int y : new int[] {80, 94, 108, 122, 136}) rect(20, y, 360, 1, 4);
        }

        void rect(int x, int y, int w, int h, int label) {
            for (int yy = y; yy < y + h; yy++)
                for (int xx = x; xx < x + w; xx++) {
                    labels[yy * W + xx] = (byte) label;
                    gray[yy * W + xx] = 0;
                }
        }

        void oval(int cx, int cy, int rx, boolean hollow) {
            for (int y = cy - 8; y <= cy + 8; y++)
                for (int x = cx - rx; x <= cx + rx; x++) {
                    double d = Math.pow((x - cx) / (double) rx, 2) + Math.pow((y - cy) / 8., 2);
                    if (d <= 1) {
                        labels[y * W + x] = 2;
                        double opening =
                                Math.pow(((x - cx) - .35 * (y - cy)) / (rx * .42), 2)
                                        + Math.pow((y - cy) / 5.4, 2);
                        gray[y * W + x] = (byte) (hollow && opening < 1 ? 255 : 0);
                    }
                }
        }

        void moving(int cx) {
            rect(cx - 9, 150, 2, 47, 1);
            rect(cx - 9, 194, 262 - (cx - 9), 4, 5);
            rect(260, 136, 2, 61, 1);
            oval(270, 136, 9, false);
        }

        void ledger() {
            for (int x = 160; x < 223; x++) {
                gray[150 * W + x] = 0;
                if (labels[150 * W + x] != 2) labels[150 * W + x] = 5;
            }
        }

        List<ScoreNoteEvent> notes() {
            return OmrScoreInterpreter.extract(
                    labels, gray, W, H, List.of(new MeasureRegion(.03f, .97f, .15f, .85f)));
        }

        List<ScoreNoteEvent> unison() {
            return notes().stream().filter(n -> n.staffStep() == -2).toList();
        }
    }

    static Page leftWhole() {
        var p = new Page();
        p.moving(201);
        p.oval(180, 150, 12, true);
        p.oval(201, 150, 9, false);
        p.ledger();
        return p;
    }

    static Page rightWhole() {
        var p = new Page();
        p.moving(180);
        p.oval(180, 150, 9, false);
        p.oval(201, 150, 12, true);
        p.ledger();
        return p;
    }

    static void hasBoth(Page p) {
        var notes = p.unison();
        assertEquals(
                "Two printed noteheads at one pitch retain distinct written voices",
                2,
                notes.size());
        assertEquals(
                1,
                notes.stream()
                        .filter(n -> n.unbeamedDurationBeats() == 4 && n.beamCount() == 0)
                        .count());
        assertEquals(1, notes.stream().filter(n -> n.beamCount() == 1).count());
    }

    @Test
    public void stemlessWholeLeftAndMovingRightRetainBothValues() {
        hasBoth(leftWhole());
    }

    @Test
    public void movingLeftAndStemlessWholeRightRetainBothValues() {
        hasBoth(rightWhole());
    }

    @Test
    public void singleWholeRemainsOneWhole() {
        var p = new Page();
        p.oval(180, 150, 12, true);
        p.ledger();
        var n = p.unison();
        assertEquals(1, n.size());
        assertEquals(4f, n.get(0).unbeamedDurationBeats(), 0);
    }

    @Test
    public void singleMovingHeadCannotInventWhole() {
        var p = new Page();
        p.moving(201);
        p.oval(201, 150, 9, false);
        p.ledger();
        assertTrue(p.unison().stream().noneMatch(n -> n.unbeamedDurationBeats() == 4));
    }

    @Test
    public void twoFilledLobesCannotInventWhole() {
        var p = new Page();
        p.moving(201);
        p.oval(180, 150, 12, false);
        p.oval(201, 150, 9, false);
        p.ledger();
        assertTrue(p.unison().stream().noneMatch(n -> n.unbeamedDurationBeats() == 4));
    }

    @Test
    public void upperNotchCannotInventWhole() {
        var p = leftWhole();
        for (int y = 142; y <= 158; y++)
            for (int x = 168; x <= 192; x++) if (p.labels[y * W + x] == 2) p.gray[y * W + x] = 0;
        for (int y = 146; y <= 148; y++)
            for (int x = 178; x <= 183; x++) p.gray[y * W + x] = (byte) 255;
        assertTrue(p.unison().stream().noneMatch(n -> n.unbeamedDurationBeats() == 4));
    }

    @Test
    public void strayShaftAndLedgerCannotInventWhole() {
        var p = new Page();
        p.moving(201);
        p.oval(201, 150, 9, false);
        p.rect(179, 142, 2, 17, 2);
        p.ledger();
        assertTrue(p.unison().stream().noneMatch(n -> n.unbeamedDurationBeats() == 4));
    }

    @Test
    public void halfSizedStemlessCounterCannotBecomeWhole() {
        var p = new Page();
        p.moving(201);
        p.oval(180, 150, 9, false);
        p.oval(201, 150, 9, false);
        for (int y = 148; y <= 152; y++)
            for (int x = 175; x <= 185; x++)
                if (Math.pow((x - 180) / 5., 2) + Math.pow((y - 150) / 2.2, 2) < 1)
                    p.gray[y * W + x] = (byte) 255;
        p.ledger();
        assertTrue(p.unison().stream().noneMatch(n -> n.unbeamedDurationBeats() == 4));
    }

    @Test
    public void faintOwnHalfShaftCannotBecomeWhole() {
        var p = leftWhole();
        for (int y = 108; y < 151; y++)
            for (int x = 190; x <= 191; x++) p.gray[y * W + x] = (byte) 190;
        assertTrue(p.unison().stream().noneMatch(n -> n.unbeamedDurationBeats() == 4));
    }

    @Test
    public void openLedgerPocketWithoutCompleteOvalCannotBecomeWhole() {
        var p = leftWhole();
        for (int y = 140; y <= 160; y++)
            for (int x = 168; x <= 175; x++) p.gray[y * W + x] = (byte) 255;
        assertTrue(p.unison().stream().noneMatch(n -> n.unbeamedDurationBeats() == 4));
    }

    @Test
    public void slurAboveLedgerCannotBecomeWhole() {
        var p = leftWhole();
        for (int y = 151; y <= 161; y++)
            for (int x = 166; x <= 192; x++) p.gray[y * W + x] = (byte) 255;
        assertTrue(p.unison().stream().noneMatch(n -> n.unbeamedDurationBeats() == 4));
    }

    @Test
    public void closeWholeRightRetainsInsetUpstemMovingVoice() {
        var p = new Page();
        p.rect(185, 105, 2, 46, 1);
        p.rect(185, 104, 78, 4, 5);
        p.rect(260, 104, 2, 32, 1);
        p.oval(270, 136, 9, false);
        p.oval(180, 150, 9, false);
        p.oval(201, 150, 12, true);
        p.ledger();
        hasBoth(p);
    }

    @Test
    public void closeWholeLeftRetainsInsetDownstemMovingVoice() {
        var p = new Page();
        p.rect(196, 150, 2, 47, 1);
        p.rect(196, 194, 68, 4, 5);
        p.rect(260, 136, 2, 61, 1);
        p.oval(270, 136, 9, false);
        p.oval(180, 150, 12, true);
        p.oval(201, 150, 9, false);
        p.ledger();
        hasBoth(p);
    }

    @Test
    public void competingSeamShaftsAbstain() throws Exception {
        var p = new Page();
        p.rect(185, 90, 2, 61, 1);
        p.rect(173, 150, 2, 47, 1);
        p.oval(180, 150, 9, false);
        p.oval(201, 150, 12, true);
        p.ledger();
        int area = 0, sx = 0, sy = 0, minX = W, maxX = 0, minY = H, maxY = 0;
        for (int y = 140; y < 161; y++)
            for (int x = 160; x < 225; x++)
                if (p.labels[y * W + x] == 2) {
                    area++;
                    sx += x;
                    sy += y;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
        var c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor = c.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        var head =
                ctor.newInstance(
                        area, minX, maxX, minY, maxY, sx / (float) area, sy / (float) area);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "stemlessWholeUnison",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        c,
                        float.class);
        m.setAccessible(true);
        assertTrue(
                "Different owned shafts across passing seams are ambiguous",
                ((List<?>) m.invoke(null, p.labels, p.gray, W, H, head, 14f)).isEmpty());
    }

    @Test
    public void splitComponentsRetainDisjointExhaustiveProducerMembers() {
        var p = leftWhole();
        var a =
                OmrScoreInterpreter.analyze(
                        p.labels, p.gray, W, H, List.of(new MeasureRegion(.03f, .97f, .15f, .85f)));
        var source = a.clockProducerSource();
        assertEquals(source.accounting().population(), source.accounting().unique());
        assertEquals(source.accounting().population(), source.accounting().assignedNotes());
        var parts =
                source.emissions().stream()
                        .filter(
                                e ->
                                        e.finalNoteOwners().stream()
                                                .anyMatch(i -> a.notes().get(i).staffStep() == -2))
                        .toList();
        assertEquals(2, parts.size());
        assertFalse(parts.get(0).pixels().isEmpty());
        assertFalse(parts.get(1).pixels().isEmpty());
        assertTrue(Collections.disjoint(parts.get(0).pixels(), parts.get(1).pixels()));
        assertEquals(parts.get(0).parent(), parts.get(1).parent());
        assertNotEquals(-1, parts.get(0).parent());
        assertEquals(parts.get(0).original(), parts.get(1).original());
        for (var part : parts) for (int pixel : part.pixels()) assertEquals(2, p.labels[pixel]);
    }

    @Test
    public void splitValuesRetainActualDecisionRoutes() {
        var p = rightWhole();
        var a =
                OmrScoreInterpreter.analyze(
                        p.labels, p.gray, W, H, List.of(new MeasureRegion(.03f, .97f, .15f, .85f)));
        for (var value : a.clockProducerSource().printedValues())
            if (a.notes().get(value.note()).staffStep() == -2) {
                assertEquals("OBSERVED_ROUTE_NOT_COMPLETE_WRITTEN_VALUE_AUTHORITY", value.status());
                assertTrue(value.observations().contains("SHARED_LOGICAL_UNISON_ATTACK"));
                assertEquals(value.detectorValue(), value.finalValue(), 0);
                assertEquals(value.detectorBeams(), value.finalBeams());
                assertFalse(value.detectorExit().isEmpty());
            }
    }

    @Test
    public void callerPixelsRemainUnchanged() {
        var p = leftWhole();
        var a = p.labels.clone();
        var b = p.gray.clone();
        p.notes();
        assertArrayEquals(a, p.labels);
        assertArrayEquals(b, p.gray);
    }
}
