// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original boxes and a five-rule raster; no source-score pixels. */
public class CurvedMeasureGeometryTest {
    @SuppressWarnings("unchecked")
    private static List<List<MeasureRegion>> rows(List<MeasureRegion> boxes) throws Exception {
        var method = MeasureNumberReconciler.class.getDeclaredMethod("rows", List.class);
        method.setAccessible(true);
        var output = new ArrayList<List<MeasureRegion>>();
        for (Object row : (List<?>) method.invoke(null, boxes)) {
            var field = row.getClass().getDeclaredField("measures");
            field.setAccessible(true);
            output.add((List<MeasureRegion>) field.get(row));
        }
        return output;
    }

    @Test
    public void adjacentClimbingBoxesRemainInLeftToRightOrder() throws Exception {
        var boxes =
                List.of(
                        new MeasureRegion(.1f, .3f, .52f, .62f),
                        new MeasureRegion(.31f, .51f, .50f, .60f),
                        new MeasureRegion(.52f, .72f, .48f, .58f),
                        new MeasureRegion(.73f, .93f, .46f, .56f));
        assertEquals(List.of(boxes), rows(boxes));
    }

    @Test
    public void adjacentFallingBoxesRemainInLeftToRightOrder() throws Exception {
        var boxes =
                List.of(
                        new MeasureRegion(.1f, .3f, .46f, .56f),
                        new MeasureRegion(.31f, .51f, .48f, .58f),
                        new MeasureRegion(.52f, .72f, .50f, .60f),
                        new MeasureRegion(.73f, .93f, .52f, .62f));
        assertEquals(List.of(boxes), rows(boxes));
    }

    @Test
    public void disconnectedSystemBoxesMustNotChainTogether() throws Exception {
        var a = new MeasureRegion(.1f, .4f, .25f, .35f);
        var b = new MeasureRegion(.1f, .4f, .4f, .5f);
        assertEquals(List.of(List.of(a), List.of(b)), rows(List.of(b, a)));
    }

    @Test
    public void overlappingHorizontalRowsMustNotJoin() throws Exception {
        var a = new MeasureRegion(.1f, .5f, .2f, .3f);
        var b = new MeasureRegion(.45f, .8f, .24f, .34f);
        assertEquals(List.of(List.of(a), List.of(b)), rows(List.of(b, a)));
    }

    @Test
    public void aSecondProjectionOfTheSameCurvedStaffDoesNotCreateAnotherSystem() throws Exception {
        int w = 1200, h = 400;
        byte[] gray = new byte[w * h], labels = new byte[w * h];
        Arrays.fill(gray, (byte) 235);
        for (int x = 30; x < 1170; x++)
            for (int line = 0; line < 5; line++) {
                float bottom = 250 - 40 * Math.max(0, Math.min(1, (x - 400) / 400f));
                int y = Math.round(bottom - line * 14);
                gray[y * w + x] = 45;
                gray[(y + 1) * w + x] = 45;
            }
        Class<?> type = Class.forName(OmrMeasurePostProcessor.class.getName() + "$StaffRun");
        var ctor =
                type.getDeclaredConstructor(
                        int.class,
                        int.class,
                        float.class,
                        int.class,
                        int.class,
                        List.class,
                        float.class);
        ctor.setAccessible(true);
        var runs = new ArrayList<Object>();
        runs.add(ctor.newInstance(154, 210, 14f, 30, 1169, List.of(30, 1169), 0f));
        var recover =
                OmrMeasurePostProcessor.class.getDeclaredMethod(
                        "recoverRawStaffs",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        float.class,
                        byte[].class);
        recover.setAccessible(true);
        recover.invoke(null, labels, gray, w, h, runs, 0f, gray);
        assertEquals(1, runs.size());
    }

    @Test
    public void newlyTrackedRowsRequireInkContrastRatherThanPaperTexture() throws Exception {
        int w = 1200, h = 400;
        byte[] gray = new byte[w * h], labels = new byte[w * h];
        Arrays.fill(gray, (byte) 235);
        for (int x = 30; x < 1170; x++)
            for (int line = 0; line < 5; line++) {
                int y =
                        Math.round(
                                250 - 40 * Math.max(0, Math.min(1, (x - 400) / 400f)) - line * 14);
                gray[y * w + x] = 45;
                gray[(y + 1) * w + x] = 45;
            }
        for (int x : new int[] {300, 900})
            for (int y = 190; y <= 252; y++) {
                if (x == 900 && (y < 154 || y > 211)) continue;
                gray[y * w + x] = (byte) (x == 300 ? 220 : 55);
                labels[y * w + x] = OmrMeasurePostProcessor.STEM_OR_REST;
            }
        for (int y = 154; y <= 211; y++) {
            gray[y * w + 900] = 55;
            labels[y * w + 900] = OmrMeasurePostProcessor.STEM_OR_REST;
        }
        var track = StaffPitchTrack.detect(gray, w, h, 154, 210, 14);
        assertNotNull(track);
        var method =
                OmrMeasurePostProcessor.class.getDeclaredMethod(
                        "findBoundaries",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int[].class,
                        float.class,
                        int.class,
                        int.class,
                        float.class,
                        StaffPitchTrack.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        var boundaries =
                (List<Integer>)
                        method.invoke(
                                null,
                                labels,
                                gray,
                                w,
                                h,
                                new int[] {154, 168, 182, 196, 210},
                                14f,
                                30,
                                1169,
                                0f,
                                track);
        assertEquals(3, boundaries.size());
        assertEquals(900, boundaries.get(1), 2);
    }
}
