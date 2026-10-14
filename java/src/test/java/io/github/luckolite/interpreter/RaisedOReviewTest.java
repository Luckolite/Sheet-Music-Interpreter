// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RaisedOReviewTest {
    static String name(String source, String lyrics, boolean photo) throws Exception {
        var p =
                new ScoreCreditsDetector.Page(
                        1800,
                        2400,
                        500,
                        List.of(
                                new ScoreCreditsDetector.Line(source, 800, 200, 1100, 235),
                                new ScoreCreditsDetector.Line(lyrics, 300, 800, 1500, 840)),
                        false,
                        photo);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "normalizeRaisedOReview", String.class, List.class);
        m.setAccessible(true);
        return (String) m.invoke(null, source, List.of(p));
    }

    @Test
    public void repeatedTypographySupportsName() throws Exception {
        assertEquals("Morgan Vale", name("M°rganVale", "y°u sing l°ve", false));
    }

    @Test
    public void ordinalGlyphVariantSupported() throws Exception {
        assertEquals("Morgan Vale", name("MºrganVale", "yºu sing lºve", false));
    }

    @Test
    public void unsupportedGlyphPreserved() throws Exception {
        assertEquals("M°rganVale", name("M°rganVale", "you sing love", false));
    }

    @Test
    public void oneWordInsufficient() throws Exception {
        assertEquals("M°rganVale", name("M°rganVale", "y°u y°u", false));
    }

    @Test
    public void degreesNotWords() throws Exception {
        assertEquals("M°rganVale", name("M°rganVale", "20° C and 40° C", false));
    }

    @Test
    public void photographExcluded() throws Exception {
        assertEquals("M°rganVale", name("M°rganVale", "y°u sing l°ve", true));
    }

    @Test
    public void realAccentUnchanged() throws Exception {
        assertEquals("Jörg Vale", name("Jörg Vale", "y°u sing l°ve", false));
    }
}
