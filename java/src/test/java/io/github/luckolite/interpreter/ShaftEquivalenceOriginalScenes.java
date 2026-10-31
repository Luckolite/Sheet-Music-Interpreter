// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original AA-fringe input. No commercial pixels, producer mocks or post-result tuning. */
public final class ShaftEquivalenceOriginalScenes {
    static final List<MeasureRegion> MEASURES = List.of(new MeasureRegion(.06f, .94f, .15f, .9f));

    static CompleteRailOriginalScene.Page page(String mode) {
        var p = new CompleteRailOriginalScene.Page(false);
        for (int x : new int[] {150, 248, 346}) {
            p.head(x, 172, x == 150 ? 255 : 50);
            p.rect(x + 10, x + 11, 90, 172, OmrMeasurePostProcessor.STEM_OR_REST, 35);
        }
        p.rect(160, 357, 90, 94, OmrMeasurePostProcessor.STEM_OR_REST, 35);
        p.rect(160, 357, 102, 106, OmrMeasurePostProcessor.STEM_OR_REST, 35);
        // A genuine AA fringe is below the existing185 law, above the170 raw/sole law.
        for (int y = 90; y <= 172; y++) p.g[y * p.w + 160] = (byte) 180;
        if (mode.equals("gap")) p.g[120 * p.w + 160] = (byte) 255;
        if (mode.equals("blackPlate"))
            p.rect(160, 357, 90, 172, OmrMeasurePostProcessor.STEM_OR_REST, 35);
        if (mode.equals("palePlate"))
            p.rect(160, 357, 90, 172, OmrMeasurePostProcessor.STEM_OR_REST, 180);
        if (mode.equals("foreignVoice")) {
            p.head(199, 158, 195);
            p.rect(188, 189, 158, 250, OmrMeasurePostProcessor.STEM_OR_REST, 35);
        }
        return p;
    }

    static CompleteRailOriginalScene.Page marginPage() {
        var p = new CompleteRailOriginalScene.Page(false);
        for (int x : new int[] {150, 248, 346}) {
            p.head(x, 172, x == 150 ? 255 : 50);
            p.rect(x - 11, x - 10, 172, 248, OmrMeasurePostProcessor.STEM_OR_REST, 35);
        }
        p.rect(139, 336, 244, 248, OmrMeasurePostProcessor.STEM_OR_REST, 35);
        p.rect(139, 336, 232, 236, OmrMeasurePostProcessor.STEM_OR_REST, 35);
        for (int y = 172; y <= 248; y++) p.g[y * p.w + 139] = (byte) 180;
        return p;
    }

    static OmrScoreInterpreter.Analysis marginAnalysis() {
        var p = marginPage();
        var before = p.g.clone();
        var labels = p.l.clone();
        // Encode inclusive pixel126 without a float product rounding it up to127.
        var measures = List.of(new MeasureRegion(Math.nextDown(126f / 500), .94f, .15f, .9f));

        var a = OmrScoreInterpreter.analyze(p.l, p.g, p.w, p.h, measures);

        if (!Arrays.equals(before, p.g) || !Arrays.equals(labels, p.l))
            throw new AssertionError("margin fixture mutation");

        return a;
    }

    static OmrScoreInterpreter.Analysis analysis(String mode) {
        var p = page(mode);
        var g = p.g.clone();
        var l = p.l.clone();

        var a = OmrScoreInterpreter.analyze(p.l, p.g, p.w, p.h, MEASURES);

        if (!Arrays.equals(g, p.g) || !Arrays.equals(l, p.l))
            throw new AssertionError("fixture mutation");

        return a;
    }
}
