// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original fictional metadata and layout regressions. */
public class ConsolidatedCreditLayoutTest {
    @Test
    public void case1() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Viola & Piano",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(java.util.Set.of(), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case2() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "for Cello and Harp, Op. 31",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(java.util.Set.of(), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case3() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Op. 14, Act IV, No. 7",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(java.util.Set.of(), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case4() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "ZX 4:3; Op. 17, No. 2",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(java.util.Set.of(), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case5() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Vocal Parts",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(java.util.Set.of(), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case6() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Theme from the Series Harbor Tales",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(java.util.Set.of(), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case7() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Music from Winter Harbor",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(java.util.Set.of(), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case8() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "(from Harbor Chronicles)",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(java.util.Set.of(), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case9() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Performed by Viola & Piano",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("Viola & Piano", r.artist());
    }

    @Test
    public void case10() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Performed by for Cello and Harp, Op. 31",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("for Cello and Harp, Op. 31", r.artist());
    }

    @Test
    public void case11() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Performed by Op. 14, Act IV, No. 7",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("Op. 14, Act IV, No. 7", r.artist());
    }

    @Test
    public void case12() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Performed by ZX 4:3; Op. 17, No. 2",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("ZX 4:3; Op. 17, No. 2", r.artist());
    }

    @Test
    public void case13() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Performed by Vocal Parts",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("Vocal Parts", r.artist());
    }

    @Test
    public void case14() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Performed by Theme from the Series Harbor Tales",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("Theme from the Series Harbor Tales", r.artist());
    }

    @Test
    public void case15() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Performed by Music from Winter Harbor",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("Music from Winter Harbor", r.artist());
    }

    @Test
    public void case16() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Performed by (from Harbor Chronicles)",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("(from Harbor Chronicles)", r.artist());
    }

    @Test
    public void case17() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Morgan Vale",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(
                java.util.Set.of("Morgan Vale"), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case18() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Opal Rivers",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(
                java.util.Set.of("Opal Rivers"), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case19() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Act Three",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(
                java.util.Set.of("Act Three"), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case20() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        290.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Viola Reed",
                                                        550.0f,
                                                        175.0f,
                                                        1140.0f,
                                                        197.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(
                java.util.Set.of("Viola Reed"), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case21() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        400.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Muffled with distortion",
                                                        100.0f,
                                                        300.0f,
                                                        300.0f,
                                                        322.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(java.util.Set.of(), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case22() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        400.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Performed by Muffled with distortion",
                                                        100.0f,
                                                        300.0f,
                                                        300.0f,
                                                        322.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("Muffled with distortion", r.artist());
    }

    @Test
    public void case23() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        400.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Muffled with distortion",
                                                        850.0f,
                                                        300.0f,
                                                        1050.0f,
                                                        322.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(
                java.util.Set.of("Muffled with distortion"),
                new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case24() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        -1.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Muffled with distortion",
                                                        100.0f,
                                                        300.0f,
                                                        300.0f,
                                                        322.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(
                java.util.Set.of("Muffled with distortion"),
                new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case25() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        400.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Count three beats if",
                                                        100.0f,
                                                        300.0f,
                                                        300.0f,
                                                        322.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(java.util.Set.of(), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case26() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        400.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Performed by Count three beats if",
                                                        100.0f,
                                                        300.0f,
                                                        300.0f,
                                                        322.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("Count three beats if", r.artist());
    }

    @Test
    public void case27() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        400.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Count three beats if",
                                                        850.0f,
                                                        300.0f,
                                                        1050.0f,
                                                        322.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(
                java.util.Set.of("Count three beats if"),
                new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case28() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        -1.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Count three beats if",
                                                        100.0f,
                                                        300.0f,
                                                        300.0f,
                                                        322.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(
                java.util.Set.of("Count three beats if"),
                new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case29() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        400.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Solo - 2nd time, All - 3rd time",
                                                        100.0f,
                                                        300.0f,
                                                        300.0f,
                                                        322.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(java.util.Set.of(), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case30() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        400.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Performed by Solo - 2nd time, All - 3rd time",
                                                        100.0f,
                                                        300.0f,
                                                        300.0f,
                                                        322.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("Solo - 2nd time; All - 3rd time", r.artist());
    }

    @Test
    public void case31() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        400.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Solo - 2nd time, All - 3rd time",
                                                        850.0f,
                                                        300.0f,
                                                        1050.0f,
                                                        322.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(
                java.util.Set.of("Solo - 2nd time, All - 3rd time"),
                new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case32() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        -1.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Solo - 2nd time, All - 3rd time",
                                                        100.0f,
                                                        300.0f,
                                                        300.0f,
                                                        322.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(
                java.util.Set.of("Solo - 2nd time, All - 3rd time"),
                new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case33() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        -1.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Vln. II", 80.0f, 300.0f, 180.0f, 322.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Vln. II", 80.0f, 750.0f, 180.0f, 772.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(java.util.Set.of(), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case34() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        -1.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Vln. II", 80.0f, 300.0f, 180.0f, 322.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(java.util.Set.of("Vln. II"), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case35() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        -1.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Vln. II", 850.0f, 300.0f, 950.0f, 322.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Vln. II", 850.0f, 750.0f, 950.0f, 772.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(java.util.Set.of("Vln. II"), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case36() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        -1.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Fl. I", 80.0f, 300.0f, 180.0f, 322.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Fl. I", 80.0f, 750.0f, 180.0f, 772.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(java.util.Set.of(), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case37() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        -1.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Fl. I", 80.0f, 300.0f, 180.0f, 322.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(java.util.Set.of("Fl. I"), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case38() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        -1.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Fl. I", 850.0f, 300.0f, 950.0f, 322.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Fl. I", 850.0f, 750.0f, 950.0f, 772.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(java.util.Set.of("Fl. I"), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case39() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        -1.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Morgan Vale",
                                                        80.0f,
                                                        300.0f,
                                                        180.0f,
                                                        322.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Morgan Vale",
                                                        80.0f,
                                                        750.0f,
                                                        180.0f,
                                                        772.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals(
                java.util.Set.of("Morgan Vale"), new java.util.HashSet<>(r.unclassifiedCredits()));
    }

    @Test
    public void case40() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1200.0f,
                                        1700.0f,
                                        -1.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        350.0f,
                                                        90.0f,
                                                        850.0f,
                                                        145.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Performed by Vln. II",
                                                        80.0f,
                                                        300.0f,
                                                        180.0f,
                                                        322.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Performed by Vln. II",
                                                        80.0f,
                                                        750.0f,
                                                        180.0f,
                                                        772.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("Vln. II", r.artist());
    }

    @Test
    public void case41() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1800.0f,
                                        2547.0f,
                                        350.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        630.0f,
                                                        80.0f,
                                                        1170.0f,
                                                        150.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Morgan Vale",
                                                        85.0f,
                                                        205.0f,
                                                        270.0f,
                                                        237.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Arranged by Rowan Finch",
                                                        1380.0f,
                                                        200.0f,
                                                        1730.0f,
                                                        238.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("Morgan Vale", r.composer());
    }

    @Test
    public void case42() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1800.0f,
                                        2547.0f,
                                        350.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        630.0f,
                                                        80.0f,
                                                        1170.0f,
                                                        150.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Morgan Vale",
                                                        85.0f,
                                                        205.0f,
                                                        270.0f,
                                                        237.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Arranged by Rowan Finch",
                                                        1380.0f,
                                                        200.0f,
                                                        1730.0f,
                                                        238.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Casey Reed",
                                                        1410.0f,
                                                        155.0f,
                                                        1720.0f,
                                                        185.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("Casey Reed", r.composer());
    }

    @Test
    public void case43() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1800.0f,
                                        2547.0f,
                                        350.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        630.0f,
                                                        80.0f,
                                                        1170.0f,
                                                        150.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Morgan Vale",
                                                        85.0f,
                                                        205.0f,
                                                        270.0f,
                                                        237.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Arranged by Rowan Finch",
                                                        1380.0f,
                                                        200.0f,
                                                        1730.0f,
                                                        238.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Music by Casey Reed",
                                                        1320.0f,
                                                        155.0f,
                                                        1720.0f,
                                                        185.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("Casey Reed", r.composer());
    }

    @Test
    public void case44() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1800.0f,
                                        2547.0f,
                                        350.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        630.0f,
                                                        80.0f,
                                                        1170.0f,
                                                        150.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Waltz", 85.0f, 205.0f, 270.0f, 237.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Arranged by Rowan Finch",
                                                        1380.0f,
                                                        200.0f,
                                                        1730.0f,
                                                        238.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("", r.composer());
    }

    @Test
    public void case45() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1800.0f,
                                        2547.0f,
                                        350.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        630.0f,
                                                        80.0f,
                                                        1170.0f,
                                                        150.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Morgan Vale",
                                                        85.0f,
                                                        205.0f,
                                                        270.0f,
                                                        237.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Transcribed by Rowan Finch",
                                                        1380.0f,
                                                        200.0f,
                                                        1730.0f,
                                                        238.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("", r.composer());
    }

    @Test
    public void case46() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1800.0f,
                                        2547.0f,
                                        350.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        630.0f,
                                                        80.0f,
                                                        1170.0f,
                                                        150.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Morgan Vale",
                                                        85.0f,
                                                        205.0f,
                                                        270.0f,
                                                        237.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Arranged by Rowan Finch",
                                                        1380.0f,
                                                        280.0f,
                                                        1730.0f,
                                                        315.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("", r.composer());
    }

    @Test
    public void case47() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1800.0f,
                                        2547.0f,
                                        350.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        630.0f,
                                                        80.0f,
                                                        1170.0f,
                                                        150.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Morgan Vale",
                                                        85.0f,
                                                        205.0f,
                                                        270.0f,
                                                        237.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "and the adaptation by Rowan Finch)",
                                                        1380.0f,
                                                        200.0f,
                                                        1730.0f,
                                                        238.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("Morgan Vale", r.composer());
    }

    @Test
    public void case48() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1800.0f,
                                        2547.0f,
                                        350.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        630.0f,
                                                        80.0f,
                                                        1170.0f,
                                                        150.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Morgan Vale",
                                                        85.0f,
                                                        205.0f,
                                                        270.0f,
                                                        237.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "and the adaptation by Rowan Finch)",
                                                        1380.0f,
                                                        200.0f,
                                                        1730.0f,
                                                        238.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Casey Reed",
                                                        1410.0f,
                                                        155.0f,
                                                        1720.0f,
                                                        185.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertFalse(r.composer().contains("Morgan Vale"));
    }

    @Test
    public void case49() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1800.0f,
                                        2547.0f,
                                        350.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        630.0f,
                                                        80.0f,
                                                        1170.0f,
                                                        150.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Morgan Vale",
                                                        85.0f,
                                                        205.0f,
                                                        270.0f,
                                                        237.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "and the adaptation by Rowan Finch)",
                                                        1380.0f,
                                                        200.0f,
                                                        1730.0f,
                                                        238.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Music by Casey Reed",
                                                        1320.0f,
                                                        155.0f,
                                                        1720.0f,
                                                        185.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("Casey Reed", r.composer());
    }

    @Test
    public void case50() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1800.0f,
                                        2547.0f,
                                        350.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        630.0f,
                                                        80.0f,
                                                        1170.0f,
                                                        150.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Waltz", 85.0f, 205.0f, 270.0f, 237.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "and the adaptation by Rowan Finch)",
                                                        1380.0f,
                                                        200.0f,
                                                        1730.0f,
                                                        238.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("", r.composer());
    }

    @Test
    public void case51() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1800.0f,
                                        2547.0f,
                                        350.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        630.0f,
                                                        80.0f,
                                                        1170.0f,
                                                        150.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Morgan Vale",
                                                        85.0f,
                                                        205.0f,
                                                        270.0f,
                                                        237.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Transcribed by Rowan Finch",
                                                        1380.0f,
                                                        200.0f,
                                                        1730.0f,
                                                        238.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("", r.composer());
    }

    @Test
    public void case52() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Lanterns",
                        java.util.List.of(
                                new ScoreCreditsDetector.Page(
                                        1800.0f,
                                        2547.0f,
                                        350.0f,
                                        java.util.List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "Harbor Lanterns",
                                                        630.0f,
                                                        80.0f,
                                                        1170.0f,
                                                        150.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Morgan Vale",
                                                        85.0f,
                                                        205.0f,
                                                        270.0f,
                                                        237.0f),
                                                new ScoreCreditsDetector.Line(
                                                        "Arranged by Rowan Finch",
                                                        1380.0f,
                                                        280.0f,
                                                        1730.0f,
                                                        315.0f)),
                                        false,
                                        false)),
                        java.util.Set.of());
        assertEquals("", r.composer());
    }
}
