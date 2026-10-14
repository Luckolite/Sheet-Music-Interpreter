// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original stage-name typography and role-context controls. */
public class MixedCaseCoauthorTest {
    @SuppressWarnings("unchecked")
    static List<String> names(String text) throws Exception {
        var m = ScoreCreditsDetector.class.getDeclaredMethod("contextualAuthorNames", String.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(null, text);
    }

    @Test
    public void mixedCaseMononymKeepsWholeList() throws Exception {
        assertEquals(List.of("Morgan Vale", "WaveTone"), names("Morgan Vale and WaveTone"));
    }

    @Test
    public void commaSeparatedVariantKeepsEachPrintedToken() throws Exception {
        assertEquals(
                List.of("Morgan Vale", "WaVeTone", "Brook"), names("Morgan Vale, WaVeTone, Brook"));
    }

    @Test
    public void oneMixedCaseTokenDoesNotSupplyRole() throws Exception {
        assertTrue(names("WaveTone").isEmpty());
    }

    @Test
    public void twoUnanchoredMononymsAreNotEnough() throws Exception {
        assertTrue(names("WaveTone and SkyLine").isEmpty());
    }

    @Test
    public void instrumentTypographyIsNotCoauthor() throws Exception {
        assertTrue(names("Morgan Vale and ViOLin").isEmpty());
    }

    @Test
    public void numericPayloadNotCoauthor() throws Exception {
        assertTrue(names("Morgan Vale and Wave123").isEmpty());
    }

    @Test
    public void lowercaseFragmentNotCoauthor() throws Exception {
        assertTrue(names("Morgan Vale and waveTone").isEmpty());
    }

    @Test
    public void ordinaryNamesRemainUnchanged() throws Exception {
        assertEquals(List.of("Morgan Vale", "Tessa Brook"), names("Morgan Vale and Tessa Brook"));
    }

    static ScoreCreditsDetector.Result read(boolean arrangement) {
        var ls = new ArrayList<ScoreCreditsDetector.Line>();
        ls.add(new ScoreCreditsDetector.Line("Harbor Evening", 500, 100, 1200, 180));
        ls.add(new ScoreCreditsDetector.Line("Morgan Vale and WaveTone", 1250, 200, 1700, 235));
        if (arrangement)
            ls.add(new ScoreCreditsDetector.Line("Arranged by Tessa Brook", 1250, 240, 1700, 270));
        return ScoreCreditsDetector.detect(
                "Harbor Evening",
                List.of(new ScoreCreditsDetector.Page(1800, 2400, 400, ls, false, false)),
                Set.of());
    }

    @Test
    public void separateArrangementSuppliesAuthorContext() {
        assertEquals("Morgan Vale; WaveTone", read(true).composer());
        assertEquals("Tessa Brook", read(true).arranger());
    }

    @Test
    public void noArrangementDoesNotInventComposerRole() {
        assertEquals("", read(false).composer());
    }
}
