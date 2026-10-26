// Copyright 2026 Luckolite and Music Sheets contributors.
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original full capture scene. No injected frame witness, fake producer, reflection, or score image. */
public final class UnboundPhaseVetoOriginals {
    static final int W = 1024, H = 480, GAP = 16, NOMINAL = 300, ACTUAL = 311, CENTRAL = 316;
    static int checks;

    static void check(boolean result, String description) {
        checks++;
        if (!result) throw new AssertionError(description);
    }

    static void rect(byte[] raw, int left, int right, int top, int bottom, int shade) {
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) raw[y * W + x] = (byte) shade;
    }

    static void head(byte[] raw, int cx, int cy) {
        for (int y = cy - 8; y <= cy + 8; y++)
            for (int x = cx - 12; x <= cx + 12; x++)
                if ((x - cx) * (x - cx) / (9.6 * 9.6) + (y - cy) * (y - cy) / (6.4 * 6.4) <= 1)
                    raw[y * W + x] = 0;
    }

    static final class Scene {
        final byte[] raw = new byte[W * H], labels = new byte[W * H];
        final ScorePageInterpretation score;
        final List<PlayingTechniqueDetector.Staff> staffs;

        Scene(boolean middle) {
            this(middle, CENTRAL);
        }

        Scene(boolean middle, int centralY) {
            Arrays.fill(raw, (byte) 245);
            for (int line = 0; line < 5; line++)
                rect(raw, 4, W - 5, ACTUAL - (4 - line) * GAP, ACTUAL - (4 - line) * GAP, 0);
            // Real inner-flank support:outer91/180, central81/180; threshold90.
            // The480px outer span remains within the finite strip maximum512.
            rect(raw, 376, 623, ACTUAL - 2 * GAP, ACTUAL - 2 * GAP, 245);
            for (int x : new int[] {260, 740}) {
                head(raw, x, ACTUAL);
                rect(raw, x + 10, x + 10, 200, ACTUAL, 0);
            }
            rect(raw, 270, 750, 200, 206, 0);
            if (middle) head(raw, 500, centralY);
            var notes =
                    List.of(
                            new ScoreNoteEvent(
                                    0, 260f / W, 0, 0, 1, ACTUAL / (float) H, false, 0, 1),
                            new ScoreNoteEvent(
                                    0, 500f / W, 0, 0, 1, centralY / (float) H, false, 0, 1),
                            new ScoreNoteEvent(
                                    0, 740f / W, 0, 0, 1, ACTUAL / (float) H, false, 0, 1));
            score = new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), notes);
            staffs =
                    List.of(
                            new PlayingTechniqueDetector.Staff(
                                    NOMINAL - 4 * GAP, NOMINAL, GAP, 0, 1));
        }

        PrintedBeamSource.Capture capture() {
            byte[] before = raw.clone(), oldLabels = labels.clone();
            var capture =
                    PrintedBeamSource.capture(
                            score,
                            labels,
                            raw,
                            W,
                            H,
                            staffs,
                            0,
                            8_000_000,
                            256_000_000,
                            64_000_000);
            check(Arrays.equals(before, raw), "raw unchanged");
            check(Arrays.equals(oldLabels, labels), "labels unchanged");
            return capture;
        }
    }

    static List<Integer> owners(ScoreBeamSource.Group group) {
        var owners = new ArrayList<Integer>();
        for (var column : group.columns())
            for (var anchor : column.members()) owners.add(anchor.noteIndex());
        return owners;
    }

    public static void main(String[] args) {
        // The new shorter-sample proof legitimately binds the original316 proposal.
        // Keep its physical-head veto, and independently retain genuinely unbound319.
        for (int centralY : new int[] {CENTRAL, 319})
            for (boolean middle : new boolean[] {false, true}) {
                var c = new Scene(middle, centralY).capture();
                boolean unbound = centralY == 319;
                check(c.statistics().refusal().isEmpty(), "source must not refuse");
                check(!c.statistics().flagRefused(), "flag work must not refuse");
                check(
                        c.statistics().bound() == (unbound ? 2 : 3),
                        "actual recovered or unbound proposal count");
                check(
                        "unbound".equals(c.diagnostics().get(1).get("status")) == unbound,
                        "physical missing-head and binding roles remain distinct");
                check(c.statistics().shaftOwners() == 2, "only original outer shafts owned");
                check(
                        c.source().detachedColumns().isEmpty(),
                        "context must not fabricate detached flags");
                if (!middle) {
                    check(
                            c.source().groups().size() == 1,
                            "no physical interior head retains outer beam");
                    check(
                            owners(c.source().groups().get(0)).equals(List.of(0, 2)),
                            "only outer notes own beam");
                } else
                    check(
                            c.source().groups().isEmpty(),
                            "physical interior head vetoes beam whether bound or unbound");
            }
        System.out.println("PASS " + checks + " original bound/unbound phase-veto assertions");
    }
}
