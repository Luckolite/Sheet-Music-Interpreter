// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;

/** Original procedural data. No book pixels, diagnostic IDs, or artificial Components. */
final class ProjectionCallerScene {
    static final int W = 480, H = 260;
    static final float GAP = 16, BASE = 70;
    static final MeasureRegion REGION = new MeasureRegion(.04f, .96f, .05f, .95f);
    final byte[] gray = new byte[W * H], labels = new byte[W * H];
    final List<ScoreNoteEvent> notes = new ArrayList<>();

    ProjectionCallerScene() {
        Arrays.fill(gray, (byte) 240);
        for (int k = 0; k < 5; k++)
            for (int y = 69 + k * 16; y <= 71 + k * 16; y++)
                for (int x = 20; x < W - 20; x++) ink(x, y, 4);
        ellipse(210, 78);
        ellipse(258, 94);
        for (int y = 78; y <= 134; y++) for (int x = 199; x <= 202; x++) ink(x, y, 1);
        for (int y = 94; y <= 142; y++) for (int x = 247; x <= 250; x++) ink(x, y, 1);
        for (int x = 199; x <= 250; x++) {
            double end = 134 + 8 * (x - 199) / 51.;
            for (int y = (int) Math.ceil(end - 7); y <= Math.floor(end); y++) ink(x, y, 3);
        }
        notes.add(note(210, 78));
        notes.add(note(258, 94));
    }

    void ink(int x, int y, int label) {
        gray[y * W + x] = 0;
        if (label >= 0 && labels[y * W + x] != 2) labels[y * W + x] = (byte) label;
    }

    void ellipse(int cx, int cy) {
        for (int y = cy - 8; y <= cy + 8; y++)
            for (int x = cx - 11; x <= cx + 11; x++)
                if ((x - cx) * (x - cx) / 121. + (y - cy) * (y - cy) / 64. <= 1) ink(x, y, 2);
    }

    void stroke(int ax, int ay, int bx, int by) {
        int n = Math.max(Math.abs(ax - bx), Math.abs(ay - by));
        for (int i = 0; i <= n; i++) {
            int x = Math.round(ax + (bx - ax) * i / (float) Math.max(1, n));
            int y = Math.round(ay + (by - ay) * i / (float) Math.max(1, n));
            ink(x, y, 5);
            ink(x + 1, y, 5);
        }
    }

    void branch() {
        stroke(208, 94, 215, 103);
        stroke(215, 103, 208, 110);
        stroke(208, 110, 214, 117);
        stroke(214, 117, 208, 124);
        stroke(208, 124, 215, 129);
    }

    void unrelated() {
        for (int y = 100; y <= 108; y++) for (int x = 290; x <= 293; x++) ink(x, y, 5);
    }

    static ScoreNoteEvent note(int x, int y) {
        return new ScoreNoteEvent(
                        0,
                        (x / (float) W - .04f) / .92f,
                        Math.round((134 - y) / 8f),
                        0,
                        1,
                        y / (float) H,
                        false,
                        0,
                        1,
                        2,
                        0)
                .withStemDirection(0);
    }

    List<ScoreNoteEvent> acceptedOwners() throws Exception {
        var origins = new IdentityHashMap<Object, RestFigureOrigins.HeadOrigin>();
        Method flood =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "findComponents",
                        byte[].class,
                        int.class,
                        int.class,
                        byte.class,
                        byte[].class,
                        IdentityHashMap.class);
        flood.setAccessible(true);
        flood.invoke(null, labels, W, H, (byte) 2, gray, origins);
        var map = new IdentityHashMap<ScoreNoteEvent, RestFigureOrigins.HeadOrigin>();
        for (var n : notes) {
            int x =
                    Math.round(
                            (REGION.left()
                                            + n.positionInMeasure()
                                                    * (REGION.right() - REGION.left()))
                                    * W);
            int y = Math.round(n.pageY() * H);
            for (var origin : origins.values())
                if (origin.contains(x, y)) {
                    if (map.put(n, origin) != null) throw new AssertionError("ambiguous test head");
                }
        }
        return RestFigureOrigins.bind(notes, map);
    }

    RestFigureOrigins.ProposalOrigin proposal(int max) {
        boolean[] mask = new boolean[48];
        for (int y = 90; y <= 137; y++)
            for (int k = 0; k < 5; k++) if (Math.abs(y - (70 + k * 16)) <= 1) mask[y - 90] = true;
        return new RestFigureOrigins.ProposalOrigin(
                gray, gray, W, H, 90, 137, mask, 197, 218, 90, max);
    }

    FiniteBeamQuarterOwnership.ProposalResult result(
            List<ScoreNoteEvent> owners,
            RestFigureOrigins.ProposalOrigin proposal,
            int min,
            int max) {
        return FiniteBeamQuarterOwnership.proposalOwnership(
                gray, W, H, REGION, owners, 0, 0, 1, 102f / H, GAP, 197, 218, min, max, proposal);
    }

    List<ScoreRestEvent> acquire(List<ScoreNoteEvent> owners) {
        return SixteenthRestDetector.detect(
                gray,
                W,
                H,
                List.of(REGION),
                List.of(new SixteenthRestDetector.Staff(BASE, BASE + 4 * GAP, GAP, 0, 1)),
                owners);
    }

    public static void main(String[] args) throws Exception {
        var p = new ProjectionCallerScene();
        var owners = p.acceptedOwners();
        System.out.println("newHelper=" + p.result(owners, p.proposal(137), 90, 137));
        System.out.println("projectionWithoutOrigins=" + p.acquire(p.notes));
        System.out.println("projectionWithOrigins=" + p.acquire(owners));
        p.branch();
        System.out.println("branchHelper=" + p.result(owners, p.proposal(137), 90, 137));
        System.out.println("branchProjection=" + p.acquire(owners));
    }
}
