// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original staff/rectangle controls for density-inferred final-system divisions. */
public class FinalFullRestRowTest {
    final int w = 400, h = 240;
    final MeasureRegion whole = new MeasureRegion(0, 1, .2f, .8f);

    List<MeasureRegion> split(int n) {
        var a = new ArrayList<MeasureRegion>();
        for (int i = 0; i < n; i++)
            a.add(
                    new MeasureRegion(
                            i / (float) n + (i > 0 ? .002f : 0),
                            (i + 1f) / n - (i + 1 < n ? .002f : 0),
                            .2f,
                            .8f));
        return a;
    }

    HalfRestRecognitionTest.Page page() {
        var p = new HalfRestRecognitionTest.Page();
        p.rect(190, 97, 209, 104);
        return p;
    }

    List<MeasureRegion> read(
            HalfRestRecognitionTest.Page p, List<MeasureRegion> raw, List<MeasureRegion> fitted) {
        return PrintedMeasureRhythmGuard.reconcile(raw, fitted, p.labels, p.gray, w, h);
    }

    @Test
    public void soleWholeRestPreservesOneMeasure() {
        for (int n : new int[] {2, 3, 4}) {
            var p = page();
            var gray = p.gray.clone();
            var labels = p.labels.clone();
            assertEquals(List.of(whole), read(p, List.of(whole), split(n)));
            assertArrayEquals(gray, p.gray);
            assertArrayEquals(labels, p.labels);
        }
    }

    @Test
    public void realInternalBarsRemain() {
        var p = page();
        p.rect(132, 80, 134, 144);
        p.rect(265, 80, 267, 144);
        assertEquals(split(3), read(p, List.of(whole), split(3)));
    }

    @Test
    public void repeatedMultiRestGeometryIsNotCollapsed() {
        var p = page();
        var expanded = Collections.nCopies(20, whole);
        assertEquals(expanded, read(p, List.of(whole), expanded));
    }

    @Test
    public void emptyStaffCannotProveOneRest() {
        var p = new HalfRestRecognitionTest.Page();
        assertEquals(split(3), read(p, List.of(whole), split(3)));
    }

    @Test
    public void soundingHeadPreventsCollapse() {
        var p = page();
        p.ellipse(300, 128, 10, 6, true);
        p.rect(309, 83, 310, 128);
        assertEquals(split(3), read(p, List.of(whole), split(3)));
    }

    @Test
    public void halfRestCannotProveFullMeasureSilence() {
        var p = new HalfRestRecognitionTest.Page();
        p.rect(190, 104, 209, 111);
        assertEquals(split(3), read(p, List.of(whole), split(3)));
    }

    @Test
    public void alreadyDetectedMultipleMeasuresRemain() {
        var p = page();
        var raw = split(2);
        assertEquals(raw, read(p, raw, raw));
    }

    @Test
    public void earlierRowIsNotReinterpretedAsFinal() {
        var p = page();
        var later = new MeasureRegion(0, 1, .85f, .99f);
        var fitted = new ArrayList<>(split(3));
        fitted.add(later);
        assertEquals(
                fitted,
                PrintedMeasureRhythmGuard.preserveFinalRestRow(
                        List.of(whole, later), fitted, p.labels, p.gray, w, h));
    }
}
