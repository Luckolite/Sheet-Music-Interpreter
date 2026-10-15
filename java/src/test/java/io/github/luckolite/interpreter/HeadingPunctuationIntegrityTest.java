// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class HeadingPunctuationIntegrityTest {
    @org.junit.Test
    public void syntheticControls() throws Exception {
        main(new String[0]);
    }

    static int checks;

    static void check(String original, String next, boolean accept) {
        var line = new ScoreCreditsDetector.Line(original, 250, 100, 900, 180);
        var page = new ScoreCreditsDetector.Page(1000, 1400, -1, List.of(line), false, true);
        var result =
                ScoreCreditsDetector.locatedCoverHeadingReading(
                        page, line, List.of(next, next, next));
        if ((result != null) != accept)
            throw new AssertionError(original + " -> " + next + " expected acceptance " + accept);
        checks++;
    }

    public static void main(String[] args) {
        check("SWINGIN'", "SWINGIN!", false);
        check("SWINGIN’", "SWINGIN!", false);
        check("SWINGIN!", "SWINGIN'", false);
        check("SWINGIN' SONGS", "SWINGIN! SONGS", false);
        check("SWINGIN'", "SWINGIN’", true);
        check("SWINGIN!", "SWINGIN!", true);
        check("SWINGIN'", "SWINGIN'", true);
        check("HARBR SONGS", "HARBOR SONGS", true);
        check("HARBOR", "HARBOR!", true);
        check("HARBOR", "HARBOR'", true);
        System.out.println("Heading stroke controls passed: " + checks);
    }
}
