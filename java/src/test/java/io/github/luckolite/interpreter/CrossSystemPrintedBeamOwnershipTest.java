// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original engraving places the next system's triplet above its staff bounding box. */
public class CrossSystemPrintedBeamOwnershipTest {
    private static final int W = 400, H = 500;
    private static final String[] THREE = {
        "..#######...", ".##########.", "###......###", "####.....###",
        "####.....###", "####.....###", ".##.....####", ".......####.",
        "......####..", "....#####...", "....#####...", "....#####...",
        "......####..", ".......####.", "##.....####.", "###....####.",
        "###....####.", "###....####.", ".###....###.", "..########..",
        "..########..", "....####...."
    };

    private static Object find(
            boolean foreignShaft,
            boolean foreignBeam,
            boolean unknown,
            boolean foreignRegion,
            boolean sameSystem)
            throws Exception {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 0; y < THREE.length; y++)
            for (int x = 0; x < 12; x++)
                if (THREE[y].charAt(x) == '#') g[(270 + y) * W + 178 + x] = 0;
        if (foreignBeam)
            for (int y = 317; y <= 323; y++) for (int x = 126; x <= 246; x++) g[y * W + x] = 0;
        if (foreignShaft)
            for (int x : new int[] {126, 246})
                for (int y = 320; y <= 390; y++)
                    for (int dx = -1; dx <= 1; dx++) g[y * W + x + dx] = 0;
        var n = new ArrayList<ScoreNoteEvent>();
        for (float x : new float[] {120, 180, 240})
            n.add(
                    new ScoreNoteEvent(0, x / W, 0, 0, 1, 320f / H, false, 0, 1, 2, 0, 1)
                            .withStemDirection(1));
        if (foreignRegion)
            for (float x : new float[] {120, 180, 240})
                n.add(
                        new ScoreNoteEvent(1, x / W, 0, 0, 1, 390f / H, false, 0, 1, 2, 0, 1)
                                .withStemDirection(unknown ? 0 : 1));
        var bar = new MeasureRegion(0, 1, .4f, .65f);
        var regions = new ArrayList<MeasureRegion>();
        regions.add(bar);
        if (foreignRegion)
            regions.add(new MeasureRegion(0, 1, sameSystem ? .4f : .68f, sameSystem ? .65f : .95f));
        Method m =
                TripletRhythmDetector.class.getDeclaredMethod(
                        "findOwnedNumeral",
                        byte[].class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        float.class,
                        float.class,
                        float.class,
                        boolean.class,
                        float.class,
                        float.class,
                        int.class,
                        boolean.class,
                        boolean.class,
                        ScoreNoteEvent.class,
                        List.class,
                        MeasureRegion.class,
                        List.class);
        m.setAccessible(true);
        return m.invoke(
                null, g, W, H, 120f, 240f, 320f, 320f, 20f, true, Float.NaN, Float.NaN, 3, true,
                false, n.get(0), n, bar, regions);
    }

    @Test
    public void numeralAboveNextSystemBelongsToItsAttachedBeam() throws Exception {
        assertNull(find(true, true, false, true, false));
    }

    @Test
    public void detachedNextSystemBeamCannotClaimANumeral() throws Exception {
        assertNotNull(find(false, true, false, true, false));
    }

    @Test
    public void shaftsWithoutConnectingBeamCannotClaimANumeral() throws Exception {
        assertNotNull(find(true, false, false, true, false));
    }

    @Test
    public void unknownShaftDirectionCannotClaimANumeral() throws Exception {
        assertNotNull(find(true, true, true, true, false));
    }

    @Test
    public void absentOtherSystemPreservesOrdinaryBelowStaffNumeral() throws Exception {
        assertNotNull(find(true, true, false, false, false));
    }

    @Test
    public void adjacentBarWithinSameSystemCannotClaimForeignSystemOwnership() throws Exception {
        assertNotNull(find(true, true, false, true, true));
    }

    @Test
    public void closerAttachedCurrentBeamPreservesItsNumeral() throws Exception {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 0; y < THREE.length; y++)
            for (int x = 0; x < 12; x++)
                if (THREE[y].charAt(x) == '#') g[(210 + y) * W + 178 + x] = 0;
        for (int y = 187; y <= 193; y++) for (int x = 114; x <= 234; x++) g[y * W + x] = 0;
        for (int y = 242; y <= 248; y++) for (int x = 126; x <= 246; x++) g[y * W + x] = 0;
        for (int x : new int[] {114, 234})
            for (int y = 120; y <= 190; y++) for (int dx = -1; dx <= 1; dx++) g[y * W + x + dx] = 0;
        for (int x : new int[] {126, 246})
            for (int y = 245; y <= 300; y++) for (int dx = -1; dx <= 1; dx++) g[y * W + x + dx] = 0;
        var n = new ArrayList<ScoreNoteEvent>();
        for (float x : new float[] {120, 180, 240})
            n.add(
                    new ScoreNoteEvent(0, x / W, 0, 0, 1, 300f / H, false, 0, 1, 2, 0, 1)
                            .withStemDirection(1));
        for (float x : new float[] {120, 180, 240})
            n.add(
                    new ScoreNoteEvent(1, x / W, 0, 0, 1, 120f / H, false, 0, 1, 2, 0, 1)
                            .withStemDirection(-1));
        var bar = new MeasureRegion(0, 1, .45f, .7f);
        Method m =
                TripletRhythmDetector.class.getDeclaredMethod(
                        "findOwnedNumeral",
                        byte[].class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        float.class,
                        float.class,
                        float.class,
                        boolean.class,
                        float.class,
                        float.class,
                        int.class,
                        boolean.class,
                        boolean.class,
                        ScoreNoteEvent.class,
                        List.class,
                        MeasureRegion.class,
                        List.class);
        m.setAccessible(true);
        assertNotNull(
                m.invoke(
                        null,
                        g,
                        W,
                        H,
                        120f,
                        240f,
                        300f,
                        300f,
                        20f,
                        true,
                        Float.NaN,
                        Float.NaN,
                        3,
                        true,
                        false,
                        n.get(0),
                        n,
                        bar,
                        List.of(bar, new MeasureRegion(0, 1, .1f, .34f))));
    }
}
