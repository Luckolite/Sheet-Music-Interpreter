// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original publication badge geometry; no score-derived fixture. */
public class AudioAccessBadgeEchoTest {
    static ScoreCreditsDetector.Line l(String s, float x, float y) {
        return new ScoreCreditsDetector.Line(s, x, y, x + 140, y + 25);
    }

    static boolean badge(ScoreCreditsDetector.Line q, List<ScoreCreditsDetector.Line> ls)
            throws Exception {
        var p = new ScoreCreditsDetector.Page(1800, 2400, -1, ls, false, false);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "publicationAudioAccessBadge",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class,
                        List.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p, q, ls);
    }

    @Test
    public void repeatedTrailingLetterRequiresCompleteBadge() throws Exception {
        var a = l("AUDIO", 100, 200);
        var b = l("AcCESSs", 100, 235);
        var c = l("INCLUDED", 100, 270);
        var ls = List.of(a, b, c);
        for (var q : ls) assertTrue(badge(q, ls));
    }

    @Test
    public void cleanBadgeStillWorks() throws Exception {
        var a = l("AUDIO", 100, 200);
        var b = l("ACCESS", 100, 235);
        var c = l("INCLUDED", 100, 270);
        for (var q : List.of(a, b, c)) assertTrue(badge(q, List.of(a, b, c)));
    }

    @Test
    public void standaloneEchoIsRetained() throws Exception {
        var b = l("Accesss", 100, 235);
        assertFalse(badge(b, List.of(b)));
    }

    @Test
    public void missingIncludedDoesNotFormBadge() throws Exception {
        var a = l("AUDIO", 100, 200);
        var b = l("Accesss", 100, 235);
        assertFalse(badge(b, List.of(a, b)));
    }

    @Test
    public void distantColumnsDoNotFormBadge() throws Exception {
        var a = l("AUDIO", 100, 200);
        var b = l("Accesss", 1000, 235);
        var c = l("INCLUDED", 100, 270);
        assertFalse(badge(b, List.of(a, b, c)));
    }

    @Test
    public void wrongOrderDoesNotFormBadge() throws Exception {
        var a = l("AUDIO", 100, 270);
        var b = l("Accesss", 100, 235);
        var c = l("INCLUDED", 100, 200);
        assertFalse(badge(b, List.of(a, b, c)));
    }

    @Test
    public void MultipleRepeatedLettersAreNotAssumed() throws Exception {
        var a = l("AUDIO", 100, 200);
        var b = l("Accessss", 100, 235);
        var c = l("INCLUDED", 100, 270);
        assertFalse(badge(b, List.of(a, b, c)));
    }
}
