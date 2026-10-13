// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original raster integration; the caller-owned OCR detector deliberately returns no text. */
public final class RasterMeterConsumerTest {
    private static final List<MeasureRegion> MEASURES = List.of(new MeasureRegion(0, 1, 0, 1));

    private static MusicalOcr emptyOcr() throws Exception {
        return new MusicalOcr(
                new PortableOcr.Inference() {
                    public float[][] detect(float[] chw, int w, int h) {
                        return new float[h][w];
                    }

                    public float[][] recognize(float[] chw, int w, int h) {
                        throw new AssertionError("blank detector cannot recognize");
                    }

                    public List<String> dictionary() {
                        return List.of("4");
                    }
                },
                new MeterFontMatcher(Path.of("java/assets/Bravura.otf").toFile()));
    }

    @Test
    public void portableConsumerRetainsRasterMeterWithoutOcrVotes() throws Exception {
        var p = RasterMeterReadingsTest.fours(16);
        assertEquals(
                List.of(new ScoreMeterChange(0, 4, 4)),
                emptyOcr().meters(p.labels, p.gray, p.w, p.h, MEASURES, List.of()));
    }

    @Test
    public void portableConsumerDoesNotLoseCompetingRasterChoices() throws Exception {
        var p = RasterMeterReadingsTest.fours(16);
        p.common(true);
        p.staff();
        assertTrue(emptyOcr().meters(p.labels, p.gray, p.w, p.h, MEASURES, List.of()).isEmpty());
    }

    @Test
    public void standaloneWithoutOcrUsesSharedRasterReading() {
        var p = RasterMeterReadingsTest.fours(16);
        assertEquals(
                List.of(new ScoreMeterChange(0, 4, 4)),
                SheetInterpreter.analyze(p.labels, p.gray, p.w, p.h).meterChanges());
    }

    @Test
    public void standaloneCannotResurrectAnOcrConflict() throws Exception {
        var p = RasterMeterReadingsTest.fours(16);
        p.common(true);
        p.staff();
        assertTrue(
                SheetInterpreter.analyze(
                                p.labels,
                                p.gray,
                                p.w,
                                p.h,
                                SheetInterpreter.Annotations.EMPTY,
                                emptyOcr())
                        .meterChanges()
                        .isEmpty());
    }

    @Test
    public void explicitAnnotationsRetainPriority() throws Exception {
        var p = RasterMeterReadingsTest.fours(16);
        p.common(true);
        p.staff();
        var annotated =
                new SheetInterpreter.Annotations(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new ScoreMeterChange(0, 3, 4)));
        assertEquals(
                annotated.meters(),
                SheetInterpreter.analyze(p.labels, p.gray, p.w, p.h, annotated, emptyOcr())
                        .meterChanges());
    }
}
