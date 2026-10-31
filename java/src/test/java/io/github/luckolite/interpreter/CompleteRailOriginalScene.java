// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original Apache-2.0 raster notation fixtures. Each case redraws its own pixels.
 * Partial-rail cases retain secondary hooks and are sixteenths; a damaged interior
 * does not supply a sustained HALF notation oracle. Named slash scenes retain two ordinary beams and do not assert
 * caller tremolo recognition. Genuine paired discovery/issuer authority is checked separately.
 */
public final class CompleteRailOriginalScene {
    static final String[] CASES = {
        "positiveUp",
        "positiveDown",
        "positiveSlope",
        "compactControl",
        "nearLeft1",
        "nearLeft2",
        "nearRight1",
        "nearRight2",
        "brokenInterior",
        "staffBridge",
        "slurBridge",
        "otherRailOnly",
        "foreignStem",
        "omittedOwner",
        "crossBar",
        "dualStem",
        "whole",
        "half",
        "paperCounter",
        "separateGroups",
        "attachedTremolo",
        "detachedTremolo",
        "pairedTremolo",
        "pairedTremolo3",
        "sharedShaft",
        "competingHead",
        "railLabelSymbol",
        "railLabelBackground",
        "thinInteriorSymbol",
        "thinInteriorBackground",
        "ordinaryTwoShaft",
        "chordTwoShaft",
        "pairedIssuerControl"
    };

    static final class Page {
        int w = 500, h = 330;
        byte[] l = new byte[w * h], g = new byte[w * h];

        Page(boolean widerGap) {
            Arrays.fill(g, (byte) 255);
            for (int y :
                    widerGap
                            ? new int[] {122, 140, 158, 176, 194}
                            : new int[] {130, 144, 158, 172, 186})
                rect(30, 470, y, y, OmrMeasurePostProcessor.STAFF, 60);
        }

        void rect(int a, int b, int t, int z, int label, int gray) {
            for (int y = t; y <= z; y++)
                for (int x = a; x <= b; x++) {
                    l[y * w + x] = (byte) label;
                    g[y * w + x] = (byte) gray;
                }
        }

        void head(int x, int y, int center) {
            for (int dy = -8; dy <= 8; dy++)
                for (int dx = -11; dx <= 11; dx++) {
                    double r = dx * dx / 121d + dy * dy / 64d;
                    if (r <= 1) {
                        l[(y + dy) * w + x + dx] = 2;
                        g[(y + dy) * w + x + dx] = (byte) (r < .55 ? center : 50);
                    }
                }
        }
    }

    static OmrScoreInterpreter.Analysis scene(String mode) {
        if (mode.equals("pairedIssuerControl")) return pairedIssuerControl();
        boolean labelSymbol = mode.equals("railLabelSymbol"),
                labelBackground = mode.equals("railLabelBackground"),
                thinSymbol = mode.equals("thinInteriorSymbol"),
                thinBackground = mode.equals("thinInteriorBackground");
        if (labelSymbol || labelBackground || thinSymbol || thinBackground) mode = "positiveUp";
        Page p = new Page(mode.equals("nearLeft2") || mode.equals("nearRight2"));
        boolean down = mode.equals("positiveDown");
        int[] xs =
                mode.equals("compactControl")
                        ? new int[] {150, 192, 234}
                        : mode.equals("separateGroups")
                                ? new int[] {120, 162, 204, 310, 352, 394}
                                : (mode.equals("ordinaryTwoShaft") || mode.equals("chordTwoShaft"))
                                        ? new int[] {150, 248}
                                        : new int[] {150, 248, 346};
        int[] shafts = new int[xs.length];
        int base = down ? 248 : 90, dir = down ? -1 : 1;
        for (int i = 0; i < xs.length; i++) {
            p.head(xs[i], 172, i == 0 ? (mode.equals("paperCounter") ? 255 : 195) : 50);
            shafts[i] = xs[i] + (down ? -11 : 10);
            if (!mode.equals("whole"))
                p.rect(
                        shafts[i],
                        shafts[i] + 1,
                        Math.min(
                                172,
                                base
                                        + (mode.equals("positiveSlope")
                                                ? Math.round((shafts[i] - (xs[0] + 10)) * .02f)
                                                : 0)),
                        Math.max(
                                172,
                                base
                                        + (mode.equals("positiveSlope")
                                                ? Math.round((shafts[i] - (xs[0] + 10)) * .02f)
                                                : 0)),
                        OmrMeasurePostProcessor.STEM_OR_REST,
                        35);
        }
        boolean slope = mode.equals("positiveSlope");
        if (!Set.of("whole", "half", "pairedTremolo", "pairedTremolo3").contains(mode))
            for (int x = shafts[0]; x <= shafts[shafts.length - 1] + 1; x++) {
                int dy = slope ? Math.round((x - shafts[0]) * .02f) : 0;
                p.rect(
                        x,
                        x,
                        base + (down ? -4 : 0) + dy,
                        base + (down ? 0 : 4) + dy,
                        OmrMeasurePostProcessor.STEM_OR_REST,
                        35);
                p.rect(
                        x,
                        x,
                        base + dir * 12 + (down ? -4 : 0) + dy,
                        base + dir * 12 + (down ? 0 : 4) + dy,
                        OmrMeasurePostProcessor.STEM_OR_REST,
                        35);
            }
        int low = base + dir * 12 + (down ? -4 : 0), high = base + dir * 12 + (down ? 0 : 4);
        int cut = shafts[0] + Math.round((shafts[1] - shafts[0]) * .375f);
        if (mode.startsWith("nearLeft")) {
            int n = mode.endsWith("2") ? 2 : 1;
            p.rect(shafts[0] + 1, shafts[0] + n, low, high, 0, 255);
        }
        if (mode.startsWith("nearRight")) {
            int n = mode.endsWith("2") ? 2 : 1;
            p.rect(shafts[1] - n, shafts[1] - 1, low, high, 0, 255);
        }
        if (Set.of("brokenInterior", "staffBridge", "slurBridge", "otherRailOnly").contains(mode)) {
            p.rect(cut - 2, cut + 2, low, high, 0, 255);
            if (mode.equals("staffBridge"))
                p.rect(
                        cut - 2,
                        cut + 2,
                        (low + high) / 2,
                        (low + high) / 2,
                        OmrMeasurePostProcessor.STAFF,
                        60);
            if (mode.equals("slurBridge")) p.rect(cut - 2, cut + 2, low, low, 0, 35);
        }
        if (mode.equals("foreignStem")) {
            p.rect(shafts[0], shafts[0] + 1, base + 20, 164, 0, 255);
            p.rect(
                    shafts[0] + 25,
                    shafts[0] + 26,
                    base,
                    172,
                    OmrMeasurePostProcessor.STEM_OR_REST,
                    35);
        }
        if (mode.equals("omittedOwner"))
            for (int y = 164; y <= 180; y++)
                for (int x = xs[1] - 11; x <= xs[1] + 11; x++)
                    if (p.l[y * p.w + x] == 2) p.l[y * p.w + x] = 0;
        if (mode.equals("dualStem"))
            p.rect(xs[0] - 11, xs[0] - 10, 172, 260, OmrMeasurePostProcessor.STEM_OR_REST, 35);
        if (mode.equals("separateGroups"))
            p.rect(shafts[2] + 2, shafts[3] - 1, base - 1, base + 17, 0, 255);
        if (mode.equals("attachedTremolo"))
            for (int k = 0; k < 3; k++)
                for (int x = shafts[0] - 8; x <= shafts[0] + 8; x++) {
                    int y = 113 + k * 9 - Math.round((x - shafts[0]) * .3f);
                    p.rect(x, x, y - 1, y + 1, 0, 35);
                }
        if (mode.equals("pairedTremolo") || mode.equals("pairedTremolo3"))
            for (int k = 0; k < (mode.endsWith("3") ? 3 : 2); k++)
                p.rect(
                        shafts[0] + 5,
                        shafts[1] - 5,
                        base + 5 + k * 10,
                        base + 8 + k * 10,
                        OmrMeasurePostProcessor.STEM_OR_REST,
                        35);
        if (mode.equals("detachedTremolo"))
            for (int k = 0; k < 3; k++)
                for (int x = shafts[0] + 5; x <= shafts[0] + 20; x++) {
                    int y = 125 + k * 9 - Math.round((x - shafts[0]) * .3f);
                    p.rect(x, x, y - 1, y + 1, OmrMeasurePostProcessor.STEM_OR_REST, 35);
                }
        if (mode.equals("chordTwoShaft")) p.head(xs[0], 151, 195);
        if (mode.equals("sharedShaft")) p.head(xs[0] + 8, 157, 195);
        if (mode.equals("competingHead")) {
            p.head(xs[0] + 49, 158, 50);
            p.rect(xs[0] + 38, xs[0] + 39, 158, 250, OmrMeasurePostProcessor.STEM_OR_REST, 35);
        }
        var ms =
                mode.equals("crossBar")
                        ? List.of(
                                new MeasureRegion(.06f, .43f, .15f, .9f),
                                new MeasureRegion(.43f, .94f, .15f, .9f))
                        : List.of(new MeasureRegion(.06f, .94f, .15f, .9f));
        if (mode.equals("crossBar")) p.rect(215, 216, 130, 186, OmrMeasurePostProcessor.STAFF, 35);
        if (labelSymbol || labelBackground) {
            int label = labelSymbol ? OmrMeasurePostProcessor.SYMBOL : 0;
            for (int x = shafts[0] + 2; x < shafts[shafts.length - 1]; x++)
                for (int y :
                        new int[] {
                            base, base + 1, base + 2, base + 3, base + 4, low, low + 1, low + 2,
                            low + 3, low + 4
                        })
                    if (p.l[y * p.w + x] == OmrMeasurePostProcessor.STEM_OR_REST)
                        p.l[y * p.w + x] = (byte) label;
        }
        if (thinSymbol || thinBackground) {
            int label = thinSymbol ? OmrMeasurePostProcessor.SYMBOL : 0;
            for (int x = shafts[0] + 2; x < shafts[1]; x++) {
                p.rect(x, x, low, high, 0, 255);
                p.rect(x, x, (low + high) / 2, (low + high) / 2, label, 35);
            }
            for (int x = shafts[0]; x <= shafts[1]; x++)
                if (x - shafts[0] <= 14 || shafts[1] - x <= 14) p.rect(x, x, low, high, label, 35);
            int span = shafts[1] - shafts[0];
            float edge = Math.min(.2f, 2f / span);
            for (float f : new float[] {edge, .25f, .5f, .75f, 1 - edge}) {
                int x = Math.round(shafts[0] + span * f);
                p.rect(x - 2, x + 2, low, high, label, 35);
            }
        }
        if (mode.equals("ordinaryTwoShaft") || mode.equals("chordTwoShaft")) {
            if (shafts.length != 2 || Arrays.stream(shafts).distinct().count() != 2)
                throw new AssertionError("fixture requires exactly2 distinct physical shafts");
            for (int shaft : shafts)
                for (int y = 90; y <= 172; y++)
                    if ((p.g[y * p.w + shaft] & 255) >= 165)
                        throw new AssertionError("missing own physical shaft");
            if (mode.equals("chordTwoShaft")
                    && ((p.l[151 * p.w + 150] & 255) != OmrMeasurePostProcessor.NOTEHEAD
                            || (p.g[151 * p.w + 160] & 255) >= 165))
                throw new AssertionError("chord not attached to original shaft");
        }
        var beforeL = p.l.clone();
        var beforeG = p.g.clone();
        var notes = OmrScoreInterpreter.analyze(p.l, p.g, p.w, p.h, ms);
        if (!Arrays.equals(beforeL, p.l) || !Arrays.equals(beforeG, p.g))
            throw new AssertionError("mutated source");
        return notes;
    }

    static OmrScoreInterpreter.Analysis pairedIssuerControl() {
        try {
            new PrintedEndpointValueIssuerOriginalTest()
                    .realPhysicalScopeAndSourceBodyIssuePrintedQuarter();
            var p = TremoloPrintedValueScene.page(0);
            var d = PairedTremoloInk.discover(p.gray(), 240, 150, p.heads());
            if (d.pairs().size() != 1) throw new AssertionError("actualpairedrole absent");
            var b = p.gray().clone();
            var l = p.labels().clone();
            var out =
                    OmrScoreInterpreter.analyze(
                            p.labels(),
                            p.gray(),
                            240,
                            150,
                            List.of(new MeasureRegion(0f, 1f, 0f, 1f)));
            if (!Arrays.equals(b, p.gray()) || !Arrays.equals(l, p.labels()))
                throw new AssertionError("source mutation");
            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
