// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independently authored heads, rules, barlines and glides; no score-derived coordinates. */
public class CrossBarSlideOriginalsTest {
    static FiniteConnectedSlidePortsOriginalChecks.Scene scene(
            int scale, boolean crossing, int direction, boolean line) {
        var s =
                new FiniteConnectedSlidePortsOriginalChecks.Scene(
                        scale, crossing, direction, -direction);
        var target = s.heads.get(1);
        s.heads.set(1, new NoteSlideDetector.Head(target.x(), target.y(), target.gap(), 0, 1));
        if (line) s.line();
        bar(s, 145);
        return s;
    }

    static void bar(FiniteConnectedSlidePortsOriginalChecks.Scene s, int column) {
        int x = column * s.scale;
        for (int y = 83 * s.scale; y <= 142 * s.scale; y++)
            for (int dx = -s.scale; dx <= s.scale; dx++) s.gray[y * s.w + x + dx] = 35;
    }

    static List<NoteSlideDetector.Stroke> found(FiniteConnectedSlidePortsOriginalChecks.Scene s) {
        byte[] before = s.gray.clone();
        var roster = List.copyOf(s.heads);
        var result = s.classify();
        assertArrayEquals(before, s.gray);
        assertEquals(roster, s.heads);
        return result;
    }

    static void noConnected(FiniteConnectedSlidePortsOriginalChecks.Scene s) {
        assertTrue(found(s).stream().noneMatch(x -> x.noteIndex() == 1 && x.connected()));
    }

    @Test
    public void fullBarlineCrossingSupportsBothStemDirectionsAndScales() {
        for (int scale : new int[] {1, 2, 3})
            for (boolean crossing : new boolean[] {false, true})
                for (int direction : new int[] {-1, 1}) {
                    var s = scene(scale, crossing, direction, true);
                    var result = found(s);
                    assertTrue(
                            "scale="
                                    + scale
                                    + " crossing="
                                    + crossing
                                    + " direction="
                                    + direction
                                    + " result="
                                    + result,
                            result.stream()
                                    .anyMatch(
                                            x ->
                                                    x.noteIndex() == 1
                                                            && x.connected()
                                                            && !x.curved()));
                }
    }

    @Test
    public void lineNeedsPhysicalBarline() {
        var s = new FiniteConnectedSlidePortsOriginalChecks.Scene(1, true, -1, 1);
        s.line();
        var t = s.heads.get(1);
        s.heads.set(1, new NoteSlideDetector.Head(t.x(), t.y(), t.gap(), 0, 1));
        noConnected(s);
    }

    @Test
    public void barlineAndHeadsAloneAreNotGlide() {
        noConnected(scene(1, true, -1, false));
    }

    @Test
    public void curvedBowAloneDoesNotBecomeGlide() {
        var s = scene(1, true, -1, false);
        s.bow();
        noConnected(s);
    }

    @Test
    public void literalPaperGapCannotUseBarlineElsewhere() {
        var s = scene(1, true, -1, true);
        for (int x = 138; x <= 142; x++)
            for (int y = 100; y <= 122; y++) s.gray[y * s.w + x] = (byte) 255;
        noConnected(s);
    }

    @Test
    public void incompleteBarlineCannotIssueCrossing() {
        var s = scene(1, true, -1, true);
        for (int y = 126; y <= 136; y++)
            for (int x = 143; x <= 147; x++) s.gray[y * s.w + x] = (byte) 255;
        noConnected(s);
    }

    @Test
    public void unrelatedSecondBoundaryIsAmbiguous() {
        var s = scene(1, true, -1, true);
        bar(s, 155);
        noConnected(s);
    }

    @Test
    public void sourceChordStaysAmbiguous() {
        var s = scene(1, true, -1, true);
        s.heads.add(new NoteSlideDetector.Head(s.x0, s.y0 + s.gap, s.gap, 0, 0));
        noConnected(s);
    }

    @Test
    public void destinationChordStaysAmbiguous() {
        var s = scene(1, true, -1, true);
        s.heads.add(new NoteSlideDetector.Head(s.x1, s.y1 - s.gap, s.gap, 0, 1));
        noConnected(s);
    }

    @Test
    public void nonAdjacentBarsCannotBind() {
        var s = scene(1, true, -1, true);
        var t = s.heads.get(1);
        s.heads.set(1, new NoteSlideDetector.Head(t.x(), t.y(), t.gap(), 0, 2));
        noConnected(s);
    }

    @Test
    public void earlierAttackInDestinationBarPreventsBoundaryBorrowing() {
        var s = scene(1, true, -1, true);
        s.head(165, 98, -1);
        s.heads.add(new NoteSlideDetector.Head(165, 98, s.gap, 0, 1));
        noConnected(s);
    }

    @Test
    public void foreignPhysicalHeadInLineCorridorIsRetained() {
        var s = scene(1, true, -1, true);
        s.head(145, 112, 1);
        s.heads.add(new NoteSlideDetector.Head(145, 112, s.gap, 1, 0));
        noConnected(s);
    }

    @Test
    public void missingActualSourceOvalCannotBorrowCoordinates() {
        var s = scene(1, true, -1, true);
        for (int y = 94; y <= 114; y++)
            for (int x = 96; x <= 122; x++) s.gray[y * s.w + x] = (byte) 255;
        noConnected(s);
    }

    @Test
    public void sourceOrderDoesNotPickAnotherPhysicalOwner() {
        var s = scene(1, true, -1, true);
        Collections.reverse(s.heads);
        var result = found(s);
        assertTrue(
                result.toString(),
                result.stream()
                        .anyMatch(x -> x.connected() && s.heads.get(x.noteIndex()).x() == s.x1));
    }

    @Test
    public void exhaustedNewRouteCannotWithdrawExistingSlide() {
        var old = List.of(new NoteSlideDetector.Stroke(20, 40, 35, 50, -1, 0, false));
        assertSame(
                old,
                FiniteConnectedSlidePorts.addAcrossBars(
                        old, null, 1, 1, List.of(), List.of(), null, null, null));
        var s = scene(1, true, -1, true);
        byte[] clean = NoteSlideDetector.removeStaffLines(s.gray, s.w, s.h, s.staffs);
        var budget = new FiniteConnectedSlidePorts.Budget(0, 0);
        assertTrue(
                FiniteConnectedSlidePorts.findAcrossBars(
                                s.gray, s.w, s.h, s.staffs, s.heads, clean, null, null, budget)
                        .isEmpty());
        assertTrue(budget.refused());
    }

    @Test
    public void receiverAttackSelectionRetainsEveryRivalAndOrdinal() {
        var source = new NoteSlideDetector.Head(100, 90, 14, 0, 3);
        var target = new NoteSlideDetector.Head(170, 104, 14, 0, 4);
        assertEquals(0, FiniteConnectedSlidePorts.previousAcrossBar(List.of(source, target), 1));
        assertEquals(
                2,
                FiniteConnectedSlidePorts.previousAcrossBar(
                        List.of(source, target, new NoteSlideDetector.Head(160, 160, 14, 0, 3)),
                        1));
        assertEquals(
                -1,
                FiniteConnectedSlidePorts.previousAcrossBar(
                        List.of(source, target, new NoteSlideDetector.Head(100, 170, 14, 0, 3)),
                        1));
        assertEquals(
                -1,
                FiniteConnectedSlidePorts.previousAcrossBar(
                        List.of(source, target, new NoteSlideDetector.Head(120, 180, 14, 0, 4)),
                        1));
    }

    @Test
    public void narrowAndTallContraryBranchesCannotDisappearFromTheProof() {
        for (int scale : new int[] {1, 2})
            for (int length : new int[] {12, 40, 80, 120, 169})
                for (int width : new int[] {1, 5}) {
                    var scene = scene(scale, true, -1, true);
                    for (int y = 110 * scale; y < Math.min(scene.h, (110 + length) * scale); y++)
                        for (int x = 135 * scale; x < (135 + width) * scale; x++)
                            scene.gray[y * scene.w + x] = 35;
                    noConnected(scene);
                }
    }

    @Test
    public void newRouteSnapshotsLazyHeadMetadataExactlyOnce() {
        var scene = scene(1, true, -1, true);
        int[] reads = {0};
        List<NoteSlideDetector.Head> lazy =
                new AbstractList<>() {
                    @Override
                    public int size() {
                        if (++reads[0] > 1) throw new AssertionError("source metadata reread");
                        return scene.heads.size();
                    }

                    @Override
                    public NoteSlideDetector.Head get(int i) {
                        throw new AssertionError("indexed original input");
                    }

                    @Override
                    public Iterator<NoteSlideDetector.Head> iterator() {
                        return scene.heads.iterator();
                    }
                };
        byte[] clean =
                NoteSlideDetector.removeStaffLines(scene.gray, scene.w, scene.h, scene.staffs);
        var observed =
                ObservedRuleSlideInk.prepare(
                        scene.gray, scene.w, scene.h, scene.staffs, scene.heads);
        var result =
                FiniteConnectedSlidePorts.findAcrossBars(
                        scene.gray,
                        scene.w,
                        scene.h,
                        scene.staffs,
                        lazy,
                        clean,
                        observed.clean(),
                        observed.tracks(),
                        new FiniteConnectedSlidePorts.Budget(256_000_000, 128 * 1024 * 1024));
        assertEquals(1, reads[0]);
        assertTrue(
                result.toString(),
                result.stream().anyMatch(x -> x.noteIndex() == 1 && x.connected()));
    }

    @Test
    public void newCallCannotReuseAnEarlierPageOrDeletedLine() {
        var scene = scene(1, true, -1, true);
        assertTrue(found(scene).stream().anyMatch(x -> x.noteIndex() == 1 && x.connected()));
        var blank = scene(1, true, -1, false);
        System.arraycopy(blank.gray, 0, scene.gray, 0, scene.gray.length);
        noConnected(scene);
    }
}
