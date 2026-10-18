// Copyright 2026 Music Sheets contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original accepted header measurement roles; no OCR/font/score raster is invoked. */
public class AcceptedMeterHeaderBoundsTest {
    static final int W = 800, H = 360;
    static final MeasureRegion M = WholeRestClassificationControlsTest.M;
    static final ScoreMeterChange VALUE = new ScoreMeterChange(0, 12, 8);
    static final MeterChangeDetector.Crop A =
            new MeterChangeDetector.Crop(200, 97, 300, 169, 100, 16);
    static final MeterChangeDetector.Crop B =
            new MeterChangeDetector.Crop(199, 96, 301, 169, 101, 16.2f);

    static class Scene {
        byte[] gray = WholeRestClassificationControlsTest.frame(false, 542, 0),
                labels = new byte[W * H];
        AcceptedMeterHeaderBounds.Source source;
        List<AcceptedMeterHeaderBounds.Header> headers;

        Scene() {
            draw(0);
            source = AcceptedMeterHeaderBounds.source(labels, gray, W, H, List.of(M));
            headers = List.of(header(source, M, VALUE, A), header(source, M, VALUE, B));
        }

        void box(int l, int t, int r, int b) {
            for (int y = t; y <= b; y++)
                for (int x = l; x <= r; x++) {
                    gray[y * W + x] = 0;
                    labels[y * W + x] = 5;
                }
        }

        void draw(int dx) { // Original complete block12 over closed two-counter8.
            box(210 + dx, 102, 212 + dx, 118);
            box(222 + dx, 102, 236 + dx, 104);
            box(234 + dx, 102, 236 + dx, 110);
            box(222 + dx, 109, 236 + dx, 111);
            box(222 + dx, 110, 224 + dx, 118);
            box(222 + dx, 116, 236 + dx, 118);
            box(216 + dx, 138, 230 + dx, 140);
            box(216 + dx, 146, 230 + dx, 148);
            box(216 + dx, 154, 230 + dx, 156);
            box(216 + dx, 138, 218 + dx, 156);
            box(228 + dx, 138, 230 + dx, 156);
        }

        List<MeterChangeDetector.Crop> run() {
            byte[] g = gray.clone(), l = labels.clone();
            var out = AcceptedMeterHeaderBounds.normalize(source, headers);
            assertArrayEquals(g, gray);
            assertArrayEquals(l, labels);
            return out;
        }
    }

    static AcceptedMeterHeaderBounds.Header header(
            AcceptedMeterHeaderBounds.Source s,
            MeasureRegion owner,
            ScoreMeterChange value,
            MeterChangeDetector.Crop crop) {
        return new AcceptedMeterHeaderBounds.Header(
                s, owner, value, crop, AcceptedMeterHeaderBounds.Producer.NATIVE);
    }

    static void unchanged(Scene s) {
        assertEquals(
                s.headers.stream().map(AcceptedMeterHeaderBounds.Header::crop).toList(), s.run());
    }

    @Test
    public void sameCompletePhysicalSupportSharesOnlyHorizontalEnvelope() {
        var s = new Scene();
        var out = s.run();
        assertEquals(199, out.get(0).left());
        assertEquals(301, out.get(0).right());
        assertEquals(301, out.get(1).right());
        assertEquals(A.top(), out.get(0).top());
        assertEquals(B.firstLine(), out.get(1).firstLine());
        assertEquals(Float.floatToRawIntBits(B.gap()), Float.floatToRawIntBits(out.get(1).gap()));
    }

    @Test
    public void differentAcceptedValuesRemainConflicting() {
        var s = new Scene();
        s.headers =
                List.of(s.headers.get(0), header(s.source, M, new ScoreMeterChange(0, 3, 4), B));
        unchanged(s);
    }

    @Test
    public void equalBytesForeignSourceDoesNotAlias() {
        var s = new Scene();
        var foreign =
                AcceptedMeterHeaderBounds.source(
                        s.labels.clone(), s.gray.clone(), W, H, List.of(M));
        s.headers = List.of(s.headers.get(0), header(foreign, M, VALUE, B));
        unchanged(s);
    }

    @Test
    public void foreignMeasureObjectDoesNotAlias() {
        var s = new Scene();
        var copy = new MeasureRegion(M.left(), M.right(), M.top(), M.bottom());
        s.headers = List.of(s.headers.get(0), header(s.source, copy, VALUE, B));
        unchanged(s);
    }

    @Test
    public void clippedComponentDoesNotSupplyAgreement() {
        var s = new Scene();
        s.headers =
                List.of(
                        s.headers.get(0),
                        header(
                                s.source,
                                M,
                                VALUE,
                                new MeterChangeDetector.Crop(211, 96, 301, 169, 101, 16.2f)));
        unchanged(s);
    }

    @Test
    public void extraIndependentSymbolIsNotDiscarded() {
        var s = new Scene();
        s.box(300, 125, 300, 125);
        unchanged(s);
    }

    @Test
    public void disjointPhysicalHeadersStaySeparate() {
        var s = new Scene();
        s.draw(200);
        s.headers =
                List.of(
                        s.headers.get(0),
                        header(
                                s.source,
                                M,
                                VALUE,
                                new MeterChangeDetector.Crop(399, 96, 501, 169, 101, 16.2f)));
        unchanged(s);
    }

    @Test
    public void emptySemanticSupportDoesNotAlias() {
        var s = new Scene();
        Arrays.fill(s.labels, (byte) 0);
        unchanged(s);
    }

    @Test
    public void rawWhiteSemanticPhantomDoesNotAlias() {
        var s = new Scene();
        Arrays.fill(s.gray, (byte) 255);
        unchanged(s);
    }

    @Test
    public void unknownProducerDoesNotAlias() {
        var s = new Scene();
        s.headers =
                List.of(
                        s.headers.get(0),
                        new AcceptedMeterHeaderBounds.Header(
                                s.source, M, VALUE, B, AcceptedMeterHeaderBounds.Producer.UNKNOWN));
        unchanged(s);
    }

    @Test
    public void clippedThirdRivalCannotManufactureAgreeingPair() {
        var s = new Scene();
        s.headers =
                List.of(
                        s.headers.get(0),
                        s.headers.get(1),
                        header(
                                s.source,
                                M,
                                VALUE,
                                new MeterChangeDetector.Crop(211, 96, 300, 169, 100, 16)));
        unchanged(s);
    }

    @Test
    public void unionCornerCannotCaptureANewForeignComponent() {
        var s = new Scene();
        s.headers =
                List.of(
                        s.headers.get(0),
                        header(
                                s.source,
                                M,
                                VALUE,
                                new MeterChangeDetector.Crop(199, 96, 301, 168, 101, 16.2f)));
        s.box(300, 168, 300, 168);
        unchanged(s);
    }

    @Test
    public void finiteResourceLimitKeepsOriginalFrames() {
        var s = new Scene();
        var big = new MeterChangeDetector.Crop(0, 0, 799, 359, 100, 16);
        s.headers =
                List.of(
                        header(s.source, M, VALUE, big),
                        header(
                                s.source,
                                M,
                                VALUE,
                                new MeterChangeDetector.Crop(0, 0, 798, 359, 100, 16)));
        unchanged(s);
    }

    @Test
    public void duplicatePhysicalHeaderResolvesRealPlateThroughExistingContext() {
        var s = new Scene();
        var literal =
                WholeRestClassifier.detect(
                                s.gray,
                                W,
                                H,
                                List.of(M),
                                List.of(new SixteenthRestDetector.Staff(100, 164, 16, 0, 1)),
                                List.of(),
                                List.of())
                        .get(0)
                        .event();
        var score =
                new ScorePageInterpretation(
                        List.of(M),
                        List.of(),
                        1,
                        List.of(),
                        List.of(),
                        List.of(VALUE),
                        List.of(literal));
        var out =
                WholeRestMeterContext.apply(
                        score,
                        s.gray,
                        W,
                        H,
                        List.of(new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 1)),
                        List.of(VALUE),
                        s.run());
        assertTrue(out.rests().get(0).isFullMeasure());
    }
}
