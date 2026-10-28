// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class BowTipOwnershipTest {
    static final int W = 500, H = 360;

    static byte[] scene(int kind) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = im.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D p = new Path2D.Double();
        if (kind == 0 || kind == 3) {
            p.moveTo(234, 79);
            p.lineTo(242, 124);
            p.lineTo(266, 79);
            if (kind == 3) {
                p.moveTo(253, 103);
                p.lineTo(246, 96);
            }
        }
        if (kind == 1) {
            p.moveTo(234, 124);
            p.lineTo(234, 79);
            p.lineTo(266, 79);
            p.lineTo(266, 124);
        }
        if (kind == 2) {
            p.moveTo(234, 79);
            p.curveTo(234, 104, 260, 104, 260, 79);
            p.lineTo(257, 124);
        }
        if (kind == 4) {
            g.setFont(new Font("Serif", Font.ITALIC, 54));
            g.drawString("sfz", 208, 124);
        }
        g.draw(p);
        g.dispose();
        byte[] out = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) out[y * W + x] = (byte) im.getRaster().getSample(x, y, 0);
        return out;
    }

    static ScoreNoteEvent event(float position) {
        return new ScoreNoteEvent(
                0,
                position,
                4,
                0,
                1,
                .5f,
                false,
                0,
                0,
                2,
                1,
                1,
                0,
                0,
                30,
                false,
                0,
                false,
                0,
                0,
                1,
                1,
                ScoreNoteEvent.Kind.PITCHED);
    }

    static void check(String name, int shape, float[] xs, float[] attacks, int expected) {
        byte[] gray = scene(shape),
                labels = new byte[W * H],
                before = gray.clone(),
                semantic = labels.clone();
        var notes = new ArrayList<NoteArticulationDetector.Anchor>();
        var events = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < xs.length; i++) {
            notes.add(new NoteArticulationDetector.Anchor(xs[i], 180, 20, 0));
            events.add(event(attacks[i]));
        }
        var result =
                NoteArticulationDetector.bowMarks(
                        labels,
                        gray,
                        W,
                        H,
                        notes,
                        events,
                        List.of(new PlayingTechniqueDetector.Staff(160, 240, 20, 0, 1)));
        assertEquals(name, expected, result.size());
        assertArrayEquals(before, gray);
        assertArrayEquals(semantic, labels);
        for (var mark : result) {
            assertEquals(
                    shape == 1 ? ScoreTechniqueChange.DOWN_BOW : ScoreTechniqueChange.UP_BOW,
                    mark.technique());
            assertEquals(attacks[0], events.get(mark.owner()).positionInMeasure(), 0f);
        }
    }

    @Test
    public void actualSkewTipNearCenterFar() {
        check("actual-skew-tip-near-center-far", 0, new float[] {231}, new float[] {.4f}, 1);
    }

    @Test
    public void sameExactAttackTwoHeads() {
        check("same-exact-attack-two-heads", 0, new float[] {231, 250}, new float[] {.4f, .4f}, 1);
    }

    @Test
    public void legacyOwnerPlusDistinctTipOwnerAbstains() {
        check(
                "legacy-owner-plus-distinct-tip-owner-abstains",
                0,
                new float[] {250, 231},
                new float[] {.4f, .6f},
                0);
    }

    @Test
    public void twoTipOwnersAbstain() {
        check("two-tip-owners-abstain", 0, new float[] {229, 231}, new float[] {.4f, .6f}, 0);
    }

    @Test
    public void distantHeadOutsideFootprint() {
        check("distant-head-outside-footprint", 0, new float[] {180}, new float[] {.4f}, 0);
    }

    @Test
    public void headInsideSpanButOutsideBothTolerances() {
        check(
                "head-inside-span-but-outside-both-tolerances",
                0,
                new float[] {267},
                new float[] {.4f},
                0);
    }

    @Test
    public void downLegacyOwnerIgnoresDistinctTipOnlyPeer() {
        check(
                "down-legacy-owner-ignores-distinct-tip-only-peer",
                1,
                new float[] {250, 231},
                new float[] {.4f, .6f},
                1);
    }

    @Test
    public void centerOwnerPreserved() {
        check("center-owner-preserved", 0, new float[] {250}, new float[] {.4f}, 1);
    }

    @Test
    public void downDoesNotUseTipOnlyPeer() {
        check("down-does-not-use-tip-only-peer", 1, new float[] {231}, new float[] {.4f}, 0);
    }

    @Test
    public void foreignConnectedSpur() {
        check("foreign-connected-spur", 3, new float[] {231}, new float[] {.4f}, 0);
    }

    @Test
    public void handwrittenFour() {
        check("handwritten-four", 2, new float[] {250}, new float[] {.4f}, 0);
    }

    @Test
    public void sfzText() {
        check("sfz-text", 4, new float[] {250}, new float[] {.4f}, 0);
    }
}
