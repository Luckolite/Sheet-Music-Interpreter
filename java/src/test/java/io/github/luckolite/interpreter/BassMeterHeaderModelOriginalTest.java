// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class BassMeterHeaderModelOriginalTest {
    static final class Page {
        final int scale, w, h;
        final byte[] labels, gray;

        Page(int scale) {
            this.scale = scale;
            w = 300 * scale;
            h = 200 * scale;
            labels = new byte[w * h];
            gray = new byte[w * h];
            Arrays.fill(gray, (byte) 255);
            for (int i = 0; i < 5; i++) rect(20, 80 + i * 16, 260, 1, 4);
        }

        void rect(int x, int y, int width, int height, int label) {
            for (int yy = y * scale; yy < (y + height) * scale; yy++)
                for (int xx = x * scale; xx < (x + width) * scale; xx++) {
                    labels[yy * w + xx] = (byte) label;
                    gray[yy * w + xx] = 0;
                }
        }

        MeterChangeDetector.Crop crop() {
            return new MeterChangeDetector.Crop(
                    92 * scale, 77 * scale, 144 * scale, 151 * scale, 80 * scale, 16f * scale);
        }

        boolean owns() {
            return MeterHeaderInkGuard.owns(crop(), labels, gray, w, h);
        }

        void bass(boolean upper, boolean lower, boolean tail, int offset) {
            rect(100, 82, 18, 26, 3);
            if (upper) rect(126, 86, 5, 5, 5);
            if (lower) rect(126 + offset, 102, 5, 5, 5);
            if (tail) rect(102, 108, 5, 23, 3);
        }
    }

    @Test
    public void fullSourceComponentUsesExistingDotsAndTailAtThreeScales() {
        for (int scale : new int[] {1, 2, 3}) {
            var p = new Page(scale);
            p.bass(true, true, true, 0);
            assertTrue(p.owns());
        }
    }

    @Test
    public void missingOrDisplacedDotsAndMissingTailStayUnknown() {
        for (int scale : new int[] {1, 2, 3})
            for (int variant = 0; variant < 4; variant++) {
                var p = new Page(scale);
                p.bass(variant != 0, variant != 1, variant != 2, variant == 3 ? 8 : 0);
                assertFalse(p.owns());
            }
    }

    @Test
    public void keySharpAndFlatWithNumeralTerminalsRemainMeterRivals() {
        for (int scale : new int[] {1, 2, 3})
            for (boolean flat : new boolean[] {false, true}) {
                var p = new Page(scale);
                p.rect(126, 86, 5, 5, 5);
                p.rect(126, 102, 5, 5, 5);
                if (flat) {
                    p.rect(100, 82, 3, 48, 3);
                    p.rect(100, 108, 19, 3, 3);
                    p.rect(116, 108, 3, 18, 3);
                    p.rect(100, 123, 19, 3, 3);
                } else {
                    p.rect(104, 82, 3, 48, 3);
                    p.rect(112, 82, 3, 48, 3);
                    p.rect(100, 94, 19, 3, 3);
                    p.rect(100, 106, 19, 3, 3);
                }
                assertFalse(p.owns());
            }
    }

    @Test
    public void clippedComponentOrWrongStaffPhaseCannotOwnTheCrop() {
        var p = new Page(1);
        p.bass(true, true, true, 0);
        assertFalse(
                MeterHeaderInkGuard.owns(
                        new MeterChangeDetector.Crop(105, 77, 145, 151, 80, 16),
                        p.labels,
                        p.gray,
                        p.w,
                        p.h));
        assertFalse(
                MeterHeaderInkGuard.owns(
                        new MeterChangeDetector.Crop(92, 77, 144, 151, 98, 16),
                        p.labels,
                        p.gray,
                        p.w,
                        p.h));
    }

    @Test
    public void anyAdditionalDigitOrUnexplainedComponentKeepsTheRival() {
        for (int variant = 0; variant < 3; variant++) {
            var p = new Page(1);
            p.bass(true, true, true, 0);
            if (variant == 0) p.rect(133, 117, 6, 15, 3);
            else if (variant == 1) p.rect(126, 120, 5, 5, 3);
            else p.rect(137, 90, 1, 1, 5);
            assertFalse(p.owns());
        }
    }

    @Test
    public void typedExclusionRequiresTheGenuineSourceSnapshotAndCannotBeForged() {
        var p = new Page(1);
        p.bass(true, true, true, 0);
        var regions = List.of(new MeasureRegion(.1f, .9f, .1f, .9f));
        var source = AcceptedMeterHeaderBounds.source(p.labels, p.gray, p.w, p.h, regions);
        var observation = ScoreMeterProducerSource.headerExclusion(source, 0, 0, p.crop());
        assertNotNull(observation);
        var captured = ScoreMeterProducerSource.capture(source, List.of(observation), List.of());
        assertFalse(captured.readings().get(0).exclusionModel().isEmpty());
        assertFalse(captured.readings().get(0).sampledSupport().isEmpty());
        assertTrue(captured.corroboratedReadings().isEmpty());
        var forged =
                new ScoreMeterProducerSource.Observation(
                        source, 0, 0, p.crop(), "", 0, "SOURCE_BOUND_HEADER_MODEL_EXCLUSION");
        assertTrue(
                ScoreMeterProducerSource.capture(source, List.of(forged), List.of())
                        .readings()
                        .get(0)
                        .exclusionModel()
                        .isEmpty());
        var foreign =
                AcceptedMeterHeaderBounds.source(
                        p.labels.clone(), p.gray.clone(), p.w, p.h, regions);
        var borrowed =
                new ScoreMeterProducerSource.Observation(
                        foreign,
                        0,
                        0,
                        p.crop(),
                        "",
                        0,
                        "SOURCE_BOUND_HEADER_MODEL_EXCLUSION",
                        observation.exclusion());
        assertTrue(
                ScoreMeterProducerSource.capture(foreign, List.of(borrowed), List.of())
                        .readings()
                        .get(0)
                        .exclusionModel()
                        .isEmpty());
        p.gray[0] = 0;
        assertTrue(
                ScoreMeterProducerSource.capture(source, List.of(observation), List.of())
                        .readings()
                        .get(0)
                        .exclusionModel()
                        .isEmpty());
    }

    @Test
    public void tinyCueAndTwoDisconnectedDigitBodiesArePreserved() {
        var p = new Page(1);
        p.rect(100, 82, 9, 13, 3);
        p.rect(113, 85, 3, 3, 5);
        p.rect(113, 93, 3, 3, 5);
        p.rect(101, 100, 3, 7, 5);
        assertFalse(p.owns());
        for (int scale : new int[] {1, 2, 3}) {
            p = new Page(scale);
            p.rect(100, 82, 24, 20, 3);
            p.rect(100, 116, 24, 20, 3);
            assertFalse(p.owns());
        }
    }
}
