// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric engraving, with a partial semantic mask on a beam tip. */
public class WideBeamOwnershipTest {
    static final int W = 320, H = 240;
    byte[] gray = new byte[W * H], labels = new byte[W * H];

    void ink(int x, int y, int label) {
        gray[y * W + x] = 0;
        labels[y * W + x] = (byte) label;
    }

    void oval(int cx, int cy, int rx, int ry, boolean semantic) {
        for (int y = cy - ry; y <= cy + ry; y++)
            for (int x = cx - rx; x <= cx + rx; x++)
                if (Math.pow((x - cx) / (double) rx, 2) + Math.pow((y - cy) / (double) ry, 2)
                        <= 1) {
                    gray[y * W + x] = 0;
                    if (semantic) labels[y * W + x] = 2;
                }
    }

    void scene(boolean bulge, boolean broken, boolean mirror) {
        Arrays.fill(gray, (byte) 255);
        for (int y = 80; y <= 144; y += 16) for (int x = 15; x < 305; x++) ink(x, y, 4);
        for (int y = 88; y <= 150; y++) for (int x = 109; x <= 111; x++) ink(x, y, 1);
        for (int y = 72; y <= 138; y++) for (int x = 157; x <= 159; x++) ink(x, y, 1);
        for (int x = 110; x <= 158; x++) {
            int bottom = 150 - Math.round((x - 110) * .25f);
            for (int y = bottom - 7; y <= bottom; y++) ink(x, y, 1);
        }
        oval(120, 88, 11, 8, true);
        oval(168, 72, 11, 8, true);
        if (bulge) oval(151, 134, 12, 12, false);
        for (int y = 130; y <= 139; y++)
            for (int x = 143; x <= 158; x++)
                if ((gray[y * W + x] & 255) < 165) labels[y * W + x] = 2;
        if (broken)
            for (int x = 125; x <= 138; x++)
                for (int y = 125; y <= 155; y++) {
                    gray[y * W + x] = (byte) 255;
                    labels[y * W + x] = 0;
                }
        if (mirror)
            for (int i = 0; i < gray.length / 2; i++) {
                int j = gray.length - 1 - i;
                byte t = gray[i];
                gray[i] = gray[j];
                gray[j] = t;
                t = labels[i];
                labels[i] = labels[j];
                labels[j] = t;
            }
    }

    List<ScoreNoteEvent> notes() {
        return OmrScoreInterpreter.analyze(
                        labels, gray, W, H, List.of(new MeasureRegion(.02f, .98f, .02f, .98f)))
                .notes();
    }

    @Test
    public void downStemWideTipIsNotAThirdNote() {
        scene(false, false, false);
        assertEquals(2, notes().size());
    }

    @Test
    public void upStemWideTipIsNotAThirdNote() {
        scene(false, false, true);
        assertEquals(2, notes().size());
    }

    @Test
    public void realRoundedHeadOnSharedShaftSurvives() {
        scene(true, false, false);
        assertEquals(3, notes().size());
    }

    @Test
    public void brokenBandCannotRemoveHead() {
        scene(false, true, false);
        assertEquals(3, notes().size());
    }
}
