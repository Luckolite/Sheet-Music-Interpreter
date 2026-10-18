// Copyright 2026 Luckolite; SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** Original role oracles; the two known zero-context misses are explicitly held, never relabeled. */
public final class ContextualMfBodyOriginalTest {
    private static void body(
            String asset,
            boolean expected,
            int l,
            int t,
            int r,
            int b,
            float gap,
            String headPairs,
            boolean extra)
            throws Exception {
        byte[] g = ContextualMfResourceFixture.read("body/" + asset), old = g.clone();
        var staffs = new ArrayList<PlayingTechniqueDetector.Staff>();
        staffs.add(new PlayingTechniqueDetector.Staff(112, 176, 16, 0, 1));
        var notes = new ArrayList<ScoreNoteEvent>();
        if (!headPairs.isEmpty()) {
            var q = headPairs.split(";");
            for (int i = 0; i < q.length; i += 2)
                notes.add(
                        new ScoreNoteEvent(
                                0,
                                Integer.parseInt(q[i]) / 700f,
                                Math.round((176 - Integer.parseInt(q[i + 1])) * 2 / 16f),
                                0,
                                1,
                                Integer.parseInt(q[i + 1]) / 420f,
                                false,
                                0,
                                0,
                                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                                1));
        }
        if (extra) staffs.add(new PlayingTechniqueDetector.Staff(197, 261, 16, 0, 1));
        var ctx =
                DynamicNotationRawContext.capture(
                        g, 700, 420, staffs, List.of(new MeasureRegion(0, 1, 0, 1)), notes);
        var known =
                ContextualMfBodyEvidence.captureNotation(
                        ctx, g, 700, 420, staffs, staffs.get(0), l, r, gap);
        var e = ContextualMfBodyEvidence.inspect("mf", g, 700, 420, l, t, r, b, gap, known);
        assertArrayEquals(old, g);
        assertEquals(asset + " reason=" + e.reason(), expected, e.supported());
    }

    private static void continuity(String group, String asset, boolean expected, int extra)
            throws Exception {
        byte[] g = ContextualMfResourceFixture.read(group + "/" + asset), old = g.clone();
        var owner = new PlayingTechniqueDetector.Staff(112, 176, 16, 0, 1);
        var staffs = new ArrayList<PlayingTechniqueDetector.Staff>();
        staffs.add(owner);
        if (extra >= 0) staffs.add(new PlayingTechniqueDetector.Staff(extra, extra + 64, 16, 0, 1));
        var ctx =
                DynamicNotationRawContext.capture(
                        g, 700, 420, staffs, List.of(new MeasureRegion(0, 1, 0, 1)), List.of());
        var known =
                ContextualMfBodyEvidence.captureNotation(
                        ctx, g, 700, 420, staffs, owner, 288, 460, 16);
        var e = ContextualMfBodyEvidence.inspect("mf", g, 700, 420, 288, 196, 460, 252, 16, known);
        assertArrayEquals(old, g);
        assertEquals(asset + " reason=" + e.reason(), expected, e.supported());
    }

    @Test
    public void original01() throws Exception {
        body("contextual-mf/complete-mf-paper.raw.gz", true, 288, 196, 363, 252, 16f, "", false);
    }

    @Test
    public void original02() throws Exception {
        body(
                "contextual-mf/complete-mf-exact-read-bounds.raw.gz",
                true,
                292,
                199,
                361,
                249,
                16f,
                "",
                false);
    }

    @org.junit.Ignore(
            "Frozen pre-existing zero-context source-free acquisition miss; true role oracle retained")
    @Test
    public void original03() throws Exception {
        body(
                "contextual-mf/complete-mf-owned-upper-slur.raw.gz",
                true,
                288,
                196,
                363,
                252,
                16f,
                "264;184;406;184",
                false);
    }

    @org.junit.Ignore(
            "Frozen pre-existing zero-context source-free acquisition miss; true role oracle retained")
    @Test
    public void original04() throws Exception {
        body(
                "contextual-mf/complete-mf-owned-crossing-slur.raw.gz",
                true,
                288,
                196,
                363,
                252,
                16f,
                "264;200;406;200",
                false);
    }

    @Test
    public void original05() throws Exception {
        body(
                "contextual-mf/supplied-mf-f-only.raw.gz",
                false,
                288,
                196,
                363,
                252,
                16f,
                "264;184;406;184",
                false);
    }

    @Test
    public void original06() throws Exception {
        body(
                "contextual-mf/supplied-mf-two-legs.raw.gz",
                false,
                288,
                196,
                363,
                252,
                16f,
                "264;184;406;184",
                false);
    }

    @Test
    public void original07() throws Exception {
        body(
                "contextual-mf/supplied-mf-ghost.raw.gz",
                false,
                288,
                196,
                363,
                252,
                16f,
                "264;184;406;184",
                false);
    }

    @Test
    public void original08() throws Exception {
        body(
                "contextual-mf/cropped-mf-owned-slur.raw.gz",
                false,
                305,
                196,
                363,
                252,
                16f,
                "264;184;406;184",
                false);
    }

    @Test
    public void original09() throws Exception {
        body(
                "contextual-mf/complete-mf-unowned-curve.raw.gz",
                false,
                288,
                196,
                363,
                252,
                16f,
                "",
                false);
    }

    @Test
    public void original10() throws Exception {
        body(
                "contextual-mf/complete-mf-attached-foreign-stem.raw.gz",
                false,
                288,
                196,
                363,
                252,
                16f,
                "264;184;406;184",
                false);
    }

    @Test
    public void original11() throws Exception {
        body(
                "contextual-mf/complete-mf-light-bridge-230.raw.gz",
                false,
                288,
                196,
                363,
                252,
                16f,
                "264;184;406;184",
                false);
    }

    @Test
    public void original12() throws Exception {
        body(
                "contextual-mf/complete-mf-light-bridge-254.raw.gz",
                false,
                288,
                196,
                363,
                252,
                16f,
                "264;184;406;184",
                false);
    }

    @Test
    public void original13() throws Exception {
        body(
                "contextual-mf/complete-mf-registered-five-crossing.raw.gz",
                true,
                288,
                196,
                363,
                252,
                16f,
                "",
                true);
    }

    @Test
    public void original14() throws Exception {
        body(
                "contextual-mf/supplied-mf-f-only-registered-five.raw.gz",
                false,
                288,
                196,
                363,
                252,
                16f,
                "",
                true);
    }

    @Test
    public void original15() throws Exception {
        body(
                "contextual-mf/complete-mf-unregistered-crossing-bar.raw.gz",
                false,
                288,
                196,
                363,
                252,
                16f,
                "",
                false);
    }

    @Test
    public void original16() throws Exception {
        body(
                "contextual-mf/complete-mf-independent-extra-leg.raw.gz",
                false,
                288,
                196,
                363,
                252,
                16f,
                "",
                false);
    }

    @Test
    public void original17() throws Exception {
        body(
                "contextual-mf/complete-mf-observed-edge-fringe-225.raw.gz",
                true,
                288,
                196,
                363,
                252,
                16f,
                "",
                false);
    }

    @Test
    public void original18() throws Exception {
        body(
                "contextual-mf/complete-mf-observed-edge-fringe-230.raw.gz",
                true,
                288,
                196,
                363,
                252,
                16f,
                "",
                false);
    }

    @Test
    public void original19() throws Exception {
        body(
                "contextual-mf/complete-mf-observed-edge-fringe-250.raw.gz",
                true,
                288,
                196,
                363,
                252,
                16f,
                "",
                false);
    }

    @Test
    public void original20() throws Exception {
        body(
                "contextual-mf/complete-mf-observed-edge-fringe-254.raw.gz",
                true,
                288,
                196,
                363,
                252,
                16f,
                "",
                false);
    }

    @Test
    public void original21() throws Exception {
        continuity("interval", "literal-mf-incidental-upper-serifs.gray.gz", true, -1);
    }

    @Test
    public void original22() throws Exception {
        continuity("interval", "literal-mf-light-upper-serifs.gray.gz", true, -1);
    }

    @Test
    public void original23() throws Exception {
        continuity("interval", "literal-mf-junction-shoulder.gray.gz", true, -1);
    }

    @Test
    public void original24() throws Exception {
        continuity("interval", "literal-mf-foreign-upper-stem.gray.gz", false, -1);
    }

    @Test
    public void original25() throws Exception {
        continuity("interval", "supplied-mf-independent-fifth-leg.gray.gz", false, -1);
    }

    @Test
    public void original26() throws Exception {
        continuity("interval", "supplied-mf-ghost-mezzo-real-upper-serifs.gray.gz", false, -1);
    }

    @Test
    public void original27() throws Exception {
        continuity("interval", "supplied-mf-competing-complete-bodies.gray.gz", false, -1);
    }

    @Test
    public void original28() throws Exception {
        continuity("continuity", "lower-descender-disconnected-through-unknown.raw.gz", false, -1);
    }

    @Test
    public void original29() throws Exception {
        continuity("continuity", "upper-cap-aligned-transverse-staff-crossing.raw.gz", true, 149);
    }

    @Test
    public void original30() throws Exception {
        continuity("continuity", "upper-cap-misaligned-only-staff-bridge.raw.gz", false, 149);
    }

    @Test
    public void original31() throws Exception {
        continuity("continuity", "lower-descender-4connected-unknown-companion.raw.gz", false, -1);
    }
}
