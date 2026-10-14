// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class CreditBoundaryEchoTest {
    static int checks;

    static void check(boolean x, String n) {
        checks++;
        if (!x) throw new AssertionError(n);
    }

    static boolean fix(String before, List<String> readings, List<Float> confidence, boolean role) {
        var original = new ScoreCreditsDetector.Line(before, 1200, 233, 1726, 260);
        var lines = new ArrayList<ScoreCreditsDetector.Line>();
        lines.add(new ScoreCreditsDetector.Line("Harbor Lanterns", 700, 80, 1100, 150));
        if (role)
            lines.add(
                    new ScoreCreditsDetector.Line(
                            "Words and Music by CASEY REED,", 1200, 195, 1730, 225));
        lines.add(original);
        var page = new ScoreCreditsDetector.Page(1800, 2400, 400, lines, false, false);
        return !ScoreCreditsDetector.mergeCreditBoundaryEchoConsensus(
                        page, original, readings, confidence)
                .equals(page.lines());
    }

    @org.junit.Test
    public void originalBoundaryControls() throws Exception {
        String before = "ROWAN FINCH, MORGAN VALEe", after = "ROWAN FINCH, MORGAN VALE";
        var reads = List.of(after, after, after);
        var high = List.of(.99f, .99f, .99f);
        check(fix(before, reads, high, true), "expanded consensus repairs mixedcase echo");
        check(
                !fix("ROWAN FINCH, MORGAN VALEE", reads, high, true),
                "uppercase doubled letter protected");
        check(!fix("ROWAN FINCH, Morgan Valee", reads, high, true), "ordinary case protected");
        check(!fix(before, reads, List.of(.99f, .979f, .99f), true), "weak confidence protected");
        check(!fix(before, List.of(after, before, after), high, true), "disagreement protected");
        check(
                !fix(
                        before,
                        List.of(
                                "ROWAN FINCH, MORGAN VALE II",
                                "ROWAN FINCH, MORGAN VALE II",
                                "ROWAN FINCH, MORGAN VALE II"),
                        high,
                        true),
                "identifier changes rejected");
        check(!fix(before, reads, high, false), "unlabelled text protected");
        check(
                !fix(
                        before,
                        List.of(
                                "ROWAN FINCH, MORGAN VAIL",
                                "ROWAN FINCH, MORGAN VAIL",
                                "ROWAN FINCH, MORGAN VAIL"),
                        high,
                        true),
                "different spelling rejected");
        System.out.println(checks + " original controls passed");
    }
}
